/*
 * Origin Veil 2.0
 *
 * Kernel-side root-probe observation and per-uid mount cloaking.
 *
 * The implementation is deliberately split into small, independent pieces:
 *   - a bounded probe-event queue for the manager's live view;
 *   - a hashed cloak set consulted by the umount path;
 *   - a hashed, LRU-bounded per-uid history;
 *   - a fixed-size open-addressed set for once-per-boot su notifications;
 *   - a small probe-hash burst limiter so a spinning app cannot flood the
 *     event queue;
 *   - kprobes on the path-taking open/access entry points.
 *
 * None of the sets allocate on lookup. Writers allocate with GFP_ATOMIC
 * because the probe path runs in kprobe context.
 */

#include <linux/anon_inodes.h>
#include <linux/atomic.h>
#include <linux/cache.h>
#include <linux/cred.h>
#include <linux/err.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/init.h>
#include <linux/jiffies.h>
#include <linux/kprobes.h>
#include <linux/list.h>
#include <linux/mutex.h>
#include <linux/poll.h>
#include <linux/ptrace.h>
#include <linux/sched.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/timekeeping.h>
#include <linux/types.h>
#include <linux/version.h>

#include "arch.h"
#include "compat/kernel_compat.h"
#include "feature/veil.h"
#include "infra/event_queue.h"
#include "klog.h" // IWYU pragma: keep
#include "policy/allowlist.h"
#include "policy/feature.h"
#include "sulog/event.h"
#include "uapi/supercall.h"
#include "uapi/veil.h"

#define VEIL2_QUEUE_DEPTH 512
#define VEIL2_CLOAK_BUCKETS 256
#define VEIL2_HISTORY_BUCKETS 256
#define VEIL2_CLOAK_MAX 4096
#define VEIL2_HISTORY_MAX 4096
#define VEIL2_DEDUP_SLOTS 256
#define VEIL2_DEDUP_PROBES 8
#define VEIL2_DEDUP_WINDOW msecs_to_jiffies(3000)
#define VEIL2_NOTIFY_SLOTS 2048
#define VEIL2_NOTIFY_TOMBSTONE ((uid_t)~0U)

static bool veil2_enabled __read_mostly;
static bool veil2_auto_cloak __read_mostly;
static struct ksu_event_queue veil2_queue;

static atomic64_t veil2_probe_count = ATOMIC64_INIT(0);
static atomic64_t veil2_event_seq = ATOMIC64_INIT(0);
static atomic_t veil2_suppressed = ATOMIC_INIT(0);
static atomic_t veil2_history_dropped = ATOMIC_INIT(0);
static atomic_t veil2_queue_dropped = ATOMIC_INIT(0);

static void veil2_notify_reset(uid_t uid);
static void veil2_notify_clear(void);

bool ksu_veil_is_enabled(void)
{
    return READ_ONCE(veil2_enabled);
}

/* ------------------------------------------------------------------ */
/* Hash helpers                                                       */
/* ------------------------------------------------------------------ */

static inline u32 veil2_uid_hash(uid_t uid, u32 buckets)
{
    u32 h = (u32)uid;

    h ^= h >> 16;
    h *= 0x7feb352dU;
    h ^= h >> 15;
    return h & (buckets - 1);
}

/* ------------------------------------------------------------------ */
/* Cloak set                                                          */
/* ------------------------------------------------------------------ */

struct veil2_cloak {
    struct hlist_node node;
    uid_t uid;
};

static struct hlist_head veil2_cloak_buckets[VEIL2_CLOAK_BUCKETS];
static u32 veil2_cloak_total;
static DEFINE_SPINLOCK(veil2_cloak_lock);

static struct veil2_cloak *veil2_cloak_find_locked(uid_t uid, u32 bucket)
{
    struct veil2_cloak *entry;

    hlist_for_each_entry (entry, &veil2_cloak_buckets[bucket], node) {
        if (entry->uid == uid)
            return entry;
    }
    return NULL;
}

bool ksu_veil_is_cloaked(uid_t uid)
{
    u32 bucket = veil2_uid_hash(uid, VEIL2_CLOAK_BUCKETS);
    unsigned long flags;
    bool found;

    spin_lock_irqsave(&veil2_cloak_lock, flags);
    found = veil2_cloak_find_locked(uid, bucket) != NULL;
    spin_unlock_irqrestore(&veil2_cloak_lock, flags);
    return found;
}

