import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

// ── Kotlin compiler options ────────────────────────────────────────────────
// Must be at TOP LEVEL (outside android {}). Placing it inside android {}
// causes an "Unresolved reference" cascade in AGP 8.6.x / Kotlin 2.x.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace  = "com.jpchurchouse.wledblewear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jpchurchouse.wledblewear"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val props = Properties().also { p ->
                rootProject.file("local.properties").inputStream().use { p.load(it) }
            }
            val ksPath          = props.getProperty("keystore.path")
            val ksPassword      = props.getProperty("keystore.password")
            val ksAlias         = props.getProperty("keystore.alias")
            val ksAliasPassword = props.getProperty("keystore.aliasPassword")
            check(!ksPath.isNullOrBlank())          { "keystore.path missing from local.properties" }
            check(!ksPassword.isNullOrBlank())      { "keystore.password missing from local.properties" }
            check(!ksAlias.isNullOrBlank())         { "keystore.alias missing from local.properties" }
            check(!ksAliasPassword.isNullOrBlank()) { "keystore.aliasPassword missing from local.properties" }
            storeFile     = file(ksPath)
            storePassword = ksPassword
            keyAlias      = ksAlias
            keyPassword   = ksAliasPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)

    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.core.splashscreen)
    implementation(libs.wear.tiles)
    implementation(libs.wear.tiles.material)
    implementation(libs.protolayout)
    implementation(libs.protolayout.material)
    implementation(libs.protolayout.expression)
    implementation(libs.datastore.preferences)
    implementation(libs.concurrent.futures)
}
