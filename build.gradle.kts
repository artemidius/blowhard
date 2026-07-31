import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

private val allowedProjectDependencies = mapOf(
    ":apps:desktop" to setOf(":shared-ui"),
    ":core:api" to emptySet(),
    ":core:application" to setOf(":core:api", ":core:domain"),
    ":core:domain" to setOf(":core:api"),
    ":shared-ui" to emptySet(),
)

val checkModuleBoundaries = tasks.register("checkModuleBoundaries") {
    group = "verification"
    description = "Checks that direct project dependencies follow the architecture rules."
}

subprojects {
    val sourcePath = path

    configurations.configureEach {
        val configurationName = name

        dependencies.whenObjectAdded {
            if (this is ProjectDependency) {
                val targetPath = path
                val allowedTargets = allowedProjectDependencies[sourcePath].orEmpty()

                check(targetPath == sourcePath || targetPath in allowedTargets) {
                    "Forbidden module dependency: " +
                        "$sourcePath -> $targetPath via $configurationName"
                }
            }
        }
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(checkModuleBoundaries)
    }
}
