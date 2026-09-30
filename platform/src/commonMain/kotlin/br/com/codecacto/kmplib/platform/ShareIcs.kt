package br.com.codecacto.kmplib.platform

/**
 * Compartilha um arquivo de agenda `.ics` (gerado por `IcsCalendar.build`, `kmplib-core`) pelo
 * share sheet nativo — Android `ACTION_SEND` com `text/calendar` via FileProvider; iOS
 * `UIActivityViewController`. Desde 2.228.0.
 *
 * É o "adicionar à agenda" que funciona em qualquer agenda: o Google Agenda, o Calendário da Apple e
 * o Outlook importam o arquivo; e-mail e mensageiros o levam adiante. Ver [ShareHandler.shareFile]
 * para o contrato de erro (a falha **propaga**).
 *
 * ```kotlin
 * val ics = IcsCalendar.build(evento, IcsCalendarOptions(calendarName = "Folha de Axé"))
 * getShareHandler().shareIcs(ics, fileName = "lua-nova.ics")
 * ```
 *
 * @param ics o texto do calendário (CRLF, como o `IcsCalendar` produz). Vai em UTF-8.
 * @param fileName nome do arquivo; ganha `.ics` se não terminar com ele. Default `"evento.ics"`.
 * @param title título do compartilhamento (chooser do Android).
 * @throws Exception se o compartilhamento não puder ser iniciado.
 */
fun ShareHandler.shareIcs(ics: String, fileName: String = "evento.ics", title: String = "") {
    require(ics.isNotBlank()) { "shareIcs: calendário vazio." }
    shareFile(ics.encodeToByteArray(), icsFileName(fileName), ICS_MIME_TYPE, title)
}

/** MIME do `.ics` (RFC 5545 §8.1). */
internal const val ICS_MIME_TYPE = "text/calendar"

/** Garante a extensão `.ics` (é por ela que a agenda do iOS/Android reconhece o anexo). */
internal fun icsFileName(fileName: String): String {
    val name = fileName.trim().ifEmpty { "evento.ics" }
    return if (name.endsWith(".ics", ignoreCase = true)) name else "$name.ics"
}
