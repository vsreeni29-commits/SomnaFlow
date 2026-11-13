plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.somnaflow.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.somnaflow.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        // A fixed sideload key kept in the repo so every build can update the previous install.
        create("sideload") {
            storeFile = rootProject.file("keystore/somnaflow.jks")
            storePassword = "somnaflow"
            keyAlias = "somnaflow"
            keyPassword = "somnaflow"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sideload") }
        release {
            // Shrinking is left off on purpose: the APK is a little larger but behaves exactly like the tested build.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true; unitTests.all { it.systemProperty("roborazzi.test.record", "true") } }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
