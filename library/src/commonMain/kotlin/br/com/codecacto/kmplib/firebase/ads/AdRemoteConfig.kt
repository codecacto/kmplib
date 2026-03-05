package br.com.codecacto.kmplib.firebase.ads

import br.com.codecacto.kmplib.core.util.AppLogger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.get
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Busca configuração de ads via Firebase Remote Config (GitLive KMP).
 */
internal object AdRemoteConfig {
    private const val TAG = "AdRemoteConfig"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Fetch e ativa Remote Config, depois chama [onResult] com o valor da key.
     * Default: true (ads habilitados).
     */
    fun fetchAdsEnabled(key: String, onResult: (Boolean) -> Unit) {
        scope.launch {
            try {
                val remoteConfig = Firebase.remoteConfig
                remoteConfig.settings {
                    minimumFetchIntervalInSeconds = 3600 // 1 hora
                }
                remoteConfig.setDefaults(key to true)
                remoteConfig.fetchAndActivate()
                val enabled: Boolean = remoteConfig[key]
                AppLogger.d(TAG, "Remote Config fetched: $key = $enabled")
                onResult(enabled)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Erro ao buscar Remote Config: ${e.message}")
                onResult(true) // Default: ads habilitados
            }
        }
    }
}
