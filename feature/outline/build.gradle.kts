plugins {
    id("factswallpaper.android.feature")
}

android {
    namespace = "com.factswallpaper.feature.outline"
}

dependencies {
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    implementation(project(":core:ai"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
