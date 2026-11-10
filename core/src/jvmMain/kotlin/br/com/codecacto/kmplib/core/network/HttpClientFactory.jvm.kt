package br.com.codecacto.kmplib.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Desktop (2.353.0): OkHttp, um dos engines oficiais do Ktor para JVM — o MESMO do Android. HTTP/2,
 * pool de conexão e as mesmas exceções que o [isPermanentTransportFailure] classifica; o app desktop
 * se comporta na rede exatamente como o app Android.
 */
internal actual fun createPlatformHttpClientEngine(): HttpClientEngine = OkHttp.create()
