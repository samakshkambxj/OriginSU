use anyhow::{Context, Result, bail};
use ksu_module_audit::{AuditConfig, scan_file_bytes, scan_zip_path};
use std::fs;
use std::path::Path;

fn main() -> Result<()> {
    let mut args = std::env::args().skip(1);
    let command = args.next().unwrap_or_default();
    let path = args.next().unwrap_or_default();
    if path.is_empty() || args.next().is_some() {
        bail!("usage: ksu-module-audit <scan-zip|scan-file> <path>");
    }

    let report = match command.as_str() {
        "scan-zip" => scan_zip_path(&path, &AuditConfig::default())?,
        "scan-file" => {
            let bytes = fs::read(&path).with_context(|| format!("read {path}"))?;
            let logical_path = Path::new(&path)
                .file_name()
                .and_then(|name| name.to_str())
                .unwrap_or(&path)
                .to_owned();
            scan_file_bytes(logical_path, &bytes, &AuditConfig::default())
        }
        _ => bail!("unknown command {command:?}; expected scan-zip or scan-file"),
    };
    println!("{}", serde_json::to_string_pretty(&report)?);
    Ok(())
}
