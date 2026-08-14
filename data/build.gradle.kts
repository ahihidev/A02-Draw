import java.util.Properties

plugins {
    id("a02.android.library")
    id("a02.android.hilt")
}

android {
    namespace = "com.a02.draw.data"
    testNamespace = "com.a02.draw.data.test"
    buildFeatures.buildConfig = true
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    val localSecrets = Properties().apply {
        rootProject.file("secrets.properties")
            .takeIf { it.isFile }
            ?.inputStream()
            ?.use(::load)
    }

    fun apiConfig(name: String, fallback: String = ""): String =
        providers.gradleProperty(name)
            .orElse(providers.environmentVariable("A02_$name"))
            .orElse(localSecrets.getProperty(name, fallback))
            .get()

    fun quoted(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    defaultConfig {
        buildConfigField(
            "String",
            "API_BASE_URL",
            quoted(apiConfig("API_BASE_URL", "https://toroarapi.duckdns.org/")),
        )
        buildConfigField(
            "String",
            "API_AES_KEY",
            quoted(apiConfig("API_AES_KEY")),
        )
        buildConfigField(
            "String",
            "API_AES_IV",
            quoted(apiConfig("API_AES_IV")),
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":domain"))
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
