#ifdef __aarch64__

#include "../syscall_hook.h"

#include <linux/kallsyms.h>
#include <linux/mutex.h>
#include <asm/cacheflush.h>
#include "infra/symbol_resolver.h"
#include "../patch_memory.h"
#include "arch.h"
#include "klog.h" // IWYU pragma: keep
#ifdef CONFIG_KSU_TAMPER_SYSCALL_TABLE
#include "hook/syscall_event_bridge.h"
#endif

syscall_fn_t *ksu_syscall_table = NULL;
int ksu_dispatcher_nr = -1;

// Hook registration table — read with READ_ONCE from tracepoint/dispatcher
// context, written with WRITE_ONCE from init/exit context.
static ksu_syscall_hook_fn syscall_hooks[__NR_syscalls];

// Track all hooked syscall entries for restoration.
// Protected by hooked_entries_lock.
struct syscall_hook_entry {
    int nr;
    syscall_fn_t orig;
    // Our replacement currently expected in the slot. Restores only happen
    // while the live slot still holds this, so we never clobber a hook
    // installed by someone else after us.
    syscall_fn_t hook;
};

static DEFINE_MUTEX(hooked_entries_lock);
static struct syscall_hook_entry hooked_entries[16];
static int hooked_count = 0;

// The syscall table lives in .rodata, but the address we resolve
// (especially from an LKM via kallsyms) is not guaranteed to be the real
// table, and a slot may already hold a foreign hook. Refuse to patch a
// slot whose current content is not core kernel text: writing blindly
// there corrupts memory, and on execve that means a bootloop. Same idea
// as backslashxx/KernelSU's "NOT pointing to kernel text" guard.
extern char _stext[], _etext[];

static bool ksu_slot_is_kernel_text(syscall_fn_t fn)
{
    unsigned long addr = (unsigned long)fn;

    return fn && addr > (unsigned long)_stext && addr < (unsigned long)_etext;
}

static int patch_syscall_table(int nr, syscall_fn_t fn)
{
    if (ksu_syscall_table == NULL)
        return -ENOENT;
    if (nr < 0 || nr >= __NR_syscalls)
        return -EINVAL;

    pr_info("patch syscall %d, 0x%lx -> 0x%lx\n", nr, (unsigned long)READ_ONCE(ksu_syscall_table[nr]),
            (unsigned long)fn);

    if (ksu_patch_text(&ksu_syscall_table[nr], &fn, sizeof(fn), KSU_PATCH_TEXT_FLUSH_DCACHE)) {
        pr_err("patch syscall %d failed\n", nr);
        return -EIO;
    }

    return 0;
}

// Direct syscall table patching: overwrite syscall_table[nr] with fn,
// save original to *old, and record for restoration at module exit.
// Refuses slots that do not point into core kernel text. Returns 0 on
// success, negative errno otherwise.
int ksu_syscall_table_hook(int nr, syscall_fn_t fn, syscall_fn_t *old)
{
    int i, ret = 0;
    bool added = false;

    if (ksu_syscall_table == NULL)
        return -ENOENT;
    if (nr < 0 || nr >= __NR_syscalls) {
        pr_info("invalid nr: %d\n", nr);
        return -EINVAL;
    }

    mutex_lock(&hooked_entries_lock);

    syscall_fn_t live = READ_ONCE(ksu_syscall_table[nr]);
    if (old)
        *old = live;

    for (i = 0; i < hooked_count; i++) {
        if (hooked_entries[i].nr == nr)
            break;
    }

    if (i < hooked_count) {
        // We already track this slot.
        if (live == hooked_entries[i].hook) {
            // Still ours: idempotent, nothing to do.
            goto out;
        }
        // Slot changed under us: re-validate before touching it.
        if (!ksu_slot_is_kernel_text(live)) {
            pr_err("refuse to re-patch syscall %d: slot 0x%lx is not kernel text\n", nr,
                   (unsigned long)live);
            ret = -EFAULT;
            goto out;
        }
        hooked_entries[i].hook = fn;
    } else {
        if (!ksu_slot_is_kernel_text(live)) {
            pr_err("refuse to patch syscall %d: slot 0x%lx is not kernel text\n", nr,
                   (unsigned long)live);
            ret = -EFAULT;
            goto out;
        }
        if (hooked_count >= (int)ARRAY_SIZE(hooked_entries)) {
            pr_warn("hooked_entries full, cannot track syscall %d for restoration\n", nr);
            ret = -ENOSPC;
            goto out;
        }
        // First hook wins: keep the original for restoration.
        hooked_entries[hooked_count].nr = nr;
        hooked_entries[hooked_count].orig = live;
        hooked_entries[hooked_count].hook = fn;
        hooked_count++;
        added = true;
    }

    ret = patch_syscall_table(nr, fn);
    if (ret && added) {
        // Patch failed: roll back the record so a hook that was never
        // installed is neither restored nor used as a call-through target.
        hooked_count--;
    }

out:
    mutex_unlock(&hooked_entries_lock);
    return ret;
}

