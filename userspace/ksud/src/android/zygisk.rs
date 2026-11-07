//! OriginZygisk provider status.
//!
//! Sui is a Zygisk module and needs a provider that is enabled, installed and
//! actually injecting. This module reports that view from the built-in engine's
//! runtime state instead of depending on an external provider's private API.

use std::fs;
use std::path::{Path, PathBuf};

use anyhow::Result;

pub const ORIGIN_ZYGISK_DIR: &str = "/data/adb/ksu/originzygisk";
const ORIGIN_ZYGISK_HOOK: &str = "/data/adb/post-fs-data.d/originzygisk.sh";
const STATE: &str = "/data/adb/rezygisk/state.json";
const MODULES: &str = "/data/adb/modules";
const UPDATES: &str = "/data/adb/modules_update";
const FAILURE_LIMIT: u32 = 3;

/// Module ids of other Zygisk providers. A provider that is installed and
/// enabled owns zygote; OriginZygisk steps aside for it.
const PROVIDER_IDS: &[&str] = &[
    "zygisksu",
    "rezygisk",
    "brezygisk",
    "shirokozygisk",
    "zygisk_next",
    "zygisknext",
    "neozygisk",
    "onyxzygisk",
    "yukizygisk",
    "nyazygisk",
];

#[derive(serde::Serialize)]
pub struct ProviderStatus {
    pub schema_version: u32,
    pub enabled: bool,
    pub engine_available: bool,
    pub enable_blockers: Vec<&'static str>,
    pub external_providers: Vec<String>,
    /// `unverified`, `starting`, `injected` or `failed`.
    pub injection_state: &'static str,
    pub reboot_required: Option<bool>,
    pub startup_failures: u32,
    pub startup_blocked: bool,
    pub startup_failure_code: Option<&'static str>,
    pub startup_failure_detail: Option<String>,
    pub startup_generation: u64,
}

fn read_trimmed(path: &Path) -> Option<String> {
    fs::read_to_string(path)
        .ok()
        .map(|value| value.trim().to_owned())
        .filter(|value| !value.is_empty())
}

fn module_enabled(root: &Path, id: &str) -> bool {
    let dir = root.join(id);
    dir.join("module.prop").is_file()
        && !dir.join("disable").exists()
        && !dir.join("remove").exists()
        && fs::read_dir(dir.join("zygisk")).is_ok_and(|mut entries| {
            entries.any(|entry| {
                entry.ok().is_some_and(|entry| {
                    entry
                        .path()
                        .extension()
                        .and_then(std::ffi::OsStr::to_str)
                        .is_some_and(|ext| ext == "so")
                })
            })
        })
}

fn providers(root: &Path) -> Vec<String> {
    let Ok(entries) = fs::read_dir(root) else {
        return Vec::new();
    };
    let mut found = Vec::new();
    for entry in entries.flatten() {
        let Some(id) = entry.file_name().to_str().map(ToOwned::to_owned) else {
            continue;
        };
        if PROVIDER_IDS.contains(&id.as_str()) && module_enabled(root, &id) {
            found.push(id);
        }
    }
    found.sort();
    found
}

pub fn external_providers() -> Vec<String> {
    let mut found = providers(Path::new(MODULES));
    found.extend(providers(Path::new(UPDATES)));
    found.sort();
    found.dedup();
    found
}

fn abi_list() -> String {
    let first = crate::android::utils::getprop("ro.system.product.cpu.abilist").unwrap_or_default();
    let second = crate::android::utils::getprop("ro.product.cpu.abilist").unwrap_or_default();
    if second.len() > first.len() {
        second
    } else {
        first
    }
}

fn monitor_path() -> PathBuf {
    let dir = Path::new(ORIGIN_ZYGISK_DIR);
    let abis = abi_list();
    let abi64 = abis.contains("arm64-v8a") || abis.contains("x86_64");
    dir.join(if abi64 {
        "bin/zygisk-ptrace64"
    } else {
        "bin/zygisk-ptrace32"
    })
}

fn engine_available() -> bool {
    let dir = Path::new(ORIGIN_ZYGISK_DIR);
    monitor_path().is_file()
        && (dir.join("lib64/libzygisk.so").is_file() || dir.join("lib/libzygisk.so").is_file())
}

fn enabled() -> bool {
    let dir = Path::new(ORIGIN_ZYGISK_DIR);
    dir.join("enable").is_file()
        && dir.join("bin").is_dir()
        && Path::new(ORIGIN_ZYGISK_HOOK).is_file()
}

fn json_bool(value: &serde_json::Value) -> bool {
    match value {
        serde_json::Value::Bool(flag) => *flag,
        serde_json::Value::Number(number) => number.as_i64() == Some(1),
        serde_json::Value::String(text) => text == "1" || text.eq_ignore_ascii_case("true"),
        _ => false,
    }
}

fn json_state_is_zero(value: &serde_json::Value) -> bool {
    match value {
        serde_json::Value::Number(number) => number.as_i64() == Some(0),
        serde_json::Value::String(text) => text.trim() == "0",
        _ => false,
    }
}

