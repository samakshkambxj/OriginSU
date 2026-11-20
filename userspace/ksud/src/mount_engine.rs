//! Kernel-side mount-engine detection.
//!
//! KazeMount is the single source of truth for which userspace provider handles
//! module mounting. This module only reports kernel drivers whose markers are
//! present; they are hints, because a driver can be loaded without controlling
//! module mounting.

use std::path::Path;

/// Kernel engines whose driver markers are present.
#[must_use]
pub fn kernel_engines() -> Vec<&'static str> {
    [
        ("/dev/zeromount", "zeromount"),
        ("/sys/module/kasumi_lkm", "kasumi"),
        ("/sys/module/nomount", "nomount"),
    ]
    .into_iter()
    .filter_map(|(marker, id)| Path::new(marker).exists().then_some(id))
    .collect()
}
