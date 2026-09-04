package br.com.codecacto.kmplib.auth.social

import br.com.codecacto.kmplib.auth.SocialProvider
import br.com.codecacto.kmplib.core.util.currentPlatform

/**
 * Quais provedores sociais **esta plataforma** oferece — a verdade que decide se o botão existe.
 *
 * ## Por que existe
 * **Sign in with Apple não é oferecido no Android** (ver [AppleAuthProvider]), e até aqui essa
 * decisão vivia só em KDoc: o `AppleAuthProvider.android.kt` falhava com uma mensagem explícita e
 * pedia que "o app esconde o botão no Android". Pedir isso a cada app é pedir que 50 telas lembrem
 * da mesma regra — e a primeira que esquecer entrega ao usuário de Android um botão que **só sabe
 * dar erro**. Foi o que aconteceu no Backhand, numa tela de login própria: "Continuar com Apple"
 * desenhado num aparelho Android, sem nada no build, no lint ou no teste que acusasse.
 *
 * Agora a regra mora **na lib**, num lugar só, e as telas prontas ([LoginScreen][
 * br.com.codecacto.kmplib.auth.ui.LoginScreen] e [RegisterScreen][
 * br.com.codecacto.kmplib.auth.ui.RegisterScreen]) já a aplicam sozinhas: `AuthMethods(apple = true)`
 * passou a significar "ofereça a Apple **onde ela existe**", e não "desenhe o botão sempre".
 *
 * ## Tela própria (protótipo)
 * Projeto com layout próprio não passa pela `LoginScreen` — e é justamente ele que precisa desta
 * constante. O uso é direto:
 * ```kotlin
 * if (SocialProvider.APPLE.disponivelNestaPlataforma) {
 *     BotaoSocial(texto = "Continuar com Apple", ...)
 * }
 * ```
 *
 * ## E o modo BACKEND, que negocia pelo navegador?
 * No [SocialLoginMode.BACKEND] a conversa com a Apple é do **servidor**, então tecnicamente o
 * navegador do Android daria conta do fluxo web da Apple. Mesmo assim ele **não** entra aqui, e é
 * decisão de produto, não limitação: o padrão de mercado é não oferecer Apple no Android, o Google
 * já é o caminho natural ali, e um botão a mais custaria à fábrica um Services ID, um domínio
 * verificado e uma chave `.p8` para atender ninguém. Projeto que um dia precise disso pede
 * nominalmente — e aí a exceção é dele, não o default de todo mundo.
 */
val provedoresSociaisDaPlataforma: Set<SocialProvider>
    get() = provedoresSociaisPara(currentPlatform)

/**
 * A regra acima como **função pura**, para o teste não depender do alvo em que roda.
 *
 * @param plataforma o valor de [currentPlatform] (`"android"` ou `"ios"`).
 */
fun provedoresSociaisPara(plataforma: String): Set<SocialProvider> = when (plataforma) {
    "ios" -> setOf(SocialProvider.GOOGLE, SocialProvider.APPLE)
    // Android — e qualquer alvo novo que apareça. Google é o denominador comum de todas as
    // plataformas; a Apple é a exceção, e exceção se declara, não se presume.
    else -> setOf(SocialProvider.GOOGLE)
}

/** `true` quando este provedor pode ser oferecido no aparelho em que o app está rodando. */
val SocialProvider.disponivelNestaPlataforma: Boolean
    get() = this in provedoresSociaisDaPlataforma
