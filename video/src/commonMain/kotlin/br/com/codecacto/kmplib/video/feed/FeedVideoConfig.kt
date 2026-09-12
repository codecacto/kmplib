package br.com.codecacto.kmplib.video.feed

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Como o feed decide a vez, quanto de memória usa e quanto adianta dos vizinhos.
 *
 * @param playThreshold a fração visível a partir da qual o vídeo toca. Ver [FEED_VIDEO_PLAY_THRESHOLD].
 * @param switchMargin quanto um vídeo precisa estar mais visível que o atual para tomar a vez. Ver
 *   [FEED_VIDEO_SWITCH_MARGIN].
 * @param maxPlayers o **tamanho do pool** — o teto de players nativos vivos ao mesmo tempo, não
 *   importa quantos vídeos a lista tenha. `2` = o que toca + o que está entrando na tela, já
 *   preparado (o primeiro quadro aparece antes de ele ganhar a vez). `1` desliga o
 *   pré-carregamento por player: menos memória e menos dado móvel, ao custo de uma espera a cada
 *   troca. Cada player segura decodificador e buffer; é por isso que o teto existe e é pequeno.
 * @param visibilityThrottleMillis de quanto em quanto tempo, no máximo, a posição de cada item é
 *   reavaliada durante a rolagem. 100 ms basta para o vídeo reagir "na hora" sem recalcular a cada
 *   quadro.
 * @param diskCacheBytes o teto do **cache de disco** do feed. Ver [FEED_VIDEO_DISK_CACHE_BYTES].
 *   ⚠️ **Android.** No iOS não há efeito: a AVFoundation não expõe cache de disco configurável para
 *   reprodução, e o que existe (o cache de HTTP do sistema) não é endereçável por aqui.
 *   ⚠️ Vale o **primeiro** feed do processo — um cache aberto não muda de tamanho (a lib avisa no
 *   log se um segundo feed pedir outro teto).
 * @param preloadEnabled adiantar os vizinhos do vídeo da vez. Ver [FeedPreloadTarget] para a escada
 *   (3 s no próximo, 1 s no 2º e 3º, 5 s em disco até o 5º) e [shouldCancelFeedPreload] para o
 *   cancelamento. Desligar deixa o feed só com o pool — funciona, e cada troca espera o vídeo abrir.
 * @param preloadOnMeteredNetwork adiantar vizinhos **também** em rede medida (dados móveis, Wi-Fi
 *   marcado como limitado) e com o Data Saver ligado. Default `false`, e é a escolha certa para o
 *   Brasil: pré-carregar é gastar o plano de dados de alguém com um vídeo que talvez ela pule.
 *   ⚠️ Isto **não** é "só tocar no Wi-Fi": o vídeo que a pessoa está vendo toca sempre, em qualquer
 *   rede. O que a chave corta é só o adiantamento do que ela ainda não pediu.
 */
@Immutable
data class FeedVideoConfig(
    val playThreshold: Float = FEED_VIDEO_PLAY_THRESHOLD,
    val switchMargin: Float = FEED_VIDEO_SWITCH_MARGIN,
    val maxPlayers: Int = DEFAULT_FEED_VIDEO_PLAYERS,
    val visibilityThrottleMillis: Long = 100L,
    val diskCacheBytes: Long = FEED_VIDEO_DISK_CACHE_BYTES,
    val preloadEnabled: Boolean = true,
    val preloadOnMeteredNetwork: Boolean = false,
) {
    init {
        require(playThreshold > 0f && playThreshold <= 1f) {
            "playThreshold deve estar em (0, 1] — veio $playThreshold"
        }
        require(switchMargin >= 0f) { "switchMargin não pode ser negativo — veio $switchMargin" }
        require(maxPlayers >= 1) { "maxPlayers deve ser pelo menos 1 — veio $maxPlayers" }
        require(visibilityThrottleMillis >= 0L) { "visibilityThrottleMillis não pode ser negativo" }
        require(diskCacheBytes > 0L) { "diskCacheBytes deve ser positivo — veio $diskCacheBytes" }
    }
}

/** O pool default: o que toca + o próximo, preparado. */
const val DEFAULT_FEED_VIDEO_PLAYERS: Int = 2

/**
 * 128 MB de cache de disco para o feed.
 *
 * A conta: vídeo de feed é curto (até ~60 s) e, num bitrate de celular, cabe em 5–15 MB. 128 MB
 * guardam algo como uma sessão inteira de rolagem — o suficiente para a pessoa voltar num post que
 * já passou sem baixá-lo de novo — sem virar um peso no aparelho. Fica no `cacheDir`, então o
 * Android o recolhe sozinho quando o disco aperta.
 */
const val FEED_VIDEO_DISK_CACHE_BYTES: Long = 128L * 1024 * 1024

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
