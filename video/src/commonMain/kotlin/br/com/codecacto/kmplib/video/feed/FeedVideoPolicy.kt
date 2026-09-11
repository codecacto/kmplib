package br.com.codecacto.kmplib.video.feed

import androidx.compose.ui.unit.IntRect

/**
 * As regras do vídeo de feed que **não dependem de player nenhum** — quem ganha a vez de tocar,
 * quanto de um item está na tela, quem fica com qual player do pool.
 *
 * Moram aqui, e não dentro do [FeedVideoController], para que Android e iOS decidam **com a mesma
 * conta**, e para que a conta seja coberta por teste. É o mesmo desenho do `videoStatusOf` do player
 * de aula: cada plataforma traduz o seu vocabulário, e a decisão é uma só.
 */

/**
 * ~60% do vídeo visível: a partir daqui ele começa a tocar. É a régua do Instagram — com menos, o
 * vídeo começa a tocar quando só a borda apareceu e a pessoa ainda está lendo o post de cima.
 */
const val FEED_VIDEO_PLAY_THRESHOLD: Float = 0.6f

/**
 * Quanto um vídeo precisa estar **mais visível** que o que está tocando para tomar a vez dele.
 *
 * Sem margem, dois vídeos que se cruzam no meio da rolagem trocariam de dono a cada quadro
 * (0,71 × 0,70, depois 0,70 × 0,71…): os dois abrindo e pausando, e o som pulando de um para o outro.
 */
const val FEED_VIDEO_SWITCH_MARGIN: Float = 0.1f

/**
 * Um vídeo do feed do ponto de vista de quem decide a vez — **só números**.
 *
 * @param key a identidade do item na lista.
 * @param visibleFraction de `0` (fora da tela) a `1` (inteiro dentro da área do feed).
 * @param top a posição vertical do item (em pixels, qualquer referencial comum a todos). Desempata
 *   dois vídeos igualmente visíveis: ganha o de cima, que é o que a pessoa leu primeiro.
 */
data class FeedVideoCandidate<K>(
    val key: K,
    val visibleFraction: Float,
    val top: Float,
)

/**
 * A fração da **área** de [item] que cai dentro de [viewport], de `0` a `1`.
 *
 * É área, e não altura, de propósito: num feed com carrossel horizontal, um vídeo que saiu metade
 * para o lado está tão "fora" quanto um que saiu metade para baixo. Retângulo de área zero (o item
 * ainda não mediu) é `0` — nunca `NaN`, que faria o vídeo ganhar ou perder a vez por acaso.
 */
fun feedVideoVisibleFraction(item: IntRect, viewport: IntRect): Float {
    val area = item.width.toLong() * item.height.toLong()
    if (area <= 0L) return 0f
    val largura = minOf(item.right, viewport.right) - maxOf(item.left, viewport.left)
    val altura = minOf(item.bottom, viewport.bottom) - maxOf(item.top, viewport.top)
    if (largura <= 0 || altura <= 0) return 0f
    return ((largura.toLong() * altura.toLong()).toFloat() / area.toFloat()).coerceIn(0f, 1f)
}

/**
 * Quem toca agora: **o mais visível** entre os que passaram de [threshold], ou `null` para nenhum.
 *
 * Três regras, nesta ordem:
 * 1. Abaixo de [threshold] ninguém toca — nem que seja o único vídeo da tela.
 * 2. O que está tocando ([current]) **continua** enquanto estiver acima do limite e nenhum outro o
 *    superar por mais de [switchMargin] (ver [FEED_VIDEO_SWITCH_MARGIN]).
 * 3. Empate de visibilidade sem ninguém tocando: ganha o de **cima** ([FeedVideoCandidate.top]
 *    menor). Dois vídeos curtos inteiros na tela é o caso comum, e "o de cima" é o que a pessoa
 *    está olhando.
 */
fun <K> pickFeedVideoToPlay(
    candidates: Collection<FeedVideoCandidate<K>>,
    current: K?,
    threshold: Float = FEED_VIDEO_PLAY_THRESHOLD,
    switchMargin: Float = FEED_VIDEO_SWITCH_MARGIN,
): K? {
    val elegiveis = candidates.filter { it.visibleFraction >= threshold }
    if (elegiveis.isEmpty()) return null
    val melhor = elegiveis.minWith(maisVisivelPrimeiro())
    val atual = elegiveis.firstOrNull { it.key == current } ?: return melhor.key
    return if (melhor.visibleFraction - atual.visibleFraction > switchMargin) melhor.key else atual.key
}

