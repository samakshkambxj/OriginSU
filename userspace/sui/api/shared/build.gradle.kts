plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "rikka.shizuku.shared"

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
    implementation(project(":aidl"))
    api(libs.androidx.annotation)
}

extra["publishLibrary"] = true
extra["POM_NAME"] = "Shizuku API - shared"
extra["POM_DESCRIPTION"] = "Shared parts for the API of Shizuku and Sui."
