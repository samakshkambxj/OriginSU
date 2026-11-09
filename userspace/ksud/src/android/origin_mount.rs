//! OriginMount: provider selection for module mounting.
//!
//! Exactly one provider injects the installed modules during a boot:
//!
//! - [`Provider::Builtin`] runs OriginSU's built-in Magic Mount executor
//!   (overlayfs with a bind-mount fallback).
//! - [`Provider::Metamodule`] runs the installed metamodule's `metamount.sh`
//!   (Kagami, Hybrid Mount, ...).
//! - [`Provider::Zeromount`] delegates to the installed ZeroMount metamodule
//!   and requires its VFS kernel driver.
//! - [`Provider::Nomount`] loads the vendored NoMount VFS driver and writes its
//!   own redirection rules.
//! - [`Provider::None`] leaves modules inert.
//!
//! The provider is selected by a persisted mode (`auto`, `builtin`,
//! `metamodule`, `zeromount`, `nomount`, `off`). NoMount and the metamodule
//! step aside on a failure so a bad driver never leaves a device with unmounted
//! modules; a metamodule failure is reported and never silently replaced,
//! because the user explicitly delegated mounting to it.
//!
//! The legacy `ksud module magic-mount` commands remain supported: they drive
//! the same Magic Mount executor and keep the old enable flag in sync with the
//! OriginMount mode.

use std::fs;
use std::path::Path;

use anyhow::{Context, Result, bail};
use log::{info, warn};
use serde_json::json;

use crate::{
    android::{
        module::{magic_mount, metamodule, mount_engine},
        nomount, utils,
    },
    defs,
};

/// Persisted provider-selection mode.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Mode {
    Auto,
    Builtin,
    Metamodule,
    Zeromount,
    Nomount,
    /// Inject nothing: modules stay inert until a provider is chosen.
    Off,
}

impl Mode {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Auto => "auto",
            Self::Builtin => "builtin",
            Self::Metamodule => "metamodule",
            Self::Zeromount => "zeromount",
            Self::Nomount => "nomount",
            Self::Off => "off",
        }
    }

    /// Parse a config/CLI token; surrounding whitespace is ignored.
    #[must_use]
    pub fn parse(value: &str) -> Option<Self> {
        match value.trim() {
            "auto" => Some(Self::Auto),
            "builtin" => Some(Self::Builtin),
            "metamodule" => Some(Self::Metamodule),
            "zeromount" => Some(Self::Zeromount),
            "nomount" => Some(Self::Nomount),
            "off" => Some(Self::Off),
            _ => None,
        }
    }
}

/// The provider that performs this boot's module mounting.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Provider {
    Builtin,
    Metamodule,
    Zeromount,
    Nomount,
    /// No provider runs this boot.
    None,
}

impl Provider {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Builtin => "builtin",
            Self::Metamodule => "metamodule",
            Self::Zeromount => "zeromount",
            Self::Nomount => "nomount",
            Self::None => "none",
        }
    }
}

/// Read the configured mode. A missing file selects [`Mode::Auto`], which keeps
/// the historical behavior: an enabled metamodule owns mounting, otherwise the
/// legacy Magic Mount enable flag decides, otherwise nothing is mounted.
///
/// An unrecognised value falls back to `auto` with a warning. Other I/O errors
/// are returned so an unreadable configuration never silently selects a
/// provider.
///
/// # Errors
/// Returns an error when the configuration file exists but cannot be read.
pub fn read_mode() -> Result<Mode> {
    read_mode_at(Path::new(defs::MOUNT_MODE_FILE))
}

fn read_mode_at(path: &Path) -> Result<Mode> {
    match fs::read_to_string(path) {
        Ok(content) => Ok(Mode::parse(&content).unwrap_or_else(|| {
            warn!(
                "invalid mount mode {:?} in {}, falling back to auto",
                content.trim(),
                path.display()
            );
            Mode::Auto
        })),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(Mode::Auto),
        Err(error) => Err(error).with_context(|| format!("failed to read {}", path.display())),
    }
}

