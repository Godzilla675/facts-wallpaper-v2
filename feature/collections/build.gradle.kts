plugins {
    id("factswallpaper.android.feature")
}

android {
    namespace = "com.factswallpaper.feature.collections"
}

dependencies {
    implementation(project(":core:render"))
    implementation(project(":core:ai"))
}
