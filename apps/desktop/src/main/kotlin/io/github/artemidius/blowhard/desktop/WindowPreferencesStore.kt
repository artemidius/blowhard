package io.github.artemidius.blowhard.desktop

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

internal class WindowPreferencesStore(private val configurationDirectory: Path) {
    private val preferencesFile = configurationDirectory.resolve(PREFERENCES_FILE_NAME)

    fun load(): WindowPreferences = try {
        if (!Files.isRegularFile(preferencesFile)) return WindowPreferences()
        val properties = Properties()
        Files.newBufferedReader(preferencesFile, StandardCharsets.UTF_8).use(properties::load)
        WindowPreferences(
            width = properties.getProperty(WIDTH_KEY)?.toIntOrNull() ?: DEFAULT_WINDOW_WIDTH,
            height = properties.getProperty(HEIGHT_KEY)?.toIntOrNull() ?: DEFAULT_WINDOW_HEIGHT,
            maximized = properties.getProperty(MAXIMIZED_KEY)?.toBooleanStrictOrNull() ?: false,
        )
    } catch (_: IllegalArgumentException) {
        WindowPreferences()
    } catch (_: IOException) {
        WindowPreferences()
    } catch (_: SecurityException) {
        WindowPreferences()
    }

    fun save(preferences: WindowPreferences): Boolean {
        var temporaryFile: Path? = null
        return try {
            Files.createDirectories(configurationDirectory)
            temporaryFile = Files.createTempFile(configurationDirectory, "window-", ".tmp")
            Files.writeString(temporaryFile, preferences.serialize(), StandardCharsets.UTF_8)
            moveIntoPlace(temporaryFile, preferencesFile)
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } finally {
            temporaryFile?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun moveIntoPlace(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

private fun WindowPreferences.serialize(): String = buildString {
    append(WIDTH_KEY).append('=').append(width).append('\n')
    append(HEIGHT_KEY).append('=').append(height).append('\n')
    append(MAXIMIZED_KEY).append('=').append(maximized).append('\n')
}

private const val PREFERENCES_FILE_NAME = "window.properties"
private const val WIDTH_KEY = "width"
private const val HEIGHT_KEY = "height"
private const val MAXIMIZED_KEY = "maximized"