/// Atomically persist `contents` next to its destination.
fn write_atomic(path: &Path, contents: &[u8]) -> Result<()> {
    let parent = path
        .parent()
        .context("atomic write target has no parent directory")?;
    fs::create_dir_all(parent).with_context(|| format!("failed to create {}", parent.display()))?;
    let temporary = parent.join(format!(
        ".{}.tmp",
        path.file_name()
            .and_then(std::ffi::OsStr::to_str)
            .unwrap_or("origin_mount")
    ));
    fs::write(&temporary, contents)
        .with_context(|| format!("failed to write {}", temporary.display()))?;
    fs::rename(&temporary, path)
        .with_context(|| format!("failed to replace {}", path.display()))?;
    Ok(())
}

/// Persist `mode`. Takes effect on the next boot.
///
/// # Errors
/// Returns an error when the file cannot be created or written.
pub fn write_mode(mode: Mode) -> Result<()> {
    write_atomic(
        Path::new(defs::MOUNT_MODE_FILE),
        format!("{}\n", mode.as_str()).as_bytes(),
    )
}

/// Parse and persist a mode token.
///
/// Selecting NoMount again is also the explicit retry after an unfinished
/// driver load, so the load guard is cleared here.
///
/// # Errors
/// Returns an error for an unknown token or when the mode cannot be written.
pub fn set_mode(value: &str) -> Result<()> {
    let mode = Mode::parse(value).ok_or_else(|| {
        anyhow::anyhow!(
            "unknown mount mode {value:?} (want auto/builtin/metamodule/zeromount/nomount/off)"
        )
    })?;
    write_mode(mode)?;
    if mode == Mode::Nomount
        && let Err(error) = nomount::forget_inflight()
    {
        warn!("OriginMount: could not clear the NoMount load guard: {error:#}");
    }
    info!(
        "OriginMount mode set to {} (takes effect on next boot)",
        mode.as_str()
    );
    Ok(())
}

/// `(id, enabled)` for the installed metamodule, if any.
fn metamodule_state() -> (Option<String>, bool) {
    let Some(path) = metamodule::get_metamodule_path() else {
        return (None, false);
    };
    let enabled =
        !path.join(defs::DISABLE_FILE_NAME).exists() && !path.join(defs::REMOVE_FILE_NAME).exists();
    let id = path
        .file_name()
        .and_then(std::ffi::OsStr::to_str)
        .map(ToString::to_string);
    (id, enabled)
}

/// Whether the active, enabled metamodule is ZeroMount and can run its mount
/// pipeline. ZeroMount ships as a metamodule, not as an OriginSU LKM, so the
/// provider only delegates to it.
fn is_zeromount_metamodule() -> bool {
    let (id, enabled) = metamodule_state();
    enabled
        && matches!(id.as_deref(), Some("meta-zeromount" | "zeromount"))
        && metamodule::get_metamodule_path()
            .is_some_and(|path| path.join(defs::METAMODULE_MOUNT_SCRIPT).exists())
}

const fn provider_for(mode: Mode, metamodule_enabled: bool, magic_mount_enabled: bool) -> Provider {
    match mode {
        Mode::Off => Provider::None,
        Mode::Metamodule => Provider::Metamodule,
        Mode::Zeromount => Provider::Zeromount,
        Mode::Nomount => Provider::Nomount,
        Mode::Builtin => Provider::Builtin,
        // Preserve the historical default: a metamodule wins, then the Magic
        // Mount enable flag, otherwise mounting remains off.
        Mode::Auto if metamodule_enabled => Provider::Metamodule,
        Mode::Auto if magic_mount_enabled => Provider::Builtin,
        Mode::Auto => Provider::None,
    }
}

/// Resolve which provider the next boot should use.
///
/// An installed module that drives NoMount itself takes over the Nomount mode:
/// two writers of the same kernel paths would race, and the installed module is
/// the one the user installed deliberately.
///
/// # Errors
/// Returns an error when the mode file exists but cannot be read.
pub fn resolve_provider() -> Result<Provider> {
    let mode = read_mode()?;
    let (_, metamodule_enabled) = metamodule_state();
    let provider = provider_for(mode, metamodule_enabled, magic_mount::is_enabled());
    if provider == Provider::Nomount && nomount::installed_driver_module().is_some() {
        return Ok(Provider::Metamodule);
    }
    Ok(provider)
}

