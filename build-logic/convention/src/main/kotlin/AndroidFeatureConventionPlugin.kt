import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Convention for feature modules: Android library + Compose + Hilt, wired to the
 * shared core modules every feature is allowed to use.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("factswallpaper.android.library")
            pluginManager.apply("factswallpaper.android.compose")
            pluginManager.apply("factswallpaper.android.hilt")

            val libs = versionCatalog()
            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:common"))
                add("implementation", project(":core:data"))
                add("implementation", project(":core:datastore"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))

                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.library("androidx-hilt-navigation-compose").get())
                add("implementation", libs.library("androidx-compose-material-icons-extended").get())
            }
        }
    }
}
