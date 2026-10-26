plugins {
    id("factswallpaper.android.feature")
}

android {
    namespace = "com.factswallpaper.feature.settings"
}

dependencies {
    implementation(project(":core:backup"))
    implementation(project(":core:wallpaper"))
}
