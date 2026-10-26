import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            val libs = versionCatalog()
            dependencies {
                add("implementation", libs.library("androidx-room-runtime").get())
                add("implementation", libs.library("androidx-room-ktx").get())
                add("ksp", libs.library("androidx-room-compiler").get())
                add("testImplementation", libs.library("androidx-room-testing").get())
            }
            extensions.configure<KspExtension> {
                arg("room.schemaLocation", "$projectDir/schemas")
                arg("room.generateKotlin", "true")
                arg("room.incremental", "true")
            }
        }
    }
}
