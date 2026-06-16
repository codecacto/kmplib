package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.ui.theme.AppColors
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Tom de acento das esferas da [LotteryBallRow]. Cada tom mapeia para um token de cor da camada de
 * tema ([MaterialTheme.colorScheme] / [AppColors]) — nunca cores hardcoded — permitindo que cada
 * "tema de jogo" do app expresse sua identidade (ex.: aleatório = [Primary], horóscopo =
 * [Secondary], sorte = [Tertiary]) sem que a lib conheça a paleta concreta.
 *
 * Para um acento totalmente custom (ex.: dourado da Numerologia), passe `accentColor` diretamente
 * na [LotteryBallRow] em vez de usar um tom pré-definido.
 */
enum class BallTone {
    /** Cor primária do tema. Default. */
    Primary,

    /** Cor secundária do tema. */
    Secondary,

    /** Cor terciária do tema. */
    Tertiary,

    /** Cor de sucesso (verde do tema). */
    Success,

    /** Cor informativa (azul do tema). */
    Info,
}

/**
 * Variante de tamanho da [LotteryBallRow].
 *
 * @property ballSize diâmetro padrão da esfera para a variante.
 * @property fontSize tamanho da fonte do número em sp (pareado ao diâmetro).
 * @property spacing espaçamento padrão entre esferas.
 */
enum class LotteryBallVariant(val ballSize: Dp, val fontSize: Int, val spacing: Dp) {
    /**
     * Variante grande para a tela de Resultado (persona 55+): esfera de 56dp (alvo de toque/leitura
     * confortável, acima do mínimo de 48dp de acessibilidade).
     */
    Normal(ballSize = 56.dp, fontSize = 22, spacing = 12.dp),

    /**
     * Variante compacta para listas (Histórico/Favoritos): esfera de 36dp, várias por linha.
     */
    Compact(ballSize = 36.dp, fontSize = 15, spacing = 6.dp),
}

/** Duração total alvo da animação de revelar (ms). ~600ms para não cansar a persona 55+. */
const val DEFAULT_REVEAL_TOTAL_MILLIS: Int = 600

/** Duração da animação de cada esfera individual (scale+fade) em ms. */
internal const val PER_BALL_ANIM_MILLIS: Int = 260

/**
 * Calcula o atraso (ms) com que a esfera de índice [index] entra na animação de revelar em
 * **stagger**, distribuindo as [count] esferas ao longo de [totalMillis] de forma que a última
 * comece a tempo de a animação inteira caber em [totalMillis].
 *
 * Lógica pura, exposta internamente para teste (sem dependência de Compose).
 *
 * @param index índice da esfera (0-based).
 * @param count total de esferas reveladas.
 * @param totalMillis duração total alvo do reveal (default [DEFAULT_REVEAL_TOTAL_MILLIS]).
 * @param perBallMillis duração da animação de uma esfera (default [PER_BALL_ANIM_MILLIS]).
 */
internal fun ballRevealDelayMillis(
    index: Int,
    count: Int,
    totalMillis: Int = DEFAULT_REVEAL_TOTAL_MILLIS,
    perBallMillis: Int = PER_BALL_ANIM_MILLIS,
): Int {
    if (count <= 1 || index <= 0) return 0
    // A última esfera deve começar até (totalMillis - perBallMillis) para terminar em totalMillis.
    val window = max(0, totalMillis - perBallMillis)
    val step = window.toFloat() / (count - 1)
    return (step * index).toInt().coerceIn(0, window)
}

/**
 * Calcula o número de linhas necessário para dispor [count] esferas em [columns] colunas.
 * Lógica pura, exposta internamente para teste.
 */
internal fun ballRowCount(count: Int, columns: Int): Int {
    if (count <= 0 || columns <= 0) return 0
    return ceil(count.toFloat() / columns).toInt()
}

