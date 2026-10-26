//! Magic Mount: Magisk-style userspace mounting of module `system/` trees.
//!
//! OriginSU delegates mounting to a metamodule when one owns it (e.g.
//! ZeroMount). When no enabled metamodule provides a mount script, this pass
//! merges every active module's `system/` tree and mounts the result:
//!
//! - `auto` (default) tries overlayfs first and falls back to bind mounts.
//! - `overlay` always uses overlayfs (falling back to bind if it cannot be
//!   set up).
//! - `bind` uses Magisk-style tmpfs skeletons and bind mounts only.
//!
//! Both engines merge modules in a deterministic order (later module ids win)
//! and support directories, regular files, symlinks, whiteouts (a character
//! device with rdev 0) and `.replace` directories. The marker itself is never
//! exposed.
//!
//! Modules with `skip_mount`, or disabled/removed modules, are skipped.
//!
//! Overlay backing: overlayfs rejects upper/work dirs on FBE-encrypted /data,
//! so the work dir itself is turned into a tmpfs at boot and the merged tree
//! is staged onto it before mounting. This keeps upper and work on the same
//! (supported) filesystem on every device. If staging fails (for example the
//! tmpfs runs out of space), the pass falls back to bind mounts, which never
//! copy module payloads into RAM.
//!
//! State is a single flag file ([defs::MAGIC_MOUNT_ENABLE_FILE]):
//! present means enabled, absent means disabled. It takes effect on the next
//! boot; the `ksud module magic-mount` CLI manages it.

use std::{
    collections::BTreeMap,
    ffi::CString,
    fs::{FileType, Metadata},
    os::unix::{
        ffi::OsStrExt,
        fs::{FileTypeExt, MetadataExt, PermissionsExt},
    },
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

/// Partitions that may live at the filesystem root instead of inside
/// `/system`. Only names the device actually exposes are used.
const PARTITION_NAMES: &[&str] = &["vendor", "product", "system_ext", "odm"];

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
/// and falls back to Magisk-style bind mounts on kernels without it.
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

/// Borrowed file name as str, if representable.
fn file_name_str(path: &Path) -> Option<&str> {
    path.file_name().and_then(std::ffi::OsStr::to_str)
}

fn path_cstring(path: &Path) -> Result<CString> {
    CString::new(path.as_os_str().as_bytes())
        .with_context(|| format!("Interior NUL in path: {}", path.display()))
}

fn opt_cstr_ptr(s: Option<&CString>) -> *const libc::c_char {
    s.map_or_else(std::ptr::null, |v| v.as_ptr())
}

/// Copy `security.*` xattrs (SELinux contexts) from `src` to `dst`.
/// Best-effort: any failure is ignored so a missing xattr backend never
/// aborts the pass. Keeps staged files labeled exactly like the originals.
fn copy_security_xattrs(src: &Path, dst: &Path) {
    let (Ok(src_c), Ok(dst_c)) = (path_cstring(src), path_cstring(dst)) else {
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
                rc as usize,
                0,
            );
        }
    }
}

/// Preserve ownership, mode and security labels. Symlinks only get ownership
/// and xattrs; chmod on a symlink would follow the link.
fn apply_metadata(src: &Path, dst: &Path) {
    let Ok(meta) = std::fs::symlink_metadata(src) else {
        return;
    };
    if !meta.file_type().is_symlink() {
        let _ =
            std::fs::set_permissions(dst, std::fs::Permissions::from_mode(meta.mode() & 0o7777));
    }
    if let Ok(dst_c) = path_cstring(dst) {
        unsafe {
            libc::lchown(dst_c.as_ptr(), meta.uid(), meta.gid());
        }
    }
    copy_security_xattrs(src, dst);
}

fn set_opaque(dir: &Path) -> Result<()> {
    let dir_c = path_cstring(dir)?;
    let rc = unsafe {
        libc::lsetxattr(
            dir_c.as_ptr(),
            c"trusted.overlay.opaque".as_ptr(),
            b"y".as_ptr().cast::<libc::c_void>(),
            1,
            0,
        )
    };
    if rc != 0 {
        bail!(
            "set trusted.overlay.opaque on {} failed: {}",
            dir.display(),
            std::io::Error::last_os_error()
        );
    }
    Ok(())
}

