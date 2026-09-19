//
// Created by weishu on 2022/12/9.
//

#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <android/log.h>
#include <dirent.h>
#include <stdlib.h>
#include <limits.h>
#include <errno.h>

#include "prelude.h"
#include "ksu.h"

static int fd = -1;

static inline int scan_driver_fd() {
	const char *kName = "[ksu_driver]";
	DIR *fd_dir = opendir("/proc/self/fd");
	if (!fd_dir) {
		return -1;
	}

	int found = -1;
	struct dirent *de;
	char path[64];
	char target[PATH_MAX];

	while ((de = readdir(fd_dir)) != NULL) {
		if (de->d_name[0] == '.') {
			continue;
		}

		char *endptr = nullptr;
		long fd_long = strtol(de->d_name, &endptr, 10);
		if (!de->d_name[0] || *endptr != '\0' || fd_long < 0 || fd_long > INT_MAX) {
			continue;
		}

		snprintf(path, sizeof(path), "/proc/self/fd/%s", de->d_name);
		ssize_t n = readlink(path, target, sizeof(target) - 1);
		if (n < 0) {
			continue;
		}
		target[n] = '\0';

		const char *base = strrchr(target, '/');
		base = base ? base + 1 : target;

		if (strstr(base, kName)) {
			found = (int)fd_long;
			break;
		}
	}

	closedir(fd_dir);
	return found;
}

static int ksuctl(unsigned long op, void* arg) {
	if (fd < 0) {
		fd = scan_driver_fd();
	}
	return ioctl(fd, op, arg);
}

static struct ksu_get_info_cmd g_version = {0};

struct ksu_get_info_cmd get_info() {
	if (!g_version.version) {
        if (ksuctl(KSU_IOCTL_GET_INFO, &g_version) < 0) {
            ksuctl(KSU_IOCTL_GET_INFO_LEGACY, &g_version);
            g_version.uapi_version = 0;
        }
	}
	return g_version;
}

uint32_t get_kernel_uapi_version() {
    struct ksu_get_info_cmd info = get_info();
    return info.uapi_version;
}

uint32_t get_manager_uapi_version() {
    return KERNEL_SU_UAPI_VERSION;
}

uint32_t get_version() {
	auto info = get_info();
	return info.version;
}

bool get_allow_list(struct ksu_new_get_allow_list_cmd *cmd) {
    if (ksuctl(KSU_IOCTL_NEW_GET_ALLOW_LIST, cmd) == 0) {
        return true;
    }

    // fallback to legacy
    int size = 0;
    int uids[1024];
    if (legacy_get_allow_list(uids, &size)) {
        cmd->count = size;
        memcpy(cmd->uids, uids, sizeof(int) * size);
        return true;
    }

    return false;
}

bool is_safe_mode() {
    struct ksu_check_safemode_cmd cmd = {};
    if (ksuctl(KSU_IOCTL_CHECK_SAFEMODE, &cmd) == 0) {
        return cmd.in_safe_mode;
    }
    // fallback
    return legacy_is_safe_mode();
}

bool is_lkm_mode() {
    auto info = get_info();
    if (info.version > 0) {
        return (info.flags & KSU_GET_INFO_FLAG_LKM) != 0;
    }
    // Legacy Compatible
    return (legacy_get_info().flags & KSU_GET_INFO_FLAG_LKM) != 0;
}

bool is_manager() {
    auto info = get_info();
    if (info.version > 0) {
        return (info.flags & KSU_GET_INFO_FLAG_MANAGER) != 0;
    }
    // Legacy Compatible
    return legacy_get_info().version > 0;
}

bool is_late_load_mode() {
    auto info = get_info();
    if (info.version > 0) {
        return (info.flags & KSU_GET_INFO_FLAG_LATE_LOAD) != 0;
    }
    return false;
}

bool is_lkm_bundled() {
    auto info = get_info();
    return (info.flags & KSU_GET_INFO_FLAG_LKM) != 0 &&
           (info.flags & KSU_GET_INFO_FLAG_BUNDLED) != 0;
}

bool is_pr_build() {
    auto info = get_info();
    if (info.version > 0) {
        return (info.flags & KSU_GET_INFO_FLAG_PR_BUILD) != 0;
    }
    return false;
}

