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

use anyhow::{Context, Result, ensure};
use log::{info, warn};

use crate::android::susfs::api::prelude as api;
use crate::defs::WORKING_DIR;

/// Persisted auto-load configuration.  A missing file means disabled.
struct AutoLoadConfig {
    enabled: bool,
    fallback_kmi: Option<String>,
}

fn autoload_config_path() -> PathBuf {
    PathBuf::from(WORKING_DIR).join("susfs_autoload.conf")
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
    println!("Loaded SUSFS guard LKM for KMI {kmi}");
    Ok(())
}