fn is_opaque(dir: &Path) -> bool {
    let Ok(dir_c) = path_cstring(dir) else {
        return false;
    };
    let mut value = [0u8; 1];
    let rc = unsafe {
        libc::lgetxattr(
            dir_c.as_ptr(),
            c"trusted.overlay.opaque".as_ptr(),
            value.as_mut_ptr().cast::<libc::c_void>(),
            value.len(),
        )
    };
    rc == 1 && value[0] == b'y'
}

/// Thin wrapper over the mount(2) syscall.
fn raw_mount(
    source: Option<&Path>,
    target: &Path,
    fstype: Option<&str>,
    flags: libc::c_ulong,
    data: Option<&str>,
) -> Result<()> {
    let target_s = path_cstring(target)?;
    // Keep the CStrings alive for the syscall.
    let source_s = source.map(path_cstring).transpose()?;
    let fstype_s = fstype.map(CString::new).transpose()?;
    let data_s = data.map(CString::new).transpose()?;
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
            "mount({}, {}, {fstype:?}) failed: {}",
            source.map_or_else(|| "-".to_string(), |p| p.display().to_string()),
            target.display(),
            std::io::Error::last_os_error(),
        );
    }
    Ok(())
}

fn bind_mount(source: &Path, target: &Path) -> Result<()> {
    raw_mount(Some(source), target, None, libc::MS_BIND, None)
        .with_context(|| format!("bind {} -> {}", source.display(), target.display()))
}

fn bind_mount_recursive(source: &Path, target: &Path) -> Result<()> {
    raw_mount(
        Some(source),
        target,
        None,
        libc::MS_BIND | libc::MS_REC,
        None,
    )
    .with_context(|| format!("bind -r {} -> {}", source.display(), target.display()))
}

/// Mark a mount read-only. Bind mounts need `MS_BIND` on the remount so only
/// their per-mount flags change; overlay and tmpfs mounts reject it and need a
/// plain remount of their own superblock.
fn remount_readonly(path: &Path, bind: bool) {
    let flags = if bind {
        libc::MS_REMOUNT | libc::MS_BIND | libc::MS_RDONLY
    } else {
        libc::MS_REMOUNT | libc::MS_RDONLY
    };
    if let Err(e) = raw_mount(None, path, None, flags, None) {
        warn!("make {} read-only failed: {e:#}", path.display());
    }
}

fn make_private(path: &Path) {
    if let Err(e) = raw_mount(None, path, None, libc::MS_PRIVATE, None) {
        debug!("make {} private failed: {e:#}", path.display());
    }
}

fn move_mount(source: &Path, target: &Path) -> Result<()> {
    raw_mount(Some(source), target, None, libc::MS_MOVE, None).with_context(|| {
        format!(
            "move mount {} -> {} failed",
            source.display(),
            target.display()
        )
    })
}

fn cleanup_work(work: &Path) {
    if let Ok(work_c) = path_cstring(work) {
        unsafe {
            libc::umount2(work_c.as_ptr(), libc::MNT_DETACH);
        }
    }
    let _ = std::fs::remove_dir_all(work);
}

fn prepare_work(work: &Path) -> Result<()> {
    cleanup_work(work);
    std::fs::create_dir_all(work).with_context(|| format!("create {}", work.display()))?;
    raw_mount(
        Some(Path::new("tmpfs")),
        work,
        Some("tmpfs"),
        0,
        Some("mode=0755"),
    )
    .with_context(|| format!("mount staging tmpfs on {}", work.display()))?;
    make_private(work);
    Ok(())
}

/// Mount target after resolving one level of symlink (e.g. /system/vendor
/// -> /vendor).
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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum NodeKind {
    Directory,
    File,
    Symlink,
    Whiteout,
}

#[derive(Debug, Clone)]
struct Node {
    name: String,
    kind: NodeKind,
    source: Option<PathBuf>,
    children: BTreeMap<String, Node>,
    replace: bool,
    skip: bool,
}

