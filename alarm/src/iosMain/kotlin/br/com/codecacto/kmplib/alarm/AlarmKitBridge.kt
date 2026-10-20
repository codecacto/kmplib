@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package br.com.codecacto.kmplib.alarm

import kotlin.concurrent.AtomicReference
import kotlin.native.ObjCName

/**
 * Ponte para o **AlarmKit** (iOS 26+), implementada pelo APP em Swift.
 *
 * ### Por que uma ponte, e não interop direto
 * O AlarmKit é um framework **só Swift** (`AlarmManager`, `AlarmAttributes`, `Alarm.Schedule` são
 * structs/actors Swift, sem cabeçalho Objective-C): o cinterop do Kotlin/Native não o enxerga — o
 * klib `platform.AlarmKit` traz só o número de versão. "Reflexão" também não existe para Swift puro.
 * O caminho oficial é o mesmo do `GoogleSignInBridge`/`ApplePushBridge`: o app escreve ~40 linhas
 * Swift e registra com [AlarmKitBridgeRegistry.register]. Sem ponte registrada (ou iOS < 26), o
 * início de ronda vai por notificação **Time Sensitive**.
 *
 * ```swift
 * import AlarmKit
 * import ComposeApp
 *
 * @available(iOS 26.0, *)
 * final class RondaAlarmKit: NSObject, AlarmKitBridge {
 *     func isAuthorized() -> Bool { AlarmManager.shared.authorizationState == .authorized }
 *     func requestAuthorization() {
 *         Task { _ = try? await AlarmManager.shared.requestAuthorization() }
 *     }
 *     func schedule(alarmId: Int32, triggerAtEpochMillis: Int64, label: String) -> Bool {
 *         guard isAuthorized() else { return false }
 *         let date = Date(timeIntervalSince1970: Double(triggerAtEpochMillis) / 1000)
 *         let alert = AlarmPresentation.Alert(title: LocalizedStringResource(stringLiteral: label),
 *                                             stopButton: .stopButton)
 *         let attrs = AlarmAttributes<RondaMetadata>(presentation: AlarmPresentation(alert: alert),
 *                                                    tintColor: .accentColor)
 *         let id = UUID.kmplibAlarm(alarmId)   // UUID determinístico a partir do Int (UUID v5 do app)
 *         Task {
 *             _ = try? await AlarmManager.shared.schedule(
 *                 id: id, configuration: .alarm(schedule: .fixed(date), attributes: attrs))
 *         }
 *         return true
 *     }
 *     func cancel(alarmId: Int32) { try? AlarmManager.shared.cancel(id: UUID.kmplibAlarm(alarmId)) }
 * }
 *
 * // Na inicialização (depois do initKmpLib…):
 * if #available(iOS 26.0, *) { AlarmKitBridgeRegistry.shared.register(bridge: RondaAlarmKit()) }
 * ```
 *
 * Info.plist: `NSAlarmKitUsageDescription`. O id é `Int` aqui e `UUID` no AlarmKit — o app mapeia de
 * forma DETERMINÍSTICA (mesmo Int → mesmo UUID), senão `cancel` não acha o alarme.
 */
@ObjCName("AlarmKitBridge")
interface AlarmKitBridge {
    /** O app tem autorização do AlarmKit (`AlarmManager.shared.authorizationState == .authorized`). */
    fun isAuthorized(): Boolean

    /** Pede a autorização do AlarmKit (o sistema mostra o diálogo uma vez). */
    fun requestAuthorization()

    /** Agenda o alarme. `false` = não agendou (sem autorização) — a lib cai na notificação. */
    fun schedule(alarmId: Int, triggerAtEpochMillis: Long, label: String): Boolean

    /** Cancela o alarme [alarmId]. */
    fun cancel(alarmId: Int)
}

/** Onde o app registra a [AlarmKitBridge]. */
@ObjCName("AlarmKitBridgeRegistry")
object AlarmKitBridgeRegistry {
    private val current = AtomicReference<AlarmKitBridge?>(null)

    /** Registra (ou troca) a ponte. Chame só em iOS 26+ (`if #available(iOS 26.0, *)`). */
    fun register(bridge: AlarmKitBridge) {
        current.value = bridge
    }

    /** Remove a ponte (o início de ronda volta para a notificação Time Sensitive). */
    fun unregister() {
        current.value = null
    }

    internal val bridge: AlarmKitBridge? get() = current.value
}
