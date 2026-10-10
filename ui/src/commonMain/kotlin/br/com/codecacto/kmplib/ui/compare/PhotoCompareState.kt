package br.com.codecacto.kmplib.ui.compare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Estado do comparador — modo, par, posição da divisória, vista e zoom. É o equivalente Compose do
 * par `x`/`onXChange` (controlado) e `defaultX` (não-controlado) da weblib: a tela cria um com
 * [rememberPhotoCompareState] e lê/escreve os campos; quem quer reagir a uma troca observa com
 * `snapshotFlow { state.pair }`.
 *
 * **Um estado, duas telas:** o MESMO objeto passado ao [PhotoCompare] da aba e ao
 * [PhotoCompareDialog] ("mostrar ao paciente") faz a tela cheia abrir exatamente no par, na vista,
 * na divisória e no zoom em que estava — e voltar do mesmo jeito.
 *
 * [pair] e [view] guardam o PEDIDO; o que aparece é sempre normalizado contra as sessões de agora
 * (par invertido desvira, id que sumiu volta ao par inicial, vista ausente cai na primeira presente).
 */
@Stable
class PhotoCompareState(
    initialMode: CompareMode = CompareMode.SLIDER,
    initialPair: ComparePair? = null,
    initialPosition: Float = DEFAULT_COMPARE_POSITION,
    initialView: String? = null,
) {
    /** Modo pedido. Fora dos `modes` oferecidos, o componente mostra o primeiro oferecido. */
    var mode: CompareMode by mutableStateOf(initialMode)

    /** Par pedido (`null` = o inicial: a mais antiga com a mais recente). */
    var pair: ComparePair? by mutableStateOf(initialPair)

    private var positionState by mutableStateOf(clampComparePosition(initialPosition))

    /** Posição da divisória, 0–100 (% da foto de ANTES à vista). Sempre presa a 0–100. */
    var position: Float
        get() = positionState
        set(value) {
            positionState = clampComparePosition(value)
        }

    /** Vista pedida (chave de [CompareView.key]); `null` = a primeira presente no par. */
    var view: String? by mutableStateOf(initialView)

    /**
     * Zoom/pan aplicado às DUAS fotos. Não é salvo na rotação de propósito: o deslocamento é em px
     * do quadro, e o quadro muda de tamanho ao girar.
     */
    var transform: CompareTransform by mutableStateOf(CompareTransform.Identity)

    /** Volta ao tamanho original (escala 1, sem deslocamento). */
    fun resetZoom() {
        transform = CompareTransform.Identity
    }

    companion object {
        /** Salva modo, par, posição e vista — só ids e números, nada de foto nem de URL. */
        val Saver: Saver<PhotoCompareState, Any> = Saver(
            save = {
                listOf(it.mode.name, it.pair?.before, it.pair?.after, it.position, it.view)
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val l = saved as List<Any?>
                val before = l[1] as String?
                val after = l[2] as String?
                PhotoCompareState(
                    initialMode = runCatching { CompareMode.valueOf(l[0] as String) }.getOrDefault(CompareMode.SLIDER),
                    initialPair = if (before != null && after != null) ComparePair(before, after) else null,
                    initialPosition = (l[3] as? Float) ?: DEFAULT_COMPARE_POSITION,
                    initialView = l[4] as String?,
                )
            },
        )
    }
}

/**
 * Cria (e salva na rotação) o estado do comparador.
 *
 * @param defaultMode modo inicial. Default: o deslizante (como no protótipo aprovado).
 * @param defaultPair par inicial. Default: a sessão mais antiga com a mais recente.
 * @param defaultPosition posição inicial da divisória (0–100). Default 50.
 * @param defaultView vista inicial. Default: a primeira presente no par.
 */
@Composable
fun rememberPhotoCompareState(
    defaultMode: CompareMode = CompareMode.SLIDER,
    defaultPair: ComparePair? = null,
    defaultPosition: Float = DEFAULT_COMPARE_POSITION,
    defaultView: String? = null,
): PhotoCompareState = rememberSaveable(saver = PhotoCompareState.Saver) {
    PhotoCompareState(defaultMode, defaultPair, defaultPosition, defaultView)
}
