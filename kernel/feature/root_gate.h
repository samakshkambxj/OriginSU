#ifndef __KSU_H_ROOT_GATE
#define __KSU_H_ROOT_GATE

#include <linux/init.h>
#include <linux/types.h>

// Root Gate: a global, reversible suspension of every non-manager root grant.
// Value 1 means root is allowed, 0 means suspended. The manager UID and
// already-rooted ksu domain processes always bypass the gate, so the manager
// can always turn it back on.
bool ksu_root_gate_allows(void);

void __init ksu_root_gate_init(void);

void __exit ksu_root_gate_exit(void);

#endif // __KSU_H_ROOT_GATE
