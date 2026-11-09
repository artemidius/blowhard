package io.github.artemidius.blowhard.desktop

internal enum class DesktopPlatform {
    MACOS,
    WINDOWS,
    LINUX,
    UNSUPPORTED,
    ;

    companion object {
        fun detect(osName: String): DesktopPlatform {
            val normalized = osName.lowercase()
            return when {
                normalized.contains("mac") || normalized.contains("darwin") -> MACOS
                normalized.contains("win") -> WINDOWS
                normalized.contains("linux") -> LINUX
                else -> UNSUPPORTED
            }
        }
    }
}
