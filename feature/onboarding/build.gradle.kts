plugins {
    id("factswallpaper.android.feature")
}

android {
    namespace = "com.factswallpaper.feature.onboarding"
}

dependencies {
    implementation(project(":core:wallpaper"))
    implementation(project(":core:security"))
    implementation(project(":core:ai"))
}
