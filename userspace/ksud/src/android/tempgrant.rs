//! Time-limited root grants.
//!
//! A temporary grant flips an app profile to `allow_su` and schedules a
//! revocation at a fixed expiry time. State lives in a JSON file so grants
//! survive a ksud restart; expiry is enforced by a detached sleeper process
//! plus a sweep on every ksud init.

use std::{
    os::unix::process::CommandExt,
    path::Path,
    process::Command,
    time::{SystemTime, UNIX_EPOCH},
};

use anyhow::{Context, Result, bail};
use const_format::concatcp;

use crate::android::{ksucalls, uapi, utils::ensure_dir_exists};
use crate::defs;

const TEMP_GRANTS_PATH: &str = concatcp!(defs::WORKING_DIR, "temp_grants.json");

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct TempGrant {
    pub uid: u32,
    pub package: String,
    pub expires_at: u64,
}

fn now_epoch() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

fn load_grants() -> Result<Vec<TempGrant>> {
    let path = Path::new(TEMP_GRANTS_PATH);
    if (!path.exists()) {
        return Ok(Vec::new());
    }
    let data = std::fs::read_to_string(path).with_context(|| "Failed to read temp grants")?;
    if data.trim().is_empty() {
        return Ok(Vec::new());
    }
    serde_json::from_str(&data).with_context(|| "Failed to parse temp grants")
}

fn save_grants(grants: &[TempGrant]) -> Result<()> {
    ensure_dir_exists(Path::new(defs::WORKING_DIR))?;
    let data = serde_json::to_string_pretty(grants)?;
    std::fs::write(TEMP_GRANTS_PATH, data).with_context(|| "Failed to write temp grants")?;
    Ok(())
}

fn copy_key(dst: &mut [libc::c_char], src: &str) {
    let bytes = src.as_bytes();
    let n = bytes.len().min(dst.len().saturating_sub(1));
    for (i, b) in bytes.iter().take(n).enumerate() {
        dst[i] = *b as libc::c_char;
    }
    dst[n] = 0;
}

fn read_profile(package: &str, uid: u32) -> Result<uapi::app_profile> {
    let mut profile: uapi::app_profile = unsafe { std::mem::zeroed() };
    profile.version = uapi::KSU_APP_PROFILE_VER as u32;
    copy_key(&mut profile.key, package);
    profile.curr_uid = uid as i32;
    let mut cmd = uapi::ksu_get_app_profile_cmd { profile };
    ksucalls::ksuctl(uapi::KSU_IOCTL_GET_APP_PROFILE_RUST, &raw mut cmd)
        .with_context(|| format!("Failed to get profile for {package}"))?;
    Ok(cmd.profile)
}

fn write_profile(profile: &uapi::app_profile) -> Result<()> {
    let mut cmd = uapi::ksu_set_app_profile_cmd {
        profile: *profile,
    };
    ksuctl(uapi::KSU_IOCTL_SET_APP_PROFILE_RUST, &raw mut cmd)
        .with_context(|| "Failed to set profile")?;
    Ok(())
}

fn set_allow_su(package: &str, uid: u32, allow: bool) -> Result<()> {
    let mut profile = read_profile(package, uid)?;
    profile.allow_su = allow;
    write_profile(&profile)
}

/// Grant root to `package`/`uid` for `timeout_secs` seconds.
pub fn grant(package: String, uid: u32, timeout_secs: u64) -> Result<()> {
    if timeout_secs == 0 {
        bail!("Timeout must be greater than zero");
    }
    ksucalls::ensure_uapi_version_matched()?;
    set_allow_su(&package, uid, true)?;

    let expires_at = now_epoch().saturating_add(timeout_secs);
    let mut grants = load_grants()?;
    grants.retain(|g| g.uid != uid);
    grants.push(TempGrant {
        uid,
        package: package.clone(),
        expires_at,
    });
    save_grants(&grants)?;

    spawn_sleeper(uid, expires_at)?;

    println!("Granted root to {package} (uid {uid}) for {timeout_secs}s");
    Ok(())
}