bool uid_should_umount(int uid) {
    struct ksu_uid_should_umount_cmd cmd = {};
    cmd.uid = uid;
    if (ksuctl(KSU_IOCTL_UID_SHOULD_UMOUNT, &cmd) == 0) {
        return cmd.should_umount;
    }
    return legacy_uid_should_umount(uid);
}

bool set_app_profile(const struct app_profile *profile) {
    struct ksu_set_app_profile_cmd cmd = {};
    cmd.profile = *profile;
    if (ksuctl(KSU_IOCTL_SET_APP_PROFILE, &cmd) == 0) {
        return true;
    }
    return legacy_set_app_profile(profile);
}

int get_app_profile(struct app_profile *profile) {
    struct ksu_get_app_profile_cmd cmd = {.profile = *profile};
    int ret = ksuctl(KSU_IOCTL_GET_APP_PROFILE, &cmd);
    if (ret == 0) {
        *profile = cmd.profile;
        return 0;
    }
    return legacy_get_app_profile(profile->key, profile) ? 0 : -1;
}

bool set_su_enabled(bool enabled) {
    struct ksu_set_feature_cmd cmd = {};
    cmd.feature_id = KSU_FEATURE_SU_COMPAT;
    cmd.value = enabled ? 1 : 0;
    if (ksuctl(KSU_IOCTL_SET_FEATURE, &cmd) == 0) {
        return true;
    }
    return legacy_set_su_enabled(enabled);
}

bool is_su_enabled() {
    struct ksu_get_feature_cmd cmd = {};
    cmd.feature_id = KSU_FEATURE_SU_COMPAT;
    if (ksuctl(KSU_IOCTL_GET_FEATURE, &cmd) == 0 && cmd.supported) {
        return cmd.value != 0;
    }
    return legacy_is_su_enabled();
}

static inline bool get_feature(uint32_t feature_id, uint64_t *out_value, bool *out_supported) {
    struct ksu_get_feature_cmd cmd = {};
    cmd.feature_id = feature_id;
    if (ksuctl(KSU_IOCTL_GET_FEATURE, &cmd) != 0) {
        return false;
    }
    if (out_value) *out_value = cmd.value;
    if (out_supported) *out_supported = cmd.supported;
    return true;
}

static inline bool set_feature(uint32_t feature_id, uint64_t value) {
    struct ksu_set_feature_cmd cmd = {};
    cmd.feature_id = feature_id;
    cmd.value = value;
    return ksuctl(KSU_IOCTL_SET_FEATURE, &cmd) == 0;
}

bool set_kernel_umount_enabled(bool enabled) {
    return set_feature(KSU_FEATURE_KERNEL_UMOUNT, enabled ? 1 : 0);
}

bool is_su_prompt_enabled() {
    uint64_t value = 0;
    bool supported = false;
    if (!get_feature(KSU_FEATURE_SU_PROMPT, &value, &supported)) {
        return false;
    }
    if (!supported) {
        return false;
    }
    return value != 0;
}

bool set_su_prompt_enabled(bool enabled) {
    return set_feature(KSU_FEATURE_SU_PROMPT, enabled ? 1 : 0);
}

bool get_su_prompt_timeout(uint64_t *out_secs) {
    uint64_t value = 0;
    bool supported = false;
    if (!get_feature(KSU_FEATURE_SU_PROMPT_TIMEOUT, &value, &supported)) {
        return false;
    }
    if (!supported) {
        return false;
    }
    if (out_secs) *out_secs = value;
    return true;
}

bool set_su_prompt_timeout(uint64_t secs) {
    return set_feature(KSU_FEATURE_SU_PROMPT_TIMEOUT, secs);
}

bool su_request_poll(struct su_request_poll_result *out) {
    struct ksu_su_request_poll_cmd cmd = {};
    if (ksuctl(KSU_IOCTL_SU_REQUEST_POLL, &cmd) != 0) {
        return false;
    }
    if (out) {
        out->has_request = true;
        out->id = cmd.id;
        out->uid = cmd.uid;
        out->pid = cmd.pid;
        out->euid = cmd.euid;
        memcpy(out->comm, cmd.comm, sizeof(out->comm));
        out->ts_ns = cmd.ts_ns;
    }
    return true;
}

