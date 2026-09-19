#include <linux/cred.h>
#include <linux/errno.h>
#include <linux/gfp.h>
#include <linux/jiffies.h>
#include <linux/ktime.h>
#include <linux/list.h>
#include <linux/mutex.h>
#include <linux/pid.h>
#include <linux/sched.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/wait.h>

#include "klog.h"
#include "policy/allowlist.h"
#include "policy/feature.h"
#include "policy/su_request.h"
#include "uapi/su_request.h"

#define SU_REQUEST_MAX_PENDING 16
#define SU_REQUEST_TIMEOUT_DEFAULT_SECS 30ULL
#define SU_REQUEST_TIMEOUT_MAX_SECS 120ULL

struct su_request {
	struct list_head list;
	__u64 id;
	__u32 uid;
	__u32 euid;
	__u32 pid;
	char comm[KSU_SU_REQUEST_COMM_LEN];
	__u64 ts_ns;
	bool answered;
	__u32 decision;
	__u32 remember;
	wait_queue_head_t wait;
};

static DEFINE_MUTEX(su_req_lock);
static LIST_HEAD(su_req_list);
static __u64 su_req_next_id = 1;
static DECLARE_WAIT_QUEUE_HEAD(su_req_poll_wait);

static bool su_prompt_enabled __read_mostly = false;
static __u64 su_prompt_timeout_secs __read_mostly = SU_REQUEST_TIMEOUT_DEFAULT_SECS;
static bool su_prompt_default_allow __read_mostly = false;

bool ksu_su_prompt_is_enabled(void)
{
	return READ_ONCE(su_prompt_enabled);
}

static int su_prompt_feature_get(u64 *value)
{
	*value = ksu_su_prompt_is_enabled() ? 1 : 0;
	return 0;
}

static int su_prompt_feature_set(u64 value)
{
	WRITE_ONCE(su_prompt_enabled, value != 0);
	pr_info("su_request: prompt %d\n", value != 0);
	return 0;
}

static int su_prompt_timeout_get(u64 *value)
{
	*value = READ_ONCE(su_prompt_timeout_secs);
	return 0;
}

static int su_prompt_timeout_set(u64 value)
{
	if (value == 0 || value > SU_REQUEST_TIMEOUT_MAX_SECS)
		return -EINVAL;
	WRITE_ONCE(su_prompt_timeout_secs, value);
	return 0;
}

static int su_prompt_default_get(u64 *value)
{
	*value = READ_ONCE(su_prompt_default_allow) ? 1 : 0;
	return 0;
}

static int su_prompt_default_set(u64 value)
{
	WRITE_ONCE(su_prompt_default_allow, value != 0);
	return 0;
}

static const struct ksu_feature_handler su_prompt_handler = {
	.feature_id = KSU_FEATURE_SU_PROMPT,
	.name = "su_prompt",
	.get_handler = su_prompt_feature_get,
	.set_handler = su_prompt_feature_set,
};

static const struct ksu_feature_handler su_prompt_timeout_handler = {
	.feature_id = KSU_FEATURE_SU_PROMPT_TIMEOUT,
	.name = "su_prompt_timeout",
	.get_handler = su_prompt_timeout_get,
	.set_handler = su_prompt_timeout_set,
};

static const struct ksu_feature_handler su_prompt_default_handler = {
	.feature_id = KSU_FEATURE_SU_PROMPT_DEFAULT_ALLOW,
	.name = "su_prompt_default_allow",
	.get_handler = su_prompt_default_get,
	.set_handler = su_prompt_default_set,
};

static struct su_request *find_request_locked(__u64 id)
{
	struct su_request *req;
	list_for_each_entry(req, &su_req_list, list) {
		if (req->id == id)
			return req;
	}
	return NULL;
}

static int pending_count_locked(void)
{
	struct su_request *req;
	int n = 0;
	list_for_each_entry(req, &su_req_list, list)
		n++;
	return n;
}

int ksu_su_request_poll(struct ksu_su_request_info *out)
{
	struct su_request *req;
	int ret = -EAGAIN;

	if (!out)
		return -EINVAL;

	mutex_lock(&su_req_lock);
	list_for_each_entry(req, &su_req_list, list) {
		if (!req->answered) {
			out->id = req->id;
			out->uid = req->uid;
			out->euid = req->euid;
			out->pid = req->pid;
			memcpy(out->comm, req->comm, sizeof(out->comm));
			out->ts_ns = req->ts_ns;
			ret = 0;
			break;
		}
	}
	mutex_unlock(&su_req_lock);
	return ret;
}

int ksu_su_request_answer(__u64 id, __u32 decision, __u32 remember)
{
	struct su_request *req;
	int ret = -ENOENT;

	if (decision != KSU_SU_REQUEST_DENY && decision != KSU_SU_REQUEST_ALLOW)
		return -EINVAL;

	mutex_lock(&su_req_lock);
	req = find_request_locked(id);
	if (req && !req->answered) {
		req->decision = decision;
		req->remember = remember ? 1 : 0;
		req->answered = true;
		wake_up(&req->wait);
		ret = 0;
	}
	mutex_unlock(&su_req_lock);
	return ret;
}

