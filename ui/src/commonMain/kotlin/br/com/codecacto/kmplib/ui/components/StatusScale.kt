package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.ui.theme.AppColors
import br.com.codecacto.kmplib.ui.theme.ColorContrast

// ---------------------------------------------------------------------------------------------
// Escala de status com N níveis e cor ARBITRÁRIA (2.220.0 — GAP-ER-07).
//
// `StatusTone` responde "que tipo de coisa é" (sucesso, aviso, perigo…) com 5 tons fixos do tema.
// Não responde "quão perto do vencimento": um semáforo de prazo (OK · 90 d · 60 d · 30 d · vencido)
// tem cinco degraus da MESMA natureza, e cada produto tem os seus. A lib não conhece esses degraus
// — o app declara a escala; a lib garante contraste, ordem de gravidade e o mesmo desenho em todo
// lugar (chip, selo, pino do mapa, legenda).
// ---------------------------------------------------------------------------------------------

/**
 * Um degrau de uma [StatusScale].
 *
 * @param key identificador estável do degrau (o que vem do domínio: `"vencido"`, `"30d"`…).
 * @param label rótulo curto exibido (já traduzido pelo app).
 * @param color cor do degrau — venha de token do tema ou de [rememberStatusRamp]; nunca `Color(0x…)`
 *   escrito na tela.
 * @param icon ícone opcional. **A cor nunca é a única informação**: ícone + rótulo carregam o sentido
 *   para quem não distingue as cores.
 * @param severity gravidade; MAIOR = mais grave. Decide [StatusScale.mostSevere] (e a cor de um
 *   grupo de pinos no mapa).
 */
@Immutable
data class StatusLevel(
    val key: String,
    val label: String,
    val color: Color,
    val icon: ImageVector? = null,
    val severity: Int = 0,
)

/**
 * Conjunto ordenado de [StatusLevel] declarado pelo app (semáforo de prazo, estágio de pedido…).
 *
 * ```kotlin
 * val ramp = rememberStatusRamp(5)            // verde → âmbar → vermelho, do tema
 * val prazo = remember(ramp) {
 *     StatusScale(
 *         StatusLevel("ok", "Em dia", ramp[0], Icons.Default.CheckCircle, severity = 0),
 *         StatusLevel("90d", "90 dias", ramp[1], Icons.Default.Schedule, severity = 1),
 *         StatusLevel("60d", "60 dias", ramp[2], Icons.Default.Schedule, severity = 2),
 *         StatusLevel("30d", "30 dias", ramp[3], Icons.Default.Warning, severity = 3),
 *         StatusLevel("venc", "Vencido", ramp[4], Icons.Default.Error, severity = 4),
 *     )
 * }
 * StatusChip(prazo.require(extintor.faixa))
 * ```
 *
 * @throws IllegalArgumentException se vazia ou com `key` repetida.
 */
@Immutable
class StatusScale(val levels: List<StatusLevel>) {

    constructor(vararg levels: StatusLevel) : this(levels.toList())

    private val byKey: Map<String, StatusLevel>

    init {
        require(levels.isNotEmpty()) { "StatusScale precisa de ao menos um nível." }
        byKey = levels.associateBy { it.key }
        require(byKey.size == levels.size) { "StatusScale com key repetida: ${levels.map { it.key }}" }
    }

    /** O nível de [key], ou `null` se a escala não o conhece. */
    fun level(key: String): StatusLevel? = byKey[key]

    /** O nível de [key]; lança se a escala não o conhece (erro de programação, não de dado). */
    fun require(key: String): StatusLevel =
        byKey[key] ?: throw IllegalArgumentException("Nível '$key' não existe na escala ${byKey.keys}.")

    /**
     * O nível MAIS GRAVE entre [keys] (maior [StatusLevel.severity]; empate = o primeiro da escala).
     * Chaves desconhecidas são ignoradas; `null` se nenhuma for conhecida.
     */
    fun mostSevere(keys: Iterable<String>): StatusLevel? =
        keys.mapNotNull { byKey[it] }.distinct().let { found ->
            levels.filter { it in found }.maxByOrNull { it.severity }
        }

    /** Os níveis do MAIS grave ao menos grave (para legenda e filtro). */
    val bySeverityDescending: List<StatusLevel>
        get() = levels.withIndex()
            .sortedWith(compareByDescending<IndexedValue<StatusLevel>> { it.value.severity }.thenBy { it.index })
            .map { it.value }

    override fun equals(other: Any?): Boolean = other is StatusScale && other.levels == levels
    override fun hashCode(): Int = levels.hashCode()
    override fun toString(): String = "StatusScale(${levels.joinToString { it.key }})"
}

