import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Exec

private fun String.isArchitectureDependencyConfiguration(): Boolean = this == "api" ||
    this == "implementation" ||
    endsWith("Api") ||
    endsWith("Implementation")

plugins {
    alias(libs.plugins.android.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
    alias(libs.plugins.sqldelight) apply false
}

version = providers.gradleProperty("blowhardReleaseVersion").get()

subprojects {
    version = rootProject.version
}

val documentedModules = listOf(
    project(":adapters:fixture"),
    project(":adapters:mistral"),
    project(":adapters:platform"),
    project(":adapters:persistence"),
    project(":core:api"),
    project(":core:application"),
    project(":core:domain"),
    project(":shared-ui"),
)

documentedModules.forEach { module ->
    module.pluginManager.apply("org.jetbrains.dokka")
    module.pluginManager.apply("org.jetbrains.kotlinx.kover")
}

dependencies {
    documentedModules.forEach { module ->
        add("dokka", module)
        add("kover", module)
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("projectFiles") {
        target("**/*.md", "**/*.toml", "**/*.yml", "**/*.yaml")
        targetExclude("**/build/**", "gradle/verification-metadata.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    tasks.matching { it.name.startsWith("dokka") }.configureEach {
        mustRunAfter(rootProject.tasks.named("spotlessCheck"))
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("config/detekt/detekt.yml"))
    basePath = rootDir
    source.setFrom(
        files(
            fileTree("apps") {
                include("**/*.kt")
                exclude("**/build/**")
            },
            fileTree("adapters") {
                include("**/*.kt")
                exclude("**/build/**")
            },
            fileTree("core") {
                include("**/*.kt")
                exclude("**/build/**")
            },
            fileTree("shared-ui") {
                include("**/*.kt")
                exclude("**/build/**")
            },
        ),
    )
}

kover {
    reports {
        total {
            filters {
                includes {
                    classes(
                        "io.github.artemidius.blowhard.core.application.*",
                        "io.github.artemidius.blowhard.core.domain.PromptText*",
                        "io.github.artemidius.blowhard.adapters.mistral.*",
                        "io.github.artemidius.blowhard.ui.presentation.*",
                    )
                }
            }

            verify {
                rule {
                    minBound(85)
                    minBound(75, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
                }
            }
        }
    }
}

val secretScanHistory = tasks.register<Exec>("secretScanHistory") {
    group = "verification"
    description = "Scans the complete Git history for secrets with Gitleaks."
    workingDir(rootDir)
    commandLine(
        "gitleaks",
        "git",
        ".",
        "--config",
        file(".gitleaks.toml").absolutePath,
        "--redact",
        "--no-banner",
    )
}

val secretScanWorkingTree = tasks.register<Exec>("secretScanWorkingTree") {
    group = "verification"
    description = "Scans the working tree, including untracked files, for secrets with Gitleaks."
    workingDir(rootDir)
    commandLine(
        "gitleaks",
        "dir",
        ".",
        "--config",
        file(".gitleaks.toml").absolutePath,
        "--redact",
        "--no-banner",
    )
}

val secretScan = tasks.register("secretScan") {
    group = "verification"
    description = "Scans Git history and the working tree for secrets."
    dependsOn(secretScanHistory, secretScanWorkingTree)
}

tasks.named("koverVerify") {
    mustRunAfter("koverXmlReport")
}

tasks.matching { it.name == "koverXmlReport" || it.name == "koverVerify" }.configureEach {
    outputs.doNotCacheIf("Coverage filters must always be evaluated from the current build configuration") {
        true
    }
}

private val allowedProjectDependencies = mapOf(
    ":adapters:fixture" to setOf(":core:domain"),
    ":adapters:mistral" to setOf(":core:domain"),
    ":adapters:platform" to setOf(":core:domain"),
    ":adapters:persistence" to setOf(":core:domain"),
    ":apps:desktop" to setOf(
        ":adapters:fixture",
        ":adapters:mistral",
        ":adapters:persistence",
        ":adapters:platform",
        ":core:application",
        ":shared-ui",
    ),
    ":core:api" to emptySet(),
    ":core:application" to setOf(":core:api", ":core:domain"),
    ":core:domain" to setOf(":core:api"),
    ":shared-ui" to setOf(":core:api"),
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
            if (configurationName.isArchitectureDependencyConfiguration() && this is ProjectDependency) {
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

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs the complete local quality and security gate."
    dependsOn(
        "spotlessCheck",
        "detekt",
        "checkModuleBoundaries",
        "dokkaGenerate",
        "koverVerify",
        "koverXmlReport",
        secretScan,
        ":core:api:checkKotlinAbi",
        ":core:api:desktopTest",
        ":core:application:checkKotlinAbi",
        ":core:application:desktopTest",
        ":core:domain:checkKotlinAbi",
        ":core:domain:desktopTest",
        ":adapters:fixture:checkKotlinAbi",
        ":adapters:fixture:desktopTest",
        ":adapters:mistral:checkKotlinAbi",
        ":adapters:mistral:desktopTest",
        ":adapters:platform:checkKotlinAbi",
        ":adapters:platform:test",
        ":adapters:persistence:checkKotlinAbi",
        ":adapters:persistence:desktopTest",
        ":adapters:persistence:verifySqlDelightMigration",
        ":shared-ui:checkKotlinAbi",
        ":shared-ui:desktopTest",
    )
}
