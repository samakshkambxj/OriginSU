#ifndef __KSU_UAPI_SU_REQUEST_H
#define __KSU_UAPI_SU_REQUEST_H

#include <linux/types.h>

/*
 * True blocking root prompt (Magisk-style Allow/Deny).
 *
 * Flow:
 *  1. An app without allow_su calls su (ioctl GRANT_ROOT, later sucompat execve).
 *  2. Kernel creates a request, queues it, and sleeps the caller
 *     (wait_event_interruptible_timeout) until the manager answers or it times out.
 *  3. Manager/ksud polls with KSU_IOCTL_SU_REQUEST_POLL and answers with
 *     KSU_IOCTL_SU_REQUEST_ANSWER. Secure default on timeout is DENY.
 *  4. On ALLOW with remember=1 the kernel persists allow_su for that uid.
 */

#define KSU_SU_REQUEST_COMM_LEN 16

/* Poll returns 0 + filled struct when a request was pending, -EAGAIN when none. */
struct ksu_su_request_poll_cmd {
	__u64 id; /* Output: request id */
	__u32 uid; /* Output: requesting app uid */
	__u32 pid; /* Output: requesting pid */
	__u32 euid; /* Output: requesting euid */
	char comm[KSU_SU_REQUEST_COMM_LEN]; /* Output: task comm */
	__aligned_u64 ts_ns; /* Output: boottime ns when queued */
} __attribute__((packed));

/* Decision values for answer.cmd_decision */
enum ksu_su_request_decision {
	KSU_SU_REQUEST_DENY = 0,
	KSU_SU_REQUEST_ALLOW = 1,
};

struct ksu_su_request_answer_cmd {
	__u64 id; /* Input: request id from poll */
	__u32 decision; /* Input: enum ksu_su_request_decision */
	__u32 remember; /* Input: 1 = persist allow_su on ALLOW, 0 = one-shot */
} __attribute__((packed));

/* Prompt behavior knobs, exposed via the feature framework. */
enum ksu_su_prompt_feature_value {
	/* KSU_FEATURE_SU_PROMPT value: 0 = auto-deny (legacy), 1 = blocking prompt */
	KSU_SU_PROMPT_MODE_DENY = 0,
	KSU_SU_PROMPT_MODE_PROMPT = 1,
};

#endif
