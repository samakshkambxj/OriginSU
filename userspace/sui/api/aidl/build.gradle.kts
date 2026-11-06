plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "rikka.shizuku.aidl"

    buildFeatures {
        buildConfig = false
        aidl = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

extra["publishLibrary"] = true
extra["POM_NAME"] = "Shizuku API - aidl"
extra["POM_DESCRIPTION"] = "Low level aidl of Shizuku and Sui."
