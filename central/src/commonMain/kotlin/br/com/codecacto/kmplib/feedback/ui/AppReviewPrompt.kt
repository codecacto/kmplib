package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.feedback.AppReviewService
import br.com.codecacto.kmplib.platform.AppReviewManager
import br.com.codecacto.kmplib.platform.ReviewPromptOutcome

/**
 * O pedido de avaliação de uma tela (2.335.0) — devolvido por [rememberAppReviewPrompt] e desenhado
 * pelo [AppReviewPromptHost]. Liga o [AppReviewManager] (regra do Nexus), o [AppReviewDialog] e a
 * estatística: o app só diz QUANDO um fluxo relevante terminou.
 */
@Stable
class AppReviewPromptState internal constructor(
    /** O manager por trás (contador, regra, config). */
    val manager: AppReviewManager,
) {
    /** O diálogo está pedido? */
    var isVisible: Boolean by mutableStateOf(false)
        private set

    /** O fluxo que disparou o pedido em curso (vai na estatística). */
    var trigger: String? by mutableStateOf(null)
        private set

    /** `true` = aberto pela regra (conta exibição); `false` = aberto por gesto ([open]). */
    var isAutomatic: Boolean by mutableStateOf(false)
        private set

    /**
     * Um fluxo relevante terminou (locação finalizada, treino concluído…): conta e, se a regra do Nexus
     * deixar, abre o pedido. Devolve se abriu.
     *
     * @param trigger nome curto do fluxo (`"locacao_finalizada"`) para a estatística.
     */
    fun onCompletion(trigger: String? = null): Boolean {
        if (isVisible) return false
        if (!manager.onCompletion()) return false
        this.trigger = trigger
        isAutomatic = true
        isVisible = true
        return true
    }

    /**
     * Abre por GESTO da pessoa ("Avaliar o app" no menu) — sem regra, sem contar exibição; "Sim"/"Não"
     * concluem igual.
     */
    fun open(trigger: String? = null) {
        if (isVisible) return
        this.trigger = trigger
        isAutomatic = false
        isVisible = true
    }

    internal fun close() {
        isVisible = false
    }
}

/**
 * O pedido de avaliação com a **regra remota do Nexus** (2.335.0): relê a config ao entrar (com cache) e
 * devolve o estado para [AppReviewPromptHost].
 *
 * @param manager default o do processo ([AppReviewService.manager]; com o `CentralServices`
 *   inicializado, a regra vem do servidor — sem ele, os defaults `{true, 3, 30, 3}`).
 */
@Composable
fun rememberAppReviewPrompt(
    manager: AppReviewManager = remember { AppReviewService.manager() },
): AppReviewPromptState {
    LaunchedEffect(manager) { manager.refreshConfig() }
    return remember(manager) { AppReviewPromptState(manager) }
}

/**
 * **O pedido de avaliação numa linha** (2.335.0): desenha o [AppReviewDialog] quando o [state] pedir,
 * registra a exibição automática no manager (teto e lembrete), conclui com "Sim"/"Não" e manda a
 * estatística (`shown`/`positive`/`negative`/`feedback_sent`/`dismissed`, com o `trigger`).
 *
 * ```kotlin
 * val review = rememberAppReviewPrompt()
 * AppReviewPromptHost(review, androidPackage = "br.com.codecacto.app", iosAppId = "123456789")
 * // no fim do fluxo relevante:
 * review.onCompletion(trigger = "locacao_finalizada")
 * // no menu "Avaliar o app":
 * review.open(trigger = "menu")
 * ```
 *
 * Build de teste e app infantil: a regra não abre nada sozinha (o `AppReviewManager` já cala); o
 * [AppReviewPromptState.open] do menu continua funcionando (diálogo sem supressão de automação).
 */
@Composable
fun AppReviewPromptHost(
    state: AppReviewPromptState,
    androidPackage: String? = null,
    iosAppId: String? = null,
    primaryColor: Color = AppReviewPromptDefaults.primaryColor(),
    onFeedbackSubmit: (String) -> Unit = {},
    recordEvents: Boolean = true,
) {
    AppReviewDialog(
        show = state.isVisible,
        onDismiss = { state.close() },
        onFeedbackSubmit = onFeedbackSubmit,
        androidPackage = androidPackage,
        iosAppId = iosAppId,
        primaryColor = primaryColor,
        suppressInAutomation = state.isAutomatic,
        trigger = state.trigger,
        recordEvents = recordEvents,
        onShown = { if (state.isAutomatic) state.manager.onPromptShown() },
        onPositive = { state.manager.onPromptOutcome(ReviewPromptOutcome.POSITIVE) },
        onNegative = { state.manager.onPromptOutcome(ReviewPromptOutcome.NEGATIVE) },
        onDismissedWithoutAnswer = { state.manager.onPromptOutcome(ReviewPromptOutcome.DISMISSED) },
    )
}

/** Defaults do [AppReviewPromptHost]. */
object AppReviewPromptDefaults {
    /** A cor da marca do tema (o `AppReviewDialog` antigo tinha um azul fixo). */
    @Composable
    fun primaryColor(): Color = androidx.compose.material3.MaterialTheme.colorScheme.primary
}
