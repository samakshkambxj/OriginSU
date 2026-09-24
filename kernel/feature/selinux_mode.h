#ifndef __KSU_H_SELINUX_MODE
#define __KSU_H_SELINUX_MODE

#include <linux/init.h>
#include <linux/types.h>

// true once permissive mode has been requested via the feature interface.
bool ksu_is_selinux_permissive(void);

void __init ksu_selinux_mode_init(void);

void __exit ksu_selinux_mode_exit(void);

#endif
