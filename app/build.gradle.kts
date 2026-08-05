plugins {
    alias(libs.plugins.android.application)
    id("a02.android.hilt")
}

val releaseSigningEnvironment = mapOf(
    "A02_RELEASE_STORE_FILE" to System.getenv("A02_RELEASE_STORE_FILE"),
    "A02_RELEASE_STORE_PASSWORD" to System.getenv("A02_RELEASE_STORE_PASSWORD"),
    "A02_RELEASE_KEY_ALIAS" to System.getenv("A02_RELEASE_KEY_ALIAS"),
    "A02_RELEASE_KEY_PASSWORD" to System.getenv("A02_RELEASE_KEY_PASSWORD"),
)
val hasCompleteReleaseSigning = releaseSigningEnvironment.values.all { !it.isNullOrBlank() }

android {
    namespace = "com.a02.draw"
    testNamespace = "com.a02.draw.test"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.a02.draw"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging.resources.excludes += setOf(
        "META-INF/AL2.0",
        "META-INF/LGPL2.1",
        "META-INF/LICENSE.md",
        "META-INF/LICENSE-notice.md",
    )

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        // Instrumentation sources currently trigger a Kotlin FIR crash inside lint itself.
        // Keep strict lint for all production code while unit/instrumentation tests run separately.
        checkTestSources = false
        // Fragment roots intentionally paint the exact Figma surface color; lint cannot
        // reliably associate those layouts with their transparent NavHost window.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "Overdraw")
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    if (hasCompleteReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(checkNotNull(releaseSigningEnvironment["A02_RELEASE_STORE_FILE"]))
                storePassword = releaseSigningEnvironment["A02_RELEASE_STORE_PASSWORD"]
                keyAlias = releaseSigningEnvironment["A02_RELEASE_KEY_ALIAS"]
                keyPassword = releaseSigningEnvironment["A02_RELEASE_KEY_PASSWORD"]
                // Some OEM package scanners still inspect the legacy JAR certificate.
                // Keep direct-distribution APKs on v1 + v2 for broad installer compatibility.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = false
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

val validatePublishableReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails before creating a publishable bundle when release signing is incomplete."
    doLast {
        val missingVariables = releaseSigningEnvironment
            .filterValues { it.isNullOrBlank() }
            .keys
            .sorted()
        check(missingVariables.isEmpty()) {
            "Release bundle is not signed. Define: ${missingVariables.joinToString()}"
        }

        val storePath = checkNotNull(releaseSigningEnvironment["A02_RELEASE_STORE_FILE"])
        check(file(storePath).isFile) {
            "Release keystore does not exist at the configured A02_RELEASE_STORE_FILE path."
        }
    }
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn(validatePublishableReleaseSigning)
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":data"))
    implementation(project(":feature:home"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
