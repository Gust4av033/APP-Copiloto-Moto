import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        versionCode = 2
        versionName = "0.1.0"
    }

    buildTypes {
        create("fieldTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".pruebas"
            versionNameSuffix = "-boton04"
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "APP · Prueba botón 04")
            resValue("string", "media_access_service_label", "APP · Prueba botón 04 · controles del reproductor")
        }
    }

    buildFeatures {
        compose = true
    }

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
}