int ksu_veil_cloak_add(uid_t uid)
{
    u32 bucket = veil2_uid_hash(uid, VEIL2_CLOAK_BUCKETS);
    struct veil2_cloak *entry;
    unsigned long flags;
    bool added = false;
    int ret = 0;

    entry = kzalloc(sizeof(*entry), GFP_ATOMIC);
    if (!entry)
        return -ENOMEM;

    spin_lock_irqsave(&veil2_cloak_lock, flags);
    if (veil2_cloak_find_locked(uid, bucket)) {
        ret = 0; /* already cloaked */
        goto out_unlock;
    }
    if (veil2_cloak_total >= VEIL2_CLOAK_MAX) {
        ret = -ENOSPC;
        goto out_unlock;
    }

    entry->uid = uid;
    hlist_add_head(&entry->node, &veil2_cloak_buckets[bucket]);
    veil2_cloak_total++;
    entry = NULL; /* ownership transferred to the set */
    added = true;

out_unlock:
    spin_unlock_irqrestore(&veil2_cloak_lock, flags);
    kfree(entry);
    if (!ret && added)
        pr_info("veil2: cloaked uid=%u\n", uid);
    return ret;
}

int ksu_veil_cloak_remove(uid_t uid)
{
    u32 bucket = veil2_uid_hash(uid, VEIL2_CLOAK_BUCKETS);
    struct veil2_cloak *entry;
    unsigned long flags;

    spin_lock_irqsave(&veil2_cloak_lock, flags);
    entry = veil2_cloak_find_locked(uid, bucket);
    if (entry) {
        hlist_del(&entry->node);
        veil2_cloak_total--;
    }
    spin_unlock_irqrestore(&veil2_cloak_lock, flags);

    if (!entry)
        return 0;

    kfree(entry);
    veil2_notify_reset(uid);
    pr_info("veil2: uncloaked uid=%u\n", uid);
    return 0;
}

void ksu_veil_cloak_clear(void)
{
    HLIST_HEAD(victims);
    struct veil2_cloak *entry;
    struct hlist_node *tmp;
    unsigned long flags;
    int i;

    spin_lock_irqsave(&veil2_cloak_lock, flags);
    for (i = 0; i < VEIL2_CLOAK_BUCKETS; i++) {
        hlist_for_each_entry_safe (entry, tmp, &veil2_cloak_buckets[i], node) {
            hlist_del(&entry->node);
            hlist_add_head(&entry->node, &victims);
        }
    }
    veil2_cloak_total = 0;
    spin_unlock_irqrestore(&veil2_cloak_lock, flags);

    hlist_for_each_entry_safe (entry, tmp, &victims, node) {
        hlist_del(&entry->node);
        kfree(entry);
    }

    veil2_notify_clear();
}

int ksu_veil_cloak_list(uid_t *out, int capacity)
{
    struct veil2_cloak *entry;
    unsigned long flags;
    int total = 0;
    int i;

    if (capacity < 0)
        capacity = 0;

    spin_lock_irqsave(&veil2_cloak_lock, flags);
    for (i = 0; i < VEIL2_CLOAK_BUCKETS; i++) {
        hlist_for_each_entry (entry, &veil2_cloak_buckets[i], node) {
            if (out && total < capacity)
                out[total] = entry->uid;
            total++;
        }
    }
    spin_unlock_irqrestore(&veil2_cloak_lock, flags);
    return total;
}

void ksu_veil_set_auto_cloak(bool on)
{
    WRITE_ONCE(veil2_auto_cloak, on);
    pr_info("veil2: auto-cloak %d\n", on);
}

bool ksu_veil_get_auto_cloak(void)
{
    return READ_ONCE(veil2_auto_cloak);
}

/* ------------------------------------------------------------------ */
/* Once-per-boot su notification set                                  */
/* ------------------------------------------------------------------ */

static uid_t veil2_notify_slots[VEIL2_NOTIFY_SLOTS];
static DEFINE_SPINLOCK(veil2_notify_lock);

