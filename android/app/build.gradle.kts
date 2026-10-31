import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.yuqingmiao2008.grassmachine"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.yuqingmiao2008.grassmachine"
        minSdk = 24
        targetSdk = 35
        versionCode = 40004
        versionName = "4.0.0-android.4"
    }

    signingConfigs {
        create("release") {
            val props = Properties().apply {
                val f = rootProject.file("gradle.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            fun v(k: String) = (project.findProperty(k) as String?) ?: props.getProperty(k) ?: ""
            storeFile = rootProject.file(v("GGM_STORE_FILE").ifEmpty { "keystore/release.jks" })
            storePassword = v("GGM_STORE_PASSWORD")
            keyAlias = v("GGM_KEY_ALIAS")
            keyPassword = v("GGM_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // 首版先关闭 R8：优先保证真机稳定性（实测通过后可改回 true，体积约 1.7MB）
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
