import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "tv.music.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "tv.music.player"
        minSdk = 28
        targetSdk = 36
        versionCode = providers.gradleProperty("tvmusic.versionCode").orNull?.toInt() ?: 19
        versionName = providers.gradleProperty("tvmusic.versionName").orNull ?: "0.11.5"
        val manifestUrl = providers.gradleProperty("tvmusic.updateManifest")
            .orElse("https://testingcf.jsdelivr.net/gh/Zxxy326/TV-Music@main/update.json")
            .get()
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"${manifestUrl.replace("\"", "\\\"")}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            isEnable = providers.gradleProperty("tvmusic.abiSplits").orNull?.toBoolean() ?: false
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
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

    packaging {
        resources.excludes += listOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/LICENSE.md",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE.md",
            "META-INF/NOTICE.txt",
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json"
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    implementation("androidx.compose.ui:ui:1.10.3")
    implementation("androidx.compose.foundation:foundation:1.10.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.tv:tv-material:1.1.0")

    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.google.zxing:core:3.5.3")

    implementation("wang.harlon.quickjs:wrapper-android:3.2.3")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")

    debugImplementation("androidx.compose.ui:ui-tooling:1.10.3")

    testImplementation("junit:junit:4.13.2")
}

val bundleLocalSources = providers.gradleProperty("tvmusic.bundleLocalSources")
    .map(String::toBoolean)
    .orElse(true)
val lxSourceDir = rootProject.file("音源")
val lxAssetsDir = layout.projectDirectory.dir("src/main/assets/lx")
val syncLxScripts = tasks.register<Copy>("syncLxScripts") {
    onlyIf { bundleLocalSources.get() }
    if (lxSourceDir.isDirectory) {
        from(lxSourceDir) { include("*.js") }
    }
    into(lxAssetsDir)
}
val cleanLxAssets = tasks.register<Delete>("cleanLxAssets") {
    delete(lxAssetsDir)
}
tasks.named("preBuild").configure {
    if (bundleLocalSources.get()) dependsOn(syncLxScripts) else dependsOn(cleanLxAssets)
}