static bool veil2_notify_once(uid_t uid)
{
    u32 start = veil2_uid_hash(uid, VEIL2_NOTIFY_SLOTS);
    unsigned long flags;
    bool inserted = false, have_tombstone = false;
    u32 tombstone = 0;
    int i;

    spin_lock_irqsave(&veil2_notify_lock, flags);
    for (i = 0; i < VEIL2_NOTIFY_SLOTS; i++) {
        u32 slot = (start + i) & (VEIL2_NOTIFY_SLOTS - 1);
        uid_t value = veil2_notify_slots[slot];

        if (value == uid)
            goto out_unlock;
        if (value == VEIL2_NOTIFY_TOMBSTONE && !have_tombstone) {
            tombstone = slot;
            have_tombstone = true;
            continue;
        }
        if (value == 0) {
            if (have_tombstone)
                slot = tombstone;
            veil2_notify_slots[slot] = uid;
            inserted = true;
            goto out_unlock;
        }
    }

    if (have_tombstone) {
        veil2_notify_slots[tombstone] = uid;
        inserted = true;
    }

out_unlock:
    spin_unlock_irqrestore(&veil2_notify_lock, flags);
    return inserted;
}

static void veil2_notify_reset(uid_t uid)
{
    u32 start = veil2_uid_hash(uid, VEIL2_NOTIFY_SLOTS);
    unsigned long flags;
    int i;

    spin_lock_irqsave(&veil2_notify_lock, flags);
    for (i = 0; i < VEIL2_NOTIFY_SLOTS; i++) {
        u32 slot = (start + i) & (VEIL2_NOTIFY_SLOTS - 1);

        if (veil2_notify_slots[slot] == uid) {
            veil2_notify_slots[slot] = VEIL2_NOTIFY_TOMBSTONE;
            break;
        }
        if (veil2_notify_slots[slot] == 0)
            break;
    }
    spin_unlock_irqrestore(&veil2_notify_lock, flags);
}

static void veil2_notify_clear(void)
{
    unsigned long flags;

    spin_lock_irqsave(&veil2_notify_lock, flags);
    memset(veil2_notify_slots, 0, sizeof(veil2_notify_slots));
    spin_unlock_irqrestore(&veil2_notify_lock, flags);
}

/* ------------------------------------------------------------------ */
/* Burst limiter                                                      */
/* ------------------------------------------------------------------ */

struct veil2_dedup {
    uid_t uid;
    __u16 kind;
    __u16 used;
    u32 count;
    unsigned long last;
};

static struct veil2_dedup veil2_dedup[VEIL2_DEDUP_SLOTS];
static DEFINE_SPINLOCK(veil2_dedup_lock);

static u32 veil2_dedup_bump(uid_t uid, __u16 kind)
{
    u32 start = veil2_uid_hash(uid ^ ((u32)kind << 16), VEIL2_DEDUP_SLOTS);
    unsigned long now = jiffies;
    unsigned long flags;
    struct veil2_dedup *slot, *free_slot = NULL, *oldest = NULL;
    u32 emit = 1;
    int i;

    spin_lock_irqsave(&veil2_dedup_lock, flags);

    for (i = 0; i < VEIL2_DEDUP_PROBES; i++) {
        slot = &veil2_dedup[(start + i) & (VEIL2_DEDUP_SLOTS - 1)];

        if (slot->used && slot->uid == uid && slot->kind == kind) {
            if (time_before(now, slot->last + VEIL2_DEDUP_WINDOW)) {
                slot->count++;
                slot->last = now;
                emit = 0;
            } else {
                emit = slot->count + 1;
                slot->count = 0;
                slot->last = now;
            }
            goto out_unlock;
        }

        if (!slot->used && !free_slot)
            free_slot = slot;
        if (slot->used && (!oldest || time_before(slot->last, oldest->last)))
            oldest = slot;
    }

    slot = free_slot ? free_slot : oldest;
    if (!slot)
        slot = &veil2_dedup[start];

    slot->used = 1;
    slot->uid = uid;
    slot->kind = kind;
    slot->count = 0;
    slot->last = now;

out_unlock:
    spin_unlock_irqrestore(&veil2_dedup_lock, flags);
    if (!emit)
        atomic_inc(&veil2_suppressed);
    return emit;
}

/* ------------------------------------------------------------------ */
/* Per-uid history                                                    */
/* ------------------------------------------------------------------ */

struct veil2_history {
    struct hlist_node hash;
    struct list_head lru;
    uid_t uid;
    u32 count;
    u32 kinds;
    u64 first_ns;
    u64 last_ns;
};

