plugins {
    id("factswallpaper.android.feature")
}

android {
    namespace = "com.factswallpaper.feature.wallpaper"
}

dependencies {
    implementation(project(":core:wallpaper"))
}
