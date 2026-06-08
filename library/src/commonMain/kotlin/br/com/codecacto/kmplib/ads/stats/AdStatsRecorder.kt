package br.com.codecacto.kmplib.ads.stats

/**
 * Fonte para gravacao de metricas de publicidade. Injetavel nos testes.
 */
interface AdStatsRecorder {
    suspend fun record(
        provider: AdProviderTag,
        appId: String,
        format: AdFormat,
        adId: String?,
        type: AdEventType,
    )
}

/** No-op — usado quando [AdStats] nao foi inicializado ou o consumer desativou. */
internal object NoopAdStatsRecorder : AdStatsRecorder {
    override suspend fun record(
        provider: AdProviderTag,
        appId: String,
        format: AdFormat,
        adId: String?,
        type: AdEventType,
    ) {
        // intencional
    }
}
