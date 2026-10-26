plugins {
    id("factswallpaper.android.feature")
}

android {
    namespace = "com.factswallpaper.feature.facts"
}

dependencies {
    implementation(project(":core:render"))
    implementation(project(":core:ai"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
}
