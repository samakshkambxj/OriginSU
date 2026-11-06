plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.refine)
}

android {
    namespace = "rikka.shizuku.server"

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
    api(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.parcelablelist)

    api(project(":aidl"))
    api(project(":shared"))
    api(project(":rish"))

    implementation(libs.refine.runtime)
    implementation(libs.hidden.compat)
    compileOnly(libs.hidden.stub)
}
