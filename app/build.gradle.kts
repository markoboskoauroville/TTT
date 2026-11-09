/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mikepenz.aboutlibraries)
    alias(libs.plugins.kotest)
    alias(libs.plugins.kotlinx.kover)
}

val projectMinSdk: String by project
val projectTargetSdk: String by project
val projectCompileSdk: String by project
val projectVersionCode: String by project
val projectVersionName: String by project
val projectVersionNameSuffix = projectVersionName.substringAfter("-", "").let { suffix ->
    if (suffix.isNotEmpty()) {
        "-$suffix"
    } else {
        suffix
    }
}

// THE BUILD NUMBER IN THE ICON (version-visible.md, as in MANTRA_SHRUTI_BOX): a vector drawable has no text, so
// the number is drawn as seven-segment strokes under the keys. CI builds with versionCode 1000 + N, so N is the
// number of the release (build-N). The templates are src/main/icon, written by design/make_app_icon.py, which
// draws the same digits in its preview; the icon the app ships, and its themed twin, are generated here.
val versionIconDir = layout.buildDirectory.dir("generated/versionIcon").get().asFile
run {
    val segments = mapOf(
        'a' to "M0,0 H5", 'b' to "M5,0 V5", 'c' to "M5,5 V10", 'd' to "M0,10 H5",
        'e' to "M0,5 V10", 'f' to "M0,0 V5", 'g' to "M0,5 H5"
    )
    val digits = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
        '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg"
    )
    val code = projectVersionCode.trim().toInt()
    val text = "v" + (if (code > 1000) code - 1000 else code)
    val scale = 0.85
    val cell = 5.0
    val gap = 2.2
    val width = (text.length * cell + (text.length - 1) * gap) * scale
    val left = 54.0 - width / 2
    val top = 64.6
    val paths = StringBuilder()
    text.forEachIndexed { i, ch ->
        val x = left + i * (cell + gap) * scale
        val data = if (ch == 'v') "M0,4 L2.5,10 L5,4"
            else digits[ch].orEmpty().map { segments.getValue(it) }.joinToString(" ")
        val moved = Regex("([MLHV])([-0-9.]+)(,([-0-9.]+))?").replace(data) { m ->
            val cmd = m.groupValues[1]
            val a = m.groupValues[2].toDouble() * scale
            val b = m.groupValues[4].takeIf { it.isNotEmpty() }?.toDouble()?.times(scale)
            // Rounded by hand: a Double prints with a dot in every locale.
            fun n(v: Double) = (Math.round(v * 100) / 100.0).toString()
            when (cmd) {
                "H" -> "H" + n(a + x)
                "V" -> "V" + n(a + top)
                else -> cmd + n(a + x) + "," + n((b ?: 0.0) + top)
            }
        }
        paths.append("    <path android:strokeColor=\"#C9D1D9\" android:strokeWidth=\"1.5\" ")
            .append("android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" ")
            .append("android:pathData=\"").append(moved).append("\" />\n")
    }
    for (name in listOf("ic_app_icon_foreground", "ic_app_icon_monochrome")) {
        val template = file("src/main/icon/$name.xml").readText()
        val out = File(versionIconDir, "drawable/$name.xml")
        out.parentFile.mkdirs()
        out.writeText(template.replace("    <!--VERSION-->\n", "    <!-- v${text.drop(1)}, generated -->\n$paths"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.set(listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-jvm-default=enable",
            "-Xwhen-guards",
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters",
            "-XXLanguage:+LocalTypeAliases",
        ))
    }
}

configure<ApplicationExtension> {
    namespace = "dev.patrickgold.florisboard"
    compileSdk = projectCompileSdk.toInt()
    buildToolsVersion = tools.versions.buildTools.get()
    ndkVersion = tools.versions.ndk.get()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        // TTT (light) has its own package id, and this is the one line in the fork that must never
        // be wrong. Sharing `com.mantraproductions.voicetype` with the full app would not sit beside
        // it — Android would treat this build as an update and replace a working keyboard with a
        // stripped one. Different app, different id, different signing key.
        applicationId = "com.mantraproductions.tttlight"
        // LOCAL TEST ONLY, never committed: -PmaTestBuild=1 gives the APK its own application id so
        // it installs beside the real app instead of colliding with it on a different signing key.
        if (project.hasProperty("maTestBuild")) {
            applicationIdSuffix = ".test"
        }
        minSdk = projectMinSdk.toInt()
        targetSdk = projectTargetSdk.toInt()
        versionCode = projectVersionCode.toInt()
        versionName = projectVersionName.substringBefore("-")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // arm64 only. The 32-bit slice was 20.8 MB of the APK, almost entirely a second copy of
        // libonnxruntime.so for the on-device engine, and every phone that can run this app is
        // 64-bit. Nothing else in the build has been anywhere near that expensive.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // The model on the phone (§191): llama.cpp, always optimised. A debug build of it is ten times
        // slower, which would make a debug APK say nothing true about the speed.
        externalNativeBuild {
            cmake {
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release")
            }
        }

        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${getGitCommitHash().get()}\"")
        buildConfigField("String", "FLADDONS_API_VERSION", "\"v~draft2\"")
        buildConfigField("String", "FLADDONS_STORE_URL", "\"beta.addons.florisboard.org\"")

        sourceSets {
            maybeCreate("main").apply {
                assets.directories += "src/main/assets"
                res.directories += versionIconDir.path
            }
        }
    }

    bundle {
        language {
            // We disable language split because FlorisBoard does not use
            // runtime Google Play Service APIs and thus cannot dynamically
            // request to download the language resources for a specific locale.
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // libmallm.so: llama.cpp and its JNI bridge (app/src/main/cpp, §191). The source is fetched by
    // tools/fetch-llama-cpp.sh, pinned by tag and commit.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    // Release signing. Credentials live in a local, untracked `keystore.properties` at the repo root
    // (see keystore.properties.template) so the keystore/passwords never get committed. When the file
    // is absent (e.g. on CI without secrets, or a contributor's machine) the release build simply has
    // no signing config attached and falls back to an unsigned build, exactly as before.
    //
    // This project is distributed as a signed APK from GitHub releases, not through Play, so the
    // only requirement on the key is that it never changes: every build must install over the last.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = if (keystorePropsFile.exists()) {
        Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
    } else {
        null
    }
    signingConfigs {
        keystoreProps?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        named("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug+${getGitCommitHash(short = true).get()}"

            isDebuggable = true
            isJniDebuggable = false
        }

        create("beta") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = projectVersionNameSuffix

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
            isShrinkResources = true
        }

        named("release") {
            versionNameSuffix = projectVersionNameSuffix

            if (keystoreProps != null) {
                signingConfig = signingConfigs.getByName("release")
            }

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
            isShrinkResources = true
        }

        create("benchmark") {
            initWith(getByName("release"))

            applicationIdSuffix = ".bench"
            versionNameSuffix = "-bench+${getGitCommitHash(short = true).get()}"

            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    lint {
        baseline = file("lint.xml")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

aboutLibraries {
    collect {
        configPath = file("src/main/config")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

tasks.withType<Test> {
    testLogging {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED)
    }
    useJUnitPlatform()
}

kover {
    useJacoco()
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    // testImplementation(composeBom)
    // androidTestImplementation(composeBom)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.window.core)
    implementation(libs.cache4k)
    // GIF search (Klipy): Compose image loading + animated GIF/WebP decoding + OkHttp network fetcher.
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mikepenz.aboutlibraries.core)
    implementation(libs.mikepenz.aboutlibraries.compose)
    implementation(libs.okhttp)
    implementation(libs.patrickgold.compose.tooltip)
    implementation(libs.patrickgold.jetpref.datastore.model)
    ksp(libs.patrickgold.jetpref.datastore.model.processor)
    implementation(libs.patrickgold.jetpref.datastore.ui)
    implementation(libs.patrickgold.jetpref.material.ui)

    // sherpa-onnx on-device STT spike (issue #104). Vendored from the v2.0.1 GitHub release AAR:
    // the Kotlin/JNI API as a jar here; the matching native .so live in src/main/jniLibs/<abi>/.
    // Not on Maven Central, so consumed as a local file (see private/docs/research/sherpa-onnx-feasibility.md).
    implementation(files("libs/sherpa-onnx-2.0.1.jar"))
    // Generic ONNX Runtime Java/JNI bridge for Smart Turn v3. sherpa-onnx ships the runtime (1.28.2, the
    // bridge's version tag is retagged to it by the fetch script); tools/fetch-sherpa-onnx.sh extracts only the API jar + tiny JNI bridge,
    // avoiding a second ~20–27 MB copy of libonnxruntime per ABI.
    implementation(files("libs/onnxruntime-android-1.24.3.jar"))

    implementation(projects.lib.android)
    implementation(projects.lib.color)
    implementation(projects.lib.dictateCore)
    implementation(projects.lib.compose)

    // Wearable Data Layer: settings sync + tethered transcription with the Wear OS app (#106).
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(projects.lib.kotlin)
    implementation(projects.lib.snygg)

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

// On-device STT (issue #104): the sherpa-onnx native libs are vendored, not committed (see
// .gitignore). Fail early with a clear instruction instead of a cryptic linker error if a fresh
// clone hasn't fetched them yet.
val verifySherpaOnnxLibs by tasks.registering {
    // Resolve paths at configuration time so the action captures only plain Files (configuration
    // cache cannot serialize references to Gradle script/Project objects).
    val projectDir = layout.projectDirectory
    val required = buildList {
        add(projectDir.file("libs/sherpa-onnx-2.0.1.jar").asFile)
        add(projectDir.file("libs/onnxruntime-android-1.24.3.jar").asFile)
        for (abi in listOf("arm64-v8a", "armeabi-v7a")) {
            add(projectDir.file("src/main/jniLibs/$abi/libonnxruntime.so").asFile)
            add(projectDir.file("src/main/jniLibs/$abi/libonnxruntime4j_jni.so").asFile)
            add(projectDir.file("src/main/jniLibs/$abi/libsherpa-onnx-jni.so").asFile)
        }
    }
    doLast {
        val missing = required.filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing vendored sherpa-onnx native libs:\n" +
                    missing.joinToString("\n") { "  - ${it.name}" } +
                    "\n\nRun:  tools/fetch-sherpa-onnx.sh",
            )
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifySherpaOnnxLibs) }

fun getGitCommitHash(short: Boolean = false): Provider<String> {
    if (!File(".git").exists()) {
        return providers.provider { "null" }
    }

    val execProvider = providers.exec {
        if (short) {
            commandLine("git", "rev-parse", "--short", "HEAD")
        } else {
            commandLine("git", "rev-parse", "HEAD")
        }
    }
    return execProvider.standardOutput.asText.map { it.trim() }
}
