//! Mount engine detection.
//!
//! Only one engine may own module mounting at a time. Either a metamodule
//! brings its own mount pipeline (NoMount, ZeroMount, Kagami/Kasumi, Hybrid
//! Mount, ...) or OriginSU's built-in Magic Mount runs. This module is the
//! single source of truth for which engine is in charge, so the manager can
//! show it and Magic Mount can stand down consistently.

use std::path::Path;

use crate::android::module::{magic_mount, metamodule};

/// Which component owns module mounting for the current boot.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Owner {
    /// No engine owns mounting (Magic Mount is disabled and no metamodule).
    None,
    /// An enabled metamodule with `metamount.sh` owns mounting.
    Metamodule,
    /// OriginSU's built-in Magic Mount owns mounting.
    MagicMount,
}

impl Owner {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::None => "none",
            Self::Metamodule => "metamodule",
            Self::MagicMount => "magic_mount",
        }
    }
}

/// Snapshot of the active mount engine.
#[derive(Debug, Clone)]
pub struct Status {
    pub owner: Owner,
    /// Stable machine id: `magic_mount`, `nomount`, `zeromount`, `kagami`,
    /// `hybrid_mount`, a metamodule id, or `none`.
    pub engine: String,
    /// Human-readable engine name from `module.prop` when available.
    pub name: String,
    /// Active metamodule id, empty when Magic Mount or no engine owns mounting.
    pub metamodule_id: String,
    pub magic_mount_enabled: bool,
    pub backend: String,
    /// Kernel-side engines whose driver markers are present. These are hints:
    /// a driver can be loaded without controlling module mounting.
    pub kernel_engines: Vec<&'static str>,
}

impl Status {
    pub fn detect() -> Self {
        let magic_mount_enabled = magic_mount::is_enabled();
        let backend = magic_mount::backend().as_str().to_string();
        let kernel_engines = detect_kernel_engines();

        if let Some(path) = metamodule::mounting_metamodule() {
            let id = path
                .file_name()
                .and_then(std::ffi::OsStr::to_str)
                .unwrap_or_default()
                .to_string();
            let (engine, name) = identify(&path, &id);
            Self {
                owner: Owner::Metamodule,
                engine,
                name,
                metamodule_id: id,
                magic_mount_enabled,
                backend,
                kernel_engines,
            }
        } else if magic_mount_enabled {
            Self {
                owner: Owner::MagicMount,
                engine: "magic_mount".to_string(),
                name: "Magic Mount".to_string(),
                metamodule_id: String::new(),
                magic_mount_enabled,
                backend,
                kernel_engines,
            }
        } else {
            Self {
                owner: Owner::None,
                engine: "none".to_string(),
                name: "None".to_string(),
                metamodule_id: String::new(),
                magic_mount_enabled,
                backend,
                kernel_engines,
            }
        }
    }

    /// Print a stable `key=value` line format for the manager.
    pub fn print(&self) {
        println!("owner={}", self.owner.as_str());
        println!("engine={}", sanitize(&self.engine));
        println!("name={}", sanitize(&self.name));
        println!("metamodule={}", sanitize(&self.metamodule_id));
        println!(
            "magic_mount={}",
            if self.magic_mount_enabled {
                "enabled"
            } else {
                "disabled"
            }
        );
        println!("backend={}", self.backend);
        println!("kernel={}", self.kernel_engines.join(","));
    }
}

/// Map a metamodule directory to a stable engine id and display name.
fn identify(path: &Path, id: &str) -> (String, String) {
    let name = crate::android::module::read_module_prop(path)
        .ok()
        .and_then(|props| props.get("name").cloned())
        .map(|name| name.trim().to_string())
        .filter(|name| !name.is_empty())
        .unwrap_or_else(|| id.to_string());

    let engine = match id {
        "nomount" => "nomount",
        "meta-zeromount" | "zeromount" => "zeromount",
        "kagami" => "kagami",
        id if id.contains("hybrid") => "hybrid_mount",
        id => id,
    };

    (engine.to_string(), name)
}

/// Kernel drivers that expose a stable marker even when their userspace
/// controller is not the active metamodule.
fn detect_kernel_engines() -> Vec<&'static str> {
    [
        ("/dev/zeromount", "zeromount"),
        ("/sys/module/kasumi_lkm", "kasumi"),
        ("/sys/module/nomount", "nomount"),
    ]
    .into_iter()
    .filter_map(|(marker, id)| Path::new(marker).exists().then_some(id))
    .collect()
}

fn sanitize(value: &str) -> String {
    value.replace(['\n', '\r'], " ").trim().to_string()
}
