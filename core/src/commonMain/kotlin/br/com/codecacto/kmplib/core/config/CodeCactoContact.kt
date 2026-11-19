package br.com.codecacto.kmplib.core.config

/**
 * Os contatos públicos da CodeCacto, num lugar só (2.375.0) — antes o número do WhatsApp estava
 * repetido em `SubscriptionBlockDefaults` (monetization) e no `DeveloperContact` (central).
 */
object CodeCactoContact {
    /** O WhatsApp de suporte da CodeCacto (65 99803-0240), em E.164 sem o `+`. */
    const val WHATSAPP_PHONE: String = "5565998030240"
}
