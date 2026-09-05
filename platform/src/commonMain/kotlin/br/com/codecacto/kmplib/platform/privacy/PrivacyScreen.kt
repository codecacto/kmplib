package br.com.codecacto.kmplib.platform.privacy

/**
 * **Esconde o conteúdo do app do seletor de aplicativos recentes** (e, no Android, também da
 * captura de tela e da gravação de tela).
 *
 * Existe para produto que carrega **dado pessoal sensível** — a lista de membros de um terreiro, o
 * processo de um cliente, o prontuário de um paciente, o extrato de alguém. Nesses casos, a
 * miniatura que o sistema guarda para desenhar o seletor de recentes é uma cópia da tela que
 * qualquer um que pegue o aparelho desbloqueado consegue ver, sem abrir o app.
 *
 * É a **primeira metade** do modo discreto; a segunda é o
 * `AppLockGate` (`kmplib-ui`, `br.com.codecacto.kmplib.ui.security`), que exige biometria para
 * voltar. As duas são independentes de propósito: um app pode querer só esconder da multitarefa.
 *
 * Prefira o composable [HideFromRecents], que liga e **desliga sozinho** ao sair da tela. Esta
 * interface é o caminho imperativo, para quem guarda a decisão numa preferência do usuário
 * ("modo discreto" ligado nas configurações) e a aplica no start do app.
 *
 * Padrão-ouro de cada plataforma:
 * - **Android:** `WindowManager.LayoutParams.FLAG_SECURE` na janela da `Activity` — a API oficial,
 *   e a única que o sistema respeita na hora de tirar a miniatura de recentes.
 *   ⚠️ **O mesmo flag bloqueia print e gravação de tela**, inclusive espelhamento. É efeito
 *   desejado num app de dado sensível, mas o usuário não é avisado pelo sistema: a captura
 *   simplesmente falha ("não foi possível capturar a tela"). Documente na sua tela de ajuste.
 * - **iOS:** não existe equivalente de `FLAG_SECURE` na API pública (o truque do `UITextField`
 *   seguro é uso indevido de API interna e quebra a cada versão). A forma correta é **cobrir a
 *   janela com um desfoque** em `applicationWillResignActive` — que é exatamente o instante em que
 *   o sistema tira a foto para o seletor de apps — e descobrir em `applicationDidBecomeActive`.
 *   Print continua sendo possível no iOS; só a multitarefa fica coberta.
 */
interface PrivacyScreen {

    /**
     * `false` quando a plataforma não oferece nenhuma forma de esconder a janela. Serve para a tela
     * de ajustes **não oferecer um interruptor que não faz nada** — hoje Android e iOS são `true`.
     */
    val isSupported: Boolean

    /** Estado atual, para a tela de ajustes refletir o que está valendo. */
    val isHidden: Boolean

    /**
     * Liga (`true`) ou desliga (`false`) a proteção. Idempotente: chamar duas vezes com o mesmo
     * valor não faz nada.
     *
     * No Android o flag é reaplicado sozinho quando a `Activity` é recriada (rotação, mudança de
     * tema do sistema) — desde que o app chame `kmpLibPlatformOnResume(activity)`, como já manda o
     * `kmplib-platform`. Sem essa chamada, girar o aparelho **derrubaria** a proteção em silêncio.
     */
    fun setHidden(hidden: Boolean)
}

/** Implementação da plataforma atual. É um **singleton**: o estado é da janela do app, não da tela. */
expect fun getPrivacyScreen(): PrivacyScreen