static struct hlist_head veil2_history_buckets[VEIL2_HISTORY_BUCKETS];
static LIST_HEAD(veil2_history_lru);
static u32 veil2_history_total;
static DEFINE_SPINLOCK(veil2_history_lock);

static struct veil2_history *veil2_history_find_locked(uid_t uid, u32 bucket)
{
    struct veil2_history *entry;

    hlist_for_each_entry (entry, &veil2_history_buckets[bucket], hash) {
        if (entry->uid == uid)
            return entry;
    }
    return NULL;
}

void ksu_veil_history_record(uid_t uid, __u16 kind)
{
    u32 bucket;
    struct veil2_history *entry, *victim = NULL;
    unsigned long flags;
    u64 now;

    if (kind == 0 || kind > KSU_VEIL2_PROBE_MAX)
        return;

    bucket = veil2_uid_hash(uid, VEIL2_HISTORY_BUCKETS);
    now = ktime_get_boottime_ns();

    entry = kzalloc(sizeof(*entry), GFP_ATOMIC);
    if (!entry) {
        atomic_inc(&veil2_history_dropped);
        return;
    }

    spin_lock_irqsave(&veil2_history_lock, flags);

    {
        struct veil2_history *existing = veil2_history_find_locked(uid, bucket);

        if (existing) {
            existing->count++;
            existing->kinds |= 1u << (kind - 1);
            existing->last_ns = now;
            list_move_tail(&existing->lru, &veil2_history_lru);
            spin_unlock_irqrestore(&veil2_history_lock, flags);
            kfree(entry);
            return;
        }
    }

    if (veil2_history_total >= VEIL2_HISTORY_MAX) {
        victim = list_first_entry(&veil2_history_lru, struct veil2_history, lru);
        list_del(&victim->lru);
        hlist_del(&victim->hash);
        veil2_history_total--;
        atomic_inc(&veil2_history_dropped);
    }

    entry->uid = uid;
    entry->count = 1;
    entry->kinds = 1u << (kind - 1);
    entry->first_ns = now;
    entry->last_ns = now;
    hlist_add_head(&entry->hash, &veil2_history_buckets[bucket]);
    list_add_tail(&entry->lru, &veil2_history_lru);
    veil2_history_total++;

    spin_unlock_irqrestore(&veil2_history_lock, flags);

    kfree(victim);
}

int ksu_veil_history_dump(struct ksu_veil2_hist_entry *out, int capacity)
{
    struct veil2_history *entry;
    unsigned long flags;
    int total = 0;

    if (capacity < 0)
        capacity = 0;

    spin_lock_irqsave(&veil2_history_lock, flags);
    list_for_each_entry_reverse (entry, &veil2_history_lru, lru) {
        if (out && total < capacity) {
            out[total].uid = entry->uid;
            out[total].count = entry->count;
            out[total].kinds = entry->kinds;
            out[total].flags = 0;
            out[total].first_ns = entry->first_ns;
            out[total].last_ns = entry->last_ns;
        }
        total++;
    }
    spin_unlock_irqrestore(&veil2_history_lock, flags);
    return total;
}

void ksu_veil_history_clear(void)
{
    LIST_HEAD(victims);
    struct veil2_history *entry, *tmp;
    unsigned long flags;

    spin_lock_irqsave(&veil2_history_lock, flags);
    list_for_each_entry_safe (entry, tmp, &veil2_history_lru, lru) {
        list_del(&entry->lru);
        hlist_del(&entry->hash);
        list_add_tail(&entry->lru, &victims);
    }
    veil2_history_total = 0;
    spin_unlock_irqrestore(&veil2_history_lock, flags);

    list_for_each_entry_safe (entry, tmp, &victims, lru) {
        list_del(&entry->lru);
        kfree(entry);
    }
}

/* ------------------------------------------------------------------ */
/* Watch list                                                         */
/* ------------------------------------------------------------------ */

struct veil2_watch {
    const char *path;
    __u16 path_len;
    __u16 kind;
    bool prefix;
};

#define VEIL2_WATCH(_path, _prefix, _kind)                                                                             \
    { .path = _path, .path_len = sizeof(_path) - 1, .prefix = _prefix, .kind = _kind }

