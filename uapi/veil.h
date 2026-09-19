#ifndef __KSU_UAPI_VEIL_H
#define __KSU_UAPI_VEIL_H

#include <linux/types.h>

#define KSU_VEIL_EVENT_VERSION 1
#define KSU_VEIL_PATH_LEN 128

/* What kind of root/identity artifact the app probed for. */
enum ksu_veil_kind {
    KSU_VEIL_KIND_SU = 1, /* su binary path */
    KSU_VEIL_KIND_MAGISK = 2, /* magisk paths */
    KSU_VEIL_KIND_KSU = 3, /* KernelSU paths (/data/adb/ksu, ...) */
    KSU_VEIL_KIND_MODULES = 4, /* /data/adb/modules */
    KSU_VEIL_KIND_PKGLIST = 5, /* app-list enumeration (packages.list) */
    KSU_VEIL_KIND_BUSYBOX = 6, /* busybox / toybox-su */
    KSU_VEIL_KIND_SU_EXEC = 7, /* app actually EXECUTED su (a real root request, not a probe) */
};
#define KSU_VEIL_KIND_MAX 7

/*
 * One probe event, delivered as the payload of a ksu_event_record over the
 * veil fd (see KSU_IOCTL_VEIL_GET_FD). The generic record header
 * (ksu_event_record_hdr) is the same one used by the sulog stream.
 */
struct ksu_veil_event {
    __u16 version;
    __u16 kind;
    __u32 uid; /* appid-resolved uid of the probing app */
    __u32 pid;
    __u32 count; /* hits coalesced within the dedup window */
    __u64 ts_ns;
    char path[KSU_VEIL_PATH_LEN]; /* artifact path that was probed */
};
/* Note: fields are naturally aligned (no padding), so the struct is laid out
 * identically with or without __packed. We omit __packed so bindgen (which has
 * no kernel __packed macro) doesn't treat it as a tentative variable. */

#endif
