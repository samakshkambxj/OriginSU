use std::{fs, path::Path, time::SystemTime};

use anyhow::{Context, Result, bail};
use const_format::concatcp;
use log::{info, warn};
use serde::{Deserialize, Serialize};

use crate::{
    android::{module, utils::is_safe_mode},
    defs,
};

/// Default number of consecutive incomplete boots before rescue kicks in.
pub const DEFAULT_MAX_FAILED: u32 = 3;
/// Bounds for the user-configurable threshold.
pub const MIN_MAX_FAILED: u32 = 2;
pub const MAX_MAX_FAILED: u32 = 10;

const CONFIG_PATH: &str = concatcp!(defs::WORKING_DIR, ".bootloop.json");
const COUNT_PATH: &str = concatcp!(defs::WORKING_DIR, ".bootloop_count");
const RESCUED_PATH: &str = concatcp!(defs::WORKING_DIR, ".bootloop_rescued");

#[derive(Serialize, Deserialize)]
struct Config {
    enabled: bool,
    max_failed: u32,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            enabled: true,
            max_failed: DEFAULT_MAX_FAILED,
        }
    }
}

#[derive(Serialize, Deserialize)]
struct RescuedInfo {
    failed_boots: u32,
    timestamp: u64,
}

fn read_config() -> Config {
    let mut config: Config = fs::read_to_string(CONFIG_PATH)
        .ok()
        .and_then(|content| serde_json::from_str(&content).ok())
        .unwrap_or_default();
    config.max_failed = config.max_failed.clamp(MIN_MAX_FAILED, MAX_MAX_FAILED);
    config
}

fn write_config(config: &Config) -> Result<()> {
    let content = serde_json::to_string_pretty(config)?;
    fs::write(CONFIG_PATH, content)?;
    Ok(())
}

fn read_count() -> u32 {
    fs::read_to_string(COUNT_PATH)
        .ok()
        .and_then(|content| content.trim().parse().ok())
        .unwrap_or(0)
}

fn write_count(count: u32) -> Result<()> {
    fs::write(COUNT_PATH, count.to_string()).with_context(|| "Failed to write boot count")?;
    Ok(())
}

fn unix_now() -> u64 {
    SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// Called from `post-fs-data`: count this boot attempt and, when too many
/// consecutive boots never reached `boot-completed`, disable all modules so
/// the device can boot again. The manager shows a notice via the rescued
/// marker until it is dismissed.
pub fn on_post_fs_data() -> Result<()> {
    if is_safe_mode() {
        // Safe mode already disables everything; don't pile up counts.
        let _ = write_count(0);
        return Ok(());
    }

    let config = read_config();
    if !config.enabled {
        let _ = write_count(0);
        return Ok(());
    }

    let count = read_count().saturating_add(1);
    write_count(count)?;
    info!(
        "bootloop protection: incomplete-boot count {count}/{}",
        config.max_failed
    );

    if count < config.max_failed {
        return Ok(());
    }

    warn!("bootloop protection: {count} consecutive incomplete boots, disabling all modules!");
    if let Err(e) = module::disable_all_modules() {
        // Keep the count so the next boot retries the rescue.
        warn!("bootloop protection: disable all modules failed: {e:#}");
        return Ok(());
    }

    let rescued = RescuedInfo {
        failed_boots: count,
        timestamp: unix_now(),
    };
    if let Ok(content) = serde_json::to_string_pretty(&rescued) {
        let _ = fs::write(RESCUED_PATH, content);
    }
    write_count(0)?;
    warn!("bootloop protection: all modules disabled, marker written for manager notice");
    Ok(())
}

/// Called from `boot-completed`: this boot succeeded, reset the counter.
/// The rescued marker is left for the manager to consume and dismiss.
pub fn on_boot_completed() -> Result<()> {
    write_count(0)?;
    Ok(())
}

/// Print protection status as JSON for the manager.
pub fn status() -> Result<()> {
    let config = read_config();
    let rescued: Option<RescuedInfo> = fs::read_to_string(RESCUED_PATH)
        .ok()
        .and_then(|content| serde_json::from_str(&content).ok());
    let output = serde_json::json!({
        "enabled": config.enabled,
        "max_failed": config.max_failed,
        "failed_count": read_count(),
        "rescued": rescued.is_some(),
        "rescued_failed_boots": rescued.as_ref().map(|r| r.failed_boots),
        "rescued_timestamp": rescued.as_ref().map(|r| r.timestamp),
    });
    println!("{output}");
    Ok(())
}

pub fn set_enabled(enabled: bool) -> Result<()> {
    let mut config = read_config();
    config.enabled = enabled;
    write_config(&config)?;
    if !enabled {
        let _ = write_count(0);
    }
    info!("bootloop protection enabled: {enabled}");
    Ok(())
}

pub fn set_max(count: u32) -> Result<()> {
    if !(MIN_MAX_FAILED..=MAX_MAX_FAILED).contains(&count) {
        bail!("Threshold must be between {MIN_MAX_FAILED} and {MAX_MAX_FAILED}");
    }
    let mut config = read_config();
    config.max_failed = count;
    write_config(&config)?;
    info!("bootloop protection threshold: {count}");
    Ok(())
}

/// Dismiss the manager rescue notice. Called after the user acknowledges it.
pub fn clear_rescued() -> Result<()> {
    if Path::new(RESCUED_PATH).exists() {
        fs::remove_file(RESCUED_PATH).with_context(|| "Failed to remove rescued marker")?;
    }
    Ok(())
}
