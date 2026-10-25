import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// AdMob ids live outside Git. Without this file the release build disables ads
// instead of silently serving test ads.
val adsPropertiesFile = rootProject.file("ads.properties")
val adsProperties = Properties().apply {
    if (adsPropertiesFile.exists()) {
        adsPropertiesFile.inputStream().use { load(it) }
    }
}

val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testNativeUnitId = "ca-app-pub-3940256099942544/2247696110"
val releaseAdMobAppId = adsProperties.getProperty("admobAppId").orEmpty()
val releaseNativeUnitId = adsProperties.getProperty("nativeUnitId").orEmpty()
val adsConfigured = releaseAdMobAppId.isNotBlank() && releaseNativeUnitId.isNotBlank()

android {
    namespace = "com.vidplay.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vidplay.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // Test ids by default. Release overrides these with ads.properties.
        buildConfigField("boolean", "ADS_CONFIGURED", "false")
        buildConfigField("String", "ADMOB_APP_ID", "\"$testAdMobAppId\"")
        buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"$testNativeUnitId\"")
        manifestPlaceholders["admobAppId"] = testAdMobAppId
    }

    flavorDimensions += "dist"

    productFlavors {
        create("play") {
            dimension = "dist"
            buildConfigField("boolean", "ADS_ENABLED", "true")
            // Google Play build: ads enabled, package kept as com.vidplay.app.
        }
        create("foss") {
            dimension = "dist"
            // Personal build: no ads, no INTERNET permission and a separate
            // package so both versions can coexist on the same device.
            applicationIdSuffix = ".personal"
            versionNameSuffix = "-personal"
            buildConfigField("boolean", "ADS_ENABLED", "false")
        }
    }

    signingConfigs {
        create("fixedDebug") {
            storeFile = rootProject.file("keystore/vidplay-debug.jks")
            storePassword = "vidplay"
            keyAlias = "vidplay-debug"
            keyPassword = "vidplay"
        }
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile", "keystore/vidplay-upload.jks"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixedDebug")
            // Debug always uses Google's test ids so nobody clicks live ads.
            buildConfigField("boolean", "ADS_CONFIGURED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "ADS_CONFIGURED", adsConfigured.toString())
            buildConfigField(
                "String",
                "ADMOB_APP_ID",
                "\"${releaseAdMobAppId.ifBlank { testAdMobAppId }}\"",
            )
            buildConfigField(
                "String",
                "ADMOB_NATIVE_UNIT_ID",
                "\"${releaseNativeUnitId.ifBlank { testNativeUnitId }}\"",
            )
            manifestPlaceholders["admobAppId"] = releaseAdMobAppId.ifBlank { testAdMobAppId }
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("fixedDebug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/INDEX.LIST",
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-process:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.11.1-0.16.0")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-video:3.3.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.biometric:biometric:1.4.0-alpha07")
    implementation("androidx.documentfile:documentfile:1.1.0")

    // Ads only in the Play build. The personal (foss) build never links the SDK.
    add("playImplementation", "com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.6.0")
    add("playImplementation", "com.google.android.ump:user-messaging-platform:4.0.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

