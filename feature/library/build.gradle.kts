plugins {
    id("factswallpaper.android.feature")
}

android {
    namespace = "com.factswallpaper.feature.library"
}

dependencies {
    implementation(project(":core:pdf"))
    implementation(project(":core:ocr"))
    implementation(libs.androidx.work.runtime.ktx)
}