/**
 * Esferas/bolas de dezenas de loteria com **animação de revelar em stagger** — o componente-coração
 * de qualquer app de loteria do ecossistema (Mega-Sena hoje; Quina/Lotofácil no backlog).
 *
 * Renderiza [numbers] como círculos grandes (variante [LotteryBallVariant.Normal], ≥ 48dp para
 * acessibilidade da persona 55+) num grid de [columns] colunas (ex.: 3 colunas × 2 linhas para os 6
 * números da Mega-Sena), ou como mini-esferas para listas ([LotteryBallVariant.Compact]).
 *
 * ### Animação de revelar
 * Quando [animateReveal] é `true`, cada esfera surge em sequência (stagger ~600ms total por padrão,
 * via [DEFAULT_REVEAL_TOTAL_MILLIS]) com **escala + fade**, funcionando como o "loading lúdico" da
 * geração — sem spinner técnico. A animação **re-dispara** sempre que [revealKey] muda (gere um valor
 * novo a cada "Gerar de novo" — ex.: timestamp ou contador). Mantenha o mesmo [revealKey] para não
 * re-animar em recomposições normais.
 *
 * ### Acessibilidade (reduzir movimento)
 * Passe [animateReveal] = `false` quando a plataforma sinalizar preferência por **movimento
 * reduzido** (a lib não lê esse sinal do SO — o app decide e repassa); nesse caso as esferas
 * aparecem instantaneamente, já reveladas. O conjunto é anunciado a leitores de tela como uma única
 * descrição agregada (ex.: "Dezenas: 4, 11, 23, 37, 48, 55") via [contentDescriptionOverride] ou o
 * texto padrão.
 *
 * ### Cores (sem hardcode)
 * O acento vem **sempre** do tema: use [tone] (token semântico) ou passe [accentColor] diretamente
 * (ex.: dourado da Numerologia, derivado do `AppTheme` pelo chamador). O número usa `onPrimary` para
 * contraste sobre o acento; sobrescreva via [numberColor].
 *
 * ```kotlin
 * // Resultado (Mega-Sena): 6 dezenas, 3 colunas, anima ao gerar de novo
 * LotteryBallRow(
 *     numbers = listOf(4, 11, 23, 37, 48, 55),
 *     columns = 3,
 *     tone = BallTone.Secondary,          // identidade do tema "Horóscopo"
 *     revealKey = generationId,           // muda a cada geração → re-anima
 *     contentDescriptionOverride = "Dezenas do seu jogo: 4, 11, 23, 37, 48, 55",
 * )
 *
 * // Histórico: mini-esferas, sem animação
 * LotteryBallRow(
 *     numbers = jogo.dezenas,
 *     columns = jogo.dezenas.size,
 *     variant = LotteryBallVariant.Compact,
 *     animateReveal = false,
 * )
 * ```
 *
 * @param numbers dezenas a exibir (na ordem dada; o componente não ordena).
 * @param modifier modificador externo.
 * @param columns número de colunas do grid (default 3, típico de 6 dezenas em 3×2). Valores ≤ 0
 *   caem para 1 coluna.
 * @param variant tamanho/estilo ([LotteryBallVariant.Normal] grande / [LotteryBallVariant.Compact]).
 * @param tone tom de acento via token do tema (ignorado se [accentColor] for informado).
 * @param accentColor acento explícito (use tokens do `AppTheme`; sobrepõe [tone] quando não nulo).
 * @param numberColor cor do número (default contrastante com o acento).
 * @param animateReveal liga/desliga a animação de revelar (passe `false` para movimento reduzido).
 * @param revealKey gatilho de re-disparo da animação (mude para re-animar; mesmo valor não re-anima).
 * @param revealTotalMillis duração total alvo do reveal (default [DEFAULT_REVEAL_TOTAL_MILLIS]).
 * @param ballSize sobrescreve o diâmetro da esfera (default = o da [variant]).
 * @param spacing sobrescreve o espaçamento entre esferas (default = o da [variant]).
 * @param contentDescriptionOverride descrição de acessibilidade agregada; se nulo, gera
 *   "Dezenas: a, b, c…".
 */
