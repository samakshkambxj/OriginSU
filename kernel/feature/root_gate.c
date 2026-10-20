// Root Gate: kernel-side master switch for root grants.
//
// Unlike per-app profiles, this is a single reversible bit that suspends every
// su grant at once. The manager keeps working (and can resume root) because
// __ksu_is_allow_uid() still allows the manager UID and ksu domain processes.

#include "feature/root_gate.h"

#include "klog.h" // IWYU pragma: keep
#include "policy/feature.h"

static bool root_gate_allowed = true;

static int root_gate_get(u64 *value)
{
    if (!value) {
        return -EINVAL;
    }
    *value = root_gate_allowed ? 1 : 0;
    return 0;
}

static int root_gate_set(u64 value)
{
    bool allowed = value != 0;

    if (allowed == root_gate_allowed) {
        return 0;
    }

    root_gate_allowed = allowed;
    pr_info("root_gate: root %s\n", allowed ? "resumed" : "suspended");
    return 0;
}

static const struct ksu_feature_handler root_gate_handler = {
    .feature_id = KSU_FEATURE_ROOT_GATE,
    .name = "root_gate",
    .get_handler = root_gate_get,
    .set_handler = root_gate_set,
};

bool ksu_root_gate_allows(void)
{
    return root_gate_allowed;
}

void __init ksu_root_gate_init(void)
{
    int ret = ksu_register_feature_handler(&root_gate_handler);
    if (ret) {
        pr_err("root_gate: failed to register feature handler: %d\n", ret);
    }
}

void __exit ksu_root_gate_exit(void)
{
    ksu_unregister_feature_handler(KSU_FEATURE_ROOT_GATE);
}
