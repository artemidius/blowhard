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
include(":adapters:fixture")
include(":adapters:mistral")
include(":core:api")
include(":core:application")
include(":core:domain")
include(":shared-ui")