static const struct veil2_watch veil2_watch_list[] = {
    VEIL2_WATCH("/system/bin/su", false, KSU_VEIL2_PROBE_SU),
    VEIL2_WATCH("/system/xbin/su", false, KSU_VEIL2_PROBE_SU),
    VEIL2_WATCH("/sbin/su", false, KSU_VEIL2_PROBE_SU),
    VEIL2_WATCH("/su/bin/su", false, KSU_VEIL2_PROBE_SU),
    VEIL2_WATCH("/system/bin/magisk", true, KSU_VEIL2_PROBE_MAGISK),
    VEIL2_WATCH("/sbin/.magisk", true, KSU_VEIL2_PROBE_MAGISK),
    VEIL2_WATCH("/data/adb/magisk", true, KSU_VEIL2_PROBE_MAGISK),
    VEIL2_WATCH("/data/adb/ksu", true, KSU_VEIL2_PROBE_KSU),
    VEIL2_WATCH("/data/adb/modules", true, KSU_VEIL2_PROBE_MODULES),
    VEIL2_WATCH("/data/system/packages.list", false, KSU_VEIL2_PROBE_PACKAGE_DB),
    VEIL2_WATCH("/data/system/packages.xml", false, KSU_VEIL2_PROBE_PACKAGE_DB),
    VEIL2_WATCH("/system/xbin/busybox", false, KSU_VEIL2_PROBE_BUSYBOX),
    VEIL2_WATCH("/system/bin/busybox", false, KSU_VEIL2_PROBE_BUSYBOX),
    VEIL2_WATCH("/proc/self/mountinfo", false, KSU_VEIL2_PROBE_MOUNTINFO),
    VEIL2_WATCH("/proc/self/maps", false, KSU_VEIL2_PROBE_PROC_MAPS),
    VEIL2_WATCH("/proc/kallsyms", false, KSU_VEIL2_PROBE_KALLSYMS),
};

static __u16 veil2_match(const char *path)
{
    int i;

    for (i = 0; i < ARRAY_SIZE(veil2_watch_list); i++) {
        const struct veil2_watch *watch = &veil2_watch_list[i];

        if (watch->prefix) {
            if (!strncmp(path, watch->path, watch->path_len))
                return watch->kind;
        } else if (!strcmp(path, watch->path)) {
            return watch->kind;
        }
    }
    return 0;
}

/* ------------------------------------------------------------------ */
/* Event reporting                                                    */
/* ------------------------------------------------------------------ */

void ksu_veil_report(uid_t uid, const char *path, __u16 kind)
{
    struct ksu_veil2_event event;
    u32 coalesced;
    u64 now;

    if (!READ_ONCE(veil2_enabled))
        return;

    atomic64_inc(&veil2_probe_count);
    ksu_veil_history_record(uid, kind);

    /*
     * A real su probe from an app that is not allowed yet is also a root
     * request. Notify once per uid per boot; uncloaking resets the mark.
     */
    if (kind == KSU_VEIL2_PROBE_SU && uid >= 10000 && !ksu_is_allow_uid_for_current(uid) && veil2_notify_once(uid))
        ksu_sulog_emit_grant_root(-EPERM, uid, uid, GFP_ATOMIC);

    coalesced = veil2_dedup_bump(uid, kind);
    if (!coalesced)
        return;

    memset(&event, 0, sizeof(event));
    event.version = KSU_VEIL2_EVENT_VERSION;
    event.kind = kind;
    event.uid = uid;
    event.pid = current->pid;
    event.count = coalesced;
    event.seq = (u64)atomic64_inc_return(&veil2_event_seq);
    now = ktime_get_boottime_ns();
    event.ts_ns = now;
    if (path)
        strscpy(event.path, path, sizeof(event.path));

    pr_info_ratelimited("veil2: probe uid=%u pid=%d kind=%u path=%s x%u\n", uid, current->pid, kind, path ? path : "?",
                        coalesced);

    if (ksu_event_queue_push(&veil2_queue, kind, 0, &event, sizeof(event), GFP_ATOMIC))
        atomic_inc(&veil2_queue_dropped);
}

/* ------------------------------------------------------------------ */
/* Kprobe-based path observation                                      */
/* ------------------------------------------------------------------ */

