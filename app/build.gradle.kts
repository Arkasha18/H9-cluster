import java.security.SecureRandom
import java.util.Base64

plugins {
    id("com.android.application")
}

fun buildConfigString(value: String): String {
    return "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"") + "\""
}

val releaseStoreFile = providers.gradleProperty("H9_CLUSTER_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("H9_CLUSTER_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("H9_CLUSTER_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("H9_CLUSTER_KEY_PASSWORD").orNull
val releaseStoreType = providers.gradleProperty("H9_CLUSTER_STORE_TYPE").orNull ?: "PKCS12"
val tboxPassword = providers.gradleProperty("H9_TBOX_PASSWORD")
    .orElse(providers.environmentVariable("H9_TBOX_PASSWORD"))
    .orElse("")
    .get()
val tboxPasswordBytes = tboxPassword.toByteArray(Charsets.UTF_8)
val tboxSecretMask = ByteArray(tboxPasswordBytes.size)
SecureRandom().nextBytes(tboxSecretMask)
val tboxSecretData = ByteArray(tboxPasswordBytes.size) { index ->
    (tboxPasswordBytes[index].toInt() xor tboxSecretMask[index].toInt()).toByte()
}
val tboxSecretMaskBase64 = Base64.getEncoder().encodeToString(tboxSecretMask)
val tboxSecretDataBase64 = Base64.getEncoder().encodeToString(tboxSecretData)
tboxPasswordBytes.fill(0)
tboxSecretMask.fill(0)
tboxSecretData.fill(0)
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "net.adminrunet.h9cluster"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "net.adminrunet.h9cluster"
        minSdk = 28
        targetSdk = 28
        versionCode = 2026082801
        versionName = "9.5.2"
        manifestPlaceholders["bootReceiverEnabled"] = "true"
        manifestPlaceholders["fdbusProbeEnabled"] = "false"
        buildConfigField(
            "String",
            "TBOX_SECRET_MASK",
            buildConfigString(tboxSecretMaskBase64)
        )
        buildConfigField(
            "String",
            "TBOX_SECRET_DATA",
            buildConfigString(tboxSecretDataBase64)
        )

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
            manifestPlaceholders["bootReceiverEnabled"] = "true"
            manifestPlaceholders["fdbusProbeEnabled"] = "true"
            buildConfigField("boolean", "DEMO_MODE", "false")
        }
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["bootReceiverEnabled"] = "false"
            manifestPlaceholders["fdbusProbeEnabled"] = "false"
            buildConfigField("boolean", "DEMO_MODE", "true")
            buildConfigField(
                "String",
                "TBOX_SECRET_MASK",
                buildConfigString("")
            )
            buildConfigField(
                "String",
                "TBOX_SECRET_DATA",
                buildConfigString("")
            )
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "DEMO_MODE", "false")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("releaseKey")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
        // Keep update compatibility with production builds already installed
        // on the vehicle; lowering versionCode would turn them into downgrades.
        disable += "HighAppVersionCode"
    }
}

dependencies {
    implementation("androidx.core:core:1.13.0")
    implementation("com.jcraft:jsch:0.1.55")
    implementation("io.noties.markwon:core:4.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
