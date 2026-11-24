package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_unsaved_discard
import br.com.codecacto.kmplib.generated.resources.kmplib_unsaved_keep_editing
import br.com.codecacto.kmplib.generated.resources.kmplib_unsaved_message
import br.com.codecacto.kmplib.generated.resources.kmplib_unsaved_title
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Guarda de formulário não salvo (2.379.0) — padrão da fábrica de 24/nov/2026.
//
// Todo formulário de cadastro/edição do app que tem algo preenchido e não salvo, quando a pessoa
// tenta sair (voltar do sistema — gesto/botão do Android, swipe do iOS —, seta do top bar, Cancelar,
// trocar de aba/menu, fechar o diálogo ou a folha), pergunta antes: "Descartar o que foi preenchido?".
// Vazio ou já salvo: sai direto. Depois de salvar com sucesso, a saída não pergunta.
//
// Três peças:
//   • [UnsavedChangesGuardState] — a regra (pura, testada): `requestExit(onExit)` sai direto ou abre
//     a confirmação; `confirmDiscard()`/`cancelDiscard()`; `markSaved()`.
//   • [UnsavedChangesGuard] — intercepta o VOLTAR DO SISTEMA (o `BackHandler` multiplataforma do
//     Compose: Android pelo `OnBackPressedDispatcher`, iOS pelo gesto de borda do
//     `ComposeUIViewController`) e desenha o diálogo de confirmação da lib.
//   • Diálogo e folha: [UnsavedChangesGuardState.dismissRequest] no `onDismissRequest` do diálogo, e
//     [rememberUnsavedChangesSheetState] como `sheetState` do `ModalBottomSheet` (barra arrastar,
//     toque fora e voltar pelo `confirmValueChange` oficial do `SheetState`).
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Estado da guarda de formulário não salvo. Crie na tela com [rememberUnsavedChangesGuard]; o
 * construtor é público para teste (de lib e de app).
 *
 * **`dirty` = diferente do último salvo**, não "tem algo preenchido": compare o rascunho com o que
 * veio do servidor (edição) ou com o formulário vazio (cadastro). Assim, salvar sem sair da tela já
 * zera a guarda sozinho.
 *
 * @param dirtySource lido a cada decisão (a tela passa o valor atual; não fica foto antiga).
 */
@Stable
class UnsavedChangesGuardState(private val dirtySource: () -> Boolean) {

    /** Liberou a saída: salvou ([markSaved]) ou descartou ([confirmDiscard]). Volta a guardar quando o `dirty` muda. */
    private var released by mutableStateOf(false)

    /** A saída que espera a resposta da pessoa; `null` = sem confirmação aberta. */
    private var pendingExit by mutableStateOf<(() -> Unit)?>(null)

    /** Há algo não salvo que a saída perderia (e a guarda não foi liberada). */
    val isDirty: Boolean get() = dirtySource() && !released

    /** A confirmação "Descartar o que foi preenchido?" está aberta. */
    val isConfirming: Boolean get() = pendingExit != null

    /**
     * Pede para sair por [onExit] — seta do top bar, Cancelar, troca de aba, fechar o diálogo.
     * Sem nada a perder, chama [onExit] na hora; com, abre a confirmação e só chama [onExit] se a
     * pessoa escolher "Descartar". Pedido repetido com a confirmação aberta troca a saída pendente
     * (vale a última).
     */
    fun requestExit(onExit: () -> Unit) {
        if (!isDirty) {
            onExit()
        } else {
            pendingExit = onExit
        }
    }

    /** "Descartar": fecha a confirmação, libera a guarda e executa a saída pendente. */
    fun confirmDiscard() {
        val exit = pendingExit ?: return
        pendingExit = null
        released = true
        exit()
    }

    /** "Continuar editando" (ou fechar a confirmação): nada acontece, o formulário continua. */
    fun cancelDiscard() {
        pendingExit = null
    }

    /**
     * Salvou com sucesso: a próxima saída não pergunta. Chame ANTES de navegar depois do salvar
     * (o `dirty` da tela pode ainda não ter recomposto). Volta a guardar quando o `dirty` muda.
     */
    fun markSaved() {
        pendingExit = null
        released = true
    }

    /** O `dirty` da tela mudou: a liberação anterior deixa de valer. */
    internal fun onDirtyChanged() {
        released = false
    }
}

/**
 * Cria o [UnsavedChangesGuardState] da tela. Passe [dirty] = "o rascunho difere do último salvo".
 */
@Composable
fun rememberUnsavedChangesGuard(dirty: Boolean): UnsavedChangesGuardState {
    val current: State<Boolean> = rememberUpdatedState(dirty)
    val state = remember { UnsavedChangesGuardState { current.value } }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(dirty) {
        if (first) first = false else state.onDirtyChanged()
    }
    return state
}

/**
 * Textos da confirmação. Defaults literais em pt-BR (teste/preview); na tela use
 * [rememberUnsavedChangesTexts], que segue o idioma do app (pt-BR / pt-PT / en / es).
 */
