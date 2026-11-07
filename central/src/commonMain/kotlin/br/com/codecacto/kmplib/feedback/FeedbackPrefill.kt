package br.com.codecacto.kmplib.feedback

/**
 * Regras puras do pré-preenchimento da [br.com.codecacto.kmplib.ui.screens.feedback.FeedbackScreen]
 * (2.341.0) — isoladas da tela para serem testadas em `commonTest`.
 *
 * Par de `contactInitialMessage` da `ContactScreen`: o app que abre o feedback a partir de um ponto
 * específico (ex.: o cartão "Contrato personalizado — R$ 30" do LocAki) faz o pedido chegar
 * IDENTIFICADO, e a pessoa continua podendo trocar o motivo e editar o texto.
 */

/**
 * Mensagem com que a tela nasce. **Não é aparada**: um texto inicial como `"Contrato personalizado:\n\n"`
 * termina em quebra de linha de propósito, para a pessoa continuar escrevendo embaixo. O envio apara.
 * Só espaço em branco conta como ausente (devolve vazio), para o botão não parecer pronto com nada escrito.
 */
internal fun feedbackInitialMessage(initialMessage: String?): String =
    if (initialMessage.isNullOrBlank()) "" else initialMessage

/**
 * Decide se um valor inicial que chegou DEPOIS da primeira composição (perfil lido de forma
 * assíncrona) deve ocupar o campo: só uma vez por campo ([alreadySeeded]), só se o valor existe
 * ([hasIncoming]) e só se a pessoa ainda não preencheu nada ([fieldIsEmpty]) — reaplicar por cima do
 * que ela escreveu ou escolheu apagaria a resposta dela.
 */
internal fun shouldSeedFeedbackField(alreadySeeded: Boolean, hasIncoming: Boolean, fieldIsEmpty: Boolean): Boolean =
    !alreadySeeded && hasIncoming && fieldIsEmpty

/**
 * Valor com que um campo de contato (nome, e-mail, WhatsApp) nasce (2.349.0): o que o APP passou à
 * tela ([screenValue] — `defaultName`/`defaultEmail`/`defaultWhatsapp`) vence; sem ele, o usuário
 * logado que o [FeedbackService] já conhece ([serviceValue], de `updateUser`). Em branco = ausente.
 */
internal fun feedbackPrefillValue(screenValue: String?, serviceValue: String?): String? =
    screenValue?.takeIf { it.isNotBlank() } ?: serviceValue?.takeIf { it.isNotBlank() }
