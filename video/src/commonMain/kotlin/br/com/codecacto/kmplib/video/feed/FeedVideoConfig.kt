package br.com.codecacto.kmplib.video.feed

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Como o feed decide a vez e quanto de memória ele usa.
 *
 * @param playThreshold a fração visível a partir da qual o vídeo toca. Ver [FEED_VIDEO_PLAY_THRESHOLD].
 * @param switchMargin quanto um vídeo precisa estar mais visível que o atual para tomar a vez. Ver
 *   [FEED_VIDEO_SWITCH_MARGIN].
 * @param maxPlayers o **tamanho do pool** — o teto de players nativos vivos ao mesmo tempo, não
 *   importa quantos vídeos a lista tenha. `2` = o que toca + o que está entrando na tela, já
 *   preparado (o primeiro quadro aparece antes de ele ganhar a vez). `1` desliga o
 *   pré-carregamento: menos memória e menos dado móvel, ao custo de uma espera a cada troca.
 *   Cada player segura decodificador e buffer; é por isso que o teto existe e é pequeno.
 * @param visibilityThrottleMillis de quanto em quanto tempo, no máximo, a posição de cada item é
 *   reavaliada durante a rolagem. 100 ms basta para o vídeo reagir "na hora" sem recalcular a cada
 *   quadro.
 */
@Immutable
data class FeedVideoConfig(
    val playThreshold: Float = FEED_VIDEO_PLAY_THRESHOLD,
    val switchMargin: Float = FEED_VIDEO_SWITCH_MARGIN,
    val maxPlayers: Int = DEFAULT_FEED_VIDEO_PLAYERS,
    val visibilityThrottleMillis: Long = 100L,
) {
    init {
        require(playThreshold > 0f && playThreshold <= 1f) {
            "playThreshold deve estar em (0, 1] — veio $playThreshold"
        }
        require(switchMargin >= 0f) { "switchMargin não pode ser negativo — veio $switchMargin" }
        require(maxPlayers >= 1) { "maxPlayers deve ser pelo menos 1 — veio $maxPlayers" }
        require(visibilityThrottleMillis >= 0L) { "visibilityThrottleMillis não pode ser negativo" }
    }
}

/** O pool default: o que toca + o próximo, preparado. */
const val DEFAULT_FEED_VIDEO_PLAYERS: Int = 2

/** Como o quadro ocupa a caixa do post. */
enum class FeedVideoScale {
    /** Preenche a caixa inteira, cortando o que sobra (o feed do Instagram). */
    Crop,

    /** O vídeo inteiro, com faixa onde a proporção não bate. */
    Fit,
}

/**
 * As cores do vídeo de feed.
 *
 * Como no [br.com.codecacto.kmplib.video.VideoPlayerColors], o botão fica **sobre o vídeo** — uma
 * imagem qualquer —, e não sobre a `surface` do app: branco sobre círculo escuro translúcido é o que
 * se enxerga sobre qualquer quadro. As cores são configuráveis para o app que quiser a marca.
 *
 * @param background o fundo da caixa enquanto nem a capa carregou.
 * @param soundIcon o alto-falante.
 * @param soundButtonBackground o círculo por trás do alto-falante.
 * @param indicator a roda de espera e o ícone de erro.
 */
@Immutable
data class FeedVideoColors(
    val background: Color = Color.Black,
    val soundIcon: Color = Color.White,
    val soundButtonBackground: Color = Color.Black.copy(alpha = 0.55f),
    val indicator: Color = Color.White,
)

/**
 * Os textos do vídeo de feed — todos para **leitor de tela**; nada disso aparece escrito.
 *
 * O botão de som é só um ícone, e sem descrição o TalkBack/VoiceOver o anuncia como "botão", sem
 * dizer o que ele faz.
 */
@Immutable
data class FeedVideoTexts(
    /** Descrição do botão quando o feed está mudo (o toque LIGA o som). */
    val turnSoundOn: String = "Ativar som",
    /** Descrição do botão quando o feed tem som (o toque DESLIGA). */
    val turnSoundOff: String = "Desativar som",
    /** O estado anunciado junto do botão quando o feed está mudo. */
    val mutedState: String = "Sem som",
    /** O estado anunciado quando o feed tem som. */
    val unmutedState: String = "Com som",
    /** O que o leitor de tela diz ao tocar no vídeo, quando há `onClick`. */
    val openLabel: String = "Abrir publicação",
    /** A descrição do indicador de erro. */
    val playbackError: String = "Não foi possível reproduzir o vídeo",
)
