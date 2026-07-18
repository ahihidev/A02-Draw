plugins {
    `kotlin-dsl`
}

group = "com.a02.draw.buildlogic"

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.1.1")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.9")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.60.1")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "a02.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "a02.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "a02.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        register("androidHilt") {
            id = "a02.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
    }
}
