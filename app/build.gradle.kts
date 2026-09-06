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
            storeFile = keystoreProperties["signing.storeFile"]?.let { file(it as String) }
            storePassword = keystoreProperties["signing.storePassword"] as String?
            keyAlias = keystoreProperties["signing.keyAlias"] as String?
            keyPassword = keystoreProperties["signing.keyPassword"] as String?
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
