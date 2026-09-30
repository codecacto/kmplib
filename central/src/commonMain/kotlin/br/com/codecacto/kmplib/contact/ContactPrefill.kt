package br.com.codecacto.kmplib.contact

/**
 * Regras puras do pré-preenchimento da [br.com.codecacto.kmplib.ui.screens.developer.ContactScreen]
 * (2.226.0) — isoladas da tela para serem testadas em `commonTest`.
 */

/**
 * Normaliza a lista de assuntos escolhíveis: apara, descarta vazios e repetidos, mantendo a ordem
 * dada pelo app. Devolve `null` quando não sobra nenhum — aí a tela mostra o campo de texto livre de
 * sempre, em vez de um seletor vazio que ninguém consegue usar.
 *
 * Se o [initialSubject] não estiver na lista, ele entra **no topo**: o app pediu para a tela abrir
 * com aquele assunto, e um seletor que não o contém o apagaria em silêncio (o campo mostraria o
 * placeholder, e o envio sairia sem assunto).
 */
internal fun contactSubjectOptions(subjects: List<String>?, initialSubject: String?): List<String>? {
    val options = subjects.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (options.isEmpty()) return null
    val initial = initialSubject?.trim().orEmpty()
    return if (initial.isEmpty() || initial in options) options else listOf(initial) + options
}

/** Assunto com que a tela nasce: o [initialSubject] aparado, ou vazio. */
internal fun contactInitialSubject(initialSubject: String?): String = initialSubject?.trim().orEmpty()

/**
 * Mensagem com que a tela nasce. **Não é aparada**: um texto inicial como `"Cargo: …\n\n"` termina
 * em quebra de linha de propósito, para a pessoa continuar escrevendo embaixo. O envio apara.
 */
internal fun contactInitialMessage(initialMessage: String?): String = initialMessage.orEmpty()
