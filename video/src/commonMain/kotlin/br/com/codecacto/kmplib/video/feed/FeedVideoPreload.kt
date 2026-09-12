package br.com.codecacto.kmplib.video.feed

import br.com.codecacto.kmplib.video.VideoStreamKind

/**
 * O **pré-carregamento** do feed: quanto de cada vídeo vizinho vale a pena buscar antes de a vez
 * chegar nele — e quando parar de buscar.
 *
 * Como o [FeedVideoPolicy], mora em `commonMain` e é **função pura**: Android e iOS decidem com a
 * mesma conta, e a conta é coberta por teste. Quem traduz para o vocabulário da plataforma é o
 * [FeedPreloader] de cada lado.
 *
 * ### Por que os números são pequenos, e por que não se aumenta "só um pouco"
 * Pré-carregar é gastar o plano de dados de outra pessoa com um vídeo que ela **talvez** nem veja.
 * A régua da indústria (documentação da ByteDance para o TikTok, e o demo `ShortFormState` da
 * própria Media3) é a mesma: **poucos segundos, poucos itens, só para frente**, e **cancelar tudo
 * assim que o vídeo que está tocando passa fome** — banda que falta ao vídeo da vez é o único
 * defeito que o usuário enxerga na hora.
 */

/**
 * Quanto pré-carregar de um item, no vocabulário neutro da lib.
 *
 * A diferença entre [Loaded] e [Cached] é **onde** o pedaço fica: o primeiro monta a fonte na
 * memória (pronta para tocar na hora), o segundo só grava no cache de disco (barato de manter, e é
 * o que evita baixar de novo o vídeo que a pessoa passou e voltou).
 */
internal sealed interface FeedPreloadTarget {

    /** Não pré-carregar. É o alvo de tudo que está **para trás** e de tudo além da janela. */
    data object None : FeedPreloadTarget

    /** Preparar a fonte e carregar [millis] a partir do começo, prontos para tocar. */
    data class Loaded(val millis: Long) : FeedPreloadTarget

    /** Só gravar [millis] no cache de disco. Não ocupa decodificador nem memória de player. */
    data class Cached(val millis: Long) : FeedPreloadTarget
}

/** 3 s para o **próximo** vídeo: é o que faz a troca ser instantânea. */
internal const val FEED_PRELOAD_NEXT_MILLIS: Long = 3_000L

/** 1 s para o 2º e o 3º: o bastante para o primeiro quadro, sem pagar o vídeo inteiro. */
internal const val FEED_PRELOAD_AHEAD_MILLIS: Long = 1_000L

/** 5 s **em disco** para o resto da janela — não ocupa player, só evita rebaixar depois. */
internal const val FEED_PRELOAD_CACHE_MILLIS: Long = 5_000L

/** Até 3 itens à frente ganham fonte pronta (o "2–3 itens" da régua). */
internal const val FEED_PRELOAD_LOADED_DISTANCE: Int = 3

/**
 * Até 5 itens à frente entram no cache de disco. **A janela existe para ter fim**: sem teto, uma
 * lista de 200 posts baixaria os 200 em segundo plano.
 */
internal const val FEED_PRELOAD_CACHE_DISTANCE: Int = 5

/**
 * 5 s. Abaixo disto o vídeo da vez está **passando fome**, e todo pré-carregamento para.
 *
 * É o mesmo corte que a documentação da ByteDance usa. O motivo é direto: o pré-carregamento
 * disputa a mesma banda com o vídeo que a pessoa está vendo, e travar o que está na tela para
 * adiantar o que talvez ela pule é o pior negócio possível.
 */
internal const val FEED_PRELOAD_STARVATION_MILLIS: Long = 5_000L

/**
 * O alvo de pré-carregamento de um item que está a [distance] posições do que toca agora.
 *
 * @param distance positivo = **abaixo** na lista (ainda vai ser visto), negativo = já passou,
 *   `0` = o que está tocando.
 * @param paused todo o pré-carregamento está suspenso (vídeo da vez passando fome, ou rede medida
 *   sem autorização). Devolve [FeedPreloadTarget.None] para todo mundo.
 *
 * **Só para frente, e é decisão consciente:** o vídeo que a pessoa já passou tem chance pequena de
 * ser revisto, e o que ela já viu costuma estar no cache de disco de qualquer forma. Pré-carregar
 * para trás dobraria o gasto de dados para cobrir o caso raro.
 */
