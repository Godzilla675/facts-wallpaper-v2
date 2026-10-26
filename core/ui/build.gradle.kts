plugins {
    id("factswallpaper.android.library")
    id("factswallpaper.android.compose")
}

android {
    namespace = "com.factswallpaper.core.ui"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
