plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Función auxiliar para autoincrementar el código de versión basado en la fecha/tiempo o un contador
val autoVersionCode = (System.currentTimeMillis() / 1000).toInt()

android {
    namespace = "com.jolie.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jolie.assistant"
        minSdk = 26
        targetSdk = 34
        
        // ¡AUTOMATIZADO! Se autoincrementa solo con el tiempo del sistema en cada build
        versionCode = autoVersionCode
        versionName = "1.${autoVersionCode % 100}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
}
// ---- Motore VPN (Xray): scarica libv2ray.aar se manca ----
val fetchXray by tasks.registering {
    doLast {
        val target = file("libs/libv2ray.aar")
        if (!target.exists() || target.length() < 1000000L) {
            try {
                target.parentFile.mkdirs()
                uri("https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar")
                    .toURL()
                    .openStream()
                    .use { input -> target.outputStream().use { out -> input.copyTo(out) } }
            } catch (e: Exception) {
                logger.warn("libv2ray.aar non scaricato: " + e.message)
                target.delete()
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchXray) }
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.3")
}