/// Whether init_event should run the metamodule mount script later in the
/// boot. The selected provider is already known, so the script never runs
/// alongside the built-in executor or the NoMount backend.
#[must_use]
pub fn should_run_metamodule_mount() -> bool {
    match resolve_provider() {
        Ok(Provider::Metamodule) => true,
        Ok(Provider::Zeromount) => is_zeromount_metamodule(),
        _ => false,
    }
}

// ---------------------------------------------------------------------------
// Runtime record
// ---------------------------------------------------------------------------

/// Per-boot record of the provider attempt.
#[derive(Debug, Clone, Default)]
pub struct Runtime {
    pub boot_id: String,
    pub provider: Option<String>,
    pub configured: Option<String>,
    pub result: String,
    pub target_count: Option<usize>,
    pub error: Option<String>,
    pub warning: Option<String>,
    pub warning_code: Option<String>,
}

fn sanitize(value: &str) -> String {
    value.replace(['\n', '\r'], " ").trim().to_string()
}

fn write_runtime(record: &Runtime) -> Result<()> {
    let contents = format!(
        "boot_id={}\nprovider={}\nconfigured={}\nresult={}\ntarget_count={}\nerror={}\nwarning={}\nwarning_code={}\n",
        sanitize(&record.boot_id),
        record.provider.as_deref().unwrap_or(""),
        record.configured.as_deref().unwrap_or(""),
        record.result,
        record
            .target_count
            .map_or_else(String::new, |count| count.to_string()),
        record.error.as_deref().map_or_else(String::new, sanitize),
        record.warning.as_deref().map_or_else(String::new, sanitize),
        record
            .warning_code
            .as_deref()
            .map_or_else(String::new, sanitize),
    );
    write_atomic(Path::new(defs::MOUNT_RUNTIME), contents.as_bytes())
}

/// Read the last boot's mount record.
#[must_use]
pub fn read_runtime() -> Option<Runtime> {
    let content = fs::read_to_string(defs::MOUNT_RUNTIME).ok()?;
    let mut record = Runtime::default();
    for line in content.lines() {
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        match key {
            "boot_id" => record.boot_id = value.to_string(),
            "provider" => record.provider = (!value.is_empty()).then(|| value.to_string()),
            "configured" => record.configured = (!value.is_empty()).then(|| value.to_string()),
            "result" => record.result = value.to_string(),
            "target_count" => record.target_count = value.parse().ok(),
            "error" => record.error = (!value.is_empty()).then(|| value.to_string()),
            "warning" => record.warning = (!value.is_empty()).then(|| value.to_string()),
            "warning_code" => {
                record.warning_code = (!value.is_empty()).then(|| value.to_string());
            }
            _ => {}
        }
    }
    Some(record)
}

fn current_boot_id() -> String {
    fs::read_to_string("/proc/sys/kernel/random/boot_id")
        .map_or_else(|_| String::new(), |value| value.trim().to_string())
}

fn write_inflight(boot_id: &str, provider: Provider) -> Result<()> {
    write_atomic(
        Path::new(defs::MOUNT_INFLIGHT),
        format!("boot_id={boot_id}\nprovider={}\n", provider.as_str()).as_bytes(),
    )
}

fn clear_inflight() {
    if let Err(error) = fs::remove_file(defs::MOUNT_INFLIGHT)
        && error.kind() != std::io::ErrorKind::NotFound
    {
        warn!("OriginMount: could not clear the in-flight record: {error}");
    }
}

/// Refuse a second provider run in the same boot.
fn guard_reentrancy(boot_id: &str) -> Result<()> {
    let Ok(content) = fs::read_to_string(defs::MOUNT_INFLIGHT) else {
        return Ok(());
    };
    let existing = content.lines().find_map(|line| {
        let (key, value) = line.split_once('=')?;
        (key == "boot_id").then(|| value.to_string())
    });
    if existing.as_deref() == Some(boot_id) {
        bail!(
            "OriginMount already ran in this boot ({boot_id}); refusing to inject again. \
             Delete {} if the previous run was interrupted",
            defs::MOUNT_INFLIGHT
        );
    }
    // A record from an earlier boot only says that boot did not finish; the
    // current boot is a fresh attempt.
    clear_inflight();
    Ok(())
}

