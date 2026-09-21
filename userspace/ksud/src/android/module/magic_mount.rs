//! Magic Mount: Magisk-style userspace mounting of module `system/` trees.
//!
//! OriginSU delegates mounting to a metamodule when one owns it (e.g.
//! ZeroMount). When no enabled metamodule provides a mount script, this pass
//! overlays every active module's `system/` tree onto the live rootfs at the
//! post-mount stage so Magisk-style modules work out of the box:
//!
//! - Directories are stacked with chained overlayfs mounts (one overlay per
//!   top-level dir, later modules win), falling back to per-file bind mounts
//!   on kernels without overlayfs.
//! - A directory containing a `.replace` marker replaces the target instead
//!   of merging: the target is covered with a tmpfs and only the module's
//!   files are bound into it (the marker itself is never exposed).
//! - Modules with `skip_mount`, or disabled/removed modules, are skipped.
//!
//! State is a single flag file ([defs::MAGIC_MOUNT_DISABLE_FILE]):
//! absent means enabled (default). It takes effect on the next boot; the
//! `ksud module magic-mount` CLI manages it.

use std::{
    ffi::CString,
    path::{Path, PathBuf},
};

use anyhow::{Context, Result, bail};
use log::{debug, info, warn};

use crate::{
    android::{
        module::{self, ModuleType::Active},
        utils::switch_mnt_ns,
    },
    defs,
};

/// Marker file inside a module directory: replace the target dir instead of
/// merging into it (Magisk semantics).
const REPLACE_MARKER: &str = ".replace";

/// Magic Mount is enabled unless the disable flag file exists.
pub fn is_enabled() -> bool {
    !Path::new(defs::MAGIC_MOUNT_DISABLE_FILE).exists()
}

/// Persist the Magic Mount toggle. Takes effect on the next boot.
pub fn set_enabled(enabled: bool) -> Result<()> {
    let flag = Path::new(defs::MAGIC_MOUNT_DISABLE_FILE);
    if enabled {
        if flag.exists() {
            std::fs::remove_file(flag)
                .with_context(|| format!("Failed to remove {}", flag.display()))?;
        }
        info!("Magic Mount enabled (takes effect on next boot)");
    } else {
        if let Some(parent) = flag.parent() {
            std::fs::create_dir_all(parent)
                .with_context(|| format!("Failed to create {}", parent.display()))?;
        }
        std::fs::write(flag, b"1")
            .with_context(|| format!("Failed to write {}", flag.display()))?;
        info!("Magic Mount disabled (takes effect on next boot)");
    }
    Ok(())
}

pub fn status() -> Result<()> {
    if is_enabled() {
        println!("enabled");
    } else {
        println!("disabled");
    }
    Ok(())
}

/// Which mount engine Magic Mount uses. `Auto` (default) prefers overlayfs
/// and falls back to per-file bind mounts on kernels without it.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MountBackend {
    Auto,
    Overlay,
    Bind,
}

impl MountBackend {
    fn as_str(self) -> &'static str {
        match self {
            Self::Auto => "auto",
            Self::Overlay => "overlay",
            Self::Bind => "bind",
        }
    }

    fn parse(s: &str) -> Result<Self> {
        match s.trim() {
            "auto" => Ok(Self::Auto),
            "overlay" => Ok(Self::Overlay),
            "bind" => Ok(Self::Bind),
            other => bail!("unknown Magic Mount backend '{other}' (want auto/overlay/bind)"),
        }
    }
}

/// Read the persisted backend (`auto` when unset or unreadable).
pub fn backend() -> MountBackend {
    std::fs::read_to_string(defs::MAGIC_MOUNT_BACKEND_FILE)
        .ok()
        .and_then(|s| MountBackend::parse(&s).ok())
        .unwrap_or(MountBackend::Auto)
}

/// Persist the backend (`auto`/`overlay`/`bind`). Takes effect next boot.
pub fn set_backend(mode: &str) -> Result<()> {
    let parsed = MountBackend::parse(mode)?;
    if let Some(parent) = Path::new(defs::MAGIC_MOUNT_BACKEND_FILE).parent() {
        std::fs::create_dir_all(parent)
            .with_context(|| format!("Failed to create {}", parent.display()))?;
    }
    std::fs::write(defs::MAGIC_MOUNT_BACKEND_FILE, parsed.as_str())
        .with_context(|| format!("Failed to write {}", defs::MAGIC_MOUNT_BACKEND_FILE))?;
    info!(
        "Magic Mount backend set to {} (takes effect on next boot)",
        parsed.as_str()
    );
    Ok(())
}

