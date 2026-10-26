import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { buildFeatures.compose = true }
            }
            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
            }
            val libs = versionCatalog()
            dependencies {
                val bom = libs.library("androidx-compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))
                add("implementation", libs.library("androidx-compose-ui").get())
                add("implementation", libs.library("androidx-compose-ui-graphics").get())
                add("implementation", libs.library("androidx-compose-ui-tooling-preview").get())
                add("implementation", libs.library("androidx-compose-foundation").get())
                add("implementation", libs.library("androidx-compose-material3").get())
                add("implementation", libs.library("androidx-lifecycle-runtime-compose").get())
                add("debugImplementation", libs.library("androidx-compose-ui-tooling").get())
                add("debugImplementation", libs.library("androidx-compose-ui-test-manifest").get())
                add("testImplementation", platform(bom))
                add("testImplementation", libs.library("androidx-compose-ui-test-junit4").get())
            }
        }
    }
}
