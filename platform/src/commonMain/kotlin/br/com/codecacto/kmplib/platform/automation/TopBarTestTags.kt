package br.com.codecacto.kmplib.platform.automation

/**
 * **Ids da barra superior da lib para automação de UI** (2.237.0) — mesmo desenho de
 * [DialogTestTags]: quem desenha o título é a lib (`AppTopBar` e as que se montam sobre ela:
 * `BackTopBar`, `MenuTopBar`, `SimpleTopBar`), então um id plantado no app não alcançaria o texto.
 *
 * ## Para que serve: fechar o teclado no iOS
 *
 * O `hideKeyboard` do Maestro, no iOS, não tem API do sistema para chamar: ele dá **swipes rápidos
 * no meio da tela** e torce para o layout baixar o teclado. Em tela Compose isso falha
 * ("Hide Keyboard... FAILED") — caso real: Minha Voz, teste do agente de 02/out/2026. A saída que a
 * documentação oficial do Maestro indica é **tocar num elemento NÃO interativo** (título, cabeçalho).
 * O título da barra é exatamente isso, existe em quase toda tela e — com
 * `Modifier.dismissKeyboardOnTapOutside()` na raiz do app — o toque nele baixa o teclado nas duas
 * plataformas. O padrão do flow (Android segue com `hideKeyboard`, que lá é o "voltar" e é confiável):
 *
 * ```yaml
 * - runFlow:
 *     when: { platform: Android }
 *     commands:
 *       - hideKeyboard
 * - runFlow:
 *     when: { platform: iOS }
 *     commands:
 *       - tapOn: { id: "topbar-titulo" }
 * ```
 *
 * Um título só por tela: a barra da tela de baixo na pilha não está composta. Durante a animação de
 * transição duas podem coexistir — espere a tela nova (`extendedWaitUntil`) antes de tocar.
 *
 * ## Voltar e menu: o passo PORTÁVEL (2.244.0)
 *
 * O `- back` do Maestro é o botão Voltar do **Android** — no iOS não existe, e o comando passa sem
 * fazer nada: o flow segue na mesma tela e quebra no passo seguinte esperando a de baixo (caso real:
 * Minha OS, `funcionalidades/02-cadastros`, 02/out/2026 — `home-tela is visible` falso no iPhone com o
 * Android verde). O ícone de voltar da barra é desenhado pela lib, então o id mora aqui:
 * `tapOn: { id: "topbar-voltar" }` funciona nas duas plataformas, sem ramo de plataforma.
 * [MENU] é o mesmo para o hambúrguer da `MenuTopBar`.
 */
object TopBarTestTags {

    /** O texto do título da barra superior (`AppTopBar` e derivadas). Nunca é clicável. */
    const val TITULO: String = "topbar-titulo"

    /** O botão de voltar da barra (`BackTopBar` / `NavigationType.BACK`). Substitui o `- back` do Maestro. */
    const val VOLTAR: String = "topbar-voltar"

    /** O botão de menu da barra (`MenuTopBar` / `NavigationType.MENU`). */
    const val MENU: String = "topbar-menu"
}