#ifdef CONFIG_KPROBES
static void veil2_check_path(const char __user *filename)
{
    char path[KSU_VEIL2_PATH_MAX];
    uid_t uid;
    __u16 kind;

    if (!READ_ONCE(veil2_enabled) || !filename)
        return;

    uid = current_uid().val;
    if (uid == 0 || (uid % 100000) < 10000)
        return; /* only real app uids are interesting */
    if (ksu_is_allow_uid_for_current(uid))
        return; /* granted apps use the sucompat redirect */

    if (ksu_strncpy_from_user_nofault(path, (const void __user *)untagged_addr((unsigned long)filename),
                                      sizeof(path)) <= 0)
        return;
    path[sizeof(path) - 1] = '\0';
    if (!path[0])
        return;

    kind = veil2_match(path);
    if (!kind)
        return;

    ksu_veil_report(uid, path, kind);

    if (READ_ONCE(veil2_auto_cloak))
        ksu_veil_cloak_add(uid);
}

static int veil2_probe_pre(struct kprobe *probe, struct pt_regs *regs)
{
    veil2_check_path((const char __user *)PT_REGS_PARM2(regs));
    return 0;
}

struct veil2_kprobe {
    struct kprobe kp;
    bool armed;
};

static struct veil2_kprobe veil2_probes[] = {
    {
        .kp = {
            .symbol_name = "do_faccessat",
            .pre_handler = veil2_probe_pre,
        },
    },
    {
        .kp = {
            .symbol_name = "do_sys_openat2",
            .pre_handler = veil2_probe_pre,
        },
    },
    {
        .kp = {
            .symbol_name = "do_faccessat2",
            .pre_handler = veil2_probe_pre,
        },
    },
};

static DEFINE_MUTEX(veil2_kprobe_lock);

static void veil2_kprobe_arm(void)
{
    int i;

    mutex_lock(&veil2_kprobe_lock);
    for (i = 0; i < ARRAY_SIZE(veil2_probes); i++) {
        struct veil2_kprobe *probe = &veil2_probes[i];
        int ret;

        if (probe->armed)
            continue;

        /* unregister_kprobe() leaves .addr resolved, so clear it for re-arm. */
        probe->kp.addr = NULL;
        ret = register_kprobe(&probe->kp);
        if (ret) {
            pr_warn("veil2: probe %s unavailable: %d\n", probe->kp.symbol_name, ret);
            continue;
        }
        probe->armed = true;
    }
    mutex_unlock(&veil2_kprobe_lock);

    pr_info("veil2: probe hooks armed\n");
}

static void veil2_kprobe_disarm(void)
{
    int i;

    mutex_lock(&veil2_kprobe_lock);
    for (i = 0; i < ARRAY_SIZE(veil2_probes); i++) {
        struct veil2_kprobe *probe = &veil2_probes[i];

        if (!probe->armed)
            continue;
        unregister_kprobe(&probe->kp);
        probe->armed = false;
    }
    mutex_unlock(&veil2_kprobe_lock);

    pr_info("veil2: probe hooks disarmed\n");
}

static u32 veil2_kprobe_count(void)
{
    u32 count = 0;
    int i;

    mutex_lock(&veil2_kprobe_lock);
    for (i = 0; i < ARRAY_SIZE(veil2_probes); i++) {
        if (veil2_probes[i].armed)
            count++;
    }
    mutex_unlock(&veil2_kprobe_lock);
    return count;
}
#else
static void veil2_kprobe_arm(void)
{
    pr_warn("veil2: CONFIG_KPROBES is disabled; path observation unavailable\n");
}

static void veil2_kprobe_disarm(void)
{
}

static u32 veil2_kprobe_count(void)
{
    return 0;
}
#endif

/* ------------------------------------------------------------------ */
/* Event fd                                                           */
/* ------------------------------------------------------------------ */

static DEFINE_MUTEX(veil2_fd_lock);
static bool veil2_fd_active;

static ssize_t veil2_read(struct file *file, char __user *buf, size_t count, loff_t *ppos)
{
    return ksu_event_queue_read(&veil2_queue, buf, count, file->f_flags);
}

static unsigned __bitwise veil2_poll(struct file *file, poll_table *wait)
{
    return ksu_event_queue_poll(&veil2_queue, file, wait);
}

static int veil2_release(struct inode *inode, struct file *file)
{
    mutex_lock(&veil2_fd_lock);
    veil2_fd_active = false;
    mutex_unlock(&veil2_fd_lock);
    pr_info("veil2: event fd released\n");
    return 0;
}

