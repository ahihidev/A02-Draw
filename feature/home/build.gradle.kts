plugins {
    id("a02.android.library")
    id("a02.android.hilt")
}

android {
    namespace = "com.a02.draw.feature.home"
    testNamespace = "com.a02.draw.feature.home.test"

    lint {
        // Destination layouts own their Figma surface while the host Activity is transparent.
        // The Overdraw detector cannot infer that relationship for library Fragments.
        disable += "Overdraw"
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
