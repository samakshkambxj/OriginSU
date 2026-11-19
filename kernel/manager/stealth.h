/* SPDX-License-Identifier: GPL-2.0 */
#ifndef __KSU_H_STEALTH
#define __KSU_H_STEALTH

#include <linux/errno.h>
#include <linux/types.h>

#ifdef CONFIG_KSU_STEALTH
void ksu_stealth_load(void);
bool ksu_stealth_is_enabled(void);
int ksu_stealth_set(bool enabled);
#else
static inline void ksu_stealth_load(void) {}
static inline bool ksu_stealth_is_enabled(void) { return false; }
static inline int ksu_stealth_set(bool enabled)
{
	(void)enabled;
	return -ENODEV;
}
#endif /* CONFIG_KSU_STEALTH */

#endif /* __KSU_H_STEALTH */
