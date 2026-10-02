import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
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
            modules("java.instrument", "jdk.unsupported")
            packageName = "Blowhard"
            packageVersion = "1.0.0"
            description = "A desktop-first Kotlin Multiplatform client for Mistral AI"
            vendor = "Blowhard contributors"

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
