plugins {
    id("factswallpaper.android.feature")
}

android {
    namespace = "com.factswallpaper.feature.templates"
}

dependencies {
    implementation(project(":core:render"))
    implementation(project(":core:ai"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.kotlinx.serialization.json)
}
