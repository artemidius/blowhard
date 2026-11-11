import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.SpdxLicenseBundleNormalizer
import com.github.jk1.license.render.InventoryMarkdownReportRenderer
import com.github.jk1.license.render.JsonReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.gradle.api.tasks.Exec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.license.report)
}

licenseReport {
    outputDir = layout.buildDirectory.dir("reports/dependency-license").get().asFile.absolutePath
    projects = arrayOf(project)
    configurations = arrayOf("runtimeClasspath")
    excludeGroups = arrayOf("Blowhard.*")
    excludeBoms = true
    allowedLicensesFile = layout.projectDirectory.file("../../config/allowed-runtime-licenses.json")
    filters = arrayOf<DependencyFilter>(SpdxLicenseBundleNormalizer())
    renderers = arrayOf<ReportRenderer>(
        JsonReportRenderer("dependencies.json", false),
        InventoryMarkdownReportRenderer(
            "THIRD_PARTY_LICENSES.md",
            "Blowhard desktop runtime",
            null,
            false,
            false,
        ),
    )
}

tasks.named("checkLicense") {
    mustRunAfter("generateLicenseReport")
}

tasks.register<JavaExec>("referenceLiveSmoke") {
    group = "verification"
    description = "Runs one bounded Mistral conversation using the macOS Keychain credential."
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.artemidius.blowhard.desktop.ReferenceLiveSmoke"
}

val verifyLicenseInventory = tasks.register<Exec>("verifyLicenseInventory") {
    group = "verification"
    description = "Verifies the generated desktop runtime dependency and license inventory."
    dependsOn("generateLicenseReport")
    workingDir(rootProject.projectDir)
    val python = if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
    commandLine(
        python,
        "tools/packaging/verify_license_inventory.py",
        "apps/desktop/build/reports/dependency-license/dependencies.json",
        "config/allowed-runtime-licenses.json",
    )
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":adapters:fixture"))
    implementation(project(":adapters:mistral"))
    implementation(project(":adapters:persistence"))
    implementation(project(":adapters:platform"))
    implementation(project(":core:application"))
    implementation(project(":shared-ui"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

compose.desktop {
    application {
        mainClass = "io.github.artemidius.blowhard.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            modules("java.instrument", "java.sql", "jdk.unsupported")
            packageName = "Blowhard"
            packageVersion = providers.gradleProperty("blowhardInstallerVersion").get()
            description = "A desktop-first Kotlin Multiplatform client for Mistral AI"
            copyright = "Copyright © 2026 Blowhard contributors"
            vendor = "Blowhard contributors"
            licenseFile.set(rootProject.file("LICENSE"))

            linux {
                packageName = "blowhard"
                debMaintainer = "artemidius@users.noreply.github.com"
                menuGroup = "Utility"
                appCategory = "utils"
            }

            macOS {
                bundleID = "io.github.artemidius.blowhard"
                dockName = "Blowhard"
            }

            windows {
                menuGroup = "Blowhard"
                perUserInstall = true
                upgradeUuid = "61d3ae82-27db-4eb0-9ee8-2fb2b3b9cd56"
            }
        }
    }
}
