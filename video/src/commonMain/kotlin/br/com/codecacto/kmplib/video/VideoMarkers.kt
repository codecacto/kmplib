package br.com.codecacto.kmplib.video

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Uma **marca no tempo** do vídeo (2.286.0) — o comentário do personal em 0:12, o áudio em 0:31.
 *
 * A lib não sabe o que a marca É (texto, áudio, ajuste): ela desenha o ponto na [VideoTimeline],
 * pula para ele no toque e o anuncia ao leitor de tela. O conteúdo — o cartão do comentário, o
 * player do áudio — é do app, que acha a marca da vez com [videoMarkerAt].
 *
 * @property id identidade estável (o id do comentário no servidor). Vira o `testTag`
 *   `video-marca-<id>` — ver [VideoTimelineTestTags.marker].
 * @property atMillis o instante, em milissegundos desde o início do vídeo.
 * @property label o que o leitor de tela diz da marca, além do tempo ("Comentário: joelho para
 *   dentro"). `null` = só o tempo. Não é desenhado — a marca é um ponto.
 * @property color a cor do ponto; [Color.Unspecified] = a do tema ([VideoTimelineColors.marker]).
 *   Útil para separar texto de áudio. A cor nunca é a única pista: o [label] diz o tipo.
 */
@Immutable
data class VideoMarker(
    val id: String,
    val atMillis: Long,
    val label: String? = null,
    val color: Color = Color.Unspecified,
)

/**
 * Marcas que caem tão perto na barra que os pontos se sobreporiam — desenhadas como **um** ponto,
 * com a contagem. O toque leva à primeira (a mais cedo).
 *
 * @property markers as marcas do grupo, em ordem de tempo (nunca vazia).
 * @property fraction onde o ponto fica na barra (0..1) — o da primeira marca.
 */
@Immutable
data class VideoMarkerCluster(
    val markers: List<VideoMarker>,
    val fraction: Float,
) {
    /** A marca que o toque abre. */
    val first: VideoMarker get() = markers.first()

    /** O instante do ponto (o da primeira marca). */
    val atMillis: Long get() = first.atMillis
}

/**
 * As marcas prontas para desenhar: **em ordem de tempo** (empate pelo [VideoMarker.id], para a
 * ordem não depender de quem veio primeiro na resposta) e **dentro do vídeo** — negativa vira `0`;
 * com a duração conhecida (> 0), a que passa do fim vira o fim. Uma marca nunca some: o comentário
 * em 1:02 de um vídeo que, comprimido, ficou com 1:01,9 continua acessível no fim da barra.
 */
fun normalizeVideoMarkers(markers: List<VideoMarker>, durationMillis: Long): List<VideoMarker> =
    markers.map { marca ->
        val at = when {
            marca.atMillis < 0L -> 0L
            durationMillis > 0L && marca.atMillis > durationMillis -> durationMillis
            else -> marca.atMillis
        }
        if (at == marca.atMillis) marca else marca.copy(atMillis = at)
    }.sortedWith(compareBy<VideoMarker>({ it.atMillis }, { it.id }))

/**
 * Agrupa as marcas cujos pontos ficariam a menos de [minDistancePx] um do outro numa barra de
 * [widthPx] — a regra que mantém cada alvo de toque com os 48 dp que a acessibilidade pede.
 *
 * Guloso, da esquerda para a direita: a marca entra no grupo aberto se cair a menos de
 * [minDistancePx] da **primeira** do grupo (e não da última — senão uma fileira de marcas a cada
 * meio segundo viraria um grupo só do começo ao fim). Recebe as marcas já normalizadas
 * ([normalizeVideoMarkers]).
 *
 * Duração desconhecida (≤ 0) ou barra sem largura: todas no início, num grupo só — o vídeo ainda
 * não abriu, não há onde distribuir.
 */
fun clusterVideoMarkers(
    markers: List<VideoMarker>,
    durationMillis: Long,
    widthPx: Float,
    minDistancePx: Float,
): List<VideoMarkerCluster> {
    if (markers.isEmpty()) return emptyList()
    if (durationMillis <= 0L || widthPx <= 0f) return listOf(VideoMarkerCluster(markers, 0f))
    val grupos = mutableListOf<VideoMarkerCluster>()
    var atual = mutableListOf<VideoMarker>()
    var inicioPx = 0f
    markers.forEach { marca ->
        val x = videoProgressOf(marca.atMillis, durationMillis) * widthPx
        if (atual.isNotEmpty() && x - inicioPx < minDistancePx) {
            atual += marca
        } else {
            if (atual.isNotEmpty()) grupos += VideoMarkerCluster(atual, inicioPx / widthPx)
            atual = mutableListOf(marca)
            inicioPx = x
        }
    }
    grupos += VideoMarkerCluster(atual, inicioPx / widthPx)
    return grupos
}

/**
 * A marca **da vez** em [positionMillis]: a última que já passou há no máximo [windowMillis]. É o
 * que o app usa para mostrar o cartão do comentário enquanto o vídeo passa por ele (e escondê-lo
 * depois). `null` = nenhuma.
 */
fun videoMarkerAt(
    markers: List<VideoMarker>,
    positionMillis: Long,
    windowMillis: Long = DEFAULT_MARKER_WINDOW_MILLIS,
): VideoMarker? = markers
    .filter { it.atMillis <= positionMillis && positionMillis - it.atMillis <= windowMillis }
    .maxWithOrNull(compareBy<VideoMarker>({ it.atMillis }, { it.id }))

/**
 * A próxima marca depois de [positionMillis] — a ação "próxima marca" do leitor de tela e do app.
 * A que está a menos de [MARKER_NAVIGATION_SLACK_MILLIS] à frente conta como "a atual" (a posição
 * logo depois de pular para uma marca é ela mesma, arredondada), e não é devolvida de novo.
 */
fun nextVideoMarker(markers: List<VideoMarker>, positionMillis: Long): VideoMarker? =
    markers.filter { it.atMillis > positionMillis + MARKER_NAVIGATION_SLACK_MILLIS }
        .minWithOrNull(compareBy<VideoMarker>({ it.atMillis }, { it.id }))

/**
 * A marca anterior a [positionMillis]. Como o "anterior" de qualquer player: logo depois de uma
 * marca (até [MARKER_PREVIOUS_SLACK_MILLIS]), volta para a de **antes** dela — senão o botão
 * prenderia a pessoa na marca que acabou de tocar.
 */
fun previousVideoMarker(markers: List<VideoMarker>, positionMillis: Long): VideoMarker? =
    markers.filter { it.atMillis < positionMillis - MARKER_PREVIOUS_SLACK_MILLIS }
        .maxWithOrNull(compareBy<VideoMarker>({ it.atMillis }, { it.id }))

/** O instante sob o ponto [xPx] de uma barra de [widthPx] — o toque e o arrasto na trilha. */
fun timelineMillisAt(xPx: Float, widthPx: Float, durationMillis: Long): Long {
    if (durationMillis <= 0L || widthPx <= 0f || xPx.isNaN()) return 0L
    val fracao = (xPx / widthPx).coerceIn(0f, 1f)
    return (fracao.toDouble() * durationMillis).toLong()
}

/** Por quanto tempo a marca continua "da vez" depois de passar, por default: 3 s. */
const val DEFAULT_MARKER_WINDOW_MILLIS: Long = 3_000L

/** Folga de "já estou nesta marca" do [nextVideoMarker]. */
const val MARKER_NAVIGATION_SLACK_MILLIS: Long = 250L

/** Folga do [previousVideoMarker]: até 1 s depois de uma marca, "anterior" é a de antes dela. */
const val MARKER_PREVIOUS_SLACK_MILLIS: Long = 1_000L
