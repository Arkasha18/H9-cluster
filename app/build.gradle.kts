plugins {
    id("com.android.application")
}

val releaseStoreFile = providers.gradleProperty("H9_CLUSTER_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("H9_CLUSTER_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("H9_CLUSTER_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("H9_CLUSTER_KEY_PASSWORD").orNull
val releaseStoreType = providers.gradleProperty("H9_CLUSTER_STORE_TYPE").orNull ?: "PKCS12"
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "net.adminrunet.h9cluster"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.adminrunet.h9cluster"
        minSdk = 28
        targetSdk = 28
        versionCode = 2026072701
        versionName = "9.1.0"
        manifestPlaceholders["bootReceiverEnabled"] = "true"
        manifestPlaceholders["fdbusProbeEnabled"] = "false"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("releaseKey") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = releaseStoreType
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".fdbusprobe"
            versionNameSuffix = "-debug"
            manifestPlaceholders["bootReceiverEnabled"] = "false"
            manifestPlaceholders["fdbusProbeEnabled"] = "true"
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("releaseKey")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
        // Keep update compatibility with production builds already installed
        // on the vehicle; lowering versionCode would turn them into downgrades.
        disable += "HighAppVersionCode"
    }
}
