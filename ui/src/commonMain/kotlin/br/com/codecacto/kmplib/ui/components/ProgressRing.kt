package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import androidx.compose.animation.core.Animatable as FloatAnimatable

/**
 * **Anel de progresso** — o par circular do [AppProgressBar], para a mesma grandeza `0f..1f`.
 *
 * ### Quando usar este, e quando usar outro (leia antes de escolher)
 *
 * | Quero mostrar… | Componente |
 * |---|---|
 * | avanço de uma tarefa, **em barra** (cabeçalho, linha de lista larga) | [AppProgressBar] |
 * | avanço de uma tarefa, **em círculo**, com número ou ✓ no meio | **`ProgressRing`** |
 * | "estou trabalhando", sem quantidade conhecida, em tela cheia | `LoadingOverlay` |
 * | "7 de 12 concluídos", em texto | [ProgressCounter] / `CounterBadge` |
 *
 * ⚠️ **Isto NÃO é o `CircularProgressIndicator` do Material** e não deve ser usado como roda de
 * espera de tela: aquele é um indicador de atividade, com traço, velocidade e tamanho definidos
 * pelo Material. Este é uma **medida** — 38% de um curso, 4 aulas de 12 —, começa às 12h, anda no
 * sentido horário e carrega o número no meio.
 *
 * ⚠️ **E não é o `ScoreRing` da weblib.** O nome se parece e a forma não: lá é um gráfico de
 * pontuação, com escala e faixas próprias. Escolher um componente pelo **papel** ("círculo com
 * número") e herdar dele uma **forma** que ninguém aprovou é o erro que já reprovou tela pronta na
 * fábrica — se o desenho pede um anel de progresso, é este.
 *
 * ### O que ele faz por você
 * - **Anima o valor**, nunca salta de 12% para 38%. Ver [animated] e [animateOnAppear], que
 *   existem porque animar na *entrada* dentro de uma `LazyColumn` faz o anel se reanimar a cada
 *   rolagem — o default é entrar com o valor certo e animar só as mudanças.
 * - **Marca o 100%** trocando de cor (e o conteúdo default vira um ✓).
 * - **Indeterminado**: `progress = null` gira um arco. É o estado do download que ainda não sabe o
 *   tamanho do arquivo — desenhar um anel parado em 0% ali seria mentir.
 * - **Acessibilidade**: publica [ProgressBarRangeInfo] (o leitor de tela anuncia o percentual, ou
 *   "em andamento" no indeterminado) e aceita [contentDescription].
 *
 * ```kotlin
 * // 44dp, número no meio, ✓ ao concluir — o do cabeçalho de curso.
 * ProgressRing(progress = curso.fracaoConcluida)
 *
 * // O de 24 no canto da linha de aula, sem número.
 * ProgressRing(progress = download.percent?.div(100f), size = 24.dp, strokeWidth = 3.dp) {}
 *
 * // Com o gradiente da marca, e outro no 100%.
 * ProgressRing(
 *     progress = fracao,
 *     brush = Brush.linearGradient(listOf(mareA, mareB)),
 *     completeBrush = Brush.linearGradient(listOf(solA, solB)),
 * )
 * ```
 *
 * @param progress a fração concluída `0f..1f` (grampeada; `NaN` vira `0f`). **`null` = progresso
 *   desconhecido**, e o anel gira.
 * @param size o diâmetro. Default [ProgressRingDefaults.Size].
 * @param strokeWidth a espessura do traço. Default [ProgressRingDefaults.StrokeWidth].
 * @param tone a cor do traço antes de concluir. Ver [ProgressTone] — a mesma escala do
 *   [AppProgressBar], para "Success" ser o mesmo verde nos dois.
 * @param completeTone a cor em 100%. `null` mantém [tone] (nenhuma troca no marco).
 * @param brush sobrescreve a cor por um gradiente — é o que permite o anel da marca.
 * @param completeBrush o gradiente do 100%. Só é usado quando há [brush] **ou** [completeBrush].
 * @param trackColor a trilha de fundo. Default: `surfaceVariant` do tema.
 * @param animated anima a mudança de valor. Ver [ProgressRingDefaults.AnimationMillis].
 * @param animateOnAppear anima também **na primeira composição**, de zero até o valor. Deixe
 *   `false` em lista: numa `LazyColumn`, o item recomposto ao voltar rolando anima de novo, e a
 *   tela inteira "respira" a cada rolagem.
 * @param contentDescription o que o leitor de tela lê. `null` = só o percentual da semântica.
 * @param content o miolo do anel. Default: [ProgressRingLabel] (o número, e o ✓ em 100%). Passe
 *   `{}` para um anel vazio.
 */
