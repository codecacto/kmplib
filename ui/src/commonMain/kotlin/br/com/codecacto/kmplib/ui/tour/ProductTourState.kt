package br.com.codecacto.kmplib.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Onde cada `Modifier.tourTarget(id)` está, em pixels da RAIZ da composição (`boundsInRoot`, já
 * recortado pelos pais que cortam — item rolado para fora tem área vazia).
 *
 * É estado do Compose: o host relê o retângulo a cada mudança, então o destaque acompanha rolagem,
 * rotação e a barra que some. Vários nós podem ter o mesmo id (versão de celular e de tablet da
 * mesma ação); vale o primeiro que está visível.
 */
@Stable
internal class TourTargetRegistry {
    private data class Entry(val id: String, val rootBounds: Rect)

    private val entries = mutableStateMapOf<Any, Entry>()

    /** Canto do host na raiz — converte `boundsInRoot` do alvo para coordenadas do host. */
    var hostOrigin: Offset by mutableStateOf(Offset.Zero)

    fun report(owner: Any, id: String, rootBounds: Rect) {
        val entry = Entry(id, rootBounds)
        // Só escreve se mudou: o callback roda a cada layout, e escrever igual invalidaria à toa.
        if (entries[owner] != entry) entries[owner] = entry
    }

    fun remove(owner: Any) {
        entries.remove(owner)
    }

    /** O retângulo do alvo em coordenadas do host, ou `null` se não está na tela. */
    fun boundsOf(id: String): Rect? = entries.values
        .firstOrNull { it.id == id && it.rootBounds.width > 0f && it.rootBounds.height > 0f }
        ?.rootBounds
        ?.translate(-hostOrigin)

    fun isVisible(id: String): Boolean = boundsOf(id) != null
}

/** O registro do host mais próximo; `null` fora de um [ProductTourHost] (o modificador vira no-op). */
internal val LocalTourTargetRegistry = staticCompositionLocalOf<TourTargetRegistry?> { null }

/**
 * Estado do tour guiado. Crie com [rememberProductTourState] e passe ao [ProductTourHost].
 *
 * - [start] abre o tour (de "Refazer o tour", por exemplo). Os passos cujo alvo não está na tela
 *   são descartados **no começo** (depois de a tela assentar um quadro); a contagem "N de M" é a
 *   dos que ficaram.
 * - [next], [back] e [skip] são os botões do balão; o app raramente os chama.
 * - Um alvo que sumir no MEIO do tour é pulado ao avançar/voltar.
 */
@Stable
class ProductTourState internal constructor(
    internal val registry: TourTargetRegistry,
) {
    private var steps: List<TourStep> by mutableStateOf(emptyList())
    private var index: Int by mutableIntStateOf(-1)
    private var pending: List<TourStep>? by mutableStateOf(null)

    /** Muda a cada [start]; o host espera a tela assentar e chama [commitStart]. */
    internal var startToken: Int by mutableIntStateOf(0)
        private set

    private val endListeners = mutableListOf<(TourEndReason) -> Unit>()

    /** `true` enquanto o balão está na tela. */
    val isActive: Boolean get() = index >= 0

    /** `true` entre o [start] e o primeiro quadro do tour (a tela ainda está sendo medida). */
    val isStarting: Boolean get() = pending != null

    /** O passo exibido, ou `null` sem tour. */
    val currentStep: TourStep? get() = steps.getOrNull(index)

    /** Posição do passo exibido, começando em 1 (o "N" de "N de M"); 0 sem tour. */
    val currentNumber: Int get() = if (isActive) index + 1 else 0

    /** Quantos passos o tour tem (o "M" de "N de M"); 0 sem tour. */
    val stepCount: Int get() = if (isActive) steps.size else 0

    /** Existe passo anterior disponível ("Voltar" aparece). */
    val canGoBack: Boolean get() = isActive && previousAvailable() != null

    /** É o último passo disponível ("Próximo" vira "Começar a usar" e "Pular" some). */
    val isLastStep: Boolean get() = isActive && nextAvailable() == null

    /**
     * Abre o tour com [steps]. Recomeça se já houver um aberto (sem avisar fim ao anterior).
     * Lista vazia não abre nada.
     *
     * Chamado de outra tela (Configurações → "Refazer o tour"): navegue PRIMEIRO até a tela que tem
     * os alvos e chame [start] em seguida — os passos são filtrados quando a tela já está composta.
     */
    fun start(steps: List<TourStep>) {
        if (steps.isEmpty()) return
        index = -1
        this.steps = emptyList()
        pending = steps
        startToken++
    }

    /** Aplica o [start] pendente: descarta os passos sem alvo na tela e mostra o primeiro. */
    internal fun commitStart() {
        val requested = pending ?: return
        pending = null
        val available = resolveTourSteps(requested, registry::isVisible)
        if (available.isEmpty()) {
            notifyEnd(TourEndReason.NOTHING_TO_SHOW)
            return
        }
        steps = available
        index = 0
    }

    /** Vai ao próximo passo disponível; no último, conclui o tour ([TourEndReason.COMPLETED]). */
    fun next() {
        if (!isActive) return
        val target = nextAvailable()
        if (target == null) end(TourEndReason.COMPLETED) else index = target
    }

    /** Volta ao passo anterior disponível; no primeiro, não faz nada. */
    fun back() {
        if (!isActive) return
        previousAvailable()?.let { index = it }
    }

    /** Fecha o tour sem concluir ([TourEndReason.SKIPPED]). Também cancela um [start] pendente. */
    fun skip() {
        if (pending != null && !isActive) {
            pending = null
            return
        }
        if (isActive) end(TourEndReason.SKIPPED)
    }

    private fun isAvailable(step: TourStep): Boolean =
        step.targetId == null || registry.isVisible(step.targetId)

    private fun nextAvailable(): Int? =
        ((index + 1) until steps.size).firstOrNull { isAvailable(steps[it]) }

    private fun previousAvailable(): Int? =
        (index - 1 downTo 0).firstOrNull { isAvailable(steps[it]) }

    private fun end(reason: TourEndReason) {
        index = -1
        steps = emptyList()
        notifyEnd(reason)
    }

    private fun notifyEnd(reason: TourEndReason) {
        endListeners.toList().forEach { it(reason) }
    }

    internal fun addEndListener(listener: (TourEndReason) -> Unit) {
        endListeners += listener
    }

    internal fun removeEndListener(listener: (TourEndReason) -> Unit) {
        endListeners -= listener
    }

    companion object {
        /** Para teste/preview: um estado com registro próprio, fora da composição. */
        internal fun create(registry: TourTargetRegistry = TourTargetRegistry()): ProductTourState =
            ProductTourState(registry)
    }
}

/**
 * Cria o [ProductTourState] da tela raiz.
 *
 * @param onEnd chamado quando o tour termina (concluído, pulado ou sem nada para mostrar) — para
 *   métrica ou para abrir algo depois. O first-run já é gravado pelo [ProductTourHost].
 */
@Composable
fun rememberProductTourState(onEnd: (TourEndReason) -> Unit = {}): ProductTourState {
    val currentOnEnd by rememberUpdatedState(onEnd)
    return remember {
        ProductTourState(TourTargetRegistry()).also { state ->
            state.addEndListener { reason -> currentOnEnd(reason) }
        }
    }
}
