package br.com.codecacto.kmplib.feedback

/**
 * Por qual canal a [br.com.codecacto.kmplib.ui.screens.feedback.FeedbackScreen] garante o retorno à
 * pessoa (2.349.0) — o contato OBRIGATÓRIO do formulário.
 *
 * Segue o ALCANCE do app nas lojas (`nexus.projects.store_reach`, espelhado no `loja.yaml` como
 * `alcance_loja:`): no Brasil quase todo mundo tem WhatsApp, fora dele não — lá o canal que todo mundo
 * tem é o e-mail. Declarado UMA vez, na inicialização ([FeedbackConfig.contactPolicy] ou
 * `CentralServicesConfig.feedbackContactPolicy`); a tela lê de lá.
 *
 * - [WHATSAPP_REQUIRED] — app `BR` (o comportamento de sempre): WhatsApp obrigatório, e-mail opcional.
 * - [EMAIL_REQUIRED] — app `GLOBAL`: e-mail obrigatório e PRIMEIRO no bloco de contato, WhatsApp
 *   opcional (se preenchido, precisa estar completo). Textos da tela e da confirmação falam em e-mail.
 */
enum class FeedbackContactPolicy {
    WHATSAPP_REQUIRED,
    EMAIL_REQUIRED;

    companion object {
        /**
         * A política do alcance cadastrado: `"GLOBAL"` (sem diferenciar caixa) → [EMAIL_REQUIRED];
         * qualquer outra coisa — `"BR"`, vazio, `null` — → [WHATSAPP_REQUIRED], o comportamento que
         * todo app já tinha (quem não declara não muda).
         */
        fun forStoreReach(storeReach: String?): FeedbackContactPolicy =
            if (storeReach?.trim().equals("GLOBAL", ignoreCase = true)) EMAIL_REQUIRED else WHATSAPP_REQUIRED
    }
}