@Composable
fun ProgressRing(
    progress: Float?,
    modifier: Modifier = Modifier,
    size: Dp = ProgressRingDefaults.Size,
    strokeWidth: Dp = ProgressRingDefaults.StrokeWidth,
    tone: ProgressTone = ProgressTone.Primary,
    completeTone: ProgressTone? = ProgressTone.Success,
    brush: Brush? = null,
    completeBrush: Brush? = null,
    trackColor: Color? = null,
    animated: Boolean = true,
    animateOnAppear: Boolean = false,
    contentDescription: String? = null,
    content: @Composable ProgressRingScope.() -> Unit = { ProgressRingLabel() },
) {
    val indeterminado = progress == null
    val alvo = normalizeProgress(progress ?: 0f)
    val concluido = !indeterminado && isProgressRingComplete(alvo)

    val corDoTraco = if (concluido) (completeTone ?: tone).toRingColor() else tone.toRingColor()
    val pincel = when {
        concluido && completeBrush != null -> completeBrush
        concluido && brush != null && completeTone != null -> SolidColor(corDoTraco)
        brush != null -> brush
        else -> SolidColor(corDoTraco)
    }
    val trilha = trackColor ?: MaterialTheme.colorScheme.surfaceVariant

    // A animação parte do valor atual (e não de zero) por default: ver `animateOnAppear`.
    val animacao = remember { FloatAnimatable(if (animateOnAppear) 0f else alvo) }
    LaunchedEffect(alvo, animated) {
        if (animated) {
            animacao.animateTo(alvo, tween(ProgressRingDefaults.AnimationMillis, easing = LinearEasing))
        } else {
            animacao.snapTo(alvo)
        }
    }
    val fracaoDesenhada = if (indeterminado) 0f else animacao.value

    val giro = if (indeterminado) {
        val transicao = rememberInfiniteTransition(label = "ProgressRing")
        transicao.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(ProgressRingDefaults.IndeterminateMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ProgressRingSpin",
        ).value
    } else {
        0f
    }

    val escopo = ProgressRingScope(
        fraction = alvo,
        percent = progressRingPercentLabel(alvo),
        isComplete = concluido,
        isIndeterminate = indeterminado,
        size = size,
        contentColor = corDoTraco,
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                progressBarRangeInfo = if (indeterminado) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    ProgressBarRangeInfo(current = alvo, range = 0f..1f)
                }
                contentDescription?.let { this.contentDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size).rotate(giro)) {
            val traco = strokeWidth.toPx()
            // O arco é desenhado no MEIO do traço: sem descontar a espessura, metade dele fica
            // fora da caixa e o anel aparece cortado quando o `size` é exatamente o do desenho.
            val diametro = (this.size.minDimension - traco).coerceAtLeast(0f)
            val canto = Offset(traco / 2f, traco / 2f)
            val medida = Size(diametro, diametro)

            drawArc(
                color = trilha,
                startAngle = 0f,
                sweepAngle = FULL_CIRCLE,
                useCenter = false,
                topLeft = canto,
                size = medida,
                // Trilha com ponta reta: com ponta arredondada o círculo fechado ganha um degrau
                // visível onde as duas pontas se encontram.
                style = Stroke(width = traco, cap = StrokeCap.Butt),
            )

            val varredura = if (indeterminado) {
                ProgressRingDefaults.IndeterminateSweep
            } else {
                progressRingSweep(fracaoDesenhada)
            }
            if (varredura > 0f) {
                drawArc(
                    brush = pincel,
                    startAngle = START_ANGLE,
                    sweepAngle = varredura,
                    useCenter = false,
                    topLeft = canto,
                    size = medida,
                    style = Stroke(width = traco, cap = StrokeCap.Round),
                )
            }
        }
        escopo.content()
    }
}

/**
 * O que o miolo do anel sabe sobre ele. Recebido como **receptor** do slot `content`, para o app
 * escrever `Text("$percent%")` sem repassar nada.
 */
