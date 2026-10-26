plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.roborazzi) apply false
}

// Ktor 3.6.0 pulls OkHttp 5.5.0, whose okhttp-android AAR requires compileSdk 37.
// The project targets the AGP 8.13 maximum of 36, so pin the last compatible
// OkHttp 5.x release (5.4.0 requires compileSdk 36). Remove when compileSdk 37
// is available.
subprojects {
    configurations.configureEach {
        resolutionStrategy.force(
            "com.squareup.okhttp3:okhttp:5.4.0",
            "com.squareup.okhttp3:okhttp-android:5.4.0",
        )
    }
}
