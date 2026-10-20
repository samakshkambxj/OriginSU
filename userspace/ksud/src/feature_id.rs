//! Feature ids shared by the feature manager and boot-patch (which also runs on the host).

#![cfg_attr(not(target_os = "android"), allow(dead_code))]

use anyhow::{Result, bail};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u32)]
pub enum FeatureId {
    SuCompat = 0,
    KernelUmount = 1,
    Sulog = 2,
    AdbRoot = 3,
    SelinuxHide = 4,
    OriginVeil = 5,
    SuPrompt = 6,
    SuPromptTimeout = 7,
    SuPromptDefaultAllow = 8,
    SelinuxMode = 9,
    ShellSuAdbOnly = 10,
}

impl FeatureId {
    pub const ALL: [Self; 11] = [
        Self::SuCompat,
        Self::KernelUmount,
        Self::Sulog,
        Self::AdbRoot,
        Self::SelinuxHide,
        Self::OriginVeil,
        Self::SuPrompt,
        Self::SuPromptTimeout,
        Self::SuPromptDefaultAllow,
        Self::SelinuxMode,
        Self::ShellSuAdbOnly,
    ];

    pub const fn from_u32(id: u32) -> Option<Self> {
        match id {
            0 => Some(Self::SuCompat),
            1 => Some(Self::KernelUmount),
            2 => Some(Self::Sulog),
            3 => Some(Self::AdbRoot),
            4 => Some(Self::SelinuxHide),
            5 => Some(Self::OriginVeil),
            6 => Some(Self::SuPrompt),
            7 => Some(Self::SuPromptTimeout),
            8 => Some(Self::SuPromptDefaultAllow),
            9 => Some(Self::SelinuxMode),
            10 => Some(Self::ShellSuAdbOnly),
            _ => None,
        }
    }

    pub const fn name(self) -> &'static str {
        match self {
            Self::SuCompat => "su_compat",
            Self::KernelUmount => "kernel_umount",
            Self::Sulog => "sulog",
            Self::AdbRoot => "adb_root",
            Self::SelinuxHide => "selinux_hide",
            Self::OriginVeil => "veil",
            Self::SuPrompt => "su_prompt",
            Self::SuPromptTimeout => "su_prompt_timeout",
            Self::SuPromptDefaultAllow => "su_prompt_default_allow",
            Self::SelinuxMode => "selinux",
            Self::ShellSuAdbOnly => "shell_su_adb_only",
        }
    }

    pub const fn description(self) -> &'static str {
        match self {
            Self::SuCompat => {
                "SU Compatibility Mode - allows authorized apps to gain root via traditional 'su' command"
            }
            Self::KernelUmount => {
                "Kernel Umount - controls whether kernel automatically unmounts modules when not needed"
            }
            Self::Sulog => {
                "SU Log - streams kernel sulog events to userspace and persists them to disk"
            }
            Self::AdbRoot => "ADB Root - Enable adbd root",
            Self::SelinuxHide => {
                "SELinux Hide - sanitize /sys/fs/selinux access results for app UIDs"
            }
            Self::OriginVeil => {
                "Origin Veil - kernel-side root-probe detection with per-app cloaking"
            }
            Self::SuPrompt => "SU Prompt - blocking Magisk-style root allow/deny prompt",
            Self::SuPromptTimeout => "SU Prompt Timeout - seconds the prompt waits for an answer",
            Self::SuPromptDefaultAllow => {
                "SU Prompt Default - allow root when the prompt times out"
            }
            Self::SelinuxMode => {
                "SELinux Mode - switch SELinux between enforcing (0) and permissive (1)"
            }
            Self::ShellSuAdbOnly => {
                "Shell SU ADB Only - when shell is granted root, additionally require the process to be spawned by adbd or a KernelSU root process (ksu domain)"
            }
        }
    }
}

pub fn parse_feature_id(name: &str) -> Result<FeatureId> {
    match name {
        "su_compat" | "0" => Ok(FeatureId::SuCompat),
        "kernel_umount" | "1" => Ok(FeatureId::KernelUmount),
        "sulog" | "2" => Ok(FeatureId::Sulog),
        "adb_root" | "3" => Ok(FeatureId::AdbRoot),
        "selinux_hide" | "4" => Ok(FeatureId::SelinuxHide),
        "veil" | "5" => Ok(FeatureId::OriginVeil),
        "su_prompt" | "6" => Ok(FeatureId::SuPrompt),
        "su_prompt_timeout" | "7" => Ok(FeatureId::SuPromptTimeout),
        "su_prompt_default_allow" | "8" => Ok(FeatureId::SuPromptDefaultAllow),
        "selinux" | "9" => Ok(FeatureId::SelinuxMode),
        "shell_su_adb_only" | "10" => Ok(FeatureId::ShellSuAdbOnly),
        _ => bail!("Unknown feature: {name}"),
    }
}
