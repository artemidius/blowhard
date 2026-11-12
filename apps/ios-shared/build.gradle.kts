plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "BlowhardIos"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app-composition"))
            implementation(project(":adapters:mistral"))
            implementation(project(":adapters:mobile-platform"))
            implementation(project(":core:api"))
            implementation(project(":core:application"))
            implementation(project(":shared-ui"))
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        iosMain.dependencies {
            implementation(libs.compose.ui)
        }
    }
}
