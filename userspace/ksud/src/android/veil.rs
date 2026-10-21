// Origin Veil 2.0 persistence: snapshot {enabled, auto-cloak, cloak-set} to
// /data/adb/ksu/veil.json on every change and re-apply at boot, since kernel
// state resets on reboot.

use crate::android::ksucalls::veil_op;

use crate::android::{feature, ksucalls};

const VEIL_CONF: &str = "/data/adb/ksu/veil.json";
const VEIL2_FEATURE_ID: u32 = 12;

#[derive(serde::Serialize, serde::Deserialize)]
struct VeilConf {
    #[serde(default = "veil_schema_version")]
    version: u32,
    #[serde(default)]
    enabled: bool,
    #[serde(default)]
    auto: bool,
    #[serde(default)]
    cloaked: Vec<u32>,
}

fn veil_schema_version() -> u32 {
    2
}

impl Default for VeilConf {
    fn default() -> Self {
        Self {
            version: veil_schema_version(),
            enabled: false,
            auto: false,
            cloaked: Vec::new(),
        }
    }
}

/// Snapshot current kernel state to disk so it survives reboot.
fn save_config() {
    let enabled = ksucalls::get_feature(VEIL2_FEATURE_ID)
        .map(|state| state.value != 0)
        .unwrap_or(false);
    let auto = ksucalls::veil_cloak_op(veil_op::GET_AUTO, 0, 0)
        .map(|v| v != 0)
        .unwrap_or(false);
    let cloaked = ksucalls::veil_cloak_list().unwrap_or_default();
    let conf = VeilConf {
        version: veil_schema_version(),
        enabled,
        auto,
        cloaked,
    };
    if let Ok(json) = serde_json::to_string(&conf) {
        let _ = std::fs::write(VEIL_CONF, json);
    }
}

/// Persist the live set (captures auto-cloaks too) whenever cloak state changes.
pub fn persist() {
    save_config();
}

/// Re-apply saved Veil state at boot (the kernel state resets on reboot).
pub fn restore_config() {
    let data = match std::fs::read_to_string(VEIL_CONF) {
        Ok(d) => d,
        Err(_) => return,
    };
    let conf: VeilConf = match serde_json::from_str(&data) {
        Ok(c) => c,
        Err(_) => return,
    };
    if conf.version > veil_schema_version() {
        return;
    }
    let _ = ksucalls::veil_cloak_op(veil_op::SET_AUTO, 0, u32::from(conf.auto));
    for uid in conf.cloaked {
        let _ = ksucalls::veil_cloak_op(veil_op::ADD, uid, 0);
    }
    if conf.enabled {
        let _ = feature::set_feature("veil2", 1);
    }
}
