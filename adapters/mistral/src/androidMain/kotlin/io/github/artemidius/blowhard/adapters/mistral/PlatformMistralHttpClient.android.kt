package io.github.artemidius.blowhard.adapters.mistral

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createPlatformMistralHttpClient(): HttpClient = HttpClient(OkHttp) {
    configureMistralClient()
}
