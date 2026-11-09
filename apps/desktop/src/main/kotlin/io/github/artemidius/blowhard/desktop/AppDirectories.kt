package io.github.artemidius.blowhard.desktop

import java.nio.file.Path

internal data class AppDirectories(val configuration: Path, val cache: Path)

internal object AppDirectoryResolver {
    fun resolve(environment: DesktopEnvironment): AppDirectories {
        val home = Path.of(requireNotBlank(environment.userHome, "User home"))
        val directories = when (environment.platform) {
            DesktopPlatform.MACOS -> AppDirectories(
                configuration = home.resolve("Library/Application Support/Blowhard"),
                cache = home.resolve("Library/Caches/Blowhard"),
            )

            DesktopPlatform.WINDOWS -> AppDirectories(
                configuration = environment.appData.toPathOrNull()?.resolve("Blowhard")
                    ?: home.resolve("AppData/Roaming/Blowhard"),
                cache = environment.localAppData.toPathOrNull()?.resolve("Blowhard/Cache")
                    ?: home.resolve("AppData/Local/Blowhard/Cache"),
            )

            DesktopPlatform.LINUX -> AppDirectories(
                configuration = environment.xdgConfigHome.toPathOrNull()?.resolve("blowhard")
                    ?: home.resolve(".config/blowhard"),
                cache = environment.xdgCacheHome.toPathOrNull()?.resolve("blowhard")
                    ?: home.resolve(".cache/blowhard"),
            )

            DesktopPlatform.UNSUPPORTED -> error("Unsupported desktop platform")
        }
        return AppDirectories(
            configuration = directories.configuration.toAbsolutePath().normalize(),
            cache = directories.cache.toAbsolutePath().normalize(),
        )
    }

    private fun String?.toPathOrNull(): Path? = this?.takeIf(String::isNotBlank)?.let(Path::of)

    private fun requireNotBlank(value: String, label: String): String {
        require(value.isNotBlank()) { "$label must not be blank" }
        return value
    }
}
