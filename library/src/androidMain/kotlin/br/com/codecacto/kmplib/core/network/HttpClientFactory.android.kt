package br.com.codecacto.kmplib.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/** Engine oficial recomendado no Android: OkHttp. */
internal actual fun createPlatformHttpClientEngine(): HttpClientEngine = OkHttp.create()
