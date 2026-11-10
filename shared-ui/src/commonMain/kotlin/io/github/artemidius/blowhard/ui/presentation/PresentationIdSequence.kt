package io.github.artemidius.blowhard.ui.presentation

internal class PresentationIdSequence(
    private val prefix: String,
    private val reserved: MutableSet<String> = mutableSetOf(),
) {
    private var value = 0L

    fun next(): String {
        var candidate: String
        do {
            value += 1
            candidate = "$prefix-$value"
        } while (!reserved.add(candidate))
        return candidate
    }
}
