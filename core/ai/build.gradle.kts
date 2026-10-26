plugins {
    id("factswallpaper.android.library")
    id("factswallpaper.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.factswallpaper.core.ai"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:security"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
