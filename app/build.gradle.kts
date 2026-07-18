plugins {
    alias(libs.plugins.android.application)
    id("a02.android.hilt")
}

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
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "OldTargetApi")
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    val releaseStorePath = System.getenv("A02_RELEASE_STORE_FILE")
    if (!releaseStorePath.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("A02_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("A02_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("A02_RELEASE_KEY_PASSWORD")
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
            if (!releaseStorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":data"))
    implementation(project(":feature:home"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
