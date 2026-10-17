import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gust4av033.appcopilotomoto"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gust4av033.appcopilotomoto"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "1.1.0-f2-music1"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_PLAYSTORE_BUILD", "false")
            buildConfigField("boolean", "ALLOW_ACCESSIBILITY_WAZE", "true")
            buildConfigField("boolean", "ALLOW_DIRECT_CALL", "true")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_PLAYSTORE_BUILD", "true")
            buildConfigField("boolean", "ALLOW_ACCESSIBILITY_WAZE", "false")
            buildConfigField("boolean", "ALLOW_DIRECT_CALL", "false")
        }
    }

    val signingProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
    fun signingValue(property: String, environment: String): String? =
        signingProperties.getProperty(property)?.takeIf(String::isNotBlank)
            ?: System.getenv(environment)?.takeIf(String::isNotBlank)
    val signingFile = signingValue("storeFile", "APP_SIGNING_STORE_FILE")
    signingConfigs {
        if (signingFile != null) create("release") {
            storeFile = rootProject.file(signingFile)
            storePassword = signingValue("storePassword", "APP_SIGNING_STORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "APP_SIGNING_KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "APP_SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "SHOW_DIAGNOSTICS_BY_DEFAULT", "true")
        }
        getByName("release") {
            buildConfigField("boolean", "SHOW_DIAGNOSTICS_BY_DEFAULT", "false")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingFile != null) signingConfig = signingConfigs.getByName("release")
        }
        create("fieldTest") {
            initWith(getByName("debug"))
            buildConfigField("boolean", "SHOW_DIAGNOSTICS_BY_DEFAULT", "true")
            applicationIdSuffix = ".pruebas"
            versionNameSuffix = "-campo"
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "APP · Campo F2")
            resValue("string", "media_access_service_label", "APP · Campo F2 · controles del reproductor")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation("com.github.msnilsen:openwakeword-android:0.1.2")
    debugImplementation(libs.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


