package br.com.codecacto.kmplib.ui.compare

import br.com.codecacto.kmplib.core.locale.DateSkeletons
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.math.abs
import kotlin.math.round

/*
 * Núcleo PURO do comparador de fotos — sem Compose. É aqui que as regras são testadas
 * (`CompareLogicTest`), e não numa simulação de gesto.
 *
 * Cada regra existe em DUAS pontas (esta e `compare.logic.ts` da weblib, `GAP-VIT-K02`): mudar uma é
 * mudar a outra. Os nomes seguem a weblib; a posição da divisória é `Float` 0..100 (a mesma escala).
 */

// ── Datas ──────────────────────────────────────────────────────────────────────────────────────

/**
 * Data lida de uma sessão/foto.
 *
 * @param epochMillis data civil = meia-noite **UTC** daquele dia — o mesmo número em qualquer fuso.
 * @param dateOnly `true` para "AAAA-MM-DD" (sem hora, sem fuso).
 */
data class ParsedCompareDate(val epochMillis: Long, val dateOnly: Boolean)

private val DATE_ONLY = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
private val ISO_DATE_TIME =
    Regex("""^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?)(Z|z|[+-]\d{2}:?\d{2})?$""")

private fun parseCivil(v: String): LocalDate? {
    val m = DATE_ONLY.matchEntire(v) ?: return null
    val y = m.groupValues[1].toInt()
    // Ano < 100 é recusado como na weblib (lá o `Date.UTC` o somaria a 1900).
    if (y < 100) return null
    // O construtor lança em 2026-02-31 — data impossível vira `null`, não 03/03.
    return runCatching { LocalDate(y, m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
}

/**
 * Lê "AAAA-MM-DD" (data civil) ou ISO 8601 com hora (com ou sem offset). Qualquer outra coisa é
 * `null` — formato livre ("12 de março") é recusado de propósito, como na weblib: uma linha do tempo
 * que ordena diferente em cada ponta é pior do que um erro visível.
 *
 * @param timeZone fuso para hora SEM offset ("2026-03-12T10:00"). Default: o do aparelho.
 */
fun parseCompareDate(
    value: String?,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ParsedCompareDate? {
    val v = value?.trim() ?: return null
    if (DATE_ONLY.matches(v)) {
        val date = parseCivil(v) ?: return null
        return ParsedCompareDate(date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), dateOnly = true)
    }
    val m = ISO_DATE_TIME.matchEntire(v) ?: return null
    val local = runCatching { LocalDateTime.parse("${m.groupValues[1]}T${m.groupValues[2]}") }.getOrNull()
        ?: return null
    val offsetText = m.groupValues[3]
    val millis = runCatching {
        if (offsetText.isEmpty()) {
            local.toInstant(timeZone).toEpochMilliseconds()
        } else {
            val normalized = when {
                offsetText.equals("Z", ignoreCase = true) -> "Z"
                offsetText.length == 5 -> offsetText.substring(0, 3) + ":" + offsetText.substring(3)
                else -> offsetText
            }
            local.toInstant(UtcOffset.parse(normalized)).toEpochMilliseconds()
        }
    }.getOrNull() ?: return null
    return ParsedCompareDate(millis, dateOnly = false)
}

/**
 * Data curta no formato da REGIÃO do aparelho (`12/03/2026` no BR, `03/12/2026` nos EUA). Data civil
 * sai igual em qualquer fuso; data com hora, no [timeZone]. Texto ilegível volta como veio: mostrar o
 * que o backend mandou é melhor do que "Invalid Date".
 */
fun formatCompareDate(value: String, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val civil = parseCivil(value.trim())
    if (civil != null) return runCatching { RegionalFormat.formatDate(civil) }.getOrDefault(value)
    val parsed = parseCompareDate(value, timeZone) ?: return value
    return runCatching {
        RegionalFormat.formatDateTime(parsed.epochMillis, skeleton = DateSkeletons.SHORT, timeZone = timeZone)
    }.getOrDefault(value)
}

// ── Sessões e par ──────────────────────────────────────────────────────────────────────────────

/**
 * As sessões que entram na comparação: só as que têm foto, da MAIS ANTIGA para a mais recente.
 * Empate de data mantém a ordem recebida; data ilegível vai para o fim (também na ordem recebida).
 */
fun comparableSessions(
    sessions: List<CompareSession>,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<CompareSession> {
    data class Ordered(val session: CompareSession, val index: Int, val time: Long)
    return sessions
        .mapIndexed { index, session ->
            Ordered(session, index, parseCompareDate(session.date, timeZone)?.epochMillis ?: Long.MAX_VALUE)
        }
        .filter { it.session.photos.isNotEmpty() }
        .sortedWith(compareBy<Ordered> { it.time }.thenBy { it.index })
        .map { it.session }
}

/** Par inicial: a sessão mais antiga com a mais recente (as duas pontas da linha do tempo). */
fun defaultComparePair(ids: List<String>): ComparePair? =
    if (ids.size < 2) null else ComparePair(before = ids.first(), after = ids.last())

/**
 * Um par VÁLIDO para as sessões de agora (`ids` em ordem cronológica): os dois ids existem, são
 * diferentes e `before` é o mais antigo. Par invertido é desvirado; par com id que sumiu (sessão
 * excluída) ou repetido volta ao par inicial. Par já válido volta a MESMA instância.
 */
fun normalizeComparePair(ids: List<String>, pair: ComparePair?): ComparePair? {
    val fallback = defaultComparePair(ids) ?: return null
    if (pair == null) return fallback
    val a = ids.indexOf(pair.before)
    val b = ids.indexOf(pair.after)
    if (a < 0 || b < 0 || a == b) return fallback
    return if (a < b) pair else ComparePair(before = pair.after, after = pair.before)
}

/**
 * A regra do protótipo aprovado ao tocar numa sessão da linha do tempo:
 *
 * - tocar numa das duas do par não muda nada;
 * - sessão ANTERIOR ao "antes" vira o novo "antes"; POSTERIOR ao "depois", o novo "depois";
 * - sessão ENTRE as duas substitui a ponta mais PRÓXIMA (empate → substitui o "depois").
 *
 * O par nunca se inverte. Sem mudança, devolve a MESMA instância. `current` deve estar normalizado.
 */
fun nextComparePair(ids: List<String>, current: ComparePair, clicked: String): ComparePair {
    val i = ids.indexOf(clicked)
    val a = ids.indexOf(current.before)
    val b = ids.indexOf(current.after)
    if (i < 0 || a < 0 || b < 0 || i == a || i == b) return current
    if (i < a) return ComparePair(before = clicked, after = current.after)
    if (i > b) return ComparePair(before = current.before, after = clicked)
    return if (i - a < b - i) {
        ComparePair(before = clicked, after = current.after)
    } else {
        ComparePair(before = current.before, after = clicked)
    }
}

// ── Vistas ─────────────────────────────────────────────────────────────────────────────────────

/** Chave de casamento de uma foto: `view` › `label` › posição na sessão (`#1`, `#2`…). */
fun photoViewKey(photo: ComparePhoto, index: Int): String =
    photo.view?.trim()?.takeIf { it.isNotEmpty() }
        ?: photo.label?.trim()?.takeIf { it.isNotEmpty() }
        ?: "#${index + 1}"

/**
 * As vistas de todas as sessões, sem repetir, na ordem em que aparecem pela primeira vez (sessões em
 * ordem cronológica). A lista é a mesma para qualquer par — o seletor não pula ao trocar o par.
 */
fun compareViews(
    sessions: List<CompareSession>,
    fallbackLabel: (position: Int) -> String,
): List<CompareView> {
    val seen = LinkedHashMap<String, CompareView>()
    sessions.forEach { session ->
        session.photos.forEachIndexed { index, photo ->
            val key = photoViewKey(photo, index)
            if (key !in seen) {
                val label = photo.label?.trim()?.takeIf { it.isNotEmpty() }
                    ?: photo.view?.trim()?.takeIf { it.isNotEmpty() }
                    ?: fallbackLabel(index + 1)
                seen[key] = CompareView(key, label)
            }
        }
    }
    return seen.values.toList()
}

/** A foto da vista [key] na sessão (a primeira, se a sessão repetir a vista). */
fun photoForView(session: CompareSession?, key: String?): ComparePhoto? {
    if (session == null || key == null) return null
    return session.photos.withIndex().firstOrNull { (i, p) -> photoViewKey(p, i) == key }?.value
}

/**
 * A vista que de fato aparece: a pedida, se existe em pelo menos uma sessão do par; senão a primeira
 * da lista que existe no par. Vista que nenhuma das duas sessões tem mostraria duas molduras vazias —
 * nunca é a escolhida.
 */
fun resolveCompareView(
    views: List<CompareView>,
    sessions: List<CompareSession?>,
    requested: String? = null,
): String? {
    fun present(key: String) = sessions.any { photoForView(it, key) != null }
    if (requested != null && present(requested)) return requested
    return views.firstOrNull { present(it.key) }?.key
}

// ── Divisória (posição em %) ───────────────────────────────────────────────────────────────────

/** Posição inicial da divisória: meio a meio. */
const val DEFAULT_COMPARE_POSITION: Float = 50f

/** Passo do ajuste pelo leitor de tela e passo largo, em pontos percentuais (iguais à weblib). */
const val DEFAULT_COMPARE_STEP: Float = 5f
const val DEFAULT_COMPARE_LARGE_STEP: Float = 25f

/** Prende a posição em 0–100. Valor não finito volta ao meio — nunca some a divisória. */
fun clampComparePosition(value: Float): Float =
    if (!value.isFinite()) DEFAULT_COMPARE_POSITION else value.coerceIn(0f, 100f)

/**
 * Posição (0–100) do dedo dentro do quadro. `null` quando o quadro não tem largura (ainda sem
 * layout) — melhor ignorar o toque do que dividir por zero.
 */
fun comparePositionFromPointer(x: Float, width: Float): Float? {
    if (!(width > 0f)) return null
    return clampComparePosition(x / width * 100f)
}

/**
 * Quanto de cada foto está à vista, inteiro e somando 100 — o que o leitor de tela anuncia
 * ("40% antes, 60% depois"). A foto de ANTES fica à ESQUERDA da divisória (como na weblib).
 */
fun compareVisibleShares(position: Float): Pair<Int, Int> {
    val before = round(clampComparePosition(position)).toInt()
    return before to (100 - before)
}

/** Distância, em px, que o dedo anda antes de o gesto ser lido como arrasto ou como rolagem. */
const val COMPARE_DRAG_SLOP_PX: Float = 6f

/** Leitura do gesto de um dedo: ainda indefinido, arrasto horizontal ou rolagem vertical. */
enum class CompareDragIntent { UNDECIDED, HORIZONTAL, VERTICAL }

/**
 * O dedo pousou na foto: é para arrastar a divisória ou para rolar a tela?
 *
 * A decisão espera o dedo andar [slop] px — pular a divisória no primeiro toque faria quem só quer
 * rolar a tela por cima da foto ver a divisória saltar. Empate conta como horizontal (weblib igual).
 */
fun compareDragIntent(dx: Float, dy: Float, slop: Float = COMPARE_DRAG_SLOP_PX): CompareDragIntent {
    val ax = abs(dx)
    val ay = abs(dy)
    if (ax < slop && ay < slop) return CompareDragIntent.UNDECIDED
    return if (ax >= ay) CompareDragIntent.HORIZONTAL else CompareDragIntent.VERTICAL
}

// ── Zoom/pan sincronizados ─────────────────────────────────────────────────────────────────────

/** Escala mínima, máxima e a do duplo toque. */
const val COMPARE_MIN_SCALE: Float = 1f
const val COMPARE_MAX_SCALE: Float = 5f
const val COMPARE_DOUBLE_TAP_SCALE: Float = 2.5f

/** Escala e deslocamento (px) aplicados às DUAS fotos ao mesmo tempo. */
data class CompareTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {
    /** Está ampliado? (Aí o arrasto de um dedo MOVE a imagem em vez de rolar a tela.) */
    val isZoomed: Boolean get() = scale > 1.001f

    companion object {
        val Identity: CompareTransform = CompareTransform(1f, 0f, 0f)
    }
}

/**
 * Deslocamento máximo (px, cada eixo) para a escala [scale] num quadro [width]×[height]: a imagem
 * ampliada nunca descola da borda do quadro.
 */
fun compareMaxOffset(scale: Float, width: Float, height: Float): Pair<Float, Float> {
    val s = scale.coerceAtLeast(1f)
    return ((s - 1f) * width / 2f).coerceAtLeast(0f) to ((s - 1f) * height / 2f).coerceAtLeast(0f)
}

/**
 * Aplica um passo de gesto (pinça [zoom] × arrasto [panX]/[panY]) ao zoom atual e devolve o novo, já
 * preso aos limites. Em escala 1 o deslocamento volta a zero. É o que mantém os dois quadros do lado
 * a lado (e as duas camadas do deslizante) SINCRONIZADOS: um estado só, aplicado aos dois.
 */
fun applyCompareTransform(
    current: CompareTransform,
    zoom: Float,
    panX: Float,
    panY: Float,
    width: Float,
    height: Float,
    minScale: Float = COMPARE_MIN_SCALE,
    maxScale: Float = COMPARE_MAX_SCALE,
): CompareTransform {
    val z = if (zoom.isFinite() && zoom > 0f) zoom else 1f
    val scale = (current.scale * z).coerceIn(minScale, maxScale)
    if (scale <= 1f) return CompareTransform.Identity
    val (mx, my) = compareMaxOffset(scale, width, height)
    val px = if (panX.isFinite()) panX else 0f
    val py = if (panY.isFinite()) panY else 0f
    return CompareTransform(
        scale = scale,
        offsetX = (current.offsetX + px).coerceIn(-mx, mx),
        offsetY = (current.offsetY + py).coerceIn(-my, my),
    )
}

/** Duplo toque: ampliado → volta a 1; em 1 → [COMPARE_DOUBLE_TAP_SCALE], centrado. */
fun toggleCompareZoom(current: CompareTransform): CompareTransform =
    if (current.scale > 1.1f) CompareTransform.Identity else CompareTransform(COMPARE_DOUBLE_TAP_SCALE, 0f, 0f)

// ── Quadro ─────────────────────────────────────────────────────────────────────────────────────

/** Proporção padrão do quadro (largura/altura): 3:4, a foto de celular em pé. */
const val DEFAULT_COMPARE_ASPECT_RATIO: Float = 3f / 4f

/**
 * Tamanho do quadro (largura, altura) com proporção FIXA [aspectRatio] (largura/altura) dentro de
 * [maxWidth] × [maxHeight]: a foto que chega depois não empurra a tela, e a foto em pé não vira faixa
 * larga num tablet (limita-se a LARGURA, nunca a altura sozinha). Proporção inválida cai em 3:4.
 */
fun compareFrameSize(aspectRatio: Float, maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
    val ratio = if (aspectRatio.isFinite() && aspectRatio > 0f) aspectRatio else DEFAULT_COMPARE_ASPECT_RATIO
    var w = maxWidth.coerceAtLeast(0f)
    if (maxHeight.isFinite() && maxHeight > 0f) w = minOf(w, maxHeight * ratio)
    return w to w / ratio
}

/** Substitui `%1$s`, `%2$s`, `%1$d`… de um texto de recurso. */
internal fun formatCompareTemplate(template: String, vararg args: Any): String {
    var out = template
    args.forEachIndexed { i, a ->
        out = out.replace("%${i + 1}\$s", a.toString()).replace("%${i + 1}\$d", a.toString())
    }
    return out
}