static const struct file_operations veil2_fops = {
    .owner = THIS_MODULE,
    .read = veil2_read,
    .poll = veil2_poll,
    .release = veil2_release,
    .llseek = noop_llseek,
};

int ksu_install_veil_fd(void)
{
    struct file *file;
    int fd;

    mutex_lock(&veil2_fd_lock);

    if (veil2_fd_active) {
        fd = -EBUSY;
        goto out_unlock;
    }

    if (READ_ONCE(veil2_queue.closed)) {
        fd = -EPIPE;
        goto out_unlock;
    }

    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0)
        goto out_unlock;

    file = anon_inode_getfile("[ksu_veil2]", &veil2_fops, NULL, O_RDONLY | O_CLOEXEC);
    if (IS_ERR(file)) {
        put_unused_fd(fd);
        fd = PTR_ERR(file);
        goto out_unlock;
    }

    veil2_fd_active = true;
    fd_install(fd, file);
    pr_info("veil2: event fd %d installed for pid %d\n", fd, current->pid);

out_unlock:
    mutex_unlock(&veil2_fd_lock);
    return fd;
}

/* ------------------------------------------------------------------ */
/* Stats and feature wiring                                           */
/* ------------------------------------------------------------------ */

void ksu_veil_stats(struct ksu_veil2_stats *out)
{
    if (!out)
        return;

    memset(out, 0, sizeof(*out));
    out->cloaked = READ_ONCE(veil2_cloak_total);
    out->history = READ_ONCE(veil2_history_total);
    out->probes = (u32)atomic64_read(&veil2_probe_count);
    out->suppressed = (u32)atomic_read(&veil2_suppressed);
    out->history_dropped = (u32)atomic_read(&veil2_history_dropped);
    out->queue_dropped = (u32)atomic_read(&veil2_queue_dropped);
    out->kprobes = veil2_kprobe_count();
    out->auto_cloak = READ_ONCE(veil2_auto_cloak) ? 1 : 0;
}

static int veil2_feature_get(u64 *value)
{
    *value = READ_ONCE(veil2_enabled) ? 1 : 0;
    return 0;
}

static int veil2_feature_set(u64 value)
{
    bool enable = value != 0;

    if (enable == READ_ONCE(veil2_enabled))
        return 0;

    WRITE_ONCE(veil2_enabled, enable);
    if (enable)
        veil2_kprobe_arm();
    else
        veil2_kprobe_disarm();

    pr_info("veil2: %s\n", enable ? "enabled" : "disabled");
    return 0;
}

static const struct ksu_feature_handler veil2_handler = {
    .feature_id = KSU_FEATURE_ORIGIN_VEIL_V2,
    .name = "veil2",
    .get_handler = veil2_feature_get,
    .set_handler = veil2_feature_set,
};

void __init ksu_veil_init(void)
{
    int ret;

    WRITE_ONCE(veil2_enabled, false);
    WRITE_ONCE(veil2_auto_cloak, false);
    veil2_cloak_total = 0;
    veil2_history_total = 0;
    memset(veil2_dedup, 0, sizeof(veil2_dedup));
    memset(veil2_notify_slots, 0, sizeof(veil2_notify_slots));

    ksu_event_queue_init(&veil2_queue, VEIL2_QUEUE_DEPTH, sizeof(struct ksu_veil2_event));

    ret = ksu_register_feature_handler(&veil2_handler);
    if (ret)
        pr_err("veil2: failed to register feature handler: %d\n", ret);

    mutex_lock(&veil2_fd_lock);
    veil2_fd_active = false;
    mutex_unlock(&veil2_fd_lock);

    pr_info("veil2: initialized\n");
}

void __exit ksu_veil_exit(void)
{
    WRITE_ONCE(veil2_enabled, false);
    veil2_kprobe_disarm();
    ksu_veil_cloak_clear();
    ksu_veil_history_clear();
    veil2_notify_clear();
    ksu_unregister_feature_handler(KSU_FEATURE_ORIGIN_VEIL_V2);

    mutex_lock(&veil2_fd_lock);
    veil2_fd_active = false;
    mutex_unlock(&veil2_fd_lock);

    ksu_event_queue_close(&veil2_queue);
    ksu_event_queue_destroy(&veil2_queue);
}
