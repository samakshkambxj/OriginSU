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
//! Overlay backing: overlayfs rejects upper/work dirs on FBE-encrypted
//! /data, so the work dir itself is turned into a tmpfs at boot and each
//! module's `system/` tree is staged onto it before mounting. This keeps
//! upper and work on the same (supported) filesystem on every device. When
//! the tmpfs cannot be mounted, the pass falls back to on-/data paths,
//! which still works on unencrypted /data.
//!
//! State is a single flag file ([defs::MAGIC_MOUNT_ENABLE_FILE]):
//! present means enabled, absent means disabled. Magic Mount is temporarily
//! disabled by default, so the pass only runs after an explicit
//! `ksud module magic-mount enable`. It takes effect on the next boot.

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

/// Magic Mount is enabled only when the enable flag file exists (disabled by
/// default while the feature is turned off).
pub fn is_enabled() -> bool {
    Path::new(defs::MAGIC_MOUNT_ENABLE_FILE).exists()
}

/// Persist the Magic Mount toggle. Takes effect on the next boot.
pub fn set_enabled(enabled: bool) -> Result<()> {
    let flag = Path::new(defs::MAGIC_MOUNT_ENABLE_FILE);
    if enabled {
        if let Some(parent) = flag.parent() {
            std::fs::create_dir_all(parent)
                .with_context(|| format!("Failed to create {}", parent.display()))?;
        }
        std::fs::write(flag, b"1")
            .with_context(|| format!("Failed to write {}", flag.display()))?;
        info!("Magic Mount enabled (takes effect on next boot)");
    } else {
        if flag.exists() {
            std::fs::remove_file(flag)
                .with_context(|| format!("Failed to remove {}", flag.display()))?;
        }
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
    // Lines look like "nodev\toverlay": compare the last field, not the
    // whole line.
    std::fs::read_to_string("/proc/filesystems")
        .map(|s| {
            s.lines()
                .any(|l| l.split_whitespace().last() == Some("overlay"))
        })
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

/// Copy `security.*` xattrs (SELinux contexts) from `src` to `dst`.
/// Best-effort: any failure is ignored so a missing xattr backend never
/// aborts the pass. Keeps staged files labeled exactly like the originals.
fn copy_security_xattrs(src: &Path, dst: &Path) {
    use std::os::unix::ffi::OsStrExt;
    let (Ok(src_c), Ok(dst_c)) = (
        CString::new(src.as_os_str().as_bytes()),
        CString::new(dst.as_os_str().as_bytes()),
    ) else {
        return;
    };
    let list_len = unsafe { libc::llistxattr(src_c.as_ptr(), std::ptr::null_mut(), 0) };
    if list_len <= 0 {
        return;
    }
    let mut names = vec![0u8; list_len as usize];
    let rc = unsafe {
        libc::llistxattr(
            src_c.as_ptr(),
            names.as_mut_ptr().cast::<libc::c_char>(),
            names.len(),
        )
    };
    if rc < 0 {
        return;
    }
    for name in names[..rc as usize].split(|&b| b == 0) {
        if name.is_empty() || !name.starts_with(b"security.") {
            continue;
        }
        let vlen = unsafe {
            libc::lgetxattr(
                src_c.as_ptr(),
                name.as_ptr().cast::<libc::c_char>(),
                std::ptr::null_mut(),
                0,
            )
        };
        if vlen < 0 {
            continue;
        }
        let mut value = vec![0u8; vlen as usize];
        let rc = unsafe {
            libc::lgetxattr(
                src_c.as_ptr(),
                name.as_ptr().cast::<libc::c_char>(),
                value.as_mut_ptr().cast::<libc::c_void>(),
                value.len(),
            )
        };
        if rc < 0 {
            continue;
        }
        unsafe {
            libc::lsetxattr(
                dst_c.as_ptr(),
                name.as_ptr().cast::<libc::c_char>(),
                value.as_ptr().cast::<libc::c_void>(),
                value.len(),
                0,
            );
        }
    }
}

/// Recursively copy a tree, preserving symlinks and unix modes plus
/// `security.*` xattrs best-effort. Returns (files, bytes) staged.
/// Sockets/fifos/devices are skipped: meaningless as an overlay upper.
fn copy_tree(src: &Path, dst: &Path) -> Result<(u64, u64)> {
    use std::os::unix::fs::PermissionsExt;
    let meta = std::fs::symlink_metadata(src).with_context(|| format!("stat {}", src.display()))?;
    if meta.file_type().is_symlink() {
        let link =
            std::fs::read_link(src).with_context(|| format!("readlink {}", src.display()))?;
        if let Some(parent) = dst.parent() {
            std::fs::create_dir_all(parent)
                .with_context(|| format!("create {}", parent.display()))?;
        }
        let _ = std::fs::remove_file(dst);
        std::os::unix::fs::symlink(&link, dst)
            .with_context(|| format!("symlink {}", dst.display()))?;
        copy_security_xattrs(src, dst);
        return Ok((0, 0));
    }
    if meta.is_dir() {
        std::fs::create_dir_all(dst).with_context(|| format!("create {}", dst.display()))?;
        std::fs::set_permissions(dst, meta.permissions())
            .with_context(|| format!("chmod {}", dst.display()))?;
        copy_security_xattrs(src, dst);
        let mut files = 0u64;
        let mut bytes = 0u64;
        let entries =
            std::fs::read_dir(src).with_context(|| format!("readdir {}", src.display()))?;
        for entry in entries.flatten() {
            let path = entry.path();
            let Some(name) = file_name_str(&path) else {
                continue;
            };
            let (f, b) = copy_tree(&path, &dst.join(name))?;
            files += f;
            bytes += b;
        }
        return Ok((files, bytes));
    }
    if meta.is_file() {
        if let Some(parent) = dst.parent() {
            std::fs::create_dir_all(parent)
                .with_context(|| format!("create {}", parent.display()))?;
        }
        std::fs::copy(src, dst)
            .with_context(|| format!("copy {} -> {}", src.display(), dst.display()))?;
        std::fs::set_permissions(
            dst,
            std::fs::Permissions::from_mode(meta.permissions().mode() & 0o7777),
        )
        .with_context(|| format!("chmod {}", dst.display()))?;
        copy_security_xattrs(src, dst);
        return Ok((1, meta.len()));
    }
    warn!("staging: skipping special file {}", src.display());
    Ok((0, 0))
}

/// Turn the work base into a tmpfs so it can back overlay uppers + workdirs.
///
/// overlayfs rejects upper/work dirs on FBE-encrypted /data. Mounting tmpfs
/// over the work base keeps upper and work on the same (supported)
/// filesystem on every device. Returns None when the tmpfs cannot be
/// mounted; callers then fall back to on-/data paths (works on unencrypted
/// /data, fails on FBE like before — now with a loud warning).
fn ensure_overlay_stage(work_base: &Path) -> Option<PathBuf> {
    // Detach any previous mount (e.g. a manual re-run in the same boot),
    // then start from a clean dir so stale uppers can't leak across runs.
    if let Ok(base) = cstr(&work_base.to_string_lossy()) {
        unsafe {
            libc::umount2(base.as_ptr(), libc::MNT_DETACH);
        }
    }
    if work_base.exists() {
        let _ = std::fs::remove_dir_all(work_base);
    }
    if let Err(e) = std::fs::create_dir_all(work_base) {
        warn!("staging: cannot create {}: {e:#}", work_base.display());
        return None;
    }
    if let Err(e) = raw_mount(
        Some("tmpfs"),
        work_base,
        Some("tmpfs"),
        0,
        Some("mode=0755"),
    ) {
        warn!(
            "staging: cannot mount tmpfs on {}: {e:#}; overlay upper stays on /data",
            work_base.display()
        );
        return None;
    }
    info!(
        "staging: overlay backing is tmpfs at {}",
        work_base.display()
    );
    Some(work_base.to_path_buf())
}

/// Copy a module's `system/` tree onto the tmpfs stage so it can serve as an
/// overlayfs upperdir. Returns the staged root, or None on failure (the
/// caller falls back to the on-/data path).
fn stage_module(id: &str, system: &Path, stage_base: &Path) -> Option<PathBuf> {
    let dest = stage_base.join("up").join(id);
    match copy_tree(system, &dest) {
        Ok((files, bytes)) => {
            info!("{id}: staged {files} files ({bytes} bytes) to tmpfs");
            Some(dest)
        }
        Err(e) => {
            warn!("{id}: tmpfs staging failed: {e:#}; using on-/data path");
            None
        }
    }
}

/// Thin wrapper over the mount(2) syscall.
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

    // overlayfs rejects FBE-encrypted /data as upper/work, so back both by
    // tmpfs and stage module trees onto it. When the tmpfs cannot be
    // mounted the pass falls back to /data paths (unencrypted /data keeps
    // working; FBE fails loudly per-mount instead of silently).
    let stage_base = if use_overlay {
        ensure_overlay_stage(work_base)
    } else {
        None
    };

    let mut work_seq: u64 = 0;
    for (id, system) in &modules {
        // Stage this module's tree for use as overlay upperdir.
        let staged = stage_base
            .as_ref()
            .and_then(|base| stage_module(id, system, base));
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
                    // Prefer the tmpfs-staged copy (works on FBE); fall back
                    // to the on-/data tree when staging failed.
                    let src = staged
                        .as_ref()
                        .and_then(|root| {
                            let p = root.join(name);
                            p.exists().then_some(p)
                        })
                        .unwrap_or_else(|| top.clone());
                    work_seq += 1;
                    let tag = format!("{id}_{work_seq}");
                    if let Err(e) = overlay_mount(&src, &target, work_base, &tag) {
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
