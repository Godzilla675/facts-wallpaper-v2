import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            configureLibraryModule()
            dependencies {
                add("implementation", versionCatalog().library("androidx-core-ktx").get())
                add("implementation", versionCatalog().library("kotlinx-coroutines-core").get())
                add("testImplementation", versionCatalog().library("junit4").get())
                add("testImplementation", versionCatalog().library("kotlinx-coroutines-test").get())
                add("testImplementation", versionCatalog().library("google-truth").get())
                add("testImplementation", versionCatalog().library("turbine").get())
            }
        }
    }
}
