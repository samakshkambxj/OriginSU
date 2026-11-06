plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "me.yuki.suirequester"

    defaultConfig {
        applicationId = "me.yuki.suirequester"
        minSdk = 23
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":api"))
    implementation(project(":provider"))
    implementation(project(":shared"))
}
