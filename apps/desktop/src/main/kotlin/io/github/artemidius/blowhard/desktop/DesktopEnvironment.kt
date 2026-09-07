package io.github.artemidius.blowhard.desktop

import java.awt.GraphicsEnvironment

internal data class DesktopEnvironment(
    val platform: DesktopPlatform,
    val userHome: String,
    val appData: String? = null,
    val localAppData: String? = null,
    val xdgConfigHome: String? = null,
    val xdgCacheHome: String? = null,
    val display: String? = null,
    val headless: Boolean = false,
) {
    companion object {
        fun current(): DesktopEnvironment = DesktopEnvironment(
            platform = DesktopPlatform.detect(System.getProperty("os.name").orEmpty()),
            userHome = System.getProperty("user.home").orEmpty(),
            appData = System.getenv("APPDATA"),
            localAppData = System.getenv("LOCALAPPDATA"),
            xdgConfigHome = System.getenv("XDG_CONFIG_HOME"),
            xdgCacheHome = System.getenv("XDG_CACHE_HOME"),
            display = System.getenv("DISPLAY"),
            headless = GraphicsEnvironment.isHeadless(),
        )
    }
}

internal val DesktopEnvironment.hasClipboardSession: Boolean
    get() = !headless && (platform != DesktopPlatform.LINUX || !display.isNullOrBlank())
