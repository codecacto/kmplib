package br.com.codecacto.kmplib.ads.router

import br.com.codecacto.kmplib.core.util.AppLogger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Fonte generica do [AdRouting] de um app. Injetavel nos testes.
 */
interface AdRoutingSource {
    fun observeRouting(appId: String, default: AdRouting): Flow<AdRouting>
    suspend fun fetchRouting(appId: String, default: AdRouting): Result<AdRouting>
}

/**
 * Implementacao padrao — observa `app_ad_configs/{appId}` no Firestore.
 *
 * Se o doc nao existe ou ha erro de desserializacao, retorna o `default`
 * passado no [AdRouter.initialize].
 */
class AdRoutingRepository(
    private val collection: String = "app_ad_configs",
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : AdRoutingSource {
    companion object {
        private const val TAG = "AdRoutingRepository"
    }

    override fun observeRouting(appId: String, default: AdRouting): Flow<AdRouting> {
        return firestore.collection(collection).document(appId).snapshots
            .map { snap ->
                if (!snap.exists) return@map default
                try {
                    snap.data(AdRouting.serializer())
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Falha ao desserializar routing de $appId, usando default", e)
                    default
                }
            }
            // Sem isso, PERMISSION_DENIED / falha de rede no listener do
            // Firestore propaga pra coroutine do AdRouter e derruba o app.
            // O comportamento esperado e cair no default e seguir.
            .catch { e ->
                AppLogger.w(TAG, "Listener do Firestore falhou para $appId, usando default", e)
                emit(default)
            }
    }

    override suspend fun fetchRouting(appId: String, default: AdRouting): Result<AdRouting> = try {
        val snap = firestore.collection(collection).document(appId).get()
        val routing = if (snap.exists) {
            try {
                snap.data(AdRouting.serializer())
            } catch (e: Exception) {
                AppLogger.w(TAG, "Falha ao desserializar routing de $appId, usando default", e)
                default
            }
        } else default
        Result.success(routing)
    } catch (e: Exception) {
        AppLogger.e(TAG, "Erro ao buscar routing de $appId", e)
        Result.failure(e)
    }
}
