#include <linux/anon_inodes.h>
#include <linux/cache.h>
#include <linux/err.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/jiffies.h>
#include <linux/mutex.h>
#include <linux/poll.h>
#include <linux/sched.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/timekeeping.h>
#include <linux/version.h>
#include <linux/export.h>
#include <linux/cred.h>
#include <linux/kprobes.h>
#include <linux/ptrace.h>

#include "feature/veil.h"
#include "arch.h"
#include "infra/event_queue.h"
#include "klog.h" // IWYU pragma: keep
#include "policy/feature.h"
#include "policy/allowlist.h"
#include "compat/kernel_compat.h"
#include "uapi/veil.h"
#include "uapi/supercall.h"
#include "sulog/event.h"

#define VEIL_MAX_QUEUED 256
#define VEIL_DEDUP_SLOTS 32
#define VEIL_DEDUP_WINDOW msecs_to_jiffies(5000)

static bool ksu_veil_enabled __read_mostly = false;
static struct ksu_event_queue veil_queue;

/* Per-uid burst coalescing so a spinning probe can't flood the queue. */
struct veil_dedup_slot {
    uid_t uid;
    __u16 kind;
    bool used;
    unsigned long last;
    __u32 count;
};
static struct veil_dedup_slot dedup[VEIL_DEDUP_SLOTS];
static DEFINE_SPINLOCK(dedup_lock);

/* ---- fd delivery (mirrors kernel/sulog/fd.c) ---- */
static DEFINE_MUTEX(veil_fd_lock);
static bool veil_fd_active;

bool ksu_veil_is_enabled(void)
{
    return ksu_veil_enabled;
}

/*
 * Returns 0 when the probe is coalesced into a recent event (caller should not
 * emit), otherwise the accumulated hit count to put on the emitted event.
 */
static __u32 veil_dedup_bump(uid_t uid, __u16 kind)
{
    int i, slot = -1, oldest = 0;
    unsigned long now = jiffies;
    __u32 emit;
    unsigned long flags;

    spin_lock_irqsave(&dedup_lock, flags);

    for (i = 0; i < VEIL_DEDUP_SLOTS; i++) {
        if (dedup[i].used && dedup[i].uid == uid && dedup[i].kind == kind) {
            slot = i;
            break;
        }
        if (!dedup[i].used && slot < 0)
            slot = i; /* first free, keep scanning for a real match */
        if (time_before(dedup[i].last, dedup[oldest].last))
            oldest = i;
    }

    if (slot >= 0 && dedup[slot].used && dedup[slot].uid == uid && dedup[slot].kind == kind) {
        if (time_before(now, dedup[slot].last + VEIL_DEDUP_WINDOW)) {
            dedup[slot].count++;
            dedup[slot].last = now;
            spin_unlock_irqrestore(&dedup_lock, flags);
            return 0; /* within window: suppress */
        }
        emit = dedup[slot].count + 1;
        dedup[slot].count = 0;
        dedup[slot].last = now;
        spin_unlock_irqrestore(&dedup_lock, flags);
        return emit;
    }

    /* no match: take the free slot, else evict the oldest */
    if (slot < 0)
        slot = oldest;
    dedup[slot].used = true;
    dedup[slot].uid = uid;
    dedup[slot].kind = kind;
    dedup[slot].last = now;
    dedup[slot].count = 0;
    spin_unlock_irqrestore(&dedup_lock, flags);
    return 1;
}

/* Notify the manager once per uid (per boot) when a non-allowlisted app probes
 * for su, so it can offer to grant root. */
#define VEIL_SU_NOTIFY_MAX 256
static uid_t su_notified[VEIL_SU_NOTIFY_MAX];
static int su_notified_count;
static DEFINE_SPINLOCK(su_notify_lock);

static bool veil_su_notify_once(uid_t uid)
{
    int i;
    bool first = true;
    unsigned long flags;

    spin_lock_irqsave(&su_notify_lock, flags);
    for (i = 0; i < su_notified_count; i++) {
        if (su_notified[i] == uid) {
            first = false;
            break;
        }
    }
    if (first && su_notified_count < VEIL_SU_NOTIFY_MAX)
        su_notified[su_notified_count++] = uid;
    spin_unlock_irqrestore(&su_notify_lock, flags);
    return first;
}

/* Clear a uid's notify mark so it can raise a fresh root request again - called
 * on uncloak, otherwise the once-per-uid dedup would suppress it forever. */