pub fn print_backend() -> Result<()> {
    println!("{}", backend().as_str());
    Ok(())
}

/// Whether an enabled metamodule provides its own mount script. When it
/// does, the metamodule owns mounting and Magic Mount stands down.
fn metamodule_owns_mounting() -> bool {
    let Some(meta) = module::metamodule::get_metamodule_path() else {
        return false;
    };
    if meta.join(defs::DISABLE_FILE_NAME).exists() {
        return false;
    }
    meta.join(defs::METAMODULE_MOUNT_SCRIPT).exists()
}

fn overlay_supported() -> bool {
    std::fs::read_to_string("/proc/filesystems")
        .map(|s| s.lines().any(|l| l.trim_end() == "overlay"))
        .unwrap_or(false)
}

fn cstr(s: &str) -> Result<CString> {
    CString::new(s).with_context(|| format!("Interior NUL in mount arg: {s}"))
}

/// Borrowed file name as str, if representable.
fn file_name_str(path: &Path) -> Option<&str> {
    path.file_name().and_then(std::ffi::OsStr::to_str)
}

fn opt_cstr_ptr(s: Option<&CString>) -> *const libc::c_char {
    s.map_or_else(std::ptr::null, |v| v.as_ptr())
}

fn raw_mount(
    source: Option<&str>,
    target: &Path,
    fstype: Option<&str>,
    flags: libc::c_ulong,
    data: Option<&str>,
) -> Result<()> {
    let target_s = cstr(&target.to_string_lossy())?;
    // Keep the CStrings alive for the syscall.
    let source_s = source.map(cstr).transpose()?;
    let fstype_s = fstype.map(cstr).transpose()?;
    let data_s = data.map(cstr).transpose()?;
    let ret = unsafe {
        libc::mount(
            opt_cstr_ptr(source_s.as_ref()),
            target_s.as_ptr(),
            opt_cstr_ptr(fstype_s.as_ref()),
            flags,
            opt_cstr_ptr(data_s.as_ref()).cast::<libc::c_void>(),
        )
    };
    if ret != 0 {
        bail!(
            "mount({source:?}, {}, {fstype:?}) failed: {}",
            target.display(),
            std::io::Error::last_os_error(),
        );
    }
    Ok(())
}

fn bind_mount(source: &Path, target: &Path) -> Result<()> {
    raw_mount(
        Some(&source.to_string_lossy()),
        target,
        None,
        libc::MS_BIND,
        None,
    )
    .with_context(|| format!("bind {} -> {}", source.display(), target.display()))
}

/// Resolve a mount target through one level of symlink (e.g. /system/vendor
/// -> /vendor). Deeper resolution is handled by canonicalizing existing
/// parents where needed.
fn resolve_target(path: &Path) -> PathBuf {
    if let Ok(link) = std::fs::read_link(path) {
        let resolved = if link.is_absolute() {
            link
        } else if let Some(parent) = path.parent() {
            parent.join(link)
        } else {
            return path.to_path_buf();
        };
        debug!("resolved {} -> {}", path.display(), resolved.display());
        return resolved;
    }
    path.to_path_buf()
}

/// Collect (module_id, module system dir) for every mountable module, sorted
/// by id for deterministic stacking order.
fn collect_mountable_modules() -> Vec<(String, PathBuf)> {
    let mut modules: Vec<(String, PathBuf)> = Vec::new();
    let _ = module::foreach_module(Active, |path| {
        let Some(id) = file_name_str(path) else {
            return Ok(());
        };
        let system = path.join("system");
        if !system.is_dir() {
            return Ok(());
        }
        if path.join("skip_mount").exists() {
            info!("{id}: skip_mount present, skipping magic mount");
            return Ok(());
        }
        modules.push((id.to_string(), system));
        Ok(())
    });
    modules.sort_by(|a, b| a.0.cmp(&b.0));
    modules
}

