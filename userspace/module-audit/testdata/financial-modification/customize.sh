#!/system/bin/sh

TARGET_PACKAGE=com.eg.android.alipaygphone
sqlite3 "/data/user/0/$TARGET_PACKAGE/databases/account.db" "UPDATE account SET enabled=0"
