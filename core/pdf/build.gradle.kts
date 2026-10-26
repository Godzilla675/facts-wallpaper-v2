plugins {
    id("factswallpaper.android.library")
    id("factswallpaper.android.hilt")
}

android {
    namespace = "com.factswallpaper.core.pdf"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:ocr"))

    implementation(libs.pdfbox.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