impl Node {
    fn new(name: impl Into<String>, kind: NodeKind, source: Option<PathBuf>) -> Self {
        Self {
            name: name.into(),
            kind,
            source,
            children: BTreeMap::new(),
            replace: false,
            skip: false,
        }
    }

    fn root() -> Self {
        Self::new("", NodeKind::Directory, None)
    }
}

fn kind_from_metadata(meta: &Metadata) -> Option<NodeKind> {
    let file_type: FileType = meta.file_type();
    if file_type.is_symlink() {
        Some(NodeKind::Symlink)
    } else if file_type.is_dir() {
        Some(NodeKind::Directory)
    } else if file_type.is_file() {
        Some(NodeKind::File)
    } else if file_type.is_char_device() && meta.rdev() == 0 {
        Some(NodeKind::Whiteout)
    } else {
        None
    }
}

fn collect_mountable_modules(partitions: &[String]) -> Vec<(String, PathBuf)> {
    let mut modules: Vec<(String, PathBuf)> = Vec::new();
    let _ = module::foreach_module(Active, |path| {
        let Some(id) = file_name_str(path) else {
            return Ok(());
        };
        let has_system = path.join("system").is_dir();
        let has_partition = partitions.iter().any(|name| path.join(name).is_dir());
        if !has_system && !has_partition {
            return Ok(());
        }
        if path.join("skip_mount").exists() {
            info!("{id}: skip_mount present, skipping magic mount");
            return Ok(());
        }
        modules.push((id.to_string(), path.to_path_buf()));
        Ok(())
    });
    modules.sort_by(|a, b| a.0.cmp(&b.0));
    modules
}

/// Names that are separate partitions on this device, either symlinked from
/// /system or absent from /system entirely (e.g. /odm).
fn partition_names() -> Vec<String> {
    let mut names: Vec<String> = PARTITION_NAMES
        .iter()
        .copied()
        .filter(|name| {
            let root = Path::new("/").join(name);
            let system = Path::new("/system").join(name);
            root.is_dir() && (system.is_symlink() || std::fs::symlink_metadata(system).is_err())
        })
        .map(str::to_string)
        .collect();

    if let Ok(entries) = std::fs::read_dir("/system") {
        for entry in entries.flatten() {
            let path = entry.path();
            let Some(name) = file_name_str(&path) else {
                continue;
            };
            if path.is_symlink()
                && Path::new("/").join(name).is_dir()
                && !names.iter().any(|n| n.as_str() == name)
            {
                names.push(name.to_string());
            }
        }
    }
    names.sort();
    names
}

/// Merge one module directory into `node`, applying Magisk override and
/// `.replace` semantics.
fn merge_dir(src: &Path, node: &mut Node) -> Result<()> {
    node.kind = NodeKind::Directory;
    node.source = Some(src.to_path_buf());
    if src.join(REPLACE_MARKER).exists() || is_opaque(src) {
        node.replace = true;
        node.children.clear();
    }

    let mut entries: Vec<_> = std::fs::read_dir(src)
        .with_context(|| format!("readdir {}", src.display()))?
        .flatten()
        .collect();
    entries.sort_by_key(std::fs::DirEntry::file_name);

    for entry in entries {
        let path = entry.path();
        let Some(file_name) = file_name_str(&path) else {
            continue;
        };
        if file_name == REPLACE_MARKER {
            continue;
        }
        let name = file_name.to_string();
        let meta =
            std::fs::symlink_metadata(&path).with_context(|| format!("stat {}", path.display()))?;
        let Some(kind) = kind_from_metadata(&meta) else {
            warn!("skipping special file {}", path.display());
            continue;
        };

        if kind == NodeKind::Directory {
            let child = node
                .children
                .entry(name.clone())
                .or_insert_with(|| Node::new(name.clone(), NodeKind::Directory, None));
            if child.kind != NodeKind::Directory {
                *child = Node::new(name.clone(), NodeKind::Directory, None);
            }
            merge_dir(&path, child)?;
        } else {
            node.children
                .insert(name.clone(), Node::new(name, kind, Some(path)));
        }
    }
    Ok(())
}

