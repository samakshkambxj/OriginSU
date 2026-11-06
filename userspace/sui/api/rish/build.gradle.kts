plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "rikka.rish"

    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.0+"
        }
    }
}

dependencies {
    implementation(project(":api"))
    implementation(libs.androidx.annotation)
}

extra["publishLibrary"] = false
extra["POM_NAME"] = "RISH"
extra["POM_DESCRIPTION"] = "RISH"