bool su_request_answer(uint64_t id, bool allow, bool remember) {
    struct ksu_su_request_answer_cmd cmd = {};
    cmd.id = id;
    cmd.decision = allow ? KSU_SU_REQUEST_ALLOW : KSU_SU_REQUEST_DENY;
    cmd.remember = remember ? 1 : 0;
    return ksuctl(KSU_IOCTL_SU_REQUEST_ANSWER, &cmd) == 0;
}

bool is_kernel_umount_enabled() {
    uint64_t value = 0;
    bool supported = false;
    if (!get_feature(KSU_FEATURE_KERNEL_UMOUNT, &value, &supported)) {
        return false;
    }
    if (!supported) {
        return false;
    }
    return value != 0;
}

int set_selinux_hide_enabled(bool enabled) {
    if (!set_feature(KSU_FEATURE_SELINUX_HIDE, enabled ? 1 : 0)) {
        return -errno;
    }
    return 0;
}

bool is_selinux_hide_enabled() {
    uint64_t value = 0;
    bool supported = false;
    if (!get_feature(KSU_FEATURE_SELINUX_HIDE, &value, &supported)) {
        return false;
    }
    if (!supported) {
        return false;
    }
    return value != 0;
}

bool is_sulog_enabled() {
    uint64_t value = 0;
    bool supported = false;
    if (!get_feature(KSU_FEATURE_SULOG, &value, &supported)) {
        return false;
    }
    if (!supported) {
        return false;
    }
    return value != 0;
}

bool set_sulog_enabled(bool enabled) {
    return set_feature(KSU_FEATURE_SULOG, enabled ? 1 : 0);
}

int get_sulog_fd(void) {
    struct ksu_get_sulog_fd_cmd cmd = {0};
    int ret = ksuctl(KSU_IOCTL_GET_SULOG_FD, &cmd);
    if (ret < 0) {
        return ret;
    }
    return ret;
}

bool is_veil_enabled() {
    uint64_t value = 0;
    bool supported = false;
    if (!get_feature(KSU_FEATURE_ORIGIN_VEIL, &value, &supported)) {
        return false;
    }
    if (!supported) {
        return false;
    }
    return value != 0;
}

bool set_veil_enabled(bool enabled) {
    return set_feature(KSU_FEATURE_ORIGIN_VEIL, enabled ? 1 : 0);
}

static bool veil_cloak_ioctl(struct ksu_veil_cloak_cmd *cmd) {
    return ksuctl(KSU_IOCTL_VEIL_CLOAK, cmd) == 0;
}

bool get_veil_cloaked_uids(struct veil_cloaked_uids *out) {
    if (!out) return false;
    out->count = 0;
    out->uids = nullptr;

    struct ksu_veil_cloak_cmd probe = {.op = KSU_VEIL_CLOAK_LIST, .count = 0, .uids = 0};
    if (!veil_cloak_ioctl(&probe)) return false;
    if (probe.count == 0) return true;

    uint32_t *uids = malloc((size_t)probe.count * sizeof(uint32_t));
    if (!uids) return false;

    struct ksu_veil_cloak_cmd cmd = {
        .op = KSU_VEIL_CLOAK_LIST,
        .count = probe.count,
        .uids = (uint64_t)(uintptr_t)uids,
    };
    if (!veil_cloak_ioctl(&cmd)) {
        free(uids);
        return false;
    }

    uint32_t got = cmd.count < probe.count ? cmd.count : probe.count;
    out->count = got;
    out->uids = uids;
    return true;
}

bool set_veil_cloaked(uint32_t uid, bool cloaked) {
    struct ksu_veil_cloak_cmd cmd = {
        .op = cloaked ? KSU_VEIL_CLOAK_ADD : KSU_VEIL_CLOAK_REMOVE,
        .uid = uid,
    };
    return veil_cloak_ioctl(&cmd);
}

bool clear_veil_cloaked(void) {
    struct ksu_veil_cloak_cmd cmd = {.op = KSU_VEIL_CLOAK_CLEAR};
    return veil_cloak_ioctl(&cmd);
}

bool is_veil_auto_cloak(bool *out_enabled) {
    struct ksu_veil_cloak_cmd cmd = {.op = KSU_VEIL_CLOAK_GET_AUTO};
    if (!veil_cloak_ioctl(&cmd)) return false;
    if (out_enabled) *out_enabled = cmd.value != 0;
    return true;
}

