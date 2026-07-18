plugins {
    alias(libs.plugins.kotlin.jvm)
    id("a02.kotlin.library")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