// Restore syscall_table[nr] to its original value and remove from tracking list.
// The restore only happens while the live slot still holds our hook; if
// someone else replaced the entry after us, the record is dropped without
// writing so their hook is left intact.
void ksu_syscall_table_unhook(int nr)
{
    int i;

    if (ksu_syscall_table == NULL)
        return;
    if (nr < 0 || nr >= __NR_syscalls)
        return;

    mutex_lock(&hooked_entries_lock);

    for (i = 0; i < hooked_count; i++) {
        if (hooked_entries[i].nr == nr) {
            syscall_fn_t live = READ_ONCE(ksu_syscall_table[nr]);
            if (live != hooked_entries[i].hook) {
                pr_warn("syscall %d slot 0x%lx no longer ours, skip restore\n", nr,
                        (unsigned long)live);
            } else if (patch_syscall_table(nr, hooked_entries[i].orig)) {
                pr_err("restore syscall %d failed, keeping record\n", nr);
                mutex_unlock(&hooked_entries_lock);
                return;
            }
            // Remove entry by swapping with last
            hooked_entries[i] = hooked_entries[--hooked_count];
            mutex_unlock(&hooked_entries_lock);
            pr_info("unhooked syscall %d\n", nr);
            return;
        }
    }

    mutex_unlock(&hooked_entries_lock);
    pr_warn("syscall %d not found in hooked entries\n", nr);
}

static int __init ksu_find_ni_syscall_slots(int *out_slots, int max_slots)
{
    unsigned long ni_syscall;
    int i, count = 0;

    if (!ksu_syscall_table || max_slots <= 0)
        return 0;

    ni_syscall = (unsigned long)ksu_resolve_symbol_for_functable_hook("__arm64_sys_ni_syscall");

    pr_info("sys_ni_syscall: 0x%lx\n", ni_syscall);

    if (!ni_syscall)
        return 0;

    for (i = 0; i < __NR_syscalls && count < max_slots; i++) {
        if ((unsigned long)ksu_syscall_table[i] == ni_syscall) {
            out_slots[count++] = i;
            pr_info("ni_syscall %d: %d\n", count, i);
        }
    }

    return count;
}

// Unified dispatcher: reads original NR from x8, dispatches to handler.
// Validates that syscallno matches our dispatcher slot (i.e. we redirected it),
// otherwise it's a spurious call — return -ENOSYS.
static long __nocfi ksu_syscall_dispatcher(const struct pt_regs *regs)
{
    if (regs->syscallno != ksu_dispatcher_nr)
        return -ENOSYS;

    int orig_nr = (int)PT_REGS_ORIG_SYSCALL(regs);

    if (regs->syscallno == orig_nr)
        return -ENOSYS;

    // Restore registers to original state before dispatching
    ((struct pt_regs *)regs)->syscallno = orig_nr;
    PT_REGS_ORIG_SYSCALL((struct pt_regs *)regs) = orig_nr;

    if (likely(orig_nr >= 0 && orig_nr < __NR_syscalls)) {
        ksu_syscall_hook_fn fn = READ_ONCE(syscall_hooks[orig_nr]);
        if (likely(fn))
            return fn(orig_nr, regs);
    }

    return -ENOSYS;
}

// Register a handler into the dispatcher's routing table.
// Does not modify the syscall table — the dispatcher slot is shared by all hooks.
int ksu_register_syscall_hook(int nr, ksu_syscall_hook_fn fn)
{
    if (nr < 0 || nr >= __NR_syscalls)
        return -EINVAL;
    if (READ_ONCE(syscall_hooks[nr])) {
        pr_warn("syscall hook for nr=%d already registered, skip\n", nr);
        return -EEXIST;
    }
    WRITE_ONCE(syscall_hooks[nr], fn);
    pr_info("registered syscall hook for nr=%d\n", nr);
    return 0;
}

// Remove a handler from the dispatcher's routing table.
// The syscall table is not touched — only the dispatcher stops routing this nr.
void ksu_unregister_syscall_hook(int nr)
{
    if (nr < 0 || nr >= __NR_syscalls)
        return;
    WRITE_ONCE(syscall_hooks[nr], NULL);
    pr_info("unregistered syscall hook for nr=%d\n", nr);
}

bool ksu_has_syscall_hook(int nr)
{
    if (nr < 0 || nr >= __NR_syscalls)
        return false;
    return READ_ONCE(syscall_hooks[nr]) != NULL;
}

void __init ksu_syscall_hook_init(void)
{
    int ni_slot;

    memset(syscall_hooks, 0, sizeof(syscall_hooks));

    ksu_syscall_table = (syscall_fn_t *)ksu_resolve_symbol_for_functable_hook("sys_call_table");
    pr_info("sys_call_table=0x%lx", (unsigned long)ksu_syscall_table);

    if (!ksu_syscall_table)
        return;

#ifdef CONFIG_KSU_TAMPER_SYSCALL_TABLE
    // Tamper mode: no dispatcher slot, the hooked entries are patched
    // directly by ksu_tamper_install().
    ksu_dispatcher_nr = -1;
    pr_info("tamper mode: syscall dispatcher not installed\n");
#else
    // Find one ni_syscall slot for the dispatcher
    if (ksu_find_ni_syscall_slots(&ni_slot, 1) < 1) {
        pr_err("failed to find ni_syscall slot for dispatcher\n");
        return;
    }

    ksu_dispatcher_nr = ni_slot;
    ksu_syscall_table_hook(ksu_dispatcher_nr, (syscall_fn_t)ksu_syscall_dispatcher, NULL);
    pr_info("dispatcher installed at slot %d\n", ksu_dispatcher_nr);
#endif
}

