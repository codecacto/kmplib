package br.com.codecacto.kmplib.location.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * # Localização contínua durante uma sessão (ronda) — 2.308.0
 *
 * ```kotlin
 * // Android: Application.onCreate()
 * initKmpLibLocationSession(this)
 * // iOS: na inicialização (retoma a ronda que estava ativa se o sistema relançou o app)
 * initKmpLibLocationSession()
 *
 * // Tela da ronda (app NA FRENTE — o Android não deixa iniciar serviço de localização do 2º plano)
 * when (PatrolLocationSession.startPatrol(sessionId = ronda.id)) {
 *     PatrolStartResult.Started -> Unit
 *     PatrolStartResult.PermissionRequired -> pedirPermissao()
 *     else -> mostrarErro()
 * }
 * PatrolLocationSession.fixes.collect { fix -> viewModel.onFix(fix) }
 * PatrolLocationSession.updateDistanceToCheckpoint(metrosAtePontoMaisProximo) // ≤ 200 m → alta precisão
 * PatrolLocationSession.stopPatrol()
 * ```
 *
 * - **Android**: Foreground Service `foregroundServiceType="location"` (notificação fixa "Ronda em
 *   andamento") + Fused Location Provider; prioridade adaptativa ([PatrolAccuracy]).
 * - **iOS**: `CLLocationManager` com `allowsBackgroundLocationUpdates`,
 *   `pausesLocationUpdatesAutomatically = false`, `CLBackgroundActivitySession` (iOS 17+) e
 *   `CLServiceSession` exigindo "ao usar o app" (iOS 18+). Info.plist do app:
 *   `NSLocationWhenInUseUsageDescription`, `NSLocationAlwaysAndWhenInUseUsageDescription` e
 *   `UIBackgroundModes` = `location`.
 *
 * A permissão é do app (`PermissionManager`/launcher): esta API só confere e recusa com
 * [PatrolStartResult.PermissionRequired]. Chame da main thread.
 */
object PatrolLocationSession {

    private val controller = PatrolSessionController(platformPatrolPort())

    /** As leituras da sessão ativa, já espaçadas pelo intervalo do modo ([PatrolAccuracy]). */
    val fixes: Flow<LocationFix> get() = controller.fixes

    /** A última leitura entregue da sessão atual (`null` no começo de cada sessão). */
    val lastFix: StateFlow<LocationFix?> get() = controller.lastFix

    /** Há uma sessão ativa? */
    val isPatrolActive: StateFlow<Boolean> get() = controller.isPatrolActive

    /** O id da sessão ativa (`null` sem sessão). */
    val activeSessionId: StateFlow<String?> get() = controller.activeSessionId

    /** O modo de precisão em vigor. */
    val accuracy: StateFlow<PatrolAccuracy> get() = controller.accuracy

    /** Por que a última sessão parou sozinha (`null` se não parou, ou se foi pelo `stopPatrol`). */
    val interruption: StateFlow<PatrolInterruption?> get() = controller.interruption

    /**
     * Inicia (ou continua) a sessão [sessionId]. Idempotente: com a mesma sessão já ativa devolve
     * [PatrolStartResult.Started]; com outra, troca o id sem desligar o GPS.
     */
    fun startPatrol(sessionId: String, accuracy: PatrolAccuracy = this.accuracy.value): PatrolStartResult =
        controller.startPatrol(sessionId, accuracy)

    /** Encerra a sessão: para o GPS, remove a notificação (Android) e apaga a sessão salva. */
    fun stopPatrol() = controller.stopPatrol()

    /** Troca o modo de precisão (vale na hora, com a sessão ativa). */
    fun setAccuracy(accuracy: PatrolAccuracy) = controller.setAccuracy(accuracy)

    /**
     * Ajusta a precisão pela distância ao ponto de ronda mais próximo: ≤ 200 m → [PatrolAccuracy.HIGH];
     * mais longe ou `null` → [PatrolAccuracy.BALANCED].
     */
    fun updateDistanceToCheckpoint(meters: Double?) = controller.updateDistanceToCheckpoint(meters)

    /**
     * Retoma a sessão que ficou salva quando o processo morreu e o sistema não pôde retomar sozinho
     * (ver [PatrolInterruption.STOPPED_BY_SYSTEM]). Chame no `ON_RESUME` da tela raiz. `null` = nada a
     * retomar.
     */
    fun resumePatrolIfNeeded(): PatrolStartResult? = controller.resumePatrolIfNeeded()

    internal val internalController: PatrolSessionController get() = controller
}

/** A ponte da plataforma (Android: serviço + Fused; iOS: Core Location). */
internal expect fun platformPatrolPort(): PatrolPlatformPort
