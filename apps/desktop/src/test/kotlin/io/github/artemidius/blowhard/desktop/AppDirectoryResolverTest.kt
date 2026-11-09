package io.github.artemidius.blowhard.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppDirectoryResolverTest {
    private val home = Path.of(System.getProperty("java.io.tmpdir"), "User Space", "Žluťoučký")

    @Test
    fun `macOS directories preserve spaces and Unicode`() {
        val directories = AppDirectoryResolver.resolve(
            DesktopEnvironment(DesktopPlatform.MACOS, userHome = home.toString()),
        )

        assertEquals(home.resolve("Library/Application Support/Blowhard").normalize(), directories.configuration)
        assertEquals(home.resolve("Library/Caches/Blowhard").normalize(), directories.cache)
    }

    @Test
    fun `Windows prefers roaming and local application data`() {
        val roaming = home.resolve("Roaming Data")
        val local = home.resolve("Local Data")
        val directories = AppDirectoryResolver.resolve(
            DesktopEnvironment(
                platform = DesktopPlatform.WINDOWS,
                userHome = home.toString(),
                appData = roaming.toString(),
                localAppData = local.toString(),
            ),
        )

        assertEquals(roaming.resolve("Blowhard").normalize(), directories.configuration)
        assertEquals(local.resolve("Blowhard/Cache").normalize(), directories.cache)
    }

    @Test
    fun `Linux respects XDG paths and has home fallbacks`() {
        val xdgConfig = home.resolve("xdg config")
        val xdgCache = home.resolve("xdg cache")
        val explicit = AppDirectoryResolver.resolve(
            DesktopEnvironment(
                platform = DesktopPlatform.LINUX,
                userHome = home.toString(),
                xdgConfigHome = xdgConfig.toString(),
                xdgCacheHome = xdgCache.toString(),
            ),
        )
        val fallback = AppDirectoryResolver.resolve(
            DesktopEnvironment(DesktopPlatform.LINUX, userHome = home.toString()),
        )

        assertEquals(xdgConfig.resolve("blowhard").normalize(), explicit.configuration)
        assertEquals(xdgCache.resolve("blowhard").normalize(), explicit.cache)
        assertEquals(home.resolve(".config/blowhard").normalize(), fallback.configuration)
        assertEquals(home.resolve(".cache/blowhard").normalize(), fallback.cache)
    }

    @Test
    fun `unsupported platform and blank home fail before file access`() {
        assertFailsWith<IllegalStateException> {
            AppDirectoryResolver.resolve(DesktopEnvironment(DesktopPlatform.UNSUPPORTED, home.toString()))
        }
        assertFailsWith<IllegalArgumentException> {
            AppDirectoryResolver.resolve(DesktopEnvironment(DesktopPlatform.LINUX, " "))
        }
    }
}
