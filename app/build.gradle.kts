import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.cbztagger.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cbztagger.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

val apkAppName = "CBZTagger"
val apkVersionName = android.defaultConfig.versionName ?: "0.0.0"
val distDir = rootProject.layout.projectDirectory.dir("dist")

fun registerExportApkTask(taskName: String, buildType: String) =
    tasks.register(taskName, Copy::class) {
        val targetFileName = "${apkAppName}_${apkVersionName}_${buildType}.apk"
        val appPrefix = "${apkAppName}_"
        val currentVersionPrefix = "${apkAppName}_${apkVersionName}_"
        val destDir = distDir.asFile

        outputs.upToDateWhen { false }
        from(layout.buildDirectory.file("outputs/apk/$buildType/app-$buildType.apk"))
        into(distDir)
        rename { targetFileName }
        doFirst {
            destDir.listFiles()
                ?.filter { file ->
                    file.isFile &&
                        file.extension.equals("apk", ignoreCase = true) &&
                        file.name.startsWith(appPrefix) &&
                        (!file.name.startsWith(currentVersionPrefix) || file.name == targetFileName)
                }
                ?.forEach { it.delete() }
        }
    }

val exportDebugApk = registerExportApkTask("exportDebugApk", "debug")
val exportReleaseApk = registerExportApkTask("exportReleaseApk", "release")

tasks.configureEach {
    if (name == "assembleDebug") {
        finalizedBy(exportDebugApk)
    } else if (name == "assembleRelease") {
        finalizedBy(exportReleaseApk)
    }
}
