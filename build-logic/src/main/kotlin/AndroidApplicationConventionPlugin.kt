import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            compileSdk = 36
            defaultConfig {
                minSdk = 24
                targetSdk = 36
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
        }
    }
}
