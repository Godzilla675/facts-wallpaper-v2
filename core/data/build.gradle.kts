plugins {
    id("factswallpaper.android.library")
    id("factswallpaper.android.hilt")
}

android {
    namespace = "com.factswallpaper.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:ai"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
