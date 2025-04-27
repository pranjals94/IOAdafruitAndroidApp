plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.mqttapp"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.mqttapp"
        minSdk = 30
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // shrinks code (with ProGuard or R8). This property enables code shrinking, which removes unused code, classes, methods, and fields from your APK.
            isShrinkResources = true // This property removes unused resources (such as images, XML files, etc.) from your APK during the build process. It works in conjunction with isMinifyEnabled and helps reduce the final size of the APK by eliminating unnecessary resources that aren't being used in the app
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core KTX for Kotlin Extensions
    implementation("androidx.core:core-ktx:1.9.0")
    // Lifecycle KTX for Lifecycle-aware components
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    // Activity Compose for Jetpack Compose
    implementation("androidx.activity:activity-compose:1.7.0")

    // ViewModel and Lifecycle integration with Jetpack Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // Compose BOM for version alignmen
    implementation(platform("androidx.compose:compose-bom:2023.03.00"))

    // jetpack Compose UI + Material3
    implementation ("androidx.compose.ui:ui-tooling:") // For tooling support
    implementation("androidx.compose.ui:ui:")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // AppCompat & Testing
    implementation("androidx.appcompat:appcompat:1.5.0")

    // Unit testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Coroutines for Compose state management
    implementation("androidx.compose.runtime:runtime-livedata:1.4.0") // for collectAsState()

    // ViewModel and Lifecycle integration with Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

}