pluginManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

plugins {
    id("com.android.settings") version "9.4.1"
}

android {
    compileSdk = 37
    minSdk = 23
    targetSdk = 36
    ndkVersion = "30.0.14904198"
    buildToolsVersion = "36.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

include(":aidl", ":shared", ":api", ":provider", ":rish", ":demo", ":demo-hidden-api-stub", ":server-shared")