/// Parse the engine's `state.json`. Current engines emit numeric flags
/// (`"state": 0`, `"64": 1`), while older payloads used strings; accept both.
fn parse_injection_state(raw: &str) -> &'static str {
    // Older engine builds emitted a trailing comma after monitor.reason.
    let normalized = raw
        .replace(",\n  }", "\n  }")
        .replace(",\n    }", "\n    }")
        .replace(",\n}", "\n}");
    let Ok(state) = serde_json::from_str::<serde_json::Value>(&normalized) else {
        return "unverified";
    };
    let monitor_ok = state
        .get("monitor")
        .and_then(|monitor| monitor.get("state"))
        .is_some_and(json_state_is_zero);
    if !monitor_ok {
        return "failed";
    }
    let injected = state.get("zygote").is_some_and(|zygote| {
        ["64", "32"]
            .iter()
            .any(|abi| zygote.get(abi).is_some_and(json_bool))
    });
    if injected { "injected" } else { "starting" }
}

fn injection_state() -> &'static str {
    let Ok(raw) = fs::read_to_string(STATE) else {
        return "unverified";
    };
    parse_injection_state(&raw)
}

#[derive(Default)]
struct BootGuard {
    failures: u32,
    latched: bool,
}

fn boot_guard() -> BootGuard {
    let dir = Path::new(ORIGIN_ZYGISK_DIR).join("bootguard");
    BootGuard {
        failures: read_trimmed(&dir.join("failures"))
            .and_then(|value| value.parse().ok())
            .unwrap_or(0),
        latched: dir.join("latched").exists(),
    }
}

pub fn provider_status() -> Result<ProviderStatus> {
    let engine = engine_available();
    let guard = boot_guard();
    let blocked = guard.latched || guard.failures >= FAILURE_LIMIT;
    let external = external_providers();
    let mut blockers = Vec::new();
    if !engine {
        blockers.push("engine_missing");
    }
    if blocked {
        blockers.push("startup_protection");
    }
    Ok(ProviderStatus {
        schema_version: 1,
        enabled: enabled(),
        engine_available: engine,
        enable_blockers: blockers,
        external_providers: external,
        injection_state: injection_state(),
        reboot_required: None,
        startup_failures: guard.failures,
        startup_blocked: blocked,
        startup_failure_code: blocked.then_some("startup_protection"),
        startup_failure_detail: None,
        startup_generation: 0,
    })
}

/// The kernel starts `ksud post-fs-data` as a oneshot init service. When that
/// service exits, init SIGKILLs its whole cgroup. Move the child into the root
/// cgroup so a setsid-detached daemon survives the teardown.
pub fn detach_from_service_cgroup() -> std::io::Result<()> {
    // Called after fork: only async-signal-safe libc calls here.
    unsafe {
        let fd = libc::open(
            c"/sys/fs/cgroup/cgroup.procs".as_ptr(),
            libc::O_WRONLY | libc::O_CLOEXEC,
        );
        if fd == -1 {
            return Err(std::io::Error::last_os_error());
        }
        let mut pid = libc::getpid() as u32;
        let mut bytes = [0u8; 10];
        let mut offset = bytes.len();
        while pid > 0 {
            offset -= 1;
            bytes[offset] = b'0' + (pid % 10) as u8;
            pid /= 10;
        }
        let size = bytes.len() - offset;
        let written = libc::write(fd, bytes[offset..].as_ptr().cast(), size);
        let error = if written == -1 {
            Some(std::io::Error::last_os_error())
        } else if written as usize != size {
            Some(std::io::Error::from_raw_os_error(libc::EIO))
        } else {
            None
        };
        libc::close(fd);
        error.map_or(Ok(()), Err)
    }
}

#[cfg(test)]
mod tests {
    use super::parse_injection_state;

    #[test]
    fn numeric_state_json_is_understood() {
        let raw = r#"{
  "root": "KernelSU",
  "monitor": {
    "state": 0
  },
  "rezygiskd": {
    "64": {
      "state": 1,
      "modules": []
    }
  },
  "zygote": {
    "64": 1
  }
}"#;
        assert_eq!(parse_injection_state(raw), "injected");
    }

    #[test]
    fn numeric_zero_zygote_is_starting() {
        let raw = r#"{"monitor":{"state":0},"zygote":{"64":0}}"#;
        assert_eq!(parse_injection_state(raw), "starting");
    }

    #[test]
    fn stopped_monitor_is_failed() {
        let raw = r#"{"monitor":{"state":2,"reason":"crashed"},"zygote":{"64":1}}"#;
        assert_eq!(parse_injection_state(raw), "failed");
    }

    #[test]
    fn legacy_string_state_still_works() {
        let raw = r#"{"monitor":{"state":"0"},"zygote":{"64":"1"}}"#;
        assert_eq!(parse_injection_state(raw), "injected");
    }

    #[test]
    fn malformed_state_is_unverified() {
        assert_eq!(parse_injection_state("not json"), "unverified");
    }
}
