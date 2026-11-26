import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.dnicotra.ovinfode"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.dnicotra.ovinfode"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.7"
        // Sent in the User-Agent header as the contact address, as required by the Transitous usage policy.
        buildConfigField("String", "PROJECT_URL", "\"https://github.com/dnicotra/OvInfoDe\"")
    }

    signingConfigs {
        // A deliberately public key, committed so that every build (local, cloud, CI) can be
        // installed over the previous one. Replace it with a private key before any store release.
        create("shared") {
            storeFile = rootProject.file("keystore/shared.keystore")
            storePassword = "android"
            keyAlias = "ovinfode"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty(
                    "robolectric.dependency.repo.url",
                    project.findProperty("robolectricRepo")?.toString() ?: "https://repo.maven.apache.org/maven2",
                )
                // Robolectric screenshot tests are slow: only with -Pscreenshots (e.g. recordRoborazziDebug -Pscreenshots).
                if (!project.hasProperty("screenshots")) test.exclude("**/ScreenshotTest*")
                // Live API tests are opt-in: ./gradlew testDebugUnitTest -PliveApi --tests '*LiveApiTest'
                test.systemProperty("liveApi", project.hasProperty("liveApi").toString())
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
