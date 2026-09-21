//! Pre-install static audit for KernelSU modules.
//!
//! Scans a module ZIP with the `ksu-module-audit` analyzer *before* anything
//! is extracted or executed, then enforces a simple firewall policy:
//!
//! - `Critical` findings always block installation unless `--force` is given.
//! - `High` findings block installation unless re-run with
//!   `--audit-confirmed` (the manager passes this after the user accepts the
//!   warning dialog) or `--force`.
//! - `Notice`/`Info` findings are reported but never block.
//!
//! Adapted from the WeiguangTWK/KernelSU module-audit feature. The upstream
//! volume-key confirmation flow is intentionally not ported: OriginSU installs
//! modules from the manager app where no console exists, so confirmation
//! happens in the manager UI instead.

use anyhow::{Context, Result, bail};
use ksu_module_audit::{AuditConfig, AuditReport, Severity, scan_zip_path};

/// Maximum findings printed in human-readable mode.
const MAX_PRINTED_FINDINGS: usize = 50;

/// Scan `zip` and print the report.
///
/// With `json`, prints the full machine-readable [`AuditReport`]; otherwise
/// prints a capped human-readable summary.
pub fn audit_zip(zip: &str, json: bool) -> Result<()> {
    let report = scan_zip_path(zip, &AuditConfig::default()).context("module audit failed")?;
    if json {
        println!("{}", serde_json::to_string_pretty(&report)?);
    } else {
        print_summary(&report);
    }
    Ok(())
}

/// Enforce the install firewall policy for `zip`.
///
/// Scans the exact package about to be installed (no TOCTOU gap between the
/// manager pre-check and this gate). Returns `Ok(())` when installation may
/// proceed.
pub fn gate_install(zip: &str, audit_confirmed: bool, force: bool) -> Result<()> {
    println!("- Auditing module package");
    let report = scan_zip_path(zip, &AuditConfig::default()).context("module audit failed")?;
    print_summary(&report);

    let critical = report.count(Severity::Critical);
    let high = report.count(Severity::High);
    if critical > 0 && !force {
        bail!(
            "Module audit blocked installation: {critical} critical finding(s). \
             Review with `ksud module audit <zip>`; re-run with --force to override."
        );
    }
    if high > 0 && !audit_confirmed && !force {
        bail!(
            "Module audit found {high} high-severity finding(s). Review with \
             `ksud module audit <zip>` and re-run with --audit-confirmed to proceed."
        );
    }
    if critical > 0 {
        println!("- Audit override accepted (--force); continuing installation");
    } else if high > 0 {
        println!("- Audit warning accepted; continuing installation");
    } else {
        println!("- Audit found no blocking issues");
    }
    Ok(())
}

fn print_summary(report: &AuditReport) {
    println!("======== Module static audit ========");
    if let Some(module_id) = report.module_id.as_deref() {
        println!("Module: {module_id}");
    }
    for finding in report.findings.iter().take(MAX_PRINTED_FINDINGS) {
        let severity = match finding.severity {
            Severity::Info => "INFO",
            Severity::Notice => "NOTICE",
            Severity::High => "HIGH",
            Severity::Critical => "CRITICAL",
        };
        let location = finding.line.map_or_else(
            || finding.path.clone(),
            |line| format!("{}:{line}", finding.path),
        );
        println!("[{severity}] {} ({})", finding.title, finding.rule_id);
        println!("  {location}");
        for step in &finding.provenance {
            println!("  -> {step}");
        }
        println!("  {}", single_line(&finding.evidence));
        println!();
    }
    if report.findings.len() > MAX_PRINTED_FINDINGS {
        println!(
            "! {} additional findings omitted from output",
            report.findings.len() - MAX_PRINTED_FINDINGS
        );
        println!();
    }
    println!("======== Audit result ========");
    println!(
        "{} critical, {} high, {} notice, {} info",
        report.count(Severity::Critical),
        report.count(Severity::High),
        report.count(Severity::Notice),
        report.count(Severity::Info),
    );
    println!(
        "Scanned {} files and {} derived artifacts",
        report.scanned_files, report.derived_artifacts
    );
}

fn single_line(evidence: &str) -> String {
    const LIMIT: usize = 300;
    let flat: String = evidence.split_whitespace().collect::<Vec<_>>().join(" ");
    if flat.len() > LIMIT {
        format!("{}...", &flat[..LIMIT])
    } else {
        flat
    }
}
