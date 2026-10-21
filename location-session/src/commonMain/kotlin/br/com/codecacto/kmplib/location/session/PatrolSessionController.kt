package br.com.codecacto.kmplib.location.session

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Resultado de [PatrolLocationSession.startPatrol] (2.308.0). */
sealed class PatrolStartResult {
    /** A sessão está ativa (ou já estava). No iOS, com autorização ainda por decidir, o pedido sai e os fixes começam quando a pessoa permitir. */
    data object Started : PatrolStartResult()

    /**
     * Falta permissão de localização: FINE no Android; negada/restrita no iOS. Peça
     * `AppPermission.PRECISE_LOCATION` (kmplib-platform ≥ 2.312.0 — FINE+COARSE juntas no Android,
     * precisão total no iOS) e tente de novo.
     */
    data object PermissionRequired : PatrolStartResult()

    /** Localização desligada no aparelho. */
    data object LocationDisabled : PatrolStartResult()

    /** Android: `initKmpLibLocationSession(context)` não foi chamado. */
    data object NotInitialized : PatrolStartResult()

    /**
     * A plataforma recusou — Android: foreground service iniciado com o app em 2º plano
     * (`ForegroundServiceStartNotAllowedException`); iOS: `Info.plist` sem `UIBackgroundModes`
     * `location` (setar `allowsBackgroundLocationUpdates` sem ele derruba o app, então a lib recusa antes).
     */
    data class Failed(val reason: String) : PatrolStartResult()
}

/** Por que uma sessão ativa parou sem `stopPatrol()` (2.308.0). */
enum class PatrolInterruption {
    /** A pessoa revogou a permissão de localização no meio da ronda. */
    PERMISSION_REVOKED,

    /** A localização do aparelho foi desligada. */
    LOCATION_DISABLED,

    /**
     * O sistema matou o processo e não deu para retomar sozinho (Android sem
     * `ACCESS_BACKGROUND_LOCATION` concedida). A sessão continua salva:
     * [PatrolLocationSession.resumePatrolIfNeeded] no `ON_RESUME` a retoma.
     */
    STOPPED_BY_SYSTEM,
}

/** O que fica salvo no aparelho para a sessão sobreviver ao processo. */
internal data class PersistedPatrol(val sessionId: String, val accuracy: PatrolAccuracy)

/** A ponte com a plataforma (Android: serviço + Fused; iOS: CLLocationManager). Dublê nos testes. */
internal interface PatrolPlatformPort {
    fun start(sessionId: String, accuracy: PatrolAccuracy): PatrolStartResult
    fun stop()
    fun applyAccuracy(accuracy: PatrolAccuracy)
    fun loadPersisted(): PersistedPatrol?
    fun persist(session: PersistedPatrol?)
}

/**
 * O estado da sessão, independente de plataforma. Chamado da main thread (UI e callbacks de
 * localização — o Fused entrega no `Looper` principal, o Core Location no delegate da main queue).
 */
internal class PatrolSessionController(private val platform: PatrolPlatformPort) {

    private val _fixes = MutableSharedFlow<LocationFix>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val fixes: Flow<LocationFix> = _fixes.asSharedFlow()

    private val _lastFix = MutableStateFlow<LocationFix?>(null)
    val lastFix: StateFlow<LocationFix?> = _lastFix.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isPatrolActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private val _accuracy = MutableStateFlow(PatrolAccuracy.BALANCED)
    val accuracy: StateFlow<PatrolAccuracy> = _accuracy.asStateFlow()

    private val _interruption = MutableStateFlow<PatrolInterruption?>(null)
    val interruption: StateFlow<PatrolInterruption?> = _interruption.asStateFlow()

    fun startPatrol(sessionId: String, accuracy: PatrolAccuracy = _accuracy.value): PatrolStartResult {
        require(sessionId.isNotBlank()) { "sessionId não pode ser vazio" }
        if (_isActive.value) {
            if (_sessionId.value != sessionId) {
                // Troca de sessão sem desligar o GPS: os fixes seguintes já são da nova.
                _sessionId.value = sessionId
                _lastFix.value = null
            }
            setAccuracy(accuracy)
            platform.persist(PersistedPatrol(sessionId, _accuracy.value))
            return PatrolStartResult.Started
        }
        val result = platform.start(sessionId, accuracy)
        if (result == PatrolStartResult.Started) {
            _accuracy.value = accuracy
            _sessionId.value = sessionId
            _lastFix.value = null
            _interruption.value = null
            _isActive.value = true
            platform.persist(PersistedPatrol(sessionId, accuracy))
        }
        return result
    }

    fun stopPatrol() {
        platform.stop()
        platform.persist(null)
        _isActive.value = false
        _sessionId.value = null
    }

    fun setAccuracy(accuracy: PatrolAccuracy) {
        if (_accuracy.value == accuracy) return
        _accuracy.value = accuracy
        if (_isActive.value) {
            platform.applyAccuracy(accuracy)
            _sessionId.value?.let { platform.persist(PersistedPatrol(it, accuracy)) }
        }
    }

    fun updateDistanceToCheckpoint(meters: Double?) {
        setAccuracy(PatrolAccuracy.forDistanceToCheckpoint(meters))
    }

    fun resumePatrolIfNeeded(): PatrolStartResult? {
        if (_isActive.value) return PatrolStartResult.Started
        val saved = platform.loadPersisted() ?: return null
        val result = startPatrol(saved.sessionId, saved.accuracy)
        if (result == PatrolStartResult.PermissionRequired) {
            // Permissão revogada com a ronda salva: não há como retomar sem a pessoa; a sessão sai.
            platform.persist(null)
            _interruption.value = PatrolInterruption.PERMISSION_REVOKED
        }
        return result
    }

    /** Leitura nova da plataforma. */
    fun onFix(fix: LocationFix) {
        if (!_isActive.value) return
        if (!shouldEmitFix(_lastFix.value, fix, _accuracy.value)) return
        _lastFix.value = fix
        _fixes.tryEmit(fix)
    }

    /** O serviço foi recriado pelo sistema com a sessão salva (Android START_STICKY). */
    fun onRestoredBySystem(saved: PersistedPatrol) {
        _sessionId.value = saved.sessionId
        _accuracy.value = saved.accuracy
        _interruption.value = null
        _isActive.value = true
    }

    /**
     * A plataforma parou sozinha. [keepForResume] mantém a sessão salva para
     * [resumePatrolIfNeeded] (morte de processo); permissão/GPS desligados descartam.
     */
    fun onInterrupted(reason: PatrolInterruption, keepForResume: Boolean) {
        platform.stop()
        if (!keepForResume) platform.persist(null)
        _interruption.value = reason
        _isActive.value = false
        if (!keepForResume) _sessionId.value = null
    }
}
