//! Transactional module changes.
//!
//! Before a module is added or updated we snapshot which modules are enabled.
//! A successful boot commits the transaction. If bootloop protection triggers
//! instead, the snapshot is restored, so only the change that broke the boot is
//! reverted instead of disabling every module.

use std::{collections::BTreeSet, fs, path::Path, time::SystemTime};

use anyhow::{Context, Result};
use const_format::concatcp;
use log::info;
use serde::{Deserialize, Serialize};

use super::read_module_prop;
use crate::defs;

const TRANSACTION_PATH: &str = concatcp!(defs::WORKING_DIR, ".module_transaction.json");

#[derive(Debug, Serialize, Deserialize)]
pub struct Transaction {
    pub module_id: String,
    pub enabled: BTreeSet<String>,
    pub disabled: BTreeSet<String>,
    pub timestamp: u64,
}

fn unix_now() -> u64 {
    SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .map(|duration| duration.as_secs())
        .unwrap_or(0)
}

/// Captures the pre-change module state. Keeps an existing snapshot: when
/// several modules are installed before the next boot, the oldest snapshot is
/// the state we want to return to.
pub fn begin(module_id: &str) -> Result<()> {
    if Path::new(TRANSACTION_PATH).exists() {
        return Ok(());
    }

    let mut enabled = BTreeSet::new();
    let mut disabled = BTreeSet::new();
    if let Ok(entries) = fs::read_dir(defs::MODULE_DIR) {
        for entry in entries.flatten() {
            let path = entry.path();
            let Ok(prop) = read_module_prop(&path) else {
                continue;
            };
            let Some(id) = prop.get("id").map(|id| id.trim()) else {
                continue;
            };
            if id.is_empty() {
                continue;
            }
            if path.join(defs::DISABLE_FILE_NAME).exists() {
                disabled.insert(id.to_owned());
            } else {
                enabled.insert(id.to_owned());
            }
        }
    }

    let transaction = Transaction {
        module_id: module_id.to_owned(),
        enabled,
        disabled,
        timestamp: unix_now(),
    };
    fs::write(
        TRANSACTION_PATH,
        serde_json::to_string_pretty(&transaction)?,
    )
    .with_context(|| "Failed to write module transaction")?;
    info!(
        "module transaction started for {module_id}: {} enabled, {} disabled",
        transaction.enabled.len(),
        transaction.disabled.len()
    );
    Ok(())
}

/// Removes the snapshot after a successful boot.
pub fn commit() -> Result<()> {
    if Path::new(TRANSACTION_PATH).exists() {
        fs::remove_file(TRANSACTION_PATH).with_context(|| "Failed to remove module transaction")?;
    }
    Ok(())
}

/// Restores the snapshot. Returns true when a transaction was reverted.
pub fn rollback() -> Result<bool> {
    let content = match fs::read_to_string(TRANSACTION_PATH) {
        Ok(content) => content,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(false),
        Err(error) => return Err(error).context("Failed to read module transaction"),
    };
    let transaction: Transaction =
        serde_json::from_str(&content).with_context(|| "Failed to parse module transaction")?;

    let Ok(entries) = fs::read_dir(defs::MODULE_DIR) else {
        return Ok(false);
    };
    for entry in entries.flatten() {
        let path = entry.path();
        let Ok(prop) = read_module_prop(&path) else {
            continue;
        };
        let Some(id) = prop.get("id").map(|id| id.trim()) else {
            continue;
        };
        if id.is_empty() {
            continue;
        }
        let disable_file = path.join(defs::DISABLE_FILE_NAME);
        if transaction.enabled.contains(id) {
            if disable_file.exists() {
                fs::remove_file(&disable_file)
                    .with_context(|| format!("Failed to re-enable module during rollback: {id}"))?;
            }
        } else if !disable_file.exists() {
            fs::File::create(&disable_file)
                .with_context(|| format!("Failed to disable module during rollback: {id}"))?;
        }
    }

    fs::remove_file(TRANSACTION_PATH)
        .with_context(|| "Failed to remove rolled back module transaction")?;
    info!(
        "module transaction rolled back (installed module: {})",
        transaction.module_id
    );
    Ok(true)
}