static void veil_su_notify_reset(uid_t uid)
{
    int i;
    unsigned long flags;

    spin_lock_irqsave(&su_notify_lock, flags);
    for (i = 0; i < su_notified_count; i++) {
        if (su_notified[i] == uid) {
            su_notified[i] = su_notified[--su_notified_count]; /* swap-remove */
            break;
        }
    }
    spin_unlock_irqrestore(&su_notify_lock, flags);
}

void ksu_veil_report(uid_t uid, const char *path, __u16 kind)
{
    struct ksu_veil_event ev;
    __u32 count;

    if (!READ_ONCE(ksu_veil_enabled))
        return;

    ksu_veil_history_record(uid, kind);

    /* First time a non-allowlisted app probes for su, surface a root request so
     * the manager can offer to grant it. Gated by sulog being enabled (the
     * "Notify on root requests" toggle turns that on). */
    if (kind == KSU_VEIL_KIND_SU && uid >= 10000 && !ksu_is_allow_uid(uid) && veil_su_notify_once(uid))
        ksu_sulog_emit_grant_root(-EPERM, uid, uid, GFP_ATOMIC);

    count = veil_dedup_bump(uid, kind);
    if (!count)
        return;

    pr_info_ratelimited("veil: probe uid=%u pid=%d path=%s x%u\n", uid, current->pid, path ? path : "?", count);

    memset(&ev, 0, sizeof(ev));
    ev.version = KSU_VEIL_EVENT_VERSION;
    ev.kind = kind;
    ev.uid = uid;
    ev.pid = current->pid;
    ev.count = count;
    ev.ts_ns = ktime_get_boottime_ns();
    if (path)
        strscpy(ev.path, path, sizeof(ev.path));

    ksu_event_queue_push(&veil_queue, kind, 0, &ev, sizeof(ev), GFP_ATOMIC);
}

/* ---- cloak set: uids we hide module mounts from ----
 * The umount path (kernel_umount.c) consults ksu_veil_is_cloaked() so a
 * cloaked app uid gets modules unmounted on its next spawn, regardless of the
 * global kernel_umount toggle or its app profile. (su is already hidden from
 * non-allowed apps by sucompat.) In-memory only for now; resets on reboot.
 */
#define VEIL_CLOAK_MAX 512

static uid_t cloak_set[VEIL_CLOAK_MAX];
static int cloak_count;
static DEFINE_SPINLOCK(cloak_lock);
static bool veil_auto_cloak __read_mostly;

bool ksu_veil_is_cloaked(uid_t uid)
{
    int i;
    bool res = false;
    unsigned long flags;

    spin_lock_irqsave(&cloak_lock, flags);
    for (i = 0; i < cloak_count; i++) {
        if (cloak_set[i] == uid) {
            res = true;
            break;
        }
    }
    spin_unlock_irqrestore(&cloak_lock, flags);
    return res;
}

int ksu_veil_cloak_add(uid_t uid)
{
    int i, ret = 0;
    unsigned long flags;

    spin_lock_irqsave(&cloak_lock, flags);
    for (i = 0; i < cloak_count; i++) {
        if (cloak_set[i] == uid)
            goto out; /* already cloaked */
    }
    if (cloak_count >= VEIL_CLOAK_MAX) {
        ret = -ENOSPC;
        goto out;
    }
    cloak_set[cloak_count++] = uid;
    pr_info("veil: cloaked uid=%u\n", uid);
out:
    spin_unlock_irqrestore(&cloak_lock, flags);
    return ret;
}

int ksu_veil_cloak_remove(uid_t uid)
{
    int i;
    unsigned long flags;

    spin_lock_irqsave(&cloak_lock, flags);
    for (i = 0; i < cloak_count; i++) {
        if (cloak_set[i] == uid) {
            cloak_set[i] = cloak_set[--cloak_count]; /* swap-remove */
            pr_info("veil: uncloaked uid=%u\n", uid);
            break;
        }
    }
    spin_unlock_irqrestore(&cloak_lock, flags);
    /* Uncloaking means "let this app ask again" - clear its notify dedup. */
    veil_su_notify_reset(uid);
    return 0;
}

