// SPDX-License-Identifier: GPL-2.0-only
/*
 * OriginSU SELinux mode switch.
 *
 * Exposes the SELinux enforcing state as a kernel feature so the manager
 * (and `ksud feature set selinux`) can switch between enforcing (0, the
 * default, matching historical behavior) and permissive (1) at runtime.
 * The choice persists via ksud's .feature_config and is re-applied on
 * every boot by ksud's init_features(), like every other feature.
 */

#include <linux/init.h>

#include "klog.h" // IWYU pragma: keep
#include "policy/feature.h"
#include "selinux/selinux.h"
#include "feature/selinux_mode.h"

// true = permissive requested. Default false: enforcing, preserving the
// historical behavior (the driver force-enforces at boot).
static bool ksu_selinux_permissive __read_mostly = false;

bool ksu_is_selinux_permissive(void)
{
    return ksu_selinux_permissive;
}

static int ksu_selinux_mode_feature_get(u64 *value)
{
    // Report the real enforcing state, not just the requested one: if
    // SELinux is disabled entirely the device is effectively permissive.
    *value = getenforce() ? 0 : 1;
    return 0;
}

static int ksu_selinux_mode_feature_set(u64 value)
{
    bool permissive = value != 0;

    ksu_selinux_permissive = permissive;
    setenforce(!permissive);
    pr_info("selinux_mode: set to %s\n", permissive ? "permissive" : "enforcing");
    return 0;
}

static const struct ksu_feature_handler ksu_selinux_mode_handler = {
    .feature_id = KSU_FEATURE_SELINUX_MODE,
    .name = "selinux",
    .get_handler = ksu_selinux_mode_feature_get,
    .set_handler = ksu_selinux_mode_feature_set,
};

void __init ksu_selinux_mode_init(void)
{
    if (ksu_register_feature_handler(&ksu_selinux_mode_handler)) {
        pr_err("Failed to register selinux feature handler\n");
    }
}

void __exit ksu_selinux_mode_exit(void)
{
    ksu_unregister_feature_handler(KSU_FEATURE_SELINUX_MODE);
}