void __exit ksu_syscall_hook_exit(void)
{
    int i;

    if (!ksu_syscall_table)
        goto clear_state;

    // First, restore all patched syscall table entries while the dispatcher
    // and hook table are still intact, so in-flight syscalls see valid state.
    // Slots that no longer hold our hook are left alone (foreign owner).
    mutex_lock(&hooked_entries_lock);
    for (i = 0; i < hooked_count; i++) {
        int nr = hooked_entries[i].nr;
        syscall_fn_t orig = hooked_entries[i].orig;

        if (READ_ONCE(ksu_syscall_table[nr]) != hooked_entries[i].hook) {
            pr_warn("restore: syscall %d no longer ours, skip\n", nr);
            continue;
        }
        pr_info("restore syscall %d to 0x%lx\n", nr, (unsigned long)orig);
        if (ksu_patch_text(&ksu_syscall_table[nr], &orig, sizeof(orig), KSU_PATCH_TEXT_FLUSH_DCACHE)) {
            pr_err("restore syscall %d failed\n", nr);
        }
    }
    hooked_count = 0;
    mutex_unlock(&hooked_entries_lock);

clear_state:
    // Now that the syscall table is restored, clear internal state.
    // At this point the tracepoint is already unregistered and synchronized
    // (done by ksu_syscall_hook_manager_exit before calling us), so no new
    // dispatches will occur.
    memset(syscall_hooks, 0, sizeof(syscall_hooks));
    ksu_dispatcher_nr = -1;

    pr_info("all syscall hooks restored\n");
}

#ifdef CONFIG_KSU_TAMPER_SYSCALL_TABLE
// Direct table-tampering trampolines. Each one runs the shared KSU handler
// for its syscall; the handler calls back to the saved original (never the
// live table entry, which points here).
static long __nocfi ksu_tamper_setresuid(const struct pt_regs *regs)
{
    return ksu_hook_setresuid(__NR_setresuid, regs);
}

static long __nocfi ksu_tamper_execve(const struct pt_regs *regs)
{
    return ksu_hook_execve(__NR_execve, regs);
}

static long __nocfi ksu_tamper_execveat(const struct pt_regs *regs)
{
    return ksu_hook_execveat(__NR_execveat, regs);
}

static long __nocfi ksu_tamper_newfstatat(const struct pt_regs *regs)
{
    return ksu_hook_newfstatat(__NR_newfstatat, regs);
}

static long __nocfi ksu_tamper_faccessat(const struct pt_regs *regs)
{
    return ksu_hook_faccessat(__NR_faccessat, regs);
}

// Install the tamper trampolines. Each entry is validated and hooked
// individually: a slot that does not point into core kernel text is
// skipped (with an error log) instead of being patched blindly, so one
// bad slot can never take the device down with it. Returns the number of
// entries that could not be hooked (0 = all good).
int ksu_tamper_install(void)
{
    int failed = 0;

    if (!ksu_syscall_table) {
        pr_err("tamper: no syscall table, hooks not installed\n");
        return 5;
    }
    failed += ksu_syscall_table_hook(__NR_setresuid, (syscall_fn_t)ksu_tamper_setresuid, NULL) ? 1 : 0;
    failed += ksu_syscall_table_hook(__NR_execve, (syscall_fn_t)ksu_tamper_execve, NULL) ? 1 : 0;
    failed += ksu_syscall_table_hook(__NR_execveat, (syscall_fn_t)ksu_tamper_execveat, NULL) ? 1 : 0;
    failed += ksu_syscall_table_hook(__NR_newfstatat, (syscall_fn_t)ksu_tamper_newfstatat, NULL) ? 1 : 0;
    failed += ksu_syscall_table_hook(__NR_faccessat, (syscall_fn_t)ksu_tamper_faccessat, NULL) ? 1 : 0;
    if (!failed)
        pr_info("tamper: direct syscall table hooks installed, no tracepoint registered\n");
    else
        pr_err("tamper: %d/5 hooks skipped, root functionality will be degraded\n", failed);
    return failed;
}

syscall_fn_t ksu_tamper_saved_orig(int nr)
{
    int i;
    syscall_fn_t orig = NULL;

    mutex_lock(&hooked_entries_lock);
    for (i = 0; i < hooked_count; i++) {
        if (hooked_entries[i].nr == nr) {
            orig = hooked_entries[i].orig;
            break;
        }
    }
    mutex_unlock(&hooked_entries_lock);
    return orig;
}
#endif

#endif /* __aarch64__ */