void ksu_veil_cloak_clear(void)
{
    unsigned long flags;

    spin_lock_irqsave(&cloak_lock, flags);
    cloak_count = 0;
    spin_unlock_irqrestore(&cloak_lock, flags);

    /* uncloak-all is a fresh start: let every app raise requests again */
    spin_lock_irqsave(&su_notify_lock, flags);
    su_notified_count = 0;
    spin_unlock_irqrestore(&su_notify_lock, flags);
}

int ksu_veil_cloak_list(uid_t *out, int capacity)
{
    int i, total;
    unsigned long flags;

    spin_lock_irqsave(&cloak_lock, flags);
    total = cloak_count;
    for (i = 0; i < cloak_count && i < capacity; i++)
        out[i] = cloak_set[i];
    spin_unlock_irqrestore(&cloak_lock, flags);
    return total;
}

void ksu_veil_set_auto_cloak(bool on)
{
    WRITE_ONCE(veil_auto_cloak, on);
    pr_info("veil: auto-cloak %d\n", on);
}

bool ksu_veil_get_auto_cloak(void)
{
    return READ_ONCE(veil_auto_cloak);
}

/* ---- watch-list of root / identity artifacts ---- */
struct veil_watch {
    const char *path;
    bool prefix; /* prefix match vs exact */
    __u16 kind;
};

static const struct veil_watch watch_list[] = {
    { "/system/bin/su", false, KSU_VEIL_KIND_SU },
    { "/system/xbin/su", false, KSU_VEIL_KIND_SU },
    { "/sbin/su", false, KSU_VEIL_KIND_SU },
    { "/su/bin/su", false, KSU_VEIL_KIND_SU },
    { "/system/bin/magisk", true, KSU_VEIL_KIND_MAGISK },
    { "/sbin/.magisk", true, KSU_VEIL_KIND_MAGISK },
    { "/data/adb/magisk", true, KSU_VEIL_KIND_MAGISK },
    { "/data/adb/ksu", true, KSU_VEIL_KIND_KSU },
    { "/data/adb/modules", true, KSU_VEIL_KIND_MODULES },
    { "/data/system/packages.list", false, KSU_VEIL_KIND_PKGLIST },
    { "/data/system/packages.xml", false, KSU_VEIL_KIND_PKGLIST },
    { "/system/xbin/busybox", false, KSU_VEIL_KIND_BUSYBOX },
    { "/system/bin/busybox", false, KSU_VEIL_KIND_BUSYBOX },
    { NULL, false, 0 }, /* terminator */
};

static __u16 veil_match(const char *path)
{
    int i;

    for (i = 0; watch_list[i].path; i++) {
        const struct veil_watch *w = &watch_list[i];

        if (w->prefix) {
            if (!strncmp(path, w->path, strlen(w->path)))
                return w->kind;
        } else if (!strcmp(path, w->path)) {
            return w->kind;
        }
    }
    return 0;
}

/* ---- persistent per-uid probe history (resets on reboot) ---- */
#define VEIL_HIST_MAX 256

struct veil_hist {
    bool used;
    uid_t uid;
    __u32 count;
    __u32 kinds; /* bitmap: bit (kind-1) per enum ksu_veil_kind */
    u64 last_ns;
};
static struct veil_hist hist[VEIL_HIST_MAX];
static DEFINE_SPINLOCK(hist_lock);

void ksu_veil_history_record(uid_t uid, __u16 kind)
{
    int i, free_slot = -1, oldest = 0;
    unsigned long flags;

    if (kind == 0 || kind > KSU_VEIL_KIND_MAX)
        return;

    spin_lock_irqsave(&hist_lock, flags);
    for (i = 0; i < VEIL_HIST_MAX; i++) {
        if (hist[i].used && hist[i].uid == uid) {
            hist[i].count++;
            hist[i].kinds |= (1u << (kind - 1));
            hist[i].last_ns = ktime_get_boottime_ns();
            goto out;
        }
        if (!hist[i].used && free_slot < 0)
            free_slot = i;
        if (hist[i].used && hist[oldest].used && hist[i].last_ns < hist[oldest].last_ns)
            oldest = i;
    }
    if (free_slot < 0)
        free_slot = oldest; /* evict the least-recent */
    hist[free_slot].used = true;
    hist[free_slot].uid = uid;
    hist[free_slot].count = 1;
    hist[free_slot].kinds = (1u << (kind - 1));
    hist[free_slot].last_ns = ktime_get_boottime_ns();
out:
    spin_unlock_irqrestore(&hist_lock, flags);
}

