/*
 * Dynamic manager operations, reached via the new single-extension ioctl
 * KSU_IOCTL_EXTENSION with extension_id=KSU_EXTENSION_DYNAMIC_MANAGER.
 * The user payload is a struct ksu_manager_cmd forwarded verbatim to
 * kernel/extension/dynamic_manager.c::ksu_dynamic_manager_set/get().
 */
use anyhow::{Context, Result, ensure};
use std::fs;
use std::io::Write;
use std::path::Path;

use crate::extension_id::ExtensionId;
use crate::ksu_uapi;
use crate::ksucalls::{dynamic_manager_io, get_extension};

const DYNAMIC_MANAGER_FILE: &str = "/data/adb/ksu/.dynamic_managers";
const DYNAMIC_MANAGER_MAGIC: u32 = 0x4d53_4b44;
const DYNAMIC_MANAGER_VERSION: u32 = 1;
/// Mirrors DYNAMIC_MANAGER_MAX_CERT_SIZE in kernel/extension/dynamic_manager.c.
/// Kernels built before that limit was raised still cap at 1024, so an ioctl
/// failure remains possible; the error below says why instead of a bare EINVAL.
const MAX_MANAGER_CERT_SIZE: u32 = 8192;
const MANAGER_HASH_LENGTH: usize = 64;

fn copy_manager_string<const N: usize>(target: &mut [libc::c_char; N], value: &str) -> Result<()> {
    anyhow::ensure!(
        value.len() < N && !value.as_bytes().contains(&0),
        "value is too long or contains NUL"
    );
    for (slot, byte) in target.iter_mut().zip(value.bytes()) {
        *slot = byte as libc::c_char;
    }
    Ok(())
}

fn lookup_package_uid(package: &str) -> Result<u32> {
    let packages = fs::read_to_string("/data/system/packages.list")?;
    for line in packages.lines() {
        let mut fields = line.split_whitespace();
        if fields.next() == Some(package) {
            let uid = fields
                .next()
                .ok_or_else(|| anyhow::anyhow!("missing package UID"))?;
            return Ok(uid.parse()?);
        }
    }
    anyhow::bail!("package is not installed")
}

fn state_bytes(state: &ksu_uapi::ksu_manager_cmd) -> Vec<u8> {
    let mut bytes = Vec::with_capacity(16 + std::mem::size_of_val(&state.entries));
    bytes.extend_from_slice(&DYNAMIC_MANAGER_MAGIC.to_le_bytes());
    bytes.extend_from_slice(&DYNAMIC_MANAGER_VERSION.to_le_bytes());
    bytes.extend_from_slice(&state.enabled.to_le_bytes());
    bytes.extend_from_slice(&state.count.to_le_bytes());
    let entries = unsafe {
        std::slice::from_raw_parts(
            state.entries.as_ptr().cast::<u8>(),
            std::mem::size_of_val(&state.entries),
        )
    };
    bytes.extend_from_slice(entries);
    bytes
}

fn persist() -> Result<()> {
    let state = manager_get()?;
    let path = Path::new(DYNAMIC_MANAGER_FILE);
    let temp_path = path.with_extension("tmp");
    let mut file = fs::OpenOptions::new()
        .create(true)
        .truncate(true)
        .write(true)
        .open(&temp_path)?;
    file.write_all(&state_bytes(&state))?;
    file.sync_all()?;
    drop(file);
    fs::rename(temp_path, path)?;
    Ok(())
}

pub fn manager_get() -> Result<ksu_uapi::ksu_manager_cmd> {
    let mut cmd: ksu_uapi::ksu_manager_cmd = unsafe { std::mem::zeroed() };
    cmd.operation = ksu_uapi::KSU_MANAGER_GET;
    unsafe {
        dynamic_manager_io(&raw mut cmd)?;
    }
    Ok(cmd)
}

pub fn manager_add(package: &str, cert_size: u32, hash: &str) -> Result<()> {
    ensure!(
        (1..=MAX_MANAGER_CERT_SIZE).contains(&cert_size),
        "certificate size {cert_size} bytes is outside 1..={MAX_MANAGER_CERT_SIZE}"
    );
    ensure!(
        hash.len() == MANAGER_HASH_LENGTH && hash.bytes().all(|b| b.is_ascii_hexdigit()),
        "certificate hash must be {MANAGER_HASH_LENGTH} hex characters"
    );
    let mut cmd: ksu_uapi::ksu_manager_cmd = unsafe { std::mem::zeroed() };
    cmd.operation = ksu_uapi::KSU_MANAGER_ADD;
    cmd.cert_size = cert_size;
    cmd.uid = lookup_package_uid(package)?;
    copy_manager_string(&mut cmd.package, package)?;
    copy_manager_string(&mut cmd.hash, hash)?;
    unsafe {
        dynamic_manager_io(&raw mut cmd).with_context(|| {
            format!(
                "kernel rejected dynamic manager {package} (cert {cert_size} bytes); \
                 kernels before the cert-size fix only accept up to 1024 bytes"
            )
        })?;
    }
    persist()
}

pub fn manager_remove(package: &str) -> Result<()> {
    let mut cmd: ksu_uapi::ksu_manager_cmd = unsafe { std::mem::zeroed() };
    cmd.operation = ksu_uapi::KSU_MANAGER_REMOVE;
    copy_manager_string(&mut cmd.package, package)?;
    unsafe {
        dynamic_manager_io(&raw mut cmd)?;
    }
    persist()
}

pub fn manager_set_enabled(enabled: bool) -> Result<()> {
    let mut cmd: ksu_uapi::ksu_manager_cmd = unsafe { std::mem::zeroed() };
    cmd.operation = ksu_uapi::KSU_MANAGER_SET_ENABLED;
    cmd.enabled = u32::from(enabled);
    unsafe {
        dynamic_manager_io(&raw mut cmd)?;
    }
    persist()
}

pub fn manager_clear() -> Result<()> {
    let mut cmd: ksu_uapi::ksu_manager_cmd = unsafe { std::mem::zeroed() };
    cmd.operation = ksu_uapi::KSU_MANAGER_CLEAR;
    unsafe {
        dynamic_manager_io(&raw mut cmd)?;
    }
    persist()
}

/// Read the sticky enable bit (1 = dynamic manager feature is on).
#[allow(dead_code)] // exposed for external ksud callers
pub fn manager_enabled() -> Result<bool> {
    let state = get_extension(ExtensionId::DynamicManager as u32)?;
    Ok(state.supported && state.value != 0)
}