@Composable
fun LotteryBallRow(
    numbers: List<Int>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    variant: LotteryBallVariant = LotteryBallVariant.Normal,
    tone: BallTone = BallTone.Primary,
    accentColor: Color? = null,
    numberColor: Color? = null,
    animateReveal: Boolean = true,
    revealKey: Any? = Unit,
    revealTotalMillis: Int = DEFAULT_REVEAL_TOTAL_MILLIS,
    ballSize: Dp = variant.ballSize,
    spacing: Dp = variant.spacing,
    contentDescriptionOverride: String? = null,
) {
    if (numbers.isEmpty()) return

    val cols = columns.coerceAtLeast(1)
    val accent = accentColor ?: tone.toColor()
    val onAccent = numberColor ?: MaterialTheme.colorScheme.onPrimary
    val description = contentDescriptionOverride ?: ("Dezenas: " + numbers.joinToString(", "))

    val rows = numbers.chunked(cols)

    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var flatIndex = 0
        rows.forEach { rowNumbers ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                rowNumbers.forEach { number ->
                    LotteryBall(
                        number = number,
                        index = flatIndex,
                        count = numbers.size,
                        accent = accent,
                        numberColor = onAccent,
                        ballSize = ballSize,
                        fontSize = variant.fontSize,
                        animateReveal = animateReveal,
                        revealKey = revealKey,
                        revealTotalMillis = revealTotalMillis,
                    )
                    flatIndex++
                }
            }
        }
    }
}

@Composable
private fun LotteryBall(
    number: Int,
    index: Int,
    count: Int,
    accent: Color,
    numberColor: Color,
    ballSize: Dp,
    fontSize: Int,
    animateReveal: Boolean,
    revealKey: Any?,
    revealTotalMillis: Int,
) {
    val scale = remember(revealKey, index) { Animatable(if (animateReveal) 0.4f else 1f) }
    val alpha = remember(revealKey, index) { Animatable(if (animateReveal) 0f else 1f) }

    LaunchedEffect(revealKey, animateReveal) {
        if (!animateReveal) {
            scale.snapTo(1f)
            alpha.snapTo(1f)
            return@LaunchedEffect
        }
        scale.snapTo(0.4f)
        alpha.snapTo(0f)
        val delayMs = ballRevealDelayMillis(index, count, revealTotalMillis).toLong()
        delay(delayMs)
        alpha.animateTo(1f, animationSpec = tween(durationMillis = PER_BALL_ANIM_MILLIS))
    }

    LaunchedEffect(revealKey, animateReveal) {
        if (!animateReveal) return@LaunchedEffect
        val delayMs = ballRevealDelayMillis(index, count, revealTotalMillis).toLong()
        delay(delayMs)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = PER_BALL_ANIM_MILLIS,
                easing = LinearOutSlowInEasing,
            ),
        )
    }

    Box(
        modifier = Modifier
            .size(ballSize)
            .scale(scale.value)
            .alpha(alpha.value)
            .clip(CircleShape)
            .background(accent)
            .border(width = 1.dp, color = accent.copy(alpha = 0.35f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString().padStart(2, '0'),
            color = numberColor,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview
@Composable
private fun LotteryBallRowNormalPreview() {
    LotteryBallRow(
        numbers = listOf(4, 11, 23, 37, 48, 55),
        columns = 3,
        tone = BallTone.Primary,
        animateReveal = false,
    )
}

@Preview
@Composable
private fun LotteryBallRowCompactPreview() {
    LotteryBallRow(
        numbers = listOf(2, 9, 17, 28, 44, 60),
        columns = 6,
        variant = LotteryBallVariant.Compact,
        tone = BallTone.Secondary,
        animateReveal = false,
    )
}

/** Resolve o tom semântico para um token de cor da camada de tema. */
@Composable
private fun BallTone.toColor(): Color = when (this) {
    BallTone.Primary -> MaterialTheme.colorScheme.primary
    BallTone.Secondary -> MaterialTheme.colorScheme.secondary
    BallTone.Tertiary -> MaterialTheme.colorScheme.tertiary
    BallTone.Success -> AppColors.current.success
    BallTone.Info -> AppColors.current.info
}
