package br.com.codecacto.kmplib.monetization.quota

import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Espelho local **dia-aware** de uma cota consumível (ADR-001 §1/§3), persistido em [AppPreferences].
 *
 * > **Não é fonte de verdade.** Quando o app tem backend, a autoridade do saldo/limite e do reset é o
 * > admin-api (`backlib-quota`). Este store é cache para (a) exibir "X de Y hoje" e (b) permitir o
 * > uso offline **até o teto Free** (fail-open LIMITADO — nunca infinito). Em app 100% offline ele é
 * > o gate efetivo, com a ressalva conhecida de ser burlável (a receita real está no RevenueCat).
 *
 * Persiste três valores, chaveados por `projectSlug` + `feature`:
 * - **dia local** (ISO `yyyy-MM-dd` no fuso do device) da contagem corrente — ao virar o dia, zera
 *   contagem e fila offline (reset local **preguiçoso**, feito em toda leitura/escrita; o reset
 *   autoritativo continua vindo do servidor);
 * - **contagem** de consumos de hoje;
 * - **fila offline**: quantos desses consumos ainda não foram confirmados pelo servidor (a reconciliar
 *   ao reconectar — ADR-001 §4).
 *
 * [nowMillis] e [timeZone] são injetáveis para teste (rollover de meia-noite).
 *
 * Promovido de `MundoBandeiras/features/monetization/DailyQuotaStore.kt` (a variante mais completa das
 * 4 cópias), generalizado por `feature`.
 */
class DailyQuotaStore(
    private val prefs: AppPreferences,
    private val projectSlug: String,
    private val feature: String,
    private val nowMillis: () -> Long = ::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private val dayKey = "kmplib_quota_day:$projectSlug:$feature"
    private val countKey = "kmplib_quota_count:$projectSlug:$feature"
    private val pendingKey = "kmplib_quota_pending:$projectSlug:$feature"

    /** Data local de hoje (ISO `yyyy-MM-dd`) no fuso do device. */
    private fun today(): String =
        Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(timeZone).date.toString()

    /**
     * Garante que a contagem corrente é do dia de hoje. Se o dia armazenado difere, zera contagem +
     * fila offline e grava o dia atual. Idempotente no mesmo dia.
     */
    private suspend fun rollOverIfNeeded() {
        val storedDay = prefs.getString(dayKey, "")
        val today = today()
        if (storedDay != today) {
            prefs.setString(dayKey, today)
            prefs.setInt(countKey, 0)
            prefs.setInt(pendingKey, 0)
        }
    }

    /** Contagem local de consumos de hoje (após rollover de dia). */
    suspend fun count(): Int {
        rollOverIfNeeded()
        return prefs.getInt(countKey, 0)
    }

    /** Quantos consumos de hoje aguardam reconciliação com o servidor (feitos offline). */
    suspend fun pendingOffline(): Int {
        rollOverIfNeeded()
        return prefs.getInt(pendingKey, 0)
    }

    /**
     * Incrementa a contagem local em [amount] após consumir. Se [offline] = `true`, também incrementa
     * a fila a reconciliar. Retorna a nova contagem do dia.
     */
    suspend fun increment(offline: Boolean, amount: Int = 1): Int {
        rollOverIfNeeded()
        val next = prefs.getInt(countKey, 0) + amount
        prefs.setInt(countKey, next)
        if (offline) prefs.setInt(pendingKey, prefs.getInt(pendingKey, 0) + amount)
        return next
    }

    /**
     * Sobrepõe a contagem local com o saldo decidido pelo servidor (a verdade do servidor prevalece —
     * ADR-001 §2/§4) e zera a fila offline. [serverCount] é coerced para `>= 0`.
     */
    suspend fun overrideWithServer(serverCount: Int) {
        rollOverIfNeeded()
        prefs.setInt(countKey, serverCount.coerceAtLeast(0))
        prefs.setInt(pendingKey, 0)
    }

    /** Zera a fila offline sem mexer na contagem (reconciliação confirmada sem novo saldo). */
    suspend fun clearPending() {
        prefs.setInt(pendingKey, 0)
    }
}
