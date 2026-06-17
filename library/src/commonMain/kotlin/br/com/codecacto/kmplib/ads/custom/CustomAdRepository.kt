package br.com.codecacto.kmplib.ads.custom

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Fonte de dados generica de [CustomAd]. Permite trocar a impl por um fake nos testes.
 */
interface CustomAdSource {
    fun observeAds(placementId: String? = null, appId: String? = null): Flow<List<CustomAd>>
    suspend fun fetchAds(placementId: String? = null, appId: String? = null): Result<List<CustomAd>>
}

/**
 * Implementacao padrao de [CustomAdSource] — leitura do Firestore.
 *
 * Usa o Firestore ja configurado no app (mesma instancia usada por outros servicos).
 * Nao escreve nada — apenas le anuncios ativos da colecao configurada.
 */
class CustomAdRepository(
    private val collection: String = "custom_ads",
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val nowProvider: () -> Long = ::currentTimeMillis
) : CustomAdSource {
    companion object {
        private const val TAG = "CustomAdRepository"
    }

    /**
     * Observa anuncios ativos em tempo real.
     *
     * Filtra no servidor por `active == true` e ordena por `priority desc`.
     * Filtra no cliente por [placementId] (se nao-nulo), [appId] (se nao-nulo)
     * e janela de tempo (`startsAt`/`endsAt` vs agora).
     */
    override fun observeAds(placementId: String?, appId: String?): Flow<List<CustomAd>> {
        return firestore.collection(collection)
            .where { "active" equalTo true }
            .orderBy("priority", Direction.DESCENDING)
            .snapshots
            .map { snapshot ->
                val now = nowProvider()
                snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.data(CustomAd.serializer()).copy(id = doc.id)
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Falha ao desserializar ad ${doc.id}", e)
                        null
                    }
                }.filter { ad ->
                    matchesAppId(ad, appId) &&
                        matchesPlacement(ad, placementId) &&
                        isInTimeWindow(ad, now)
                }
            }
            // Sem isso, PERMISSION_DENIED / falha de rede no listener do
            // Firestore propaga pra coroutine do CustomAdManager e derruba
            // o app. Cai pra lista vazia (nao mostra anuncios).
            .catch { e ->
                AppLogger.w(TAG, "Listener de custom_ads falhou, sem anuncios", e)
                emit(emptyList())
            }
    }

    /**
     * Busca anuncios ativos uma unica vez (sem observer).
     */
    override suspend fun fetchAds(placementId: String?, appId: String?): Result<List<CustomAd>> = try {
        val now = nowProvider()
        val snapshot = firestore.collection(collection)
            .where { "active" equalTo true }
            .orderBy("priority", Direction.DESCENDING)
            .get()

        val ads = snapshot.documents.mapNotNull { doc ->
            try {
                doc.data(CustomAd.serializer()).copy(id = doc.id)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Falha ao desserializar ad ${doc.id}", e)
                null
            }
        }.filter { ad ->
            matchesAppId(ad, appId) &&
                matchesPlacement(ad, placementId) &&
                isInTimeWindow(ad, now)
        }

        Result.success(ads)
    } catch (e: Exception) {
        AppLogger.e(TAG, "Erro ao buscar custom ads", e)
        Result.failure(e)
    }
}

internal fun matchesPlacement(ad: CustomAd, placementId: String?): Boolean =
    placementId == null || ad.placementId == placementId

/**
 * Casa o anuncio com o app do filtro.
 *
 * - `appId == null` (filtro): nao filtra por app (comportamento atual — todos casam).
 * - Senao: casa se `appId == ad.appId` (LEGADO, 1 app) OU `ad.appIds.contains(appId)` (N:N).
 *
 * Docs antigos sem `appIds` (lista vazia) seguem casando pelo `appId` legado.
 */
internal fun matchesAppId(ad: CustomAd, appId: String?): Boolean =
    appId == null || ad.appId == appId || ad.appIds.contains(appId)

internal fun isInTimeWindow(ad: CustomAd, nowMillis: Long): Boolean {
    val afterStart = ad.startsAt?.let { nowMillis >= it } ?: true
    val beforeEnd = ad.endsAt?.let { nowMillis <= it } ?: true
    return afterStart && beforeEnd
}
