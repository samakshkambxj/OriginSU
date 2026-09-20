use ksu_module_audit::{AuditReport, Severity};
use std::fs::File;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;
use zip::write::SimpleFileOptions;

fn fixture_zip(name: &str) -> PathBuf {
    let root = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("testdata")
        .join(name);
    let path = std::env::temp_dir().join(format!(
        "ksu-module-audit-{name}-{}.zip",
        std::process::id()
    ));
    let file = File::create(&path).unwrap();
    let mut zip = zip::ZipWriter::new(file);
    for file_name in ["module.prop", "customize.sh"] {
        zip.start_file(file_name, SimpleFileOptions::default())
            .unwrap();
        zip.write_all(&std::fs::read(root.join(file_name)).unwrap())
            .unwrap();
    }
    zip.finish().unwrap();
    path
}

fn run_fixture(name: &str) -> AuditReport {
    let path = fixture_zip(name);
    let output = Command::new(env!("CARGO_BIN_EXE_ksu-module-audit"))
        .args(["scan-zip", path.to_str().unwrap()])
        .output()
        .unwrap();
    std::fs::remove_file(path).unwrap();
    assert!(
        output.status.success(),
        "{}",
        String::from_utf8_lossy(&output.stderr)
    );
    serde_json::from_slice(&output.stdout).unwrap()
}

#[test]
fn cli_reports_partition_write_fixture() {
    let report = run_fixture("partition-write");
    assert!(report.findings.iter().any(|finding| {
        finding.rule_id == "KSU-AUDIT-FS-001" && finding.severity == Severity::Critical
    }));
}

#[test]
fn cli_keeps_module_owned_cleanup_informational() {
    let report = run_fixture("clean-module");
    assert!(report.findings.iter().any(|finding| {
        finding.rule_id == "KSU-AUDIT-FS-011" && finding.severity == Severity::Info
    }));
    assert!(!report.requires_confirmation());
}

#[test]
fn cli_recursively_audits_renamed_decoder_fixture() {
    let report = run_fixture("heuristic-unpack");
    assert!(report.findings.iter().any(|finding| {
        finding.rule_id == "KSU-AUDIT-PACK-001"
            && finding
                .evidence
                .contains("OpenSSL-compatible argument grammar")
    }));
    assert!(report.findings.iter().any(|finding| {
        finding.rule_id == "KSU-AUDIT-NET-001"
            && finding.provenance == ["heuristic base64 payload (layer 1)"]
    }));
}

#[test]
fn cli_discovers_nested_payload_without_decoder_command() {
    let report = run_fixture("content-discovery");
    assert!(report.findings.iter().any(|finding| {
        finding.rule_id == "KSU-AUDIT-FS-010"
            && finding.provenance.len() == 2
            && finding
                .provenance
                .iter()
                .all(|layer| layer.starts_with("content-discovered base64 payload"))
    }));
}

#[test]
fn cli_audits_persistent_startup_script_body() {
    let report = run_fixture("persistent-script");
    assert!(report.findings.iter().any(|finding| {
        finding.rule_id == "KSU-AUDIT-PERSIST-001"
            && finding
                .evidence
                .contains("/data/adb/boot-completed.d/persisted.sh")
    }));
    assert!(report.findings.iter().any(|finding| {
        finding.path == "/data/adb/boot-completed.d/persisted.sh"
            && finding.rule_id == "KSU-AUDIT-FS-001"
            && finding.severity == Severity::Critical
    }));
}

#[test]
fn cli_reports_financial_application_modification_as_critical() {
    let report = run_fixture("financial-modification");
    assert!(report.findings.iter().any(|finding| {
        finding.rule_id == "KSU-AUDIT-FIN-001" && finding.severity == Severity::Critical
    }));
    assert_eq!(report.required_confirmation_presses(), 2);
}

#[test]
fn cli_reports_root_accessibility_hijack_as_critical() {
    let report = run_fixture("accessibility-hijack");
    assert!(report.findings.iter().any(|finding| {
        finding.rule_id == "KSU-AUDIT-PRIV-001" && finding.severity == Severity::Critical
    }));
    assert!(report.findings.iter().any(|finding| {
        finding.rule_id == "KSU-AUDIT-PRIV-002" && finding.severity == Severity::Critical
    }));
    assert_eq!(report.required_confirmation_presses(), 2);
}