int ksu_veil_history_dump(struct ksu_veil_hist_entry *out, int capacity)
{
    int i, n = 0;
    unsigned long flags;

    spin_lock_irqsave(&hist_lock, flags);
    for (i = 0; i < VEIL_HIST_MAX; i++) {
        if (!hist[i].used)
            continue;
        if (n < capacity) {
            out[n].uid = hist[i].uid;
            out[n].count = hist[i].count;
            out[n].kinds = hist[i].kinds;
            out[n].pad = 0;
            out[n].last_ns = hist[i].last_ns;
        }
        n++;
    }
    spin_unlock_irqrestore(&hist_lock, flags);
    return n;
}

void ksu_veil_history_clear(void)
{
    unsigned long flags;

    spin_lock_irqsave(&hist_lock, flags);
    memset(hist, 0, sizeof(hist));
    spin_unlock_irqrestore(&hist_lock, flags);
}

/* ---- probe detection via kprobes ----
 * The inline sucompat hook (fs/open.c) only calls ksu_handle_faccessat for
 * allowlisted uids, so it never sees a non-root app probing. We kprobe the
 * faccessat and openat entry points (covering access/faccessat/faccessat2 and
 * open/openat/openat2) and match the path against the watch-list above.
 * Armed only while Veil is enabled.
 */
#ifdef CONFIG_KPROBES
static DEFINE_MUTEX(veil_kp_lock);
static bool kp_faccessat_ok;
static bool kp_openat_ok;

static void veil_check_path(const char __user *filename)
{
    char path[64] = { 0 };
    uid_t uid;
    __u16 kind;

    if (!READ_ONCE(ksu_veil_enabled))
        return;

    uid = current_uid().val;
    if (uid == 0)
        return; /* root is not a prober */
    if (ksu_is_allow_uid_for_current(uid))
        return; /* granted apps go through the sucompat redirect */

    if (!filename)
        return;
    ksu_strncpy_from_user_nofault(path, filename, sizeof(path));
    path[sizeof(path) - 1] = '\0';

    kind = veil_match(path);
    if (!kind)
        return;

    ksu_veil_report(uid, path, kind);
    /*
     * Auto-cloak real apps only. System/native uids (appid < AID_APP_START)
     * legitimately touch su paths via the framework and must not have their
     * module mounts stripped. Manual cloak is unaffected (this guards only the
     * auto path). The modulo handles secondary-user uids (user*100000 + appid).
     */
    if (READ_ONCE(veil_auto_cloak) && (uid % 100000) >= 10000)
        ksu_veil_cloak_add(uid);
}

/* do_faccessat(int dfd, const char __user *filename, ...) -> arg index 1.
 * PT_REGS_PARM2 works on arm64 and x86_64 (regs_get_kernel_argument is
 * arm64-only, and OriginSU also builds x86_64 LKM). */
static int veil_faccessat_pre(struct kprobe *p, struct pt_regs *regs)
{
    veil_check_path((const char __user *)PT_REGS_PARM2(regs));
    return 0;
}

/* do_sys_openat2(int dfd, const char __user *filename, ...) -> arg index 1 */
static int veil_openat_pre(struct kprobe *p, struct pt_regs *regs)
{
    veil_check_path((const char __user *)PT_REGS_PARM2(regs));
    return 0;
}

static struct kprobe veil_faccessat_kp = {
    .symbol_name = "do_faccessat",
    .pre_handler = veil_faccessat_pre,
};

static struct kprobe veil_openat_kp = {
    .symbol_name = "do_sys_openat2",
    .pre_handler = veil_openat_pre,
};

static void veil_kprobe_arm(void)
{
    mutex_lock(&veil_kp_lock);
    if (!kp_faccessat_ok) {
        /* unregister_kprobe leaves .addr set (resolved from .symbol_name);
         * register_kprobe rejects having both, so clear it to re-resolve. */
        veil_faccessat_kp.addr = NULL;
        if (register_kprobe(&veil_faccessat_kp))
            pr_err("veil: register_kprobe(do_faccessat) failed\n");
        else
            kp_faccessat_ok = true;
    }
    if (!kp_openat_ok) {
        veil_openat_kp.addr = NULL;
        if (register_kprobe(&veil_openat_kp))
            pr_err("veil: register_kprobe(do_sys_openat2) failed\n");
        else
            kp_openat_ok = true;
    }
    pr_info("veil: kprobes armed (faccessat=%d openat=%d)\n", kp_faccessat_ok, kp_openat_ok);
    mutex_unlock(&veil_kp_lock);
}