bool set_veil_auto_cloak(bool enabled) {
    struct ksu_veil_cloak_cmd cmd = {
        .op = KSU_VEIL_CLOAK_SET_AUTO,
        .value = enabled ? 1 : 0,
    };
    return veil_cloak_ioctl(&cmd);
}

bool get_veil_history(struct veil_history *out) {
    if (!out) return false;
    out->count = 0;
    out->entries = nullptr;

    struct ksu_veil_history_cmd probe = {.count = 0, .pad = 0, .entries = 0};
    if (ksuctl(KSU_IOCTL_VEIL_HISTORY, &probe) != 0) return false;
    if (probe.count == 0) return true;

    struct ksu_veil_hist_entry *entries =
        malloc((size_t)probe.count * sizeof(struct ksu_veil_hist_entry));
    if (!entries) return false;

    struct ksu_veil_history_cmd cmd = {
        .count = probe.count,
        .pad = 0,
        .entries = (uint64_t)(uintptr_t)entries,
    };
    if (ksuctl(KSU_IOCTL_VEIL_HISTORY, &cmd) != 0) {
        free(entries);
        return false;
    }

    uint32_t got = cmd.count < probe.count ? cmd.count : probe.count;
    out->count = got;
    out->entries = entries;
    return true;
}

bool clear_veil_history(void) {
    struct ksu_veil_history_cmd cmd = {.count = 0, .pad = 1, .entries = 0};
    return ksuctl(KSU_IOCTL_VEIL_HISTORY, &cmd) == 0;
}

void get_full_version(char* buff) {
	struct ksu_get_full_version_cmd cmd = {0};
	if (ksuctl(KSU_IOCTL_GET_FULL_VERSION, &cmd) == 0) {
		strncpy(buff, cmd.version_full, KSU_FULL_VERSION_STRING - 1);
		buff[KSU_FULL_VERSION_STRING - 1] = '\0';
	} else {
        return legacy_get_full_version(buff);
	}
}


void get_hook_type(char *buff) {
    struct ksu_hook_type_cmd cmd = {0};
    if (ksuctl(KSU_IOCTL_HOOK_TYPE, &cmd) == 0) {
        strncpy(buff, cmd.hook_type, 32 - 1);
        buff[32 - 1] = '\0';
    } else {
        legacy_get_hook_type(buff, 32);
    }
}

int get_kernel_patch_implement() {
    struct ksu_get_kernel_patch_implement cmd = {0};
    if (ksuctl(KSU_IOCTL_GET_KERNEL_PATCH_IMPLEMENT, &cmd) != 0)
        return 0;
    return cmd.type;
}

bool get_dynamic_manager(struct ksu_dynamic_manager_cmd *cfg)
{
	if (!cfg) 
		return false;

	struct ksu_dynamic_manager_cmd cmd = {0};
	cmd.operation = DYNAMIC_MANAGER_OP_GET;

	if (ksuctl(KSU_IOCTL_DYNAMIC_MANAGER, &cmd) != 0)
		return false;

	*cfg = cmd;
	return true;
}

/**
 * Get active manager list
 * @param out_cmd count, total_count, managers's out ptr
 * @return true on success, false on failure
 * @warning dynamic alloc memory INTERNALLY!!!
 */
bool get_managers_list(struct ksu_get_managers_cmd **out_cmd) {
    if (!out_cmd) return false;

    struct ksu_get_managers_cmd probe = {.count = 0};
    if (ksuctl(KSU_IOCTL_GET_MANAGERS, &probe) != 0) {
        return false;
    }

    if (probe.total_count == 0) { // it shouldn't happen, but just in case...
        *out_cmd = nullptr;
        return false;
    }

    size_t payload_size = sizeof((*out_cmd)->managers[0]) * probe.total_count;
    size_t total_size = sizeof(struct ksu_get_managers_cmd) + payload_size;

    struct ksu_get_managers_cmd *cmd = malloc(total_size);
    if (!cmd) return false;

    cmd->count = probe.total_count;
    if (ksuctl(KSU_IOCTL_GET_MANAGERS, cmd) != 0) {
        free(cmd);
        return false;
    }

    *out_cmd = cmd;
    return true;
}
