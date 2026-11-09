package io.github.artemidius.blowhard.desktop

internal data class WindowPreferences(
    val width: Int = DEFAULT_WINDOW_WIDTH,
    val height: Int = DEFAULT_WINDOW_HEIGHT,
    val maximized: Boolean = false,
) {
    init {
        require(width in MINIMUM_WINDOW_WIDTH..MAXIMUM_WINDOW_WIDTH) { "Window width is outside supported bounds" }
        require(height in MINIMUM_WINDOW_HEIGHT..MAXIMUM_WINDOW_HEIGHT) { "Window height is outside supported bounds" }
    }
}

internal fun normalizedWindowPreferences(width: Int, height: Int, maximized: Boolean): WindowPreferences =
    WindowPreferences(
        width = width.coerceIn(MINIMUM_WINDOW_WIDTH, MAXIMUM_WINDOW_WIDTH),
        height = height.coerceIn(MINIMUM_WINDOW_HEIGHT, MAXIMUM_WINDOW_HEIGHT),
        maximized = maximized,
    )

internal const val MINIMUM_WINDOW_WIDTH = 720
internal const val MINIMUM_WINDOW_HEIGHT = 480
internal const val DEFAULT_WINDOW_WIDTH = 1100
internal const val DEFAULT_WINDOW_HEIGHT = 760
private const val MAXIMUM_WINDOW_WIDTH = 7680
private const val MAXIMUM_WINDOW_HEIGHT = 4320
