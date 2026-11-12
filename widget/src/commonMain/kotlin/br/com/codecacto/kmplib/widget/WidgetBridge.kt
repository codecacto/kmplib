@file:OptIn(kotlin.time.ExperimentalTime::class)

package br.com.codecacto.kmplib.widget

import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * O instantâneo de dados que o widget lê (2.355.0): chave → texto (JSON do app). O widget NÃO abre o
 * banco do app — lê só o que o app gravou aqui, que é a recomendação das duas plataformas (Glance roda
 * num processo/contexto próprio; a extensão do iOS é OUTRO processo e só enxerga o App Group).
 *
 * Grave os próximos dias de uma vez (ex.: `{"2026-11-11": {...}, "2026-11-12": {...}}`): assim o widget
 * vira o dia à meia-noite mesmo com o app fechado.
 */
interface WidgetDataStore {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
}

/**
 * O armazenamento do widget. Android: `SharedPreferences` "kmplib_widget" do app (o Glance lê com o
 * mesmo nome, ver [WidgetDataStores]); [appGroupId] é ignorado. iOS: `NSUserDefaults(suiteName: appGroupId)`
 * — **obrigatório** e igual ao App Group da extensão (`group.br.com.codecacto.<app>`), declarado nos dois
 * targets no Xcode.
 */
expect fun createWidgetDataStore(appGroupId: String? = null): WidgetDataStore

/**
 * Pedido de atualização do widget e a renovação diária (2.355.0).
 *
 * - **Android:** o app registra no `Application.onCreate` o que atualizar — `Widgets.registerRefresher {
 *   VersiculoWidget().updateAll(context) }` (Glance) — e a lib chama em [requestUpdate] e à meia-noite
 *   ([scheduleDailyRefresh], WorkManager, com o app fechado). O refresher roda também o recálculo do
 *   instantâneo, se o app quiser (é código do app).
 * - **iOS:** o `WidgetCenter.reloadAllTimelines()` não tem ponte para Objective-C: o Swift do app
 *   registra um [WidgetReloader] (`Widgets.shared.setReloader(…)`) e a lib o chama. A virada do dia no
 *   iOS é da *timeline* da extensão (política `.after(meia-noite)`), não daqui.
 */
expect object Widgets {
    /** Pede ao sistema que redesenhe os widgets do app agora (depois de gravar o instantâneo). */
    fun requestUpdate()

    /**
     * Liga/desliga a renovação diária logo depois da meia-noite local (Android: trabalho único
     * reagendado a cada execução; iOS: sem efeito — é a timeline da extensão).
     */
    fun scheduleDailyRefresh(enabled: Boolean)
}

/** O que o Swift do app implementa para recarregar os widgets (iOS). */
interface WidgetReloader {
    fun reloadAllTimelines()
}

/** O instante da próxima meia-noite local depois de [now] (+[marginSeconds]). Puro. */
fun nextMidnight(now: Instant = Clock.System.now(), timeZone: TimeZone = TimeZone.currentSystemDefault(), marginSeconds: Long = 60): Instant {
    val tomorrow = now.toLocalDateTime(timeZone).date.plus(DatePeriod(days = 1))
    return Instant.fromEpochSeconds(tomorrow.atStartOfDayIn(timeZone).epochSeconds + marginSeconds)
}

/** Nome das preferências do Android onde o widget lê (o Glance usa o mesmo). */
const val WIDGET_PREFERENCES_NAME: String = "kmplib_widget"
