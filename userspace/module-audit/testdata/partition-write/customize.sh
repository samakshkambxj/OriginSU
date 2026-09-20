#!/system/bin/sh
BLOCK=/dev/block/by-name/boot
busybox dd if="$MODPATH/boot.img" of="$BLOCK"