/// Revoke a temporary grant immediately. Returns true if a grant existed.
pub fn revoke(uid: u32) -> Result<bool> {
    let mut grants = load_grants()?;
    let Some(entry) = grants.iter().find(|g| g.uid == uid).cloned() else {
        return Ok(false);
    };
    if let Err(e) = set_allow_su(&entry.package, uid, false) {
        log::warn!("tempgrant: failed to revoke uid {uid}: {e:#}");
    }
    grants.retain(|g| g.uid != uid);
    save_grants(&grants)?;
    println!("Revoked temporary root grant for uid {uid}");
    Ok(true)
}

pub fn list() -> Result<()> {
    let grants = load_grants()?;
    println!("{}", serde_json::to_string_pretty(&grants)?);
    Ok(())
}

/// Revoke every grant whose expiry time has passed. Best-effort; used at boot.
pub fn sweep_expired() {
    let now = now_epoch();
    let grants = match load_grants() {
        Ok(g) => g,
        Err(e) => {
            log::warn!("tempgrant: failed to load grants for sweep: {e:#}");
            return;
        }
    };
    let mut remaining = Vec::with_capacity(grants.len());
    for entry in grants {
        if entry.expires_at <= now {
            if let Err(e) = set_allow_su(&entry.package, entry.uid, false) {
                log::warn!("tempgrant: failed to revoke expired uid {}: {e:#}", entry.uid);
                // Keep the entry so a later sweep retries.
                remaining.push(entry);
            } else {
                log::info!("tempgrant: revoked expired grant for uid {}", entry.uid);
            }
        } else {
            remaining.push(entry);
        }
    }
    if let Err(e) = save_grants(&remaining) {
        log::warn!("tempgrant: failed to persist sweep: {e:#}");
    }
}

/// Detached waiter: sleeps until `expires_at`, then revokes `uid` unless the
/// grant was removed or replaced (re-grant) in the meantime.
pub fn wait_and_revoke(uid: u32, expires_at: u64) -> Result<()> {
    let now = now_epoch();
    if expires_at > now {
        let delay = expires_at - now;
        log::info!("tempgrant: waiter for uid {uid} sleeping {delay}s");
        std::thread::sleep(std::time::Duration::from_secs(delay));
    }
    let grants = load_grants()?;
    match grants.iter().find(|g| g.uid == uid) {
        Some(entry) if entry.expires_at == expires_at => {
            if let Err(e) = set_allow_su(&entry.package, uid, false) {
                log::warn!("tempgrant: waiter failed to revoke uid {uid}: {e:#}");
                return Ok(());
            }
            let remaining: Vec<TempGrant> =
                grants.into_iter().filter(|g| g.uid != uid).collect();
            save_grants(&remaining)?;
            log::info!("tempgrant: waiter revoked uid {uid}");
        }
        Some(_) => {
            log::info!("tempgrant: waiter for uid {uid} superseded by a newer grant");
        }
        None => {
            log::info!("tempgrant: waiter for uid {uid}: grant already gone");
        }
    }
    Ok(())
}

fn spawn_sleeper(uid: u32, expires_at: u64) -> Result<()> {
    let exe = std::env::current_exe().with_context(|| "Failed to locate ksud binary")?;
    let devnull = std::fs::File::open("/dev/null")?;
    // SAFETY: setsid in the child before exec; no locks held across fork here
    // beyond the allocator, matching the daemonize pattern used elsewhere.
    unsafe {
        Command::new(exe)
            .arg("grant-temp-wait")
            .arg("--uid")
            .arg(uid.to_string())
            .arg("--until")
            .arg(expires_at.to_string())
            .stdin(devnull.try_clone()?)
            .stdout(devnull.try_clone()?)
            .stderr(devnull)
            .pre_exec(|| {
                libc::setsid();
                Ok(())
            })
            .spawn()
            .with_context(|| "Failed to spawn grant waiter")?;
    }
    Ok(())
}
