package io.github.artemidius.blowhard.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopPlatformTest {
    @Test
    fun `operating system names map conservatively`() {
        assertEquals(DesktopPlatform.MACOS, DesktopPlatform.detect("Mac OS X"))
        assertEquals(DesktopPlatform.MACOS, DesktopPlatform.detect("Darwin"))
        assertEquals(DesktopPlatform.WINDOWS, DesktopPlatform.detect("Windows 11"))
        assertEquals(DesktopPlatform.LINUX, DesktopPlatform.detect("Linux"))
        assertEquals(DesktopPlatform.UNSUPPORTED, DesktopPlatform.detect("FreeBSD"))
    }

    @Test
    fun `Linux clipboard requires a non-headless X11 or XWayland session`() {
        val base = DesktopEnvironment(DesktopPlatform.LINUX, userHome = "/tmp/user")

        assertFalse(base.hasClipboardSession)
        assertFalse(base.copy(display = ":99", headless = true).hasClipboardSession)
        assertTrue(base.copy(display = ":99").hasClipboardSession)
    }

    @Test
    fun `current CI host is one of the supported desktop platforms`() {
        val environment = DesktopEnvironment.current()

        assertNotEquals(DesktopPlatform.UNSUPPORTED, environment.platform)
        val directories = AppDirectoryResolver.resolve(environment)
        assertTrue(directories.configuration.isAbsolute)
        assertTrue(directories.cache.isAbsolute)
    }
}
