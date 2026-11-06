plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "rikka.shizuku.provider"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

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
    implementation(project(":api"))
    api(libs.androidx.annotation)
    implementation(libs.androidx.core)
}

extra["publishLibrary"] = true
extra["POM_NAME"] = "Shizuku API - provider"
extra["POM_DESCRIPTION"] = "Content Provider for Shizuku."
