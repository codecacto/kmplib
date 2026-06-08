package br.com.codecacto.kmplib.ads.stats

import br.com.codecacto.kmplib.core.util.AppLogger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore

/**
 * Recorder que grava metricas no Firestore via `FieldValue.increment` —
 * atomico no servidor, sem race entre clientes concorrentes.
 *
 * Schema (colecao `ad_stats`, doc ID composto):
 * ```
 * ad_stats/{provider}__{appId}__{format}__{adId|all}__{YYYY-MM-DD}
 *   provider:    "admob" | "custom"
 *   appId:       string
 *   format:      "banner" | "interstitial" | "app_open"
 *   adId:        string (custom) | "all" (admob agregado)
 *   day:         "YYYY-MM-DD"
 *   impressions: number (FieldValue.increment)
 *   clicks:      number (FieldValue.increment)
 *   updatedAt:   number (millis UTC)
 * ```
 *
 * Custos: 1 write por evento. Em volume alto, considerar throttling ou
 * batching no consumer.
 */
class FirestoreAdStatsRecorder(
    private val collection: String = "ad_stats",
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : AdStatsRecorder {
    companion object {
        private const val TAG = "FirestoreAdStatsRecorder"
    }

    override suspend fun record(
        provider: AdProviderTag,
        appId: String,
        format: AdFormat,
        adId: String?,
        type: AdEventType,
    ) {
        val day = AdStats.todayKey()
        val docId = buildStatKey(provider, appId, format, adId, day)
        val counterField = when (type) {
            AdEventType.IMPRESSION -> "impressions"
            AdEventType.CLICK -> "clicks"
        }

        // Best-effort: estatistica de ad nao pode quebrar o app se rules
        // estiverem ausentes ou se houver falha de rede.
        try {
            firestore.collection(collection).document(docId).set(
                data = mapOf(
                    // Metadata pra facilitar query no admin (where appId == ...)
                    "provider" to provider.name.lowercase(),
                    "appId" to appId,
                    "format" to format.name.lowercase(),
                    "adId" to (adId ?: "all"),
                    "day" to day,
                    counterField to FieldValue.increment(1),
                    "updatedAt" to System_currentTimeMillis(),
                ),
                merge = true,
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao gravar ad_stats/$docId (best-effort, ignorado)", e)
        }
    }

    // Wrapper pra evitar conflitos de imports com kotlinx-datetime.
    private fun System_currentTimeMillis(): Long =
        br.com.codecacto.kmplib.core.util.currentTimeMillis()
}