fn list_top_entries(system: &Path) -> Vec<PathBuf> {
    let mut out = Vec::new();
    let Ok(entries) = std::fs::read_dir(system) else {
        return Vec::new();
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if file_name_str(&path).is_some_and(|n| n != REPLACE_MARKER) {
            out.push(path);
        }
    }
    out.sort();
    out
}

/// Recursively collect every directory (deepest first) under `dir` that
/// contains a `.replace` marker. Returns (source dir, target dir) pairs.
fn collect_replace_dirs(system: &Path) -> Vec<(PathBuf, PathBuf)> {
    let mut out = Vec::new();
    let mut stack = vec![system.to_path_buf()];
    while let Some(dir) = stack.pop() {
        let Ok(entries) = std::fs::read_dir(&dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                if path.join(REPLACE_MARKER).is_file()
                    && let Ok(rel) = path.strip_prefix(system) {
                    out.push((path.clone(), PathBuf::from("/system").join(rel)));
                }
                stack.push(path);
            }
        }
    }
    // Deepest first so child tmpfs mounts land on top of parent overlays.
    out.sort_by_key(|a| std::cmp::Reverse(a.0.components().count()));
    out
}

/// Mirror a module subtree onto a tmpfs target with empty files, then bind
/// each real file over its placeholder. Returns the number of bound files.
fn populate_replace_dir(src: &Path, target: &Path) -> Result<usize> {
    // Create the placeholder tree first.
    let mut dirs = vec![src.to_path_buf()];
    let mut files = Vec::new();
    while let Some(dir) = dirs.pop() {
        let Ok(entries) = std::fs::read_dir(&dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                dirs.push(path);
            } else if path.is_file() {
                if file_name_str(&path) == Some(REPLACE_MARKER) {
                    continue;
                }
                files.push(path);
            }
        }
    }
    let mut bound = 0;
    for src_file in &files {
        let rel = src_file.strip_prefix(src).with_context(|| {
            format!("strip prefix {} from {}", src.display(), src_file.display())
        })?;
        let dst = target.join(rel);
        if let Some(parent) = dst.parent()
            && !parent.exists()
        {
            std::fs::create_dir_all(parent)
                .with_context(|| format!("create {}", parent.display()))?;
        }
        if !dst.exists() {
            std::fs::write(&dst, b"")
                .with_context(|| format!("create placeholder {}", dst.display()))?;
        }
        bind_mount(src_file, &dst)?;
        bound += 1;
    }
    Ok(bound)
}

fn replace_mount(src: &Path, target: &Path) -> Result<()> {
    // The target must exist (we replace something real); resolve symlinks so
    // e.g. /system/vendor lands on /vendor.
    let mut target = resolve_target(target);
    if !target.exists()
        && let Some(parent) = target.parent()
        && let Ok(canon) = parent.canonicalize()
        && let Some(leaf) = target.file_name()
    {
        target = canon.join(leaf);
    }
    if !target.exists() {
        bail!("replace target {} does not exist", target.display());
    }
    // Cover with tmpfs, then bind module files into it.
    raw_mount(Some("tmpfs"), &target, Some("tmpfs"), 0, Some("mode=755"))?;
    let bound = populate_replace_dir(src, &target)?;
    info!(
        "replace-mounted {} -> {} ({bound} files)",
        src.display(),
        target.display()
    );
    Ok(())
}

fn overlay_mount(src: &Path, target: &Path, work_base: &Path, tag: &str) -> Result<()> {
    let target = resolve_target(target);
    if !target.exists() {
        // New top-level dir (e.g. a module-only path). Only creatable when an
        // ancestor is writable (e.g. /data mounts); system partitions are
        // read-only so this is best-effort.
        if let Err(e) = std::fs::create_dir_all(&target) {
            bail!(
                "target {} does not exist and cannot be created: {e}",
                target.display(),
            );
        }
    }
    let work = work_base.join(tag);
    std::fs::create_dir_all(&work).with_context(|| format!("create workdir {}", work.display()))?;
    let opts = format!(
        "lowerdir={},upperdir={},workdir={}",
        target.display(),
        src.display(),
        work.display(),
    );
    // Chained overlay: if the target is already an overlay from a previous
    // module, it simply becomes the new lower layer (last module wins).
    raw_mount(Some("overlay"), &target, Some("overlay"), 0, Some(&opts))?;
    info!("overlay-mounted {} -> {}", src.display(), target.display());
    Ok(())
}

