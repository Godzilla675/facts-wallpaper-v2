plugins {
    id("factswallpaper.android.application")
    id("factswallpaper.android.compose")
    id("factswallpaper.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.factswallpaper.app"

    defaultConfig {
        applicationId = "com.factswallpaper.app"
        versionCode = 1
        versionName = "2.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:security"))
    implementation(project(":core:pdf"))
    implementation(project(":core:ocr"))
    implementation(project(":core:ai"))
    implementation(project(":core:render"))
    implementation(project(":core:wallpaper"))
    implementation(project(":core:backup"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:library"))
    implementation(project(":feature:outline"))
    implementation(project(":feature:facts"))
    implementation(project(":feature:collections"))
    implementation(project(":feature:templates"))
    implementation(project(":feature:wallpaper"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
