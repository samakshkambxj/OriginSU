plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "rikka.shizuku.api"

    buildFeatures {
        buildConfig = false
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

dependencies {
    api(project(":aidl"))
    api(project(":shared"))

    api(libs.androidx.annotation)
}

extra["publishLibrary"] = true
extra["POM_NAME"] = "Shizuku API - API"
extra["POM_DESCRIPTION"] = "API of Shizuku and Sui."
