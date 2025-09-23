plugins {
    id("com.android.application")
    kotlin("android")
    // 👇 Génération des classes à partir des .proto
    id("com.google.protobuf") version "0.9.4"
}

android {
    namespace = "com.samohammer.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.samohammer.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    // IMPORTANT : Compose compiler 1.5.4 ↔ Kotlin 1.9.20
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // (Optionnel, explicite) Emplacement des fichiers .proto
    // sourceSets["main"].proto.srcDir("src/main/proto")
}

dependencies {
    // BOM Compose aligné (compatible Kotlin 1.9.20 / compiler 1.5.4)
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.1.2")

    // Nécessaire pour certains thèmes Material
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // --- DataStore Proto + Protobuf runtime (lite) ---
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("com.google.protobuf:protobuf-javalite:3.25.3")
}

protobuf {
    protoc {
        // Version du compilateur Protobuf
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                // Génère des classes Java "lite" (Android-friendly)
                create("java") { option("lite") }
            }
        }
    }
}
