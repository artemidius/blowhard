import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                byNames.add("io.github.artemidius.blowhard.adapters.persistence.db.**")
            }
        }
    }

    android {
        namespace = "io.github.artemidius.blowhard.adapters.persistence"
        compileSdk = libs.versions.android.compile.sdk.get().toInt()
        minSdk = libs.versions.android.min.sdk.get().toInt()
        withHostTest {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }

        named("desktopMain") {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
            }
        }

        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        named("desktopTest") {
            resources.srcDir("src/commonMain/sqldelight/databases")
        }
    }
}

sqldelight {
    databases {
        create("BlowhardDatabase") {
            packageName.set("io.github.artemidius.blowhard.adapters.persistence.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
