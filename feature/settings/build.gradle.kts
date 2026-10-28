plugins {
    id("factswallpaper.android.feature")
}

android {
    namespace = "com.factswallpaper.feature.settings"
}

dependencies {
    implementation(project(":core:backup"))
    implementation(project(":core:wallpaper"))
    implementation(project(":core:ai"))
    implementation(project(":core:security"))
    implementation(project(":core:render"))
    implementation(project(":core:ocr"))
}
