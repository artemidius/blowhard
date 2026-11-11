pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Blowhard"

include(":apps:desktop")
include(":adapters:documents")
include(":adapters:fixture")
include(":adapters:mistral")
include(":adapters:platform")
include(":adapters:persistence")
include(":core:api")
include(":core:application")
include(":core:domain")
include(":shared-ui")
