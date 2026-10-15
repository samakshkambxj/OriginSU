/* SPDX-License-Identifier: GPL-2.0 */
#ifndef __SUSFS_KSU_UMOUNT_GATE_H
#define __SUSFS_KSU_UMOUNT_GATE_H

#include <linux/cred.h>
#include <linux/sched.h>
#include <linux/types.h>

/* Set once, by ksu_umount_gate.c's init, from whether KernelSU's symbol was found: false = the gates
 * ask KernelSU, true = answer the pre-#34 way (uid >= 10000) without calling anything.  Read by the
 * gates, never recomputed, and never a loader parameter: the only thing that matters is one fact
 * about the device, and it is established where the module can still see the whole picture. */
extern bool susfs_ksu_umount_gate_off;

/* Defined in ksu_umount_gate.c: the one indirect call, through the address resolved at load. */
bool susfs_ksu_umount_ask(uid_t uid);

/* Upstream's susfs_is_current_proc_umounted_app(): app uid AND KernelSU would umount for it.  The
 * uid half is upstream's literal 10000; the flag half is the predicate that flag is set from, so a
 * su-granted app, the manager and a profile with "umount modules" off are not hidden from. */
static inline bool susfs_is_current_proc_umounted_app(void)
{
	uid_t uid = current_uid().val;

	if (uid < 10000)
		return false;
	if (READ_ONCE(susfs_ksu_umount_gate_off))
		return true;	/* nothing resolved to ask: hide from app uids, as this module did before #34 */

	return susfs_ksu_umount_ask(uid);
}

/* The uids KernelSU itself asks the umount question about, which is the set its
 * TIF_PROC_UMOUNTED flag can ever be set for: an app uid, the webview zygote, or an isolated
 * process (kernel/feature/kernel_umount.c:93, with is_appuid()/is_isolated_process() and the
 * range constants from kernel/policy/allowlist.h:8-13).  Asking ksu_uid_should_umount() about a
 * uid OUTSIDE this set does not answer the same question: that function returns
 * default_non_root_profile.umount_modules for a uid that has no profile, and the value is TRUE
 * (kernel/policy/allowlist.c:55), so uid 0, 1000 and 2000 all answer "yes" - which is not what
 * upstream's flag says about root, init, system_server or the adb shell. */
static inline bool susfs_ksu_umount_uid_applies(uid_t uid)
{
	uid_t appid = uid % 100000;			/* PER_USER_RANGE */

	return (appid >= 10000 && appid <= 19999) ||	/* is_appuid() */
	       (appid >= 99000 && appid <= 99999) ||	/* is_isolated_process() */
	       uid == 1053;				/* WEBVIEW_ZYGOTE_UID */
}

/* Upstream's susfs_is_current_proc_umounted(): the same umount answer WITHOUT the app-uid half,
 * which is what its UID_UMOUNTED_PROC rule scheme asks for (and what makes that scheme cover
 * init-derived processes too - the webview zygote is one of them, and it is inside the set
 * above).  There is deliberately no uid fallback when KernelSU's symbol did not resolve: there
 * would be nothing to ask, and answering "true" would hide from every process alive - false is
 * what the upstream flag does on a kernel without the SUSFS patch anyway.
 *
 * The boundary is what keeps this a mirror of the flag rather than of the question: everything
 * KernelSU never umounts for - root, init, system_server, the adb shell, su itself - is answered
 * false here instead of being handed the "no profile, therefore umount" default. */
static inline bool susfs_is_current_proc_umounted(void)
{
	uid_t uid = current_uid().val;

	if (READ_ONCE(susfs_ksu_umount_gate_off))
		return false;
	if (!susfs_ksu_umount_uid_applies(uid))
		return false;

	return susfs_ksu_umount_ask(uid);
}

int susfs_ksu_umount_gate_init(void);

#endif /* __SUSFS_KSU_UMOUNT_GATE_H */