fn bind_tree(src: &Path, target_base: &Path) -> Result<usize> {
    // Fallback for kernels without overlayfs: bind-mount each file whose
    // target already exists. New files cannot be materialized on read-only
    // partitions and are skipped with a warning.
    let mut bound = 0;
    let mut stack = vec![src.to_path_buf()];
    while let Some(dir) = stack.pop() {
        let Ok(entries) = std::fs::read_dir(&dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                stack.push(path);
                continue;
            }
            if !path.is_file() || file_name_str(&path) == Some(REPLACE_MARKER) {
                continue;
            }
            let rel = path.strip_prefix(src).with_context(|| {
                format!("strip prefix {} from {}", src.display(), path.display())
            })?;
            let mut target = target_base.join(rel);
            target = resolve_target(&target);
            if !target.exists() {
                warn!(
                    "no overlayfs: cannot materialize new file {}, skipping",
                    target.display(),
                );
                continue;
            }
            bind_mount(&path, &target)?;
            bound += 1;
        }
    }
    Ok(bound)
}

/// Run the Magic Mount pass. Safe to call when disabled or when a metamodule
/// owns mounting (both are no-ops). Per-module failures are logged and do
/// not abort the pass.
pub fn run_magic_mount() -> Result<()> {
    if !is_enabled() {
        info!("Magic Mount disabled, skipping");
        return Ok(());
    }
    if metamodule_owns_mounting() {
        info!("Metamodule owns mounting, Magic Mount stands down");
        return Ok(());
    }

    // Mounts must be visible system-wide: join init's mount namespace.
    // Best-effort: post-fs-data normally already runs there.
    if let Err(e) = switch_mnt_ns(1) {
        warn!("join init mount namespace failed (continuing anyway): {e:#}");
    }

    let modules = collect_mountable_modules();
    if modules.is_empty() {
        info!("Magic Mount: no mountable modules");
        return Ok(());
    }

    let work_base = Path::new(defs::MAGIC_MOUNT_WORK_DIR);
    if work_base.exists() {
        let _ = std::fs::remove_dir_all(work_base);
    }
    std::fs::create_dir_all(work_base)
        .with_context(|| format!("create {}", work_base.display()))?;

    let use_overlay = match backend() {
        MountBackend::Auto => overlay_supported(),
        MountBackend::Bind => false,
        MountBackend::Overlay => {
            if !overlay_supported() {
                warn!("backend forced to overlay but kernel lacks overlayfs; using bind fallback");
                false
            } else {
                true
            }
        }
    };
    info!(
        "Magic Mount: {} module(s), backend {} (overlayfs {})",
        modules.len(),
        backend().as_str(),
        if use_overlay { "in use" } else { "not used" },
    );

    let mut work_seq: u64 = 0;
    for (id, system) in &modules {
        // Phase 1: overlay (or bind) each top-level entry.
        for top in list_top_entries(system) {
            let Some(name) = file_name_str(&top) else {
                continue;
            };
            let target = PathBuf::from("/system").join(name);
            // resolve_target() transparently redirects /system/<part>
            // symlinks (vendor, product, ...) onto the real partitions.
            if top.is_dir() {
                if use_overlay {
                    work_seq += 1;
                    let tag = format!("{id}_{work_seq}");
                    if let Err(e) = overlay_mount(&top, &target, work_base, &tag) {
                        warn!("{id}: overlay {name} failed: {e:#}");
                    }
                } else {
                    match bind_tree(&top, &target) {
                        Ok(n) => info!("{id}: bind-mounted {n} files under {name}"),
                        Err(e) => warn!("{id}: bind fallback for {name} failed: {e:#}"),
                    }
                }
            } else if top.is_file() {
                let target = resolve_target(&target);
                if target.exists() {
                    if let Err(e) = bind_mount(&top, &target) {
                        warn!("{id}: bind {name} failed: {e:#}");
                    }
                } else {
                    warn!("{id}: target {} missing, skipping {name}", target.display());
                }
            }
        }
        // Phase 2: `.replace` dirs win over the merged view (deepest first).
        for (src, target) in collect_replace_dirs(system) {
            if let Err(e) = replace_mount(&src, &target) {
                warn!("{id}: replace {} failed: {e:#}", src.display());
            }
        }
    }

    info!("Magic Mount pass complete");
    Ok(())
}