fn record(
    boot_id: &str,
    configured: Provider,
    provider: Provider,
    result: &str,
    target_count: Option<usize>,
    error: Option<String>,
    warning: Option<(String, &'static str)>,
) {
    let (warning, warning_code) = match warning {
        Some((message, code)) => (Some(message), Some(code.to_string())),
        None => (None, None),
    };
    let runtime = Runtime {
        boot_id: boot_id.to_string(),
        provider: (provider != Provider::None).then(|| provider.as_str().to_string()),
        configured: Some(configured.as_str().to_string()),
        result: result.to_string(),
        target_count,
        error,
        warning,
        warning_code,
    };
    if let Err(error) = write_runtime(&runtime) {
        warn!("OriginMount: could not write the runtime record: {error:#}");
    }
}

// ---------------------------------------------------------------------------
// Boot pass
// ---------------------------------------------------------------------------

/// Mount with the built-in engine after another provider declined the boot.
fn fallback_to_builtin(
    boot_id: &str,
    configured: Provider,
    warning: String,
    warning_code: &'static str,
) -> Result<()> {
    warn!("OriginMount: {warning}");
    write_inflight(boot_id, configured)?;
    let result = magic_mount::run_magic_mount_forced();
    clear_inflight();
    match result {
        Ok(()) => {
            record(
                boot_id,
                configured,
                Provider::Builtin,
                "mounted",
                None,
                None,
                Some((warning, warning_code)),
            );
            Ok(())
        }
        Err(error) => {
            record(
                boot_id,
                configured,
                Provider::Builtin,
                "failed",
                None,
                Some(format!("{error:#}")),
                Some((warning, warning_code)),
            );
            Err(error).context("fallback to the built-in engine failed")
        }
    }
}

/// Run the selected provider for this boot.
///
/// This is the boot-stage entry point. It executes the built-in executor or
/// the NoMount backend and records the outcome. The metamodule and ZeroMount
/// providers are recorded as delegated and their script is executed later by
/// [`run_metamodule_mount`] so its historical position in `post-fs-data` is
/// preserved.
///
/// # Errors
/// Returns an error only when the mode cannot be read or the built-in fallback
/// itself fails. A NoMount or ZeroMount failure is recorded and falls back to
/// the built-in executor instead.
pub fn run_boot_pass() -> Result<()> {
    if utils::has_magisk() {
        warn!("OriginMount: Magisk detected, skip module mount");
        return Ok(());
    }
    if utils::is_safe_mode() {
        warn!("OriginMount: safe mode, skip module mount");
        return Ok(());
    }
    if let Err(error) = utils::switch_mnt_ns(1) {
        warn!("OriginMount: join init mount namespace failed (continuing anyway): {error:#}");
    }

    let mode = read_mode()?;
    let configured = resolve_provider()?;
    let boot_id = current_boot_id();
    guard_reentrancy(&boot_id)?;

    match configured {
        Provider::None => {
            info!("OriginMount: mounting is disabled (mode={})", mode.as_str());
            record(
                &boot_id,
                configured,
                Provider::None,
                "disabled",
                None,
                None,
                None,
            );
            Ok(())
        }
        Provider::Metamodule => {
            info!("OriginMount: delegating module mounting to the metamodule");
            record(
                &boot_id,
                configured,
                Provider::Metamodule,
                "delegated",
                None,
                None,
                None,
            );
            Ok(())
        }
        Provider::Zeromount => {
            if !is_zeromount_metamodule() {
                return fallback_to_builtin(
                    &boot_id,
                    configured,
                    "ZeroMount is not the active metamodule; mounted with the built-in engine"
                        .to_string(),
                    "zeromount_fallback",
                );
            }
            info!("OriginMount: delegating module mounting to ZeroMount");
            record(
                &boot_id,
                configured,
                Provider::Zeromount,
                "delegated",
                None,
                None,
                None,
            );
            Ok(())
        }
        Provider::Builtin => {
            write_inflight(&boot_id, Provider::Builtin)?;
            let result = magic_mount::run_magic_mount_forced();
            clear_inflight();
            match result {
                Ok(()) => {
                    record(
                        &boot_id,
                        configured,
                        Provider::Builtin,
                        "mounted",
                        None,
                        None,
                        None,
                    );
                    info!("OriginMount: built-in Magic Mount completed");
                    Ok(())
                }
                Err(error) => {
                    record(
                        &boot_id,
                        configured,
                        Provider::Builtin,
                        "failed",
                        None,
                        Some(format!("{error:#}")),
                        None,
                    );
                    Err(error).context("built-in module mounting failed")
                }
            }
        }
        Provider::Nomount => {
            write_inflight(&boot_id, Provider::Nomount)?;
            let result = nomount::apply_current_plan();
            clear_inflight();
            match result {
                Ok(injection) => {
                    let result = if injection.applied == 0 {
                        "noop"
                    } else {
                        "mounted"
                    };
                    record(
                        &boot_id,
                        configured,
                        Provider::Nomount,
                        result,
                        Some(injection.applied),
                        injection.warnings.first().cloned(),
                        None,
                    );
                    for warning in &injection.warnings {
                        warn!("OriginMount: NoMount: {warning}");
                    }
                    info!(
                        "OriginMount: NoMount applied {} rule(s), removed {}",
                        injection.applied, injection.removed
                    );
                    Ok(())
                }
                Err(error) => {
                    warn!(
                        "OriginMount: NoMount failed ({error:#}); falling back to the built-in engine"
                    );
                    let fallback = magic_mount::run_magic_mount_forced();
                    let warning = format!(
                        "NoMount unavailable ({error:#}); mounted with the built-in engine"
                    );
                    match fallback {
                        Ok(()) => {
                            record(
                                &boot_id,
                                configured,
                                Provider::Builtin,
                                "mounted",
                                None,
                                None,
                                Some((warning, "nomount_fallback")),
                            );
                            Ok(())
                        }
                        Err(fallback_error) => {
                            record(
                                &boot_id,
                                configured,
                                Provider::Builtin,
                                "failed",
                                None,
                                Some(format!("{fallback_error:#}")),
                                Some((warning, "nomount_fallback")),
                            );
                            Err(fallback_error)
                                .context("NoMount failed and the built-in fallback also failed")
                        }
                    }
                }
            }
        }
    }
}

/// Execute the selected metamodule's mount script and update the runtime
/// record. Called by `post-fs-data` after the module scripts, matching the
/// historical KernelSU ordering.
///
/// # Errors
/// Returns an error when the script exists but fails.
pub fn run_metamodule_mount() -> Result<()> {
    let boot_id = current_boot_id();
    // `Auto` resolves to `Metamodule` when a metamodule is installed, so this
    // is the engine that is actually about to run, not necessarily the mode
    // token the user picked.
    let provider = resolve_provider().unwrap_or(Provider::Metamodule);
    let has_script = metamodule::get_metamodule_path()
        .is_some_and(|path| path.join(defs::METAMODULE_MOUNT_SCRIPT).exists());
    let result = metamodule::exec_mount_script(defs::MODULE_DIR);
    match result {
        Ok(()) => {
            record(
                &boot_id,
                provider,
                provider,
                if has_script { "mounted" } else { "noop" },
                None,
                None,
                None,
            );
            Ok(())
        }
        Err(error) => {
            record(
                &boot_id,
                provider,
                provider,
                "failed",
                None,
                Some(format!("{error:#}")),
                None,
            );
            Err(error)
        }
    }
}

/// Run the selected provider immediately (CLI entry point).
///
/// # Errors
/// Returns an error when the provider fails.
pub fn run_now() -> Result<()> {
    // An explicit request is a deliberate re-run, not the accidental second
    // invocation the boot guard protects against. Clearing the record also
    // lets an operator retry after an interrupted pass.
    clear_inflight();
    let provider = resolve_provider()?;
    run_boot_pass()?;
    let run_metamodule = provider == Provider::Metamodule
        || (provider == Provider::Zeromount && is_zeromount_metamodule());
    if run_metamodule {
        run_metamodule_mount()?;
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// Status and legacy compatibility
// ---------------------------------------------------------------------------

/// Compact NoMount summary used by the OriginMount status output.
#[derive(Debug, Clone, Default)]
pub struct NomountSummary {
    pub driver_state: String,
    pub driver_version: String,
    pub rules_expected: usize,
    pub rules_applied: usize,
    pub uid_count: usize,
    pub isolated_blocked: bool,
    pub deferred_to: Option<String>,
    pub owns_injection: bool,
    pub unavailable_code: Option<String>,
    pub unavailable_detail: Option<String>,
    pub error: Option<String>,
}

/// Snapshot shown by `ksud origin-mount status` and the manager.
#[derive(Debug, Clone)]
pub struct Status {
    pub mode: Mode,
    pub provider: Provider,
    pub metamodule_id: String,
    pub magic_mount_enabled: bool,
    pub backend: &'static str,
    pub kernel_engines: Vec<String>,
    pub runtime: Option<Runtime>,
    pub nomount: Option<NomountSummary>,
}

impl Status {
    #[must_use]
    pub fn collect() -> Self {
        let mode = read_mode().unwrap_or(Mode::Auto);
        let provider = resolve_provider().unwrap_or(Provider::None);
        let (metamodule_id, _) = metamodule_state();
        let nomount = nomount::Status::collect()
            .ok()
            .map(|status| NomountSummary {
                driver_state: status.probe.state.as_str().to_string(),
                driver_version: status.probe.version.unwrap_or_default(),
                rules_expected: status.expected_rules,
                rules_applied: status.applied_rules,
                uid_count: status.uid_count,
                isolated_blocked: status.isolated_blocked,
                deferred_to: status.deferred_to,
                owns_injection: status.owns_injection,
                unavailable_code: status.unavailable_code.map(ToString::to_string),
                unavailable_detail: status.unavailable_detail,
                error: status.error,
            });
        Self {
            mode,
            provider,
            metamodule_id: metamodule_id.unwrap_or_default(),
            magic_mount_enabled: magic_mount::is_enabled(),
            backend: magic_mount::backend().as_str(),
            kernel_engines: mount_engine::Status::detect()
                .kernel_engines
                .into_iter()
                .map(ToString::to_string)
                .collect(),
            runtime: read_runtime(),
            nomount,
        }
    }

    /// Print key=value lines for the manager, or one JSON object.
    ///
    /// # Errors
    /// Returns an error when writing to stdout fails.
    pub fn print(&self, as_json: bool) -> Result<()> {
        use std::io::Write as _;
        let stdout = std::io::stdout();
        let mut out = stdout.lock();
        if as_json {
            let payload = json!({
                "schema_version": 1,
                "mode": self.mode.as_str(),
                "provider": self.provider.as_str(),
                "metamodule": self.metamodule_id,
                "magic_mount": self.magic_mount_enabled,
                "backend": self.backend,
                "kernel": self.kernel_engines,
                "runtime": self.runtime.as_ref().map(|runtime| json!({
                    "boot_id": runtime.boot_id,
                    "provider": runtime.provider,
                    "configured": runtime.configured,
                    "result": runtime.result,
                    "target_count": runtime.target_count,
                    "error": runtime.error,
                    "warning": runtime.warning,
                    "warning_code": runtime.warning_code,
                })),
                "nomount": self.nomount.as_ref().map(|nomount| json!({
                    "driver_state": nomount.driver_state,
                    "driver_version": nomount.driver_version,
                    "rules_expected": nomount.rules_expected,
                    "rules_applied": nomount.rules_applied,
                    "uid_count": nomount.uid_count,
                    "isolated_blocked": nomount.isolated_blocked,
                    "deferred_to": nomount.deferred_to,
                    "owns_injection": nomount.owns_injection,
                    "unavailable_code": nomount.unavailable_code,
                    "unavailable_detail": nomount.unavailable_detail,
                    "error": nomount.error,
                })),
            });
            writeln!(out, "{payload}").context("failed to write origin-mount status")?;
        } else {
            writeln!(out, "schema_version=1").context("failed to write origin-mount status")?;
            writeln!(out, "mode={}", self.mode.as_str())
                .context("failed to write origin-mount status")?;
            writeln!(out, "provider={}", self.provider.as_str())
                .context("failed to write origin-mount status")?;
            writeln!(out, "metamodule={}", self.metamodule_id)
                .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "magic_mount={}",
                if self.magic_mount_enabled {
                    "enabled"
                } else {
                    "disabled"
                }
            )
            .context("failed to write origin-mount status")?;
            writeln!(out, "backend={}", self.backend)
                .context("failed to write origin-mount status")?;
            writeln!(out, "kernel={}", self.kernel_engines.join(","))
                .context("failed to write origin-mount status")?;
            let runtime = self.runtime.clone().unwrap_or_default();
            writeln!(out, "runtime_boot_id={}", runtime.boot_id)
                .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "runtime_provider={}",
                runtime.provider.as_deref().unwrap_or("")
            )
            .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "runtime_configured={}",
                runtime.configured.as_deref().unwrap_or("")
            )
            .context("failed to write origin-mount status")?;
            writeln!(out, "runtime_result={}", runtime.result)
                .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "runtime_targets={}",
                runtime
                    .target_count
                    .map_or_else(String::new, |count| count.to_string())
            )
            .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "runtime_error={}",
                runtime.error.as_deref().unwrap_or("")
            )
            .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "runtime_warning={}",
                runtime.warning.as_deref().unwrap_or("")
            )
            .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "runtime_warning_code={}",
                runtime.warning_code.as_deref().unwrap_or("")
            )
            .context("failed to write origin-mount status")?;
            let nomount = self.nomount.clone().unwrap_or_default();
            writeln!(out, "nomount_driver_state={}", nomount.driver_state)
                .context("failed to write origin-mount status")?;
            writeln!(out, "nomount_driver_version={}", nomount.driver_version)
                .context("failed to write origin-mount status")?;
            writeln!(out, "nomount_rules_expected={}", nomount.rules_expected)
                .context("failed to write origin-mount status")?;
            writeln!(out, "nomount_rules_applied={}", nomount.rules_applied)
                .context("failed to write origin-mount status")?;
            writeln!(out, "nomount_uid_count={}", nomount.uid_count)
                .context("failed to write origin-mount status")?;
            writeln!(out, "nomount_isolated={}", nomount.isolated_blocked)
                .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "nomount_deferred_to={}",
                nomount.deferred_to.as_deref().unwrap_or("")
            )
            .context("failed to write origin-mount status")?;
            writeln!(out, "nomount_owns_injection={}", nomount.owns_injection)
                .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "nomount_unavailable_code={}",
                nomount.unavailable_code.as_deref().unwrap_or("")
            )
            .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "nomount_unavailable_detail={}",
                nomount.unavailable_detail.as_deref().unwrap_or("")
            )
            .context("failed to write origin-mount status")?;
            writeln!(
                out,
                "nomount_error={}",
                nomount.error.as_deref().unwrap_or("")
            )
            .context("failed to write origin-mount status")?;
        }
        out.flush().context("failed to flush origin-mount status")
    }
}

