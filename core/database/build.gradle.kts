plugins {
    id("factswallpaper.android.library")
    id("factswallpaper.android.hilt")
    id("factswallpaper.android.room")
}

android {
    namespace = "com.factswallpaper.core.database"

    // Room exports its versioned schema next to this file (committed to git).
    sourceSets {
        getByName("test").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