int ksu_su_request_prompt(__u32 uid, __u32 euid)
{
	struct su_request *req;
	long timeout_jiffies;
	long waited;
	bool allow;
	__u32 decision;
	__u32 remember;

	if (!ksu_su_prompt_is_enabled())
		return 0;

	/* Already allowed races: fast-path grant without queueing. */
	if (ksu_is_allow_uid(uid))
		return 1;

	mutex_lock(&su_req_lock);
	/* Coalesce: if same uid already has a pending request, wait on it. */
	{
		struct su_request *iter;
		list_for_each_entry(iter, &su_req_list, list) {
			if (!iter->answered && iter->uid == uid) {
				req = iter;
				/* Hold a ref by keeping it in list; wait below without lock. */
				goto wait_existing;
			}
		}
	}

	if (pending_count_locked() >= SU_REQUEST_MAX_PENDING) {
		mutex_unlock(&su_req_lock);
		pr_warn_ratelimited("su_request: queue full, deny uid=%u\n", uid);
		return 0;
	}

	req = kzalloc(sizeof(*req), GFP_KERNEL);
	if (!req) {
		mutex_unlock(&su_req_lock);
		return 0;
	}
	req->id = su_req_next_id++;
	if (su_req_next_id == 0)
		su_req_next_id = 1;
	req->uid = uid;
	req->euid = euid;
	req->pid = task_pid_nr(current);
	get_task_comm(req->comm, current);
	req->ts_ns = ktime_get_boottime_ns();
	req->answered = false;
	req->decision = KSU_SU_REQUEST_DENY;
	init_waitqueue_head(&req->wait);
	list_add_tail(&req->list, &su_req_list);
	wake_up(&su_req_poll_wait);
	mutex_unlock(&su_req_lock);

	pr_info("su_request: queued id=%llu uid=%u pid=%u comm=%s\n",
		req->id, uid, req->pid, req->comm);

	mutex_lock(&su_req_lock);
wait_existing:
	timeout_jiffies = msecs_to_jiffies((unsigned int)(READ_ONCE(su_prompt_timeout_secs) * 1000ULL));
	mutex_unlock(&su_req_lock);

	waited = wait_event_interruptible_timeout(req->wait, READ_ONCE(req->answered), timeout_jiffies);

	mutex_lock(&su_req_lock);
	/* Re-find: it is still in list (requester frees it). */
	{
		struct su_request *live = find_request_locked(req->id);
		if (!live) {
			mutex_unlock(&su_req_lock);
			return 0;
		}
		req = live;
		allow = req->answered && req->decision == KSU_SU_REQUEST_ALLOW;
		decision = req->decision;
		remember = req->remember;
		list_del(&req->list);
	}
	mutex_unlock(&su_req_lock);

	if (!waited)
		pr_info("su_request: timeout id=%llu uid=%u default=%s\n",
			req->id, uid, READ_ONCE(su_prompt_default_allow) ? "allow" : "deny");

	if (!req->answered)
		allow = READ_ONCE(su_prompt_default_allow);

	/* Persistence (remember=1) is applied by the manager via SET_APP_PROFILE
	 * after it sees ALLOW; kernel only grants this session here. */
	(void)decision;
	(void)remember;

	{
		bool do_allow = allow;
		__u64 rid = req->id;
		kfree(req);
		pr_info("su_request: id=%llu uid=%u -> %s\n", rid, uid, do_allow ? "ALLOW" : "DENY");
		return do_allow ? 1 : 0;
	}
}

void __init ksu_su_request_init(void)
{
	int ret;

	ret = ksu_register_feature_handler(&su_prompt_handler);
	if (ret)
		pr_err("su_request: register prompt handler failed: %d\n", ret);
	ret = ksu_register_feature_handler(&su_prompt_timeout_handler);
	if (ret)
		pr_err("su_request: register timeout handler failed: %d\n", ret);
	ret = ksu_register_feature_handler(&su_prompt_default_handler);
	if (ret)
		pr_err("su_request: register default handler failed: %d\n", ret);
	pr_info("su_request: initialized\n");
}

void __exit ksu_su_request_exit(void)
{
	struct su_request *req, *tmp;

	ksu_unregister_feature_handler(KSU_FEATURE_SU_PROMPT);
	ksu_unregister_feature_handler(KSU_FEATURE_SU_PROMPT_TIMEOUT);
	ksu_unregister_feature_handler(KSU_FEATURE_SU_PROMPT_DEFAULT_ALLOW);

	mutex_lock(&su_req_lock);
	list_for_each_entry_safe(req, tmp, &su_req_list, list) {
		req->answered = true;
		req->decision = KSU_SU_REQUEST_DENY;
		wake_up(&req->wait);
	}
	mutex_unlock(&su_req_lock);
}
