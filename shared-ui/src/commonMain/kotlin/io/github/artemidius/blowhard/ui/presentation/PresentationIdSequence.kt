package io.github.artemidius.blowhard.ui.presentation

internal class PresentationIdSequence(private val prefix: String, initialValue: Long = 0L) {
    private var value = initialValue

    fun next(): String {
        value += 1
        return "$prefix-$value"
    }
}
