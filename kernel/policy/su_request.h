#ifndef KSU_SU_REQUEST_H
#define KSU_SU_REQUEST_H

#include <linux/types.h>
#include <linux/pid.h>

/* Blocking root prompt queue. Caller sleeps until manager answers or timeout. */
struct ksu_su_request_info {
    __u64 id;
    __u32 uid;
    __u32 euid;
    __u32 pid;
    char comm[16];
    __u64 ts_ns;
};

/*
 * Wait for a manager decision for uid/euid/pid.
 * Returns 1 = ALLOW (and persists allow_su when remember was set),
 *         0 = DENY/timeout/prompt-disabled.
 * Must be called in sleepable process context (GFP_KERNEL ok).
 */
int ksu_su_request_prompt(__u32 uid, __u32 euid);

/* Manager side: non-blocking poll for the next unanswered request. */
int ksu_su_request_poll(struct ksu_su_request_info *out);

/* Manager side: answer a pending request id. */
int ksu_su_request_answer(__u64 id, __u32 decision, __u32 remember);

bool ksu_su_prompt_is_enabled(void);

void __init ksu_su_request_init(void);
void __exit ksu_su_request_exit(void);

#endif
