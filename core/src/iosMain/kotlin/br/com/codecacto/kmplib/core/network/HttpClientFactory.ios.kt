package br.com.codecacto.kmplib.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/** Engine oficial recomendado no iOS/Kotlin-Native: Darwin (URLSession). */
internal actual fun createPlatformHttpClientEngine(): HttpClientEngine = Darwin.create()
