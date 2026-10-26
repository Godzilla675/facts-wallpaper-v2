plugins {
    id("factswallpaper.android.library")
    id("factswallpaper.android.compose")
}

android {
    namespace = "com.factswallpaper.core.designsystem"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.compose.material.icons.extended)
}
