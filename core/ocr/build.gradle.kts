plugins {
    id("factswallpaper.android.library")
    id("factswallpaper.android.hilt")
}

android {
    namespace = "com.factswallpaper.core.ocr"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.mlkit.text.recognition.devanagari)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.tesseract4android)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
