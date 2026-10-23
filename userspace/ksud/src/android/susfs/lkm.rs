//! Loader for the SUSFS guard LKM (`susfs_guard_lkm.ko`).
//!
//! The guard module is a standalone KernelSU-loadable module: it is embedded
//! into ksud under the name `<kmi>_susfs_guard_lkm.ko` and loaded through
//! `ksuinit::load_module()`, which relocates the symbols that the kernel does
//! not export.  Once it is loaded its control nodes speak the same reboot(2)
//! ABI as a built-in SUSFS, so the rest of `android::susfs` works unchanged.

use std::ffi::CString;
use std::fs;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::{Context, Result, ensure};
use log::{info, warn};
use serde::{Deserialize, Serialize};

use crate::android::susfs::api::prelude as api;
use crate::defs::WORKING_DIR;

const AUTOLOAD_ATTEMPTS_FILE: &str = "susfs_autoload_attempts";
const AUTOLOAD_DISABLED_FILE: &str = "susfs_autoload_disabled";
const LOADED_MARKER_FILE: &str = "susfs_loaded_lkm.json";
const MAX_AUTOLOAD_ATTEMPTS: u32 = 3;

/// Persisted auto-load configuration.  A missing file means disabled.
struct AutoLoadConfig {
    enabled: bool,
    fallback_kmi: Option<String>,
}

#[derive(Serialize, Deserialize, Default)]
struct LoadedLkmInfo {
    kmi: String,
    asset_name: String,
    sha256: String,
    size: u64,
    loaded_at: u64,
}

fn working_file(name: &str) -> PathBuf {
    PathBuf::from(WORKING_DIR).join(name)
}

fn autoload_config_path() -> PathBuf {
    working_file("susfs_autoload.conf")
}

fn unix_now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_secs())
        .unwrap_or_default()
}

fn read_autoload_attempts() -> u32 {
    fs::read_to_string(working_file(AUTOLOAD_ATTEMPTS_FILE))
        .ok()
        .and_then(|content| content.trim().parse().ok())
        .unwrap_or_default()
}

fn write_autoload_attempts(attempts: u32) -> Result<()> {
    fs::create_dir_all(WORKING_DIR).context("Failed to create KernelSU working directory")?;
    fs::write(working_file(AUTOLOAD_ATTEMPTS_FILE), attempts.to_string())
        .context("Failed to write SUSFS auto-load attempt counter")
}

fn read_autoload_disabled_reason() -> Option<String> {
    fs::read_to_string(working_file(AUTOLOAD_DISABLED_FILE))
        .ok()
        .map(|content| content.trim().to_owned())
        .filter(|content| !content.is_empty())
}

fn write_autoload_disabled_reason(reason: &str) -> Result<()> {
    fs::create_dir_all(WORKING_DIR).context("Failed to create KernelSU working directory")?;
    fs::write(working_file(AUTOLOAD_DISABLED_FILE), reason)
        .context("Failed to write SUSFS auto-load safety marker")
}

fn clear_autoload_safety_state() -> Result<()> {
    let _ = fs::remove_file(working_file(AUTOLOAD_DISABLED_FILE));
    write_autoload_attempts(0)
}

fn read_loaded_marker() -> Option<LoadedLkmInfo> {
    fs::read_to_string(working_file(LOADED_MARKER_FILE))
        .ok()
        .and_then(|content| serde_json::from_str(&content).ok())
}

fn write_loaded_marker(kmi: &str, asset_name: &str, module_data: &[u8]) -> Result<()> {
    fs::create_dir_all(WORKING_DIR).context("Failed to create KernelSU working directory")?;
    let marker = LoadedLkmInfo {
        kmi: kmi.to_owned(),
        asset_name: asset_name.to_owned(),
        sha256: sha256::digest(module_data),
        size: module_data.len() as u64,
        loaded_at: unix_now(),
    };
    let content = serde_json::to_string_pretty(&marker)
        .context("Failed to serialize loaded SUSFS LKM marker")?;
    fs::write(working_file(LOADED_MARKER_FILE), content)
        .context("Failed to write loaded SUSFS LKM marker")?;
    Ok(())
}

