#ifndef __KSU_H_KSU_SYSCALL_HOOK
#define __KSU_H_KSU_SYSCALL_HOOK
#include <asm/syscall.h>

#if defined(__x86_64__)
typedef sys_call_ptr_t syscall_fn_t;
#elif defined(__riscv)
typedef long (*syscall_fn_t)(const struct pt_regs *);
#endif

extern syscall_fn_t *ksu_syscall_table;

// Dispatcher slot number in syscall table
extern int ksu_dispatcher_nr;

// Syscall hook handler type.
// orig_nr: the original syscall number before redirection
// regs: the original pt_regs from userspace
// Handler is responsible for calling ksu_syscall_table[orig_nr](regs) if needed.
typedef long (*ksu_syscall_hook_fn)(int orig_nr, const struct pt_regs *regs);

// --- Dispatcher-based hook API (register/unregister) ---
// Register a handler into the dispatcher's routing table for syscall @nr.
// When a marked process invokes syscall @nr, the sys_enter tracepoint redirects
// it to the unified dispatcher, which looks up @fn by @nr and calls it.
// Does NOT modify the syscall table itself — the dispatcher slot is shared.
// Returns 0 on success, -EEXIST if already registered, -EINVAL if nr invalid.
int ksu_register_syscall_hook(int nr, ksu_syscall_hook_fn fn);

// Remove a handler from the dispatcher's routing table for syscall @nr.
// The syscall table is not touched — only the dispatcher stops routing @nr.
void ksu_unregister_syscall_hook(int nr);

// Check if a handler is registered in the dispatcher for syscall @nr.
bool ksu_has_syscall_hook(int nr);

// --- Direct syscall table patching API (hook/unhook) ---
// Directly overwrite syscall_table[@nr] with @fn using fixmap + stop_machine.
// Saves the original handler to *@old (if non-NULL) and records the entry
// for restoration at module exit. Use this for boot-time hooks that replace
// a real syscall entry (e.g. ksud hooking __NR_execve/__NR_read/__NR_fstat).
// The live slot must currently point into core kernel text, otherwise the
// patch is refused and a negative errno is returned (a mis-resolved table
// must never be written to: on execve that means a bootloop). Returns 0 on
// success, negative errno otherwise.
int ksu_syscall_table_hook(int nr, syscall_fn_t fn, syscall_fn_t *old);

// Restore syscall_table[@nr] to its original value recorded by
// ksu_syscall_table_hook(), and remove the entry from the tracking list.
// The restore only happens while the live slot still holds our hook; if
// someone else replaced the entry after us, our record is dropped without
// writing so their hook is left intact. Use this to cleanly undo a direct
// hook when it is no longer needed
// (e.g. ksud unhooking __NR_read after init.rc injection is done).
void ksu_syscall_table_unhook(int nr);

#ifdef CONFIG_KSU_TAMPER_SYSCALL_TABLE
// Directly patch the hooked syscall entries (setresuid, execve, execveat,
// newfstatat, faccessat) with tamper trampolines. No tracepoint is
// registered and no dispatcher slot is consumed. Entries whose live slot
// does not point into core kernel text are skipped individually, so one
// bad slot can never take the device down. Returns the number of entries
// that could not be hooked (0 = all good).
int ksu_tamper_install(void);

// Saved original handler for a tampered entry, or NULL when @nr was not
// tampered. Handlers use this (instead of the live table, which points at
// our trampolines) to call through to the real syscall.
syscall_fn_t ksu_tamper_saved_orig(int nr);
#endif

void ksu_syscall_hook_init(void);
void ksu_syscall_hook_exit(void);

#endif
