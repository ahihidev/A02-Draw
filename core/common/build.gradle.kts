plugins {
    alias(libs.plugins.kotlin.jvm)
    id("a02.kotlin.library")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
