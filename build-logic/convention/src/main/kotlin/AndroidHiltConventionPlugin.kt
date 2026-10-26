import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")
            val libs = versionCatalog()
            dependencies {
                add("implementation", libs.library("hilt-android").get())
                add("ksp", libs.library("hilt-compiler").get())
                add("testImplementation", libs.library("hilt-android").get())
                add("testImplementation", libs.library("hilt-compiler").get())
            }
        }
    }
}