fn collect_modules() -> Result<Option<Node>> {
    let partitions = partition_names();
    let modules = collect_mountable_modules(&partitions);
    if modules.is_empty() {
        return Ok(None);
    }

    let mut root = Node::root();
    let mut system = Node::new("system", NodeKind::Directory, None);
    let mut has_content = false;

    for (id, module_path) in &modules {
        let system_dir = module_path.join("system");
        if system_dir.is_dir() {
            debug!("{id}: collecting {}", system_dir.display());
            merge_dir(&system_dir, &mut system)
                .with_context(|| format!("collect {id} system tree"))?;
            has_content = true;
        }
        for name in &partitions {
            let dir = module_path.join(name);
            if !dir.is_dir() {
                continue;
            }
            let child = system
                .children
                .entry(name.clone())
                .or_insert_with(|| Node::new(name.clone(), NodeKind::Directory, None));
            if child.kind != NodeKind::Directory {
                *child = Node::new(name.clone(), NodeKind::Directory, None);
            }
            merge_dir(&dir, child).with_context(|| format!("collect {id} {name} tree"))?;
            has_content = true;
        }
    }

    if !has_content {
        return Ok(None);
    }

    // Partition trees are mounted at their real root paths, not under
    // /system (where they are usually symlinks).
    for name in partitions {
        if let Some(node) = system.children.remove(&name) {
            root.children.insert(name, node);
        }
    }
    if system.replace {
        warn!(
            "a module requests .replace on /system; only its individual directories can be mounted"
        );
    }
    // The /system container itself is not module-owned: never create a tmpfs
    // over it. Only its children may need one.
    system.source = None;
    root.children.insert("system".to_string(), system);

    prune_empty(&mut root, Path::new("/"));
    if root.children.is_empty() {
        return Ok(None);
    }
    Ok(Some(root))
}

/// Drop directories that contribute nothing (no files, no `.replace`) where
/// the live target is already a directory, so an empty module tree does not
/// produce a mount pass.
fn prune_empty(node: &mut Node, target: &Path) {
    node.children.retain(|name, child| {
        let child_target = target.join(name);
        prune_empty(child, &child_target);
        let target_is_dir = std::fs::symlink_metadata(&child_target)
            .map(|meta| meta.file_type().is_dir())
            .unwrap_or(false);
        !(child.kind == NodeKind::Directory
            && child.children.is_empty()
            && !child.replace
            && target_is_dir)
    });
}

/// Materialize a merged node into the overlay staging directory.
fn materialize(node: &Node, dst: &Path, fallback_meta: Option<&Path>) -> Result<()> {
    match node.kind {
        NodeKind::Directory => {
            std::fs::create_dir_all(dst).with_context(|| format!("create {}", dst.display()))?;
            for (name, child) in &node.children {
                materialize(child, &dst.join(name), None)?;
            }
            if let Some(meta_src) = node.source.as_deref().or(fallback_meta) {
                apply_metadata(meta_src, dst);
            }
            if node.replace {
                set_opaque(dst)
                    .with_context(|| format!("mark {} as a replacement", dst.display()))?;
            }
        }
        NodeKind::File => {
            let src = node
                .source
                .as_ref()
                .with_context(|| format!("missing source for file {}", node.name))?;
            if let Some(parent) = dst.parent() {
                std::fs::create_dir_all(parent)
                    .with_context(|| format!("create {}", parent.display()))?;
            }
            std::fs::copy(src, dst)
                .with_context(|| format!("copy {} -> {}", src.display(), dst.display()))?;
            apply_metadata(src, dst);
        }
        NodeKind::Symlink => {
            let src = node
                .source
                .as_ref()
                .with_context(|| format!("missing source for symlink {}", node.name))?;
            let link =
                std::fs::read_link(src).with_context(|| format!("readlink {}", src.display()))?;
            if let Some(parent) = dst.parent() {
                std::fs::create_dir_all(parent)
                    .with_context(|| format!("create {}", parent.display()))?;
            }
            let _ = std::fs::remove_file(dst);
            std::os::unix::fs::symlink(&link, dst)
                .with_context(|| format!("symlink {}", dst.display()))?;
            apply_metadata(src, dst);
        }
        NodeKind::Whiteout => {
            let dst_c = path_cstring(dst)?;
            let ret = unsafe { libc::mknod(dst_c.as_ptr(), libc::S_IFCHR | 0o600, 0) };
            if ret != 0 {
                bail!(
                    "create whiteout {} failed: {}",
                    dst.display(),
                    std::io::Error::last_os_error()
                );
            }
        }
    }
    Ok(())
}

