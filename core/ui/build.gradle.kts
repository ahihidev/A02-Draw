plugins {
    id("a02.android.library")
}

android {
    namespace = "com.a02.draw.core.ui"
    testNamespace = "com.a02.draw.core.ui.test"
}

dependencies {
    api(project(":core:common"))
    api(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    api(libs.androidx.activity.ktx)
    api(libs.androidx.fragment.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.material)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
}