@Stable
class ProgressRingScope internal constructor(
    /** A fração `0f..1f` pedida (o alvo, não o valor animado do quadro). */
    val fraction: Float,
    /** O número a mostrar. Ver [progressRingPercentLabel] — ele nunca mente 100% nem 0%. */
    val percent: Int,
    /** `true` em 100%. */
    val isComplete: Boolean,
    /** `true` quando `progress` era `null`. */
    val isIndeterminate: Boolean,
    /** O diâmetro do anel, para dimensionar o que vai dentro. */
    val size: Dp,
    /** A cor do traço em vigor — o miolo default a usa no ✓. */
    val contentColor: Color,
)

/**
 * O miolo default: o **percentual**, e um ✓ quando chega a 100%.
 *
 * O tamanho do texto sai do diâmetro do anel **em densidade, não em `sp` do usuário**: o anel tem
 * tamanho fixo, e um número que cresce com a escala de fonte do aparelho transborda o círculo em
 * vez de ajudar. Quem lê com leitor de tela recebe o percentual pela semântica do [ProgressRing],
 * que é o canal certo para isso.
 */
@Composable
fun ProgressRingScope.ProgressRingLabel() {
    if (isIndeterminate) return
    if (isComplete) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * ProgressRingDefaults.CheckRatio),
        )
        return
    }
    val corpo = with(LocalDensity.current) { (size * ProgressRingDefaults.LabelRatio).toSp() }
    Text(
        text = "$percent%",
        fontSize = corpo,
        lineHeight = corpo,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** As medidas do [ProgressRing]. */
object ProgressRingDefaults {
    /** 44dp. */
    val Size: Dp = 44.dp

    /** 4dp. */
    val StrokeWidth: Dp = 4.dp

    /** Quanto dura a animação de um valor para o outro. */
    const val AnimationMillis: Int = 450

    /** Uma volta do arco indeterminado. */
    const val IndeterminateMillis: Int = 1_100

    /** O tamanho do arco que gira quando não se sabe o progresso: um quarto de volta. */
    const val IndeterminateSweep: Float = 90f

    /** Proporção do ✓ em relação ao diâmetro. */
    const val CheckRatio: Float = 0.45f

    /** Proporção da altura do número em relação ao diâmetro. */
    const val LabelRatio: Float = 0.28f
}

/**
 * O ângulo varrido, em graus, para uma fração `0f..1f`.
 *
 * **Fração maior que zero nunca desenha zero.** Um curso com uma aula de doze concluída (8%) já
 * daria 30°, mas 1% daria 3,6° — menos que a ponta arredondada do traço, e o anel ficaria idêntico
 * ao de quem não começou. O piso de [MIN_VISIBLE_SWEEP] faz "eu comecei" aparecer, que é a única
 * informação que essa pessoa quer ver ali.
 */
fun progressRingSweep(fraction: Float): Float {
    val fracao = normalizeProgress(fraction)
    if (fracao <= 0f) return 0f
    return (fracao * FULL_CIRCLE).coerceAtLeast(MIN_VISIBLE_SWEEP)
}

/**
 * O número que vai no meio do anel.
 *
 * Duas regras, e as duas existem para o rótulo não mentir:
 * - **arredonda para baixo**, então 99,6% mostra `99` e não `100` — "100%" com uma aula faltando é
 *   a reclamação clássica de plataforma de curso;
 * - **fração maior que zero nunca mostra `0`**: quem tem 0,4% concluído vê `1`, não `0`.
 */
fun progressRingPercentLabel(fraction: Float): Int {
    val fracao = normalizeProgress(fraction)
    if (fracao <= 0f) return 0
    if (fracao >= 1f) return 100
    return floor(fracao * 100f).toInt().coerceIn(1, 99)
}

/** `true` só em 100% cravado. Ver o KDoc de [progressRingPercentLabel]. */
fun isProgressRingComplete(fraction: Float): Boolean = normalizeProgress(fraction) >= 1f

/** 12 horas. O anel começa no topo e anda no sentido horário. */
private const val START_ANGLE = -90f

private const val FULL_CIRCLE = 360f

/** O menor arco visível com traço de 4dp e ponta arredondada. */
private const val MIN_VISIBLE_SWEEP = 6f

@Composable
private fun ProgressTone.toRingColor(): Color = progressToneColor(this)
