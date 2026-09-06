import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    val keystorePropertiesFile = rootProject.file("local.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            // CI 通过环境变量注入签名（SIGNING_STORE_FILE 为 workflow 解码出的 keystore 路径），
            // 本地开发仍读取 local.properties
            storeFile = System.getenv("SIGNING_STORE_FILE")?.let { file(it) }
                ?: keystoreProperties["signing.storeFile"]?.let { file(it as String) }
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                ?: keystoreProperties["signing.storePassword"] as String?
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                ?: keystoreProperties["signing.keyAlias"] as String?
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                ?: keystoreProperties["signing.keyPassword"] as String?
        }
    }
    namespace = "com.shutiao.flow"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.shutiao.flow"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 3
        versionName = "3.2"
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes.add("kotlin/**")
        }
    }
}

dependencies {
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