static void veil_kprobe_disarm(void)
{
    mutex_lock(&veil_kp_lock);
    if (kp_faccessat_ok) {
        unregister_kprobe(&veil_faccessat_kp);
        kp_faccessat_ok = false;
    }
    if (kp_openat_ok) {
        unregister_kprobe(&veil_openat_kp);
        kp_openat_ok = false;
    }
    pr_info("veil: kprobes disarmed\n");
    mutex_unlock(&veil_kp_lock);
}
#else
static void veil_kprobe_arm(void)
{
    pr_warn("veil: CONFIG_KPROBES disabled; probe detection unavailable\n");
}
static void veil_kprobe_disarm(void)
{
}
#endif

static ssize_t veil_read(struct file *file, char __user *buf, size_t count, loff_t *ppos)
{
    return ksu_event_queue_read(&veil_queue, buf, count, file->f_flags);
}

static unsigned __bitwise veil_poll(struct file *file, poll_table *wait)
{
    return ksu_event_queue_poll(&veil_queue, file, wait);
}

static int veil_release(struct inode *inode, struct file *file)
{
    mutex_lock(&veil_fd_lock);
    veil_fd_active = false;
    mutex_unlock(&veil_fd_lock);
    pr_info("veil: fd released\n");
    return 0;
}

static const struct file_operations veil_fops = {
    .owner = THIS_MODULE,
    .read = veil_read,
    .poll = veil_poll,
    .release = veil_release,
    .llseek = noop_llseek,
};

int ksu_install_veil_fd(void)
{
    struct file *filp;
    int fd;

    mutex_lock(&veil_fd_lock);

    if (veil_fd_active) {
        fd = -EBUSY;
        goto out_unlock;
    }

    if (READ_ONCE(veil_queue.closed)) {
        fd = -EPIPE;
        goto out_unlock;
    }

    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0)
        goto out_unlock;

    filp = anon_inode_getfile("[ksu_veil]", &veil_fops, NULL, O_RDONLY | O_CLOEXEC);
    if (IS_ERR(filp)) {
        put_unused_fd(fd);
        fd = PTR_ERR(filp);
        goto out_unlock;
    }

    veil_fd_active = true;
    fd_install(fd, filp);
    pr_info("veil: fd installed %d for pid %d\n", fd, current->pid);

out_unlock:
    mutex_unlock(&veil_fd_lock);
    return fd;
}

/* ---- enable/disable via the feature framework (get/set_feature ioctls) ---- */
static int veil_feature_get(u64 *value)
{
    *value = ksu_veil_enabled ? 1 : 0;
    return 0;
}

static int veil_feature_set(u64 value)
{
    bool enable = value != 0;

    ksu_veil_enabled = enable;
    if (enable)
        veil_kprobe_arm();
    else
        veil_kprobe_disarm();
    pr_info("veil: set to %d\n", enable);
    return 0;
}

static const struct ksu_feature_handler veil_handler = {
    .feature_id = KSU_FEATURE_ORIGIN_VEIL,
    .name = "veil",
    .get_handler = veil_feature_get,
    .set_handler = veil_feature_set,
};

void __init ksu_veil_init(void)
{
    int ret;

    ksu_veil_enabled = false;
    veil_auto_cloak = false;
    cloak_count = 0;
    memset(dedup, 0, sizeof(dedup));

    ksu_event_queue_init(&veil_queue, VEIL_MAX_QUEUED, sizeof(struct ksu_veil_event));

    ret = ksu_register_feature_handler(&veil_handler);
    if (ret)
        pr_err("veil: failed to register feature handler: %d\n", ret);

    mutex_lock(&veil_fd_lock);
    veil_fd_active = false;
    mutex_unlock(&veil_fd_lock);

    pr_info("veil: initialized\n");
}

void __exit ksu_veil_exit(void)
{
    veil_kprobe_disarm();
    ksu_veil_cloak_clear();
    WRITE_ONCE(veil_auto_cloak, false);
    ksu_unregister_feature_handler(KSU_FEATURE_ORIGIN_VEIL);

    mutex_lock(&veil_fd_lock);
    veil_fd_active = false;
    mutex_unlock(&veil_fd_lock);

    ksu_event_queue_close(&veil_queue);
    ksu_event_queue_destroy(&veil_queue);
}
