plugins {
    id("factswallpaper.android.library")
    id("factswallpaper.android.compose")
}

android {
    namespace = "com.factswallpaper.core.ui"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
