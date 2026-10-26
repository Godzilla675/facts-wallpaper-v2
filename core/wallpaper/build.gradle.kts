plugins {
    id("factswallpaper.android.library")
}

android {
    namespace = "com.factswallpaper.core.wallpaper"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}
