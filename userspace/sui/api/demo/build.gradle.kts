plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.refine)
}

val apiVersionName = rootProject.extra["api_version_name"].toString()

android {
    namespace = "rikka.shizuku.demo"

    defaultConfig {
        applicationId = "rikka.shizuku.demo"
        versionCode = 1
        versionName = apiVersionName

        externalNativeBuild {
            cmake {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                cppFlags += listOf("-std=c++17")
                arguments += listOf("-DANDROID_TOOLCHAIN=clang", "-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.annotation)

    implementation(project(":api"))
    implementation(project(":provider"))

    compileOnly(project(":demo-hidden-api-stub"))
    implementation(libs.hiddenapibypass)
}
