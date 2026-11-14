import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Credenciais de assinatura ficam fora do repositório (local.properties, não versionado).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    localProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.ntplayone.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ntplayone.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 12
        versionName = "1.4.5"
    }

    signingConfigs {
        create("release") {
            val store = localProps.getProperty("signing.storeFile")
            val pass = signingValue("signing.storePassword", "NT_SIGN_STORE_PASSWORD")
            val alias = localProps.getProperty("signing.keyAlias")
            val keyPass = signingValue("signing.keyPassword", "NT_SIGN_KEY_PASSWORD")
            if (store != null && pass != null && alias != null && keyPass != null) {
                storeFile = rootProject.file(store)
                storePassword = pass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("com.google.zxing:core:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}