//! Stealth mode control.
//!
//! While enabled the kernel stops reporting the manager flag through GET_INFO,
//! so the manager UI renders as if root were not installed. The switch itself
//! stays reachable to the manager and to root, and is persisted in
//! `/data/adb/ksu/stealth`.

use anyhow::{bail, Result};

use crate::ksu_uapi;
use crate::ksucalls::ksuctl;

/// Report the current switch. Returns `None` when the kernel does not expose
/// the stealth ioctls.
fn read_state() -> Result<Option<bool>> {
    let mut cmd = ksu_uapi::ksu_stealth_cmd { enabled: 0 };
    match ksuctl(ksu_uapi::KSU_IOCTL_STEALTH_GET, &raw mut cmd) {
        Ok(_) => Ok(Some(cmd.enabled != 0)),
        Err(_) => Ok(None),
    }
}

pub fn print_status() -> Result<()> {
    match read_state()? {
        Some(true) => println!("enabled"),
        Some(false) => println!("disabled"),
        None => println!("unsupported"),
    }
    Ok(())
}

pub fn set_enabled(enabled: bool) -> Result<()> {
    let mut cmd = ksu_uapi::ksu_stealth_cmd {
        enabled: u8::from(enabled),
    };
    if ksuctl(ksu_uapi::KSU_IOCTL_STEALTH_SET, &raw mut cmd).is_err() {
        bail!("stealth is not supported by the running kernel");
    }
    println!("stealth {}", if enabled { "enabled" } else { "disabled" });
    Ok(())
}