fn overlay_top(
    label: &str,
    node: &Node,
    upper_dir: &Path,
    work_dir: &Path,
    target: &Path,
) -> Result<bool> {
    let target = resolve_target(target);
    match node.kind {
        NodeKind::Directory => {
            if !target.is_dir() {
                warn!(
                    "{label}: overlay target {} is not a directory, skipping",
                    target.display()
                );
                return Ok(false);
            }
            std::fs::create_dir_all(work_dir)
                .with_context(|| format!("create workdir {}", work_dir.display()))?;
            let opts = format!(
                "lowerdir={},upperdir={},workdir={}",
                target.display(),
                upper_dir.display(),
                work_dir.display(),
            );
            raw_mount(
                Some(Path::new("overlay")),
                &target,
                Some("overlay"),
                0,
                Some(&opts),
            )?;
            make_private(&target);
            remount_readonly(&target, false);
            info!(
                "overlay-mounted {} -> {}",
                upper_dir.display(),
                target.display()
            );
            Ok(true)
        }
        NodeKind::File => {
            let Some(src) = node.source.as_ref() else {
                return Ok(false);
            };
            if !target.is_file() {
                warn!(
                    "{label}: file target {} is missing or not a file, skipping",
                    target.display()
                );
                return Ok(false);
            }
            bind_mount(src, &target)?;
            remount_readonly(&target, true);
            info!("bind-mounted {} -> {}", src.display(), target.display());
            Ok(true)
        }
        NodeKind::Symlink => {
            warn!("{label}: replacing a root path with a symlink is not supported, skipping");
            Ok(false)
        }
        NodeKind::Whiteout => {
            if target.is_dir() {
                raw_mount(
                    Some(Path::new("tmpfs")),
                    &target,
                    Some("tmpfs"),
                    0,
                    Some("mode=0755"),
                )?;
                make_private(&target);
                remount_readonly(&target, false);
                info!("hid {} with an empty tmpfs", target.display());
                Ok(true)
            } else {
                warn!(
                    "{label}: cannot hide non-directory target {}",
                    target.display()
                );
                Ok(false)
            }
        }
    }
}

fn run_overlay_mount(root: &Node) -> Result<()> {
    let work = Path::new(defs::MAGIC_MOUNT_WORK_DIR);
    prepare_work(work)?;

    let stage = work.join("stage");
    let upper = stage.join("upper");
    let overlay_work = stage.join("work");
    let preflight = (|| -> Result<()> {
        std::fs::create_dir_all(&upper).with_context(|| format!("create {}", upper.display()))?;
        std::fs::create_dir_all(&overlay_work)
            .with_context(|| format!("create {}", overlay_work.display()))?;
        for (name, node) in &root.children {
            if name == "system" {
                for (child_name, child) in &node.children {
                    if child.kind == NodeKind::Directory
                        && child.children.is_empty()
                        && !child.replace
                    {
                        continue;
                    }
                    materialize(child, &upper.join("system").join(child_name), None)
                        .with_context(|| format!("stage system/{child_name}"))?;
                }
                continue;
            }
            if node.kind == NodeKind::Directory && node.children.is_empty() && !node.replace {
                continue;
            }
            let target = Path::new("/").join(name);
            materialize(node, &upper.join(name), Some(&target))
                .with_context(|| format!("stage {name}"))?;
        }
        Ok(())
    })();

    if let Err(e) = preflight {
        cleanup_work(work);
        return Err(e);
    }

    let mut mounted = 0usize;
    if let Some(system) = root.children.get("system") {
        for (name, node) in &system.children {
            if node.kind == NodeKind::Directory && node.children.is_empty() && !node.replace {
                continue;
            }
            let target = Path::new("/system").join(name);
            let upper_dir = upper.join("system").join(name);
            let work_dir = overlay_work.join("system").join(name);
            match overlay_top(name, node, &upper_dir, &work_dir, &target) {
                Ok(true) => mounted += 1,
                Ok(false) => {}
                Err(e) => warn!("system/{name}: overlay mount failed: {e:#}"),
            }
        }
    }
    for (name, node) in &root.children {
        if name == "system" {
            continue;
        }
        if node.kind == NodeKind::Directory && node.children.is_empty() && !node.replace {
            continue;
        }
        let target = Path::new("/").join(name);
        let upper_dir = upper.join(name);
        let work_dir = overlay_work.join(name);
        match overlay_top(name, node, &upper_dir, &work_dir, &target) {
            Ok(true) => mounted += 1,
            Ok(false) => {}
            Err(e) => warn!("{name}: overlay mount failed: {e:#}"),
        }
    }

    if mounted == 0 {
        cleanup_work(work);
        bail!("no overlay could be mounted");
    }
    info!("overlay-backed Magic Mount staged {mounted} top-level mount(s)");
    Ok(())
}

