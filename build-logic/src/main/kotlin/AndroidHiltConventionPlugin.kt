import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", "com.google.dagger:hilt-android:2.60.1")
            add("ksp", "com.google.dagger:hilt-compiler:2.60.1")
        }
    }
}