/// Called when the manager unloads KernelSU so stale module metadata is not
/// shown after a temporary uninstall.
pub fn clear_loaded_marker() {
    let _ = fs::remove_file(working_file(LOADED_MARKER_FILE));
}

fn validate_kmi(kmi: &str) -> Result<()> {
    ensure!(
        !kmi.is_empty()
            && kmi.len() <= 64
            && kmi
                .bytes()
                .all(|byte| { byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-') }),
        "Invalid KMI '{kmi}'"
    );
    Ok(())
}

fn read_autoload_config() -> Result<AutoLoadConfig> {
    let content = match fs::read_to_string(autoload_config_path()) {
        Ok(content) => content,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Ok(AutoLoadConfig {
                enabled: false,
                fallback_kmi: None,
            });
        }
        Err(error) => return Err(error.into()),
    };

    let mut config = AutoLoadConfig {
        enabled: false,
        fallback_kmi: None,
    };
    for line in content.lines() {
        if line == "enabled=1" {
            config.enabled = true;
        } else if let Some(kmi) = line.strip_prefix("fallback_kmi=") {
            validate_kmi(kmi)?;
            config.fallback_kmi = Some(kmi.to_owned());
        }
    }
    Ok(config)
}

pub fn autoload_enabled() -> Result<bool> {
    Ok(read_autoload_config()?.enabled)
}

pub fn set_autoload(enabled: bool, kmi: Option<&str>) -> Result<()> {
    let path = autoload_config_path();
    if !enabled {
        match fs::remove_file(&path) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(error).context("Failed to disable SUSFS auto-load"),
        }
        clear_autoload_safety_state()?;
        println!("SUSFS auto-load disabled");
        return Ok(());
    }

    ensure!(
        cfg!(target_arch = "aarch64"),
        "SUSFS guard LKM is only available for arm64"
    );

    let kmi = match kmi {
        Some(kmi) => kmi.to_owned(),
        None => crate::boot_patch::get_current_kmi().context(
            "Failed to detect kernel KMI; specify it with `ksud susfs autoload enable --kmi <KMI>`",
        )?,
    };
    validate_kmi(&kmi)?;
    let asset_name = format!("{kmi}_susfs_guard_lkm.ko");
    crate::assets::get_asset(&asset_name)
        .with_context(|| format!("SUSFS guard asset is unavailable for KMI {kmi}"))?;

    fs::create_dir_all(WORKING_DIR).context("Failed to create KernelSU working directory")?;
    let temporary_path = path.with_extension("conf.tmp");
    fs::write(&temporary_path, format!("enabled=1\nfallback_kmi={kmi}\n"))
        .context("Failed to write SUSFS auto-load config")?;
    if let Err(error) = fs::rename(&temporary_path, &path) {
        let _ = fs::remove_file(&temporary_path);
        return Err(error).context("Failed to save SUSFS auto-load config");
    }

    clear_autoload_safety_state()?;
    println!("SUSFS auto-load enabled (KMI fallback: {kmi})");
    Ok(())
}

/// Version of an already-available SUSFS, whether built in or loaded as an LKM.
fn detected_susfs_version() -> Option<String> {
    match api::version() {
        Ok(version) if version.starts_with('v') && !version.is_empty() => Some(version),
        _ => None,
    }
}