@Immutable
data class UnsavedChangesTexts(
    val title: String = "Descartar o que foi preenchido?",
    val message: String = "Se sair agora, o que você preencheu será perdido.",
    val keepEditing: String = "Continuar editando",
    val discard: String = "Descartar",
)

/** [UnsavedChangesTexts] no idioma do app. */
@Composable
fun rememberUnsavedChangesTexts(): UnsavedChangesTexts = UnsavedChangesTexts(
    title = kmpStringResource(Res.string.kmplib_unsaved_title),
    message = kmpStringResource(Res.string.kmplib_unsaved_message),
    keepEditing = kmpStringResource(Res.string.kmplib_unsaved_keep_editing),
    discard = kmpStringResource(Res.string.kmplib_unsaved_discard),
)

/** Ids para os flows Maestro (os botões usam os de [br.com.codecacto.kmplib.platform.automation.DialogTestTags]). */
object UnsavedChangesTestTags {
    /** O diálogo "Descartar o que foi preenchido?". "Descartar" = `dialogo-btn-confirmar`; "Continuar editando" = `dialogo-btn-cancelar`. */
    const val DIALOGO: String = "formulario-descartar"
}

/**
 * Liga a guarda na tela: intercepta o voltar do sistema enquanto há algo não salvo e desenha a
 * confirmação. Componha uma vez, em qualquer ponto da tela do formulário.
 *
 * @param state de [rememberUnsavedChangesGuard].
 * @param onBack o que o voltar do sistema faz depois de confirmado (normalmente
 *   `navController.popBackStack()` — o mesmo da seta do top bar). Sem nada a perder, o voltar do
 *   sistema nem passa por aqui: o `BackHandler` fica desligado e a navegação segue normal.
 * @param interceptSystemBack `false` dentro de um diálogo/folha: lá o voltar já chega pelo
 *   `onDismissRequest` ([UnsavedChangesGuardState.dismissRequest]) ou pelo
 *   [rememberUnsavedChangesSheetState].
 * @param texts textos da confirmação.
 */
@Composable
fun UnsavedChangesGuard(
    state: UnsavedChangesGuardState,
    onBack: () -> Unit,
    interceptSystemBack: Boolean = true,
    texts: UnsavedChangesTexts = rememberUnsavedChangesTexts(),
) {
    val currentOnBack by rememberUpdatedState(onBack)
    if (interceptSystemBack) {
        UnsavedChangesBackHandler(
            enabled = state.isDirty && !state.isConfirming,
            onBack = { state.requestExit { currentOnBack() } },
        )
    }
    ConfirmationDialog(
        show = state.isConfirming,
        title = texts.title,
        message = texts.message,
        confirmText = texts.discard,
        cancelText = texts.keepEditing,
        onConfirm = state::confirmDiscard,
        onDismiss = state::cancelDiscard,
        primaryColor = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag(UnsavedChangesTestTags.DIALOGO),
    )
}

/**
 * `onDismissRequest` de um diálogo de formulário (toque fora, voltar, X, Cancelar): pergunta antes
 * quando há algo não salvo; senão chama [onDismiss] na hora.
 *
 * ```kotlin
 * AppDialog(onDismissRequest = guard.dismissRequest(onClose)) { … }
 * ```
 */
fun UnsavedChangesGuardState.dismissRequest(onDismiss: () -> Unit): () -> Unit = { requestExit(onDismiss) }

/**
 * `SheetState` de um `ModalBottomSheet`/`AppModalBottomSheet` de formulário: arrastar para baixo,
 * tocar fora e voltar do sistema passam pelo `confirmValueChange` e, com algo não salvo, a folha
 * fica aberta e a confirmação aparece. "Descartar" anima a folha para baixo e então chama
 * [onDismiss] (o mesmo que o app passa em `onDismissRequest`).
 *
 * Para Cancelar/X dentro da folha: `guard.requestExit { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberUnsavedChangesSheetState(
    guard: UnsavedChangesGuardState,
    onDismiss: () -> Unit,
    skipPartiallyExpanded: Boolean = true,
): SheetState {
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val holder = remember { arrayOfNulls<SheetState>(1) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
        confirmValueChange = { value ->
            sheetValueChangeAllowed(value, guard) {
                val s = holder[0]
                if (s == null) {
                    currentOnDismiss()
                } else {
                    scope.launch { s.hide() }.invokeOnCompletion { currentOnDismiss() }
                }
            }
        },
    )
    holder[0] = sheetState
    return sheetState
}

/** Regra do `confirmValueChange`: só o Hidden com algo não salvo é segurado (e vira pedido de saída). */
@OptIn(ExperimentalMaterial3Api::class)
internal fun sheetValueChangeAllowed(
    value: SheetValue,
    guard: UnsavedChangesGuardState,
    exit: () -> Unit,
): Boolean {
    if (value != SheetValue.Hidden || !guard.isDirty) return true
    guard.requestExit(exit)
    return false
}

/**
 * `@Suppress("DEPRECATION")`: o `BackHandler` multiplataforma está marcado em favor do
 * `NavigationEventHandler`, e o `navigationevent-compose` não publica variante Kotlin/Native —
 * mesmo motivo do `FullScreenNotice` e do tour.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
private fun UnsavedChangesBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