fn mount_mirror(source: &Path, stage: &Path) -> Result<()> {
    let meta =
        std::fs::symlink_metadata(source).with_context(|| format!("stat {}", source.display()))?;
    let Some(kind) = kind_from_metadata(&meta) else {
        debug!("mirror: skipping special file {}", source.display());
        return Ok(());
    };
    if let Some(parent) = stage.parent() {
        std::fs::create_dir_all(parent).with_context(|| format!("create {}", parent.display()))?;
    }
    match kind {
        NodeKind::Symlink => {
            let link = std::fs::read_link(source)
                .with_context(|| format!("readlink {}", source.display()))?;
            let _ = std::fs::remove_file(stage);
            std::os::unix::fs::symlink(&link, stage)
                .with_context(|| format!("symlink {}", stage.display()))?;
            apply_metadata(source, stage);
        }
        NodeKind::Directory => {
            std::fs::create_dir_all(stage)
                .with_context(|| format!("create {}", stage.display()))?;
            bind_mount_recursive(source, stage)?;
        }
        NodeKind::File => {
            std::fs::write(stage, b"")
                .with_context(|| format!("create placeholder {}", stage.display()))?;
            bind_mount(source, stage)?;
        }
        NodeKind::Whiteout => {
            debug!("mirror: skipping whiteout {}", source.display());
        }
    }
    Ok(())
}

fn child_needs_tmpfs(target: &Path, child: &Node) -> bool {
    match child.kind {
        NodeKind::Symlink => true,
        NodeKind::Whiteout => std::fs::symlink_metadata(target).is_ok(),
        _ => match std::fs::symlink_metadata(target) {
            Ok(meta) => {
                let real_kind = kind_from_metadata(&meta);
                real_kind != Some(child.kind) || real_kind == Some(NodeKind::Symlink)
            }
            Err(_) => true,
        },
    }
}