internal fun feedPreloadTargetFor(distance: Int, paused: Boolean = false): FeedPreloadTarget = when {
    paused -> FeedPreloadTarget.None
    distance <= 0 -> FeedPreloadTarget.None
    distance == 1 -> FeedPreloadTarget.Loaded(FEED_PRELOAD_NEXT_MILLIS)
    distance <= FEED_PRELOAD_LOADED_DISTANCE -> FeedPreloadTarget.Loaded(FEED_PRELOAD_AHEAD_MILLIS)
    distance <= FEED_PRELOAD_CACHE_DISTANCE -> FeedPreloadTarget.Cached(FEED_PRELOAD_CACHE_MILLIS)
    else -> FeedPreloadTarget.None
}

/**
 * `true` quando o vídeo da vez está sem folga de buffer e o pré-carregamento **tem de parar**.
 *
 * @param bufferedAheadMillis quanto há carregado à frente da posição atual; `null` = a plataforma
 *   não sabe informar (o AVPlayer em HLS, por exemplo). **`null` não é fome**: parar o
 *   pré-carregamento por desconhecimento o desligaria para sempre em quem não reporta.
 * @param buffering o vídeo da vez está parado esperando dado. Isto **é** fome, independentemente
 *   do número — e é o sinal que toda plataforma sabe dar.
 */
internal fun shouldCancelFeedPreload(bufferedAheadMillis: Long?, buffering: Boolean): Boolean {
    if (buffering) return true
    val folga = bufferedAheadMillis ?: return false
    return folga < FEED_PRELOAD_STARVATION_MILLIS
}

/**
 * A chave com que um vídeo é guardado no cache de disco — **a URL sem a query**.
 *
 * A query sai de propósito: a URL de um vídeo nosso é **assinada** (`…/post-42.mp4?token=…&exp=…`)
 * e muda a cada abertura da tela. Endereçado pela URL inteira, o mesmo vídeo entraria no cache uma
 * vez por token, o disco encheria de cópias e nenhuma delas seria reaproveitada — que é exatamente
 * o defeito que o cache existe para resolver. Mesma decisão do cache de PDF (`pdfCacheIdFor`) e da
 * chave de download (`MediaDownloadRequest.id`).
 *
 * ⚠️ A contrapartida, registrada: dois vídeos **diferentes** que só se distinguem pela query
 * (`/video?id=1` × `/video?id=2`) colidiriam. Não é o formato de nenhum CDN de vídeo que usamos —
 * o identificador vive no caminho —, mas quem servir vídeo assim precisa saber.
 */
internal fun feedVideoCacheKey(url: String): String {
    val semFragmento = url.substringBefore('#')
    val semQuery = semFragmento.substringBefore('?')
    return semQuery.ifBlank { url }
}

/** Um item do feed do ponto de vista de quem pré-carrega. */
internal data class FeedPreloadItem(
    val url: String,
    val kind: VideoStreamKind,
    val cacheKey: String = feedVideoCacheKey(url),
)

/**
 * Quem adianta os vizinhos do vídeo da vez. Um por feed; criado pelo
 * [rememberFeedVideoController] e alimentado pelo [FeedVideoController] a cada rolagem.
 *
 * - **Android:** `DefaultPreloadManager` da Media3 (ver `FeedVideoPreload.android.kt`).
 * - **iOS:** [NoFeedPreloader]. Não existe equivalente na AVFoundation, e isso **não** deixa o iOS
 *   sem pré-carregamento: lá ele é feito pelos próprios players do pool, com
 *   `preferredForwardBufferDuration` curto nos itens fora da vez (ver `FeedVideoEngine.ios.kt`).
 */
internal interface FeedPreloader {

    /**
     * O estado do feed mudou.
     *
     * @param items os itens **em ordem de tela**, de cima para baixo.
     * @param currentIndex a posição do que toca agora em [items]; `-1` = nenhum.
     * @param paused suspender todo o pré-carregamento — ver [shouldCancelFeedPreload].
     */
    fun update(items: List<FeedPreloadItem>, currentIndex: Int, paused: Boolean)

    /** Esquece os itens, mantendo o subsistema vivo (o feed foi para o segundo plano). */
    fun reset()

    /** A tela saiu de vez. */
    fun release()
}

/** O pré-carregador que não pré-carrega nada. Default do controller e a implementação do iOS. */
internal object NoFeedPreloader : FeedPreloader {
    override fun update(items: List<FeedPreloadItem>, currentIndex: Int, paused: Boolean) = Unit
    override fun reset() = Unit
    override fun release() = Unit
}

/** Cria o pré-carregador da plataforma. Só o [rememberFeedVideoController] chama. */
internal expect fun createFeedPreloader(config: FeedVideoConfig): FeedPreloader