/**
 * Os itens que devem ter um player **preparado**, em ordem de prioridade: o que toca primeiro, depois
 * os mais visíveis — até caberem [poolSize].
 *
 * O segundo da lista é o **pré-carregamento**: o vídeo que está entrando na tela já abre o manifesto
 * e desenha o primeiro quadro parado, e quando a vez chega a ele não há espera. Item fora da tela
 * (`visibleFraction == 0`) nunca entra — prepará-lo é gastar dado móvel com o que ninguém está vendo.
 */
internal fun <K> feedVideosToPrepare(
    candidates: Collection<FeedVideoCandidate<K>>,
    active: K?,
    poolSize: Int,
): List<K> {
    if (poolSize <= 0) return emptyList()
    val resto = candidates
        .filter { it.key != active && it.visibleFraction > 0f }
        .sortedWith(maisVisivelPrimeiro())
        .map { it.key }
    return (listOfNotNull(active) + resto).take(poolSize)
}

/**
 * Quem fica com qual player do pool — **sem recarregar o que não precisa**.
 *
 * 1. Item que já tinha player ([current]) e continua desejado **fica com ele** — trocar de player no
 *    meio da reprodução é a tela piscando.
 * 2. Item novo prefere o player livre que **já está com a mesma fonte** carregada ([slotSources]) —
 *    é o vídeo que saiu da vez e voltou: retoma de onde parou, sem abrir o manifesto de novo.
 * 3. Depois, o player livre **vazio**; por último, o livre de menor índice (que será recarregado).
 *
 * @param wanted em ordem de prioridade (ver [feedVideosToPrepare]); só os [poolSize] primeiros levam.
 * @param slotSources a fonte carregada em cada player (`null` = vazio). Pode ser mais curta que
 *   [poolSize]: o pool nasce sob demanda, e a posição que falta é um player que ainda não existe.
 * @return item → índice do player.
 */
internal fun <K> assignFeedVideoSlots(
    wanted: List<K>,
    current: Map<K, Int>,
    slotSources: List<String?>,
    poolSize: Int,
    sourceOf: (K) -> String,
): Map<K, Int> {
    val desejados = wanted.distinct().take(poolSize.coerceAtLeast(0))
    val resultado = LinkedHashMap<K, Int>()
    for (key in desejados) {
        val slot = current[key] ?: continue
        if (slot in 0 until poolSize && slot !in resultado.values) resultado[key] = slot
    }
    for (key in desejados) {
        if (key in resultado) continue
        val livres = (0 until poolSize).filter { it !in resultado.values }
        if (livres.isEmpty()) break
        val fonte = sourceOf(key)
        val escolhido = livres.firstOrNull { slotSources.getOrNull(it) == fonte }
            ?: livres.firstOrNull { slotSources.getOrNull(it) == null }
            ?: livres.first()
        resultado[key] = escolhido
    }
    return resultado
}

/**
 * A proporção (largura ÷ altura) em que a mídia do feed é desenhada, **limitada** à régua do
 * Instagram: entre **4:5** (vertical) e **1,91:1** (horizontal).
 *
 * Fora dessa faixa um vídeo 9:16 ocuparia a tela inteira e esconderia o post seguinte; um panorama
 * viraria uma fita. Sem medida conhecida (post antigo, `null` ou zero) devolve [fallback] — 4:5, com
 * corte central, que é o que o Instagram faz.
 *
 * Serve para foto e vídeo: os dois precisam da mesma caixa para o feed não "pular" de altura.
 */
fun feedMediaAspectRatio(
    widthPx: Int?,
    heightPx: Int?,
    min: Float = FEED_MEDIA_MIN_ASPECT_RATIO,
    max: Float = FEED_MEDIA_MAX_ASPECT_RATIO,
    fallback: Float = FEED_MEDIA_MIN_ASPECT_RATIO,
): Float {
    if (widthPx == null || heightPx == null || widthPx <= 0 || heightPx <= 0) return fallback
    return (widthPx.toFloat() / heightPx.toFloat()).coerceIn(min, max)
}

/** 4:5 — a mídia mais vertical que o feed desenha. */
const val FEED_MEDIA_MIN_ASPECT_RATIO: Float = 4f / 5f

/** 1,91:1 — a mais horizontal. */
const val FEED_MEDIA_MAX_ASPECT_RATIO: Float = 1.91f

private fun <K> maisVisivelPrimeiro(): Comparator<FeedVideoCandidate<K>> =
    compareByDescending<FeedVideoCandidate<K>> { it.visibleFraction }.thenBy { it.top }
