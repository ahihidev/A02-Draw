import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk = 36
            defaultConfig {
                minSdk = 24
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                consumerProguardFiles("consumer-rules.pro")
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            buildFeatures.viewBinding = true
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
                // Kotlin FIR in the current lint toolchain crashes while resolving KSP/Room
                // generated types from test variants. Production sources remain fully checked.
                checkTestSources = false
                disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "OldTargetApi")
            }
            testOptions.unitTests.isIncludeAndroidResources = true
        }
    }
}
