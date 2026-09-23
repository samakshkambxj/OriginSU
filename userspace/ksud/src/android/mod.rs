mod bootloop;
pub mod cli;
mod debug;
mod dynamic_manager;
mod feature;
mod init_event;
#[cfg(all(target_arch = "aarch64", target_os = "android"))]
mod kpm;
mod ksucalls;
mod late_load;
mod module;
mod profile;
pub(crate) mod resetprop;
mod restorecon;
mod sepolicy;
mod soft_reboot;
mod su;
mod su_notify;
mod sulog;
pub mod susfs;
mod tempgrant;
#[allow(nonstandard_style, unused, unsafe_op_in_unsafe_fn)]
pub mod uapi;
mod umount_config;
mod unload;
pub mod utils;
mod veil;
