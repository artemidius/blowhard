package io.github.artemidius.blowhard.desktop

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowPreferencesStoreTest {
    @Test
    fun `preferences round trip under a Unicode path`() {
        val root = createTempDirectory("Blowhard Space Ž-")
        try {
            val store = WindowPreferencesStore(root.resolve("configuration"))
            val expected = WindowPreferences(width = 1440, height = 900, maximized = true)

            assertTrue(store.save(expected))
            assertEquals(expected, store.load())
            assertEquals(
                "width=1440\nheight=900\nmaximized=true\n",
                Files.readString(root.resolve("configuration/window.properties")),
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `missing or malformed preferences use bounded defaults`() {
        val root = createTempDirectory("blowhard-window-")
        try {
            val store = WindowPreferencesStore(root)
            assertEquals(WindowPreferences(), store.load())
            Files.writeString(root.resolve("window.properties"), "width=-1\nheight=999999\nmaximized=maybe")
            assertEquals(WindowPreferences(), store.load())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `write failure does not escape platform boundary`() {
        val root = createTempDirectory("blowhard-window-")
        try {
            val fileInsteadOfDirectory = root.resolve("blocked")
            Files.writeString(fileInsteadOfDirectory, "not a directory")

            assertFalse(WindowPreferencesStore(fileInsteadOfDirectory).save(WindowPreferences()))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `window dimensions are clamped before persistence`() {
        assertEquals(
            WindowPreferences(width = MINIMUM_WINDOW_WIDTH, height = MINIMUM_WINDOW_HEIGHT),
            normalizedWindowPreferences(width = 1, height = 1, maximized = false),
        )
        assertEquals(7680, normalizedWindowPreferences(Int.MAX_VALUE, Int.MAX_VALUE, false).width)
        assertEquals(4320, normalizedWindowPreferences(Int.MAX_VALUE, Int.MAX_VALUE, false).height)
    }
}
