plugins {
    id("factswallpaper.android.library")
    id("factswallpaper.android.hilt")
}

android {
    namespace = "com.factswallpaper.core.security"
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
