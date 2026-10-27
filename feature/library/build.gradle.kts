plugins {
    id("factswallpaper.android.feature")
}

android {
    namespace = "com.factswallpaper.feature.library"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:pdf"))
    implementation(project(":core:ocr"))
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
