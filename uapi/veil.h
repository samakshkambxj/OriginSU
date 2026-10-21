#ifndef __KSU_UAPI_VEIL_H
#define __KSU_UAPI_VEIL_H

#include <linux/types.h>

/*
 * Origin Veil 2.0 UAPI.
 *
 * This is intentionally a new ABI. The feature id, ioctl numbers, command
 * structs and event layout are all distinct from the first Veil interface so
 * an old manager can never accidentally drive the new kernel code (or vice
 * versa).
 */

#define KSU_VEIL2_ABI_VERSION 2
#define KSU_VEIL2_EVENT_VERSION 2
#define KSU_VEIL2_PATH_MAX 160

/* What kind of root/identity artifact the app probed for. */
enum ksu_veil2_probe {
    KSU_VEIL2_PROBE_SU = 1,          /* su binary path */
    KSU_VEIL2_PROBE_MAGISK = 2,      /* Magisk paths */
    KSU_VEIL2_PROBE_KSU = 3,         /* KernelSU / ksud paths */
    KSU_VEIL2_PROBE_MODULES = 4,     /* /data/adb/modules */
    KSU_VEIL2_PROBE_PACKAGE_DB = 5,  /* package list / package database */
    KSU_VEIL2_PROBE_BUSYBOX = 6,     /* busybox / toybox helpers */
    KSU_VEIL2_PROBE_SU_EXEC = 7,     /* app actually executed su */
    KSU_VEIL2_PROBE_MOUNTINFO = 8,   /* /proc/self/mountinfo */
    KSU_VEIL2_PROBE_PROC_MAPS = 9,   /* /proc/self/maps */
    KSU_VEIL2_PROBE_KALLSYMS = 10,   /* /proc/kallsyms */
};
#define KSU_VEIL2_PROBE_MAX 10

/*
 * One probe event, delivered as the payload of a ksu_event_record over the
 * veil fd (see KSU_IOCTL_VEIL2_GET_FD). The generic record header is the same
 * one used by the sulog stream.
 */
struct ksu_veil2_event {
    __u16 version;
    __u16 kind;
    __u32 uid;
    __u32 pid;
    __u32 count; /* hits coalesced into this event */
    __u32 flags;
    __u64 seq;
    __u64 ts_ns;
    char path[KSU_VEIL2_PATH_MAX];
};

#endif /* __KSU_UAPI_VEIL_H */