pub fn load_if_autoload_enabled() -> Result<()> {
    // The guard LKM is loaded fresh by ksud during this boot. Any marker from
    // a previous boot is stale until load() writes a new one.
    clear_loaded_marker();
    let config = read_autoload_config()?;
    if !config.enabled {
        return Ok(());
    }
    if !cfg!(target_arch = "aarch64") {
        warn!("SUSFS auto-load is enabled, but the SUSFS LKM is only available for arm64");
        return Ok(());
    }
    if let Some(version) = detected_susfs_version() {
        info!("SUSFS {version} is already available; skipping guard LKM load");
        let _ = write_autoload_attempts(0);
        return Ok(());
    }

    let attempts = read_autoload_attempts().saturating_add(1);
    write_autoload_attempts(attempts)?;
    if attempts >= MAX_AUTOLOAD_ATTEMPTS {
        warn!(
            "SUSFS auto-load failed to complete {attempts} boots; disabling it to protect the boot"
        );
        set_autoload(false, None)?;
        write_autoload_disabled_reason(&format!(
            "Autoload disabled after {attempts} incomplete boots"
        ))?;
        return Ok(());
    }

    let kmi = crate::boot_patch::get_current_kmi()
        .ok()
        .or(config.fallback_kmi);
    let Some(kmi) = kmi else {
        warn!("SUSFS auto-load is enabled, but KMI detection failed and no fallback KMI is saved");
        return Ok(());
    };

    if let Err(error) = load(Some(&kmi)) {
        warn!("Failed to auto-load SUSFS guard LKM for KMI {kmi}: {error:#}");
    }
    Ok(())
}

/// Called from boot-completed: the guard LKM did not stop the boot, so reset
/// the consecutive-failure counter.
pub fn on_boot_completed() -> Result<()> {
    write_autoload_attempts(0)
}

/// Clear the boot-loop safety marker after the user reviews or re-enables
/// autoload.
pub fn reset_autoload_guard() -> Result<()> {
    clear_autoload_safety_state()?;
    println!("SUSFS auto-load safety state reset");
    Ok(())
}

/// Print guard LKM state as JSON for the manager.
pub fn info_json() -> Result<()> {
    let config = read_autoload_config()?;
    let loaded = read_loaded_marker();
    let loaded_version = detected_susfs_version();
    let disabled_reason = read_autoload_disabled_reason();
    let output = serde_json::json!({
        "loaded_version": loaded_version,
        "loaded_kmi": loaded.as_ref().map(|info| info.kmi.as_str()),
        "asset_name": loaded.as_ref().map(|info| info.asset_name.as_str()),
        "asset_sha256": loaded.as_ref().map(|info| info.sha256.as_str()),
        "asset_size": loaded.as_ref().map(|info| info.size),
        "loaded_at": loaded.as_ref().map(|info| info.loaded_at),
        "autoload_enabled": config.enabled,
        "autoload_kmi": config.fallback_kmi,
        "autoload_attempts": read_autoload_attempts(),
        "autoload_disabled_reason": disabled_reason,
        "supported_kmis": crate::assets::list_supported_susfs_guard_kmi(),
    });
    println!("{}", serde_json::to_string(&output)?);
    Ok(())
}

pub fn load(kmi: Option<&str>) -> Result<()> {
    ensure!(
        cfg!(target_arch = "aarch64"),
        "SUSFS guard LKM is only available for arm64"
    );

    if let Some(version) = detected_susfs_version() {
        info!("SUSFS {version} is already available; skipping guard LKM load");
        println!("SUSFS {version} is already loaded");
        return Ok(());
    }

    let kmi = match kmi {
        Some(kmi) => kmi.to_owned(),
        None => crate::boot_patch::get_current_kmi().context(
            "Failed to detect kernel KMI; specify it explicitly with `ksud susfs load --kmi <KMI>`",
        )?,
    };
    validate_kmi(&kmi)?;

    let asset_name = format!("{kmi}_susfs_guard_lkm.ko");
    let module_data = crate::assets::get_asset(&asset_name)
        .with_context(|| format!("Failed to load SUSFS guard asset {asset_name}"))?;
    let params = CString::new("").expect("empty module parameters are valid");

    info!("Loading SUSFS guard LKM for KMI {kmi}");
    ksuinit::load_module(&module_data, &params).context("Failed to load SUSFS guard LKM")?;
    if let Err(error) = write_loaded_marker(&kmi, &asset_name, &module_data) {
        warn!("Failed to record loaded SUSFS guard LKM metadata: {error:#}");
    }
    println!("Loaded SUSFS guard LKM for KMI {kmi}");
    Ok(())
}