/// Legacy `magic-mount status` output used by the existing manager toggle.
///
/// # Errors
/// Returns an error when writing to stdout fails.
pub fn legacy_status() -> Result<()> {
    let mode = read_mode()?;
    let enabled = magic_mount::is_enabled() || mode == Mode::Builtin;
    println!("{}", if enabled { "enabled" } else { "disabled" });
    Ok(())
}

/// Legacy `magic-mount enable/disable`, keeping the OriginMount mode coherent.
///
/// # Errors
/// Returns an error when the flag or mode cannot be written.
pub fn legacy_set_enabled(enabled: bool) -> Result<()> {
    magic_mount::set_enabled(enabled)?;
    let mode = read_mode()?;
    if enabled {
        if mode == Mode::Off {
            write_mode(Mode::Auto)?;
        }
    } else if mode == Mode::Builtin {
        write_mode(Mode::Off)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::{Mode, Provider, provider_for};

    #[test]
    fn mode_round_trips() {
        for mode in [
            Mode::Auto,
            Mode::Builtin,
            Mode::Metamodule,
            Mode::Zeromount,
            Mode::Nomount,
            Mode::Off,
        ] {
            assert_eq!(Mode::parse(mode.as_str()), Some(mode));
        }
        assert_eq!(Mode::parse(" nope "), None);
    }

    #[test]
    fn auto_preserves_legacy_precedence() {
        assert_eq!(provider_for(Mode::Auto, true, true), Provider::Metamodule);
        assert_eq!(provider_for(Mode::Auto, false, true), Provider::Builtin);
        assert_eq!(provider_for(Mode::Auto, false, false), Provider::None);
        assert_eq!(provider_for(Mode::Nomount, true, true), Provider::Nomount);
        assert_eq!(
            provider_for(Mode::Zeromount, true, true),
            Provider::Zeromount
        );
        assert_eq!(provider_for(Mode::Off, true, true), Provider::None);
    }
}