fn do_bind_mount(base: &Path, work: &Path, mut node: Node, has_tmpfs: bool) -> Result<()> {
    let target = base.join(&node.name);
    let stage = work.join(&node.name);

    match node.kind {
        NodeKind::File => {
            let src = node
                .source
                .as_ref()
                .with_context(|| format!("missing source for file {}", node.name))?;
            if has_tmpfs {
                if let Some(parent) = stage.parent() {
                    std::fs::create_dir_all(parent)
                        .with_context(|| format!("create {}", parent.display()))?;
                }
                std::fs::write(&stage, b"")
                    .with_context(|| format!("create placeholder {}", stage.display()))?;
                bind_mount(src, &stage)?;
                remount_readonly(&stage, true);
            } else {
                bind_mount(src, &target)?;
                remount_readonly(&target, true);
            }
            debug!("bind-mounted {} -> {}", src.display(), target.display());
        }
        NodeKind::Symlink => {
            if !has_tmpfs {
                bail!("symlink {} needs a tmpfs parent", node.name);
            }
            let src = node
                .source
                .as_ref()
                .with_context(|| format!("missing source for symlink {}", node.name))?;
            mount_mirror(src, &stage)?;
            debug!("created symlink {} from {}", stage.display(), src.display());
        }
        NodeKind::Whiteout => {
            debug!("whiteout {}", target.display());
        }
        NodeKind::Directory => {
            let mut create_tmpfs = !has_tmpfs && node.replace && node.source.is_some();

            if !has_tmpfs && !create_tmpfs {
                for (name, child) in &mut node.children {
                    if child_needs_tmpfs(&target.join(name), child) {
                        if node.source.is_none() {
                            warn!(
                                "cannot create a tmpfs on {}, skipping {name}",
                                target.display()
                            );
                            child.skip = true;
                            continue;
                        }
                        create_tmpfs = true;
                        break;
                    }
                }
            }

            let has_tmpfs = has_tmpfs || create_tmpfs;

            if has_tmpfs {
                std::fs::create_dir_all(&stage)
                    .with_context(|| format!("create {}", stage.display()))?;
                let meta_src = if target.exists() {
                    Some(target.as_path())
                } else {
                    node.source.as_deref()
                };
                if let Some(meta_src) = meta_src {
                    apply_metadata(meta_src, &stage);
                }
            }

            if create_tmpfs {
                bind_mount(&stage, &stage)?;
                debug!("created tmpfs skeleton at {}", stage.display());
            }

            if !node.replace && target.exists() {
                let entries = std::fs::read_dir(&target)
                    .with_context(|| format!("readdir {}", target.display()))?;
                for entry in entries.flatten() {
                    let name = entry.file_name().to_string_lossy().to_string();
                    if let Some(child) = node.children.remove(&name) {
                        if child.skip {
                            continue;
                        }
                        if let Err(e) = do_bind_mount(&target, &stage, child, has_tmpfs) {
                            if has_tmpfs {
                                return Err(e);
                            }
                            warn!("{}/{name}: {e:#}", target.display());
                        }
                    } else if has_tmpfs {
                        mount_mirror(&entry.path(), &stage.join(&name))?;
                    }
                }
            }

            for (name, child) in std::mem::take(&mut node.children) {
                if child.skip {
                    continue;
                }
                if let Err(e) = do_bind_mount(&target, &stage, child, has_tmpfs) {
                    if has_tmpfs {
                        return Err(e);
                    }
                    warn!("{}/{name}: {e:#}", target.display());
                }
            }

            if create_tmpfs {
                remount_readonly(&stage, true);
                move_mount(&stage, &target)?;
                make_private(&target);
                info!("bind-mounted module tree onto {}", target.display());
            }
        }
    }
    Ok(())
}

fn run_bind_mount(root: &Node) -> Result<()> {
    let work = Path::new(defs::MAGIC_MOUNT_WORK_DIR);
    prepare_work(work)?;
    let result = do_bind_mount(Path::new("/"), work, root.clone(), false);
    cleanup_work(work);
    result
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

    let Some(root) = collect_modules()? else {
        info!("Magic Mount: no mountable modules");
        return Ok(());
    };

    let backend = backend();
    let overlay_available = match backend {
        MountBackend::Bind => false,
        MountBackend::Overlay | MountBackend::Auto => overlay_supported(),
    };
    if matches!(backend, MountBackend::Overlay) && !overlay_available {
        warn!("backend forced to overlay but the kernel lacks overlayfs; using bind mounts");
    }

    let mut used_overlay = false;
    if overlay_available {
        match run_overlay_mount(&root) {
            Ok(()) => used_overlay = true,
            Err(e) => {
                warn!("overlay backend unavailable ({e:#}); falling back to bind mounts");
            }
        }
    }
    if !used_overlay {
        run_bind_mount(&root).context("bind-backed Magic Mount failed")?;
    }

    info!(
        "Magic Mount pass complete (backend {}, engine {})",
        backend.as_str(),
        if used_overlay { "overlayfs" } else { "bind" }
    );
    Ok(())
}
