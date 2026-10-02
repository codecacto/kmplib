package br.com.codecacto.kmplib.platform.automation

import androidx.compose.ui.Modifier

/**
 * A mesma flag do `WithTestTagsAsResourceId` (kmplib-ui), como **modificador** — para ligar no nó-raiz de uma
 * **janela própria** (diálogo, folha inferior, menu suspenso, popup) sem acrescentar um nó de layout.
 *
 * ## Por que janela própria precisa disto (2.234.0)
 *
 * No Android, `Dialog`, `AlertDialog`, `ModalBottomSheet`, `DropdownMenu`, `Popup`,
 * `DatePickerDialog` e `TimePickerDialog` abrem **outra janela** — outro `AndroidComposeView`, com
 * árvore de semântica própria. A flag que o `AppTheme` liga na raiz do app **não atravessa** para
 * ela: na hierarquia, todo nó do diálogo sai com `resource-id=""`, e o Maestro não toca em nada
 * dentro dele (teste do Mac, Palpite Certo, 02/out/2026 — o "digite EXCLUIR" da exclusão de conta
 * ficou inalcançável).
 *
 * Todos os diálogos, folhas e menus da kmplib já aplicam isto. **App que abre a própria janela**
 * (um `Dialog {}` ou `ModalBottomSheet {}` escrito na tela) aplica no `modifier` do nó-raiz dela:
 * ```kotlin
 * ModalBottomSheet(onDismissRequest = …, modifier = Modifier.exposeTestTagsAsResourceId()) { … }
 * Dialog(onDismissRequest = …) { Surface(Modifier.exposeTestTagsAsResourceId()) { … } }
 * ```
 * No iOS devolve o próprio modificador — a `testTag` já vira `accessibilityIdentifier` em qualquer
 * janela.
 */
expect fun Modifier.exposeTestTagsAsResourceId(): Modifier
