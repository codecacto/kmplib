package br.com.codecacto.kmplib.ui.components

/**
 * Placeholders padrão (pt-BR) dos campos de formulário da lib — login, cadastro, contato, feedback
 * e endereço. Um lugar só, para a regra valer em todas as telas de uma vez.
 *
 * **Placeholder nunca é dado real** (regra da fábrica, 15/set/2026). O texto dentro do campo é
 * **instrução** ("Digite seu e-mail") ou **formato** ("(00) 00000-0000"), nunca um valor que alguém
 * poderia ter digitado — `João Silva`, `seu@email.com`, `(11) 98765-4321`, `123`. Valor plausível
 * dentro do campo é lido como campo **já preenchido**, e quem não percebe envia o formulário
 * achando que informou; o cinza do placeholder não desfaz essa leitura. Pelo mesmo motivo a senha
 * não leva `••••••••`: é exatamente o desenho de uma senha já digitada.
 *
 * App com i18n passa o próprio texto (`stringResource`) no parâmetro do componente/`*Texts`; estes
 * são só os defaults.
 */
object FormPlaceholders {
    const val NAME: String = "Digite seu nome completo"
    const val EMAIL: String = "Digite seu e-mail"
    const val EMAIL_OR_USERNAME: String = "Digite seu e-mail ou usuário"
    const val USERNAME: String = "Digite seu usuário"
    const val PASSWORD: String = "Digite sua senha"
    const val CONFIRM_PASSWORD: String = "Repita a senha"

    /** Telefone/WhatsApp: o FORMATO da máscara, com zeros — nunca um número que poderia existir. */
    const val PHONE: String = "(00) 00000-0000"

    /** Número do endereço. */
    const val ADDRESS_NUMBER: String = "Digite o número"
}
