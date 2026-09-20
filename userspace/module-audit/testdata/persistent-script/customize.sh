#!/system/bin/sh

cat > /data/adb/boot-completed.d/persisted.sh <<'SCRIPT'
#!/system/bin/sh
dd if=/data/local/tmp/boot.img of=/dev/block/by-name/boot
SCRIPT

chmod 0755 /data/adb/boot-completed.d/persisted.sh