/**
 * [count] cores igualmente espaçadas ao longo de [stops] (interpolação linear em sRGB, opacas).
 * `count == 1` devolve a primeira parada; `stops` com uma cor só devolve essa cor repetida.
 */
fun rampColors(stops: List<Color>, count: Int): List<Color> {
    require(stops.isNotEmpty()) { "rampColors precisa de ao menos uma parada." }
    require(count >= 1) { "rampColors: count deve ser ≥ 1 (foi $count)." }
    if (stops.size == 1 || count == 1) return List(count) { stops.first().copy(alpha = 1f) }
    val segments = stops.size - 1
    return List(count) { i ->
        val position = i.toFloat() / (count - 1) * segments
        val index = position.toInt().coerceAtMost(segments - 1)
        val t = position - index
        val a = stops[index]
        val b = stops[index + 1]
        Color(
            red = a.red + (b.red - a.red) * t,
            green = a.green + (b.green - a.green) * t,
            blue = a.blue + (b.blue - a.blue) * t,
            alpha = 1f,
        )
    }
}

/**
 * Rampa de [count] cores do **tema** para uma escala de gravidade: `success` → `warning` → `error`
 * (a primeira é a mais tranquila, a última a mais grave). É a forma de ter 5 degraus de semáforo sem
 * escrever cor nenhuma na tela — trocar a paleta do app troca a rampa junto.
 */
@Composable
fun rememberStatusRamp(count: Int = 5): List<Color> {
    val colors = AppColors.current
    val danger = MaterialTheme.colorScheme.error
    return remember(count, colors.success, colors.warning, danger) {
        rampColors(listOf(colors.success, colors.warning, danger), count)
    }
}

/** Forma de desenho do [StatusChip]. */
enum class StatusChipStyle {
    /** Fundo da cor a 15% sobre a superfície; texto/ícone na cor, escurecida só o necessário. */
    TINTED,

    /** Fundo cheio na cor; texto/ícone claro ou escuro, o de maior contraste. */
    SOLID,
}

/** Opacidade do fundo do estilo [StatusChipStyle.TINTED] (a mesma do [StatusBadge] semântico). */
internal const val STATUS_TINT_ALPHA = 0.15f

/**
 * Cores de conteúdo e fundo de um chip de status, com contraste **garantido** (WCAG AA de texto,
 * 4,5:1) — função pura, testada. [surface] é onde o chip está desenhado.
 */
internal fun statusChipColors(color: Color, style: StatusChipStyle, surface: Color): Pair<Color, Color> {
    val opaque = color.copy(alpha = 1f)
    return when (style) {
        StatusChipStyle.TINTED -> {
            val background = ColorContrast.compositeOver(opaque, STATUS_TINT_ALPHA, surface)
            ColorContrast.adjustForContrast(opaque, background) to background
        }
        StatusChipStyle.SOLID -> ColorContrast.pickOnColor(opaque) to opaque
    }
}

/**
 * Chip de status **ícone + rótulo + cor**, com a cor que o app quiser e contraste garantido.
 *
 * Diferente do [StatusBadge] (que recebe tom fixo ou par de cores cru), aqui a cor é arbitrária e a
 * lib ajusta o texto até ele ser legível — o amarelo de um semáforo sobre fundo amarelo-claro sai
 * escurecido o suficiente, não ilegível. A semântica de acessibilidade é só o [label] (o ícone é
 * decorativo, o sentido está no texto).
 *
 * @param surface cor sobre a qual o chip é desenhado (para compor o fundo tingido). Default:
 *   `colorScheme.surface`.
 */
@Composable
fun StatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: StatusChipStyle = StatusChipStyle.TINTED,
    surface: Color = MaterialTheme.colorScheme.surface,
) {
    val (content, background) = remember(color, style, surface) { statusChipColors(color, style, surface) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .heightIn(min = 24.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        }
        Text(
            text = label,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** [StatusChip] de um degrau de [StatusScale] (rótulo, cor e ícone do próprio nível). */
@Composable
fun StatusChip(
    level: StatusLevel,
    modifier: Modifier = Modifier,
    style: StatusChipStyle = StatusChipStyle.TINTED,
    surface: Color = MaterialTheme.colorScheme.surface,
) = StatusChip(
    label = level.label,
    color = level.color,
    modifier = modifier,
    icon = level.icon,
    style = style,
    surface = surface,
)

/** [StatusChip] de um [StatusTone] semântico (a cor sai de [statusToneColor]). */
@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: StatusChipStyle = StatusChipStyle.TINTED,
) = StatusChip(
    label = label,
    color = statusToneColor(tone),
    modifier = modifier,
    icon = icon,
    style = style,
)
