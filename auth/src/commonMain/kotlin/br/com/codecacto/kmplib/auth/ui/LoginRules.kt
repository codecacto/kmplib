package br.com.codecacto.kmplib.ui.screens.login

/**
 * Rótulos **default** que a `backlib-auth-local` manda em `GET {authBasePath}/config` quando o
 * produto NÃO configurou um rótulo próprio (`AuthIdentifierPolicy.defaultLabel`). São texto fixo em
 * português — o servidor não sabe o idioma do aparelho.
 */
private val SERVER_DEFAULT_IDENTIFIER_LABELS: Set<String> = setOf(
    "e-mail", "email",
    "usuário", "usuario", "nome de usuário", "nome de usuario",
    "e-mail ou usuário", "e-mail ou usuario", "email ou usuário", "email ou usuario",
)

/**
 * O rótulo do campo de identificação: o do **servidor** só vence quando é um rótulo PRÓPRIO do
 * produto ("Matrícula", "Código do operador"); o default do servidor cede ao texto local, que tem
 * tradução (2.241.0).
 *
 * ## O defeito que isto fecha (Meu Estacionamento, 02/out/2026)
 *
 * A regra era `serverLabel.ifBlank { local }` — e o servidor **nunca** manda vazio: sem rótulo
 * configurado ele devolve o default do modo, em português. Resultado, em TODO app que repassa o
 * `identifierLabel` da configuração: aparelho em inglês mostrava "E-mail ou usuário" ao lado de
 * "Password / Forgot my password / Sign in". O texto local (`kmplib_identifier_label` e irmãos, nos
 * quatro idiomas) existia e nunca era usado.
 *
 * O rótulo próprio continua vencendo, e continua sem tradução: quem o escreveu foi o produto.
 *
 * @param serverLabel `LoginState.identifierLabel` (o que veio da configuração do servidor).
 * @param localLabel o texto local do modo em uso (`LoginTexts.emailLabel`/`usernameLabel`/
 *   `identifierLabel`), já no idioma da tela.
 */
fun resolveIdentifierLabel(serverLabel: String, localLabel: String): String {
    val normalized = serverLabel.trim()
    if (normalized.isEmpty()) return localLabel
    return if (normalized.lowercase() in SERVER_DEFAULT_IDENTIFIER_LABELS) localLabel else normalized
}

/**
 * Se a tela de login desenha a **marca do app** (ícone + nome, `AppBrandHeader`) por conta própria.
 *
 * Só quando o app não trouxe marca nenhuma — nem `logo`, nem título — e não há painel de marca ao
 * lado (janela expandida), que já é a marca da tela. Quem passa `logo` ou título continua com a
 * tela exatamente como era.
 */
internal fun loginShowsAppBrand(
    appBrand: Boolean,
    hasLogo: Boolean,
    hasTitle: Boolean,
    withBrandPanel: Boolean,
): Boolean = appBrand && !hasLogo && !hasTitle && !withBrandPanel
