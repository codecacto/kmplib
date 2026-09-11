package br.com.codecacto.kmplib.video.feed

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.LayoutBoundsHolder
import br.com.codecacto.kmplib.video.VideoStatus
import br.com.codecacto.kmplib.video.VideoStreamKind

/**
 * O **coordenador** dos vídeos de um feed: decide quem toca, empresta os players do pool e aplica o
 * som. Um por feed — obtenha com [rememberFeedVideoController] (ou deixe o [FeedVideoHost] criar).
 *
 * ### O que ele garante
 * - **Um vídeo toca por vez**: o mais visível acima de [FeedVideoConfig.playThreshold]
 *   ([pickFeedVideoToPlay]). Os outros ficam na capa ou parados no primeiro quadro.
 * - **Players não se multiplicam com a lista.** São no máximo [FeedVideoConfig.maxPlayers], criados
 *   sob demanda e **reciclados** de item em item ([assignFeedVideoSlots]). Uma `LazyColumn` com 200
 *   posts de vídeo tem, no pior caso, 2 players vivos.
 * - **Item que sai da composição devolve o player esvaziado** (sem decodificador, sem buffer); ao ir
 *   para o segundo plano (`ON_STOP`), **todos os players são destruídos** e recriados na volta.
 * - **Som global** ([sound]): o mesmo estado para todos os vídeos.
 *
 * ### Pausar o feed sem sair da tela
 * [isPlaybackEnabled] = `false` pausa o feed inteiro: um bottom sheet por cima, uma aba que ficou
 * escondida mas continua composta. O ciclo de vida já cobre a navegação para outra tela e o app indo
 * para o segundo plano — isto é para o que ele não enxerga.
 */
@Stable
class FeedVideoController internal constructor(
    /** A configuração com que o controller nasceu. */
    val config: FeedVideoConfig,
    /** O som do feed. Ver [FeedVideoSoundState]. */
    val sound: FeedVideoSoundState,
    private val engineFactory: () -> FeedVideoEngine,
) {

    /** A área do feed: o retângulo contra o qual a visibilidade de cada item é medida. */
    internal val viewport: LayoutBoundsHolder = LayoutBoundsHolder()

    private class Entrada(
        var url: String,
        var kind: VideoStreamKind,
        var fraction: Float = 0f,
        var top: Float = 0f,
    )

    private class Slot(val engine: FeedVideoEngine) {
        /** A última chave que usou este player — para saber se ele pode ser esvaziado. */
        var ultimaChave: Any? = null
    }

    private val entradas = LinkedHashMap<Any, Entrada>()
    private val slots = mutableListOf<Slot>()
    private var atribuicao: Map<Any, Int> by mutableStateOf(emptyMap())
    private var emPrimeiroPlano = true
    private var liberado = false

    /** O item que tem a vez de tocar agora, ou `null`. */
    internal var activeKey: Any? by mutableStateOf(null)
        private set

    /**
     * `false` pausa o feed inteiro sem sair da tela (sheet por cima, aba escondida). Os players
     * continuam preparados: voltar a `true` retoma na hora.
     */
    val isPlaybackEnabled: Boolean get() = reproducaoLigada

    // Apoio privado: um `var … private set` geraria `setPlaybackEnabled(Z)V` e colidiria com a ação
    // abaixo no alvo JVM.
    private var reproducaoLigada: Boolean by mutableStateOf(true)

    /** Liga/desliga a reprodução do feed. Ver [isPlaybackEnabled]. */
    fun setPlaybackEnabled(enabled: Boolean) {
        if (reproducaoLigada == enabled) return
        reproducaoLigada = enabled
        recalcular()
    }

    /** `true` quando há um vídeo andando agora (com ou sem som). */
    val isPlaying: Boolean
        get() = engineFor(activeKey ?: return false)?.status == VideoStatus.Playing

    /** Liga ou desliga o som do feed inteiro. */
    fun setMuted(muted: Boolean) {
        sound.setMuted(muted)
        applySound()
    }

    /** O que o botão de alto-falante faz. */
    fun toggleMuted() = setMuted(!sound.isMuted)

    // ---------------------------------------------------------------------------------------
    // Registro dos itens — chamado pelo `FeedVideo`
    // ---------------------------------------------------------------------------------------

    /** O player emprestado a [key], ou `null` (o item mostra a capa). Estado do Compose. */
    internal fun engineFor(key: Any): FeedVideoEngine? = atribuicao[key]?.let { slots.getOrNull(it)?.engine }

    /** Declara (ou troca) a fonte de [key]. Trocar a URL — renovação de URL assinada — recarrega. */
    internal fun setSource(key: Any, url: String, kind: VideoStreamKind) {
        val atual = entradas[key]
        if (atual == null) {
            entradas[key] = Entrada(url, kind)
            return
        }
        if (atual.url == url && atual.kind == kind) return
        atual.url = url
        atual.kind = kind
        recalcular()
    }

    /** A posição de [key] mudou (rolagem). Ignorado enquanto a fonte não foi declarada. */
    internal fun report(key: Any, visibleFraction: Float, top: Float) {
        val entrada = entradas[key] ?: return
        if (entrada.fraction == visibleFraction && entrada.top == top) return
        entrada.fraction = visibleFraction
        entrada.top = top
        recalcular()
    }

    /** O item saiu da composição. O player dele volta ao pool **esvaziado**. */
    internal fun remove(key: Any) {
        if (entradas.remove(key) == null) return
        recalcular()
    }

    /** Recarrega o player de [key] — o "tentar de novo" depois de um erro. */
    internal fun retry(key: Any) {
        val entrada = entradas[key] ?: return
        val engine = engineFor(key) ?: return
        engine.load(entrada.url, entrada.kind)
        engine.setMuted(sound.isMuted)
        aplicarReproducao()
    }

    // ---------------------------------------------------------------------------------------
    // Ciclo de vida — chamado pelo `rememberFeedVideoController`
    // ---------------------------------------------------------------------------------------

    /** `ON_START`: volta a preparar e tocar o que está na tela. */
    internal fun onStart() {
        if (liberado || emPrimeiroPlano) return
        emPrimeiroPlano = true
        recalcular()
    }

    /**
     * `ON_STOP`: **destrói todos os players**. Não é só pausar: um feed no segundo plano não tem por
     * que segurar dois decodificadores de vídeo, e a Media3 recomenda exatamente isto (liberar em
     * `onStop`). Na volta eles são recriados, e a capa cobre a espera.
     */
    internal fun onStop() {
        if (!emPrimeiroPlano) return
        emPrimeiroPlano = false
        destruirPlayers()
    }

    /** A tela saiu: destrói tudo. O controller não é mais usado depois disto. */
    internal fun release() {
        liberado = true
        emPrimeiroPlano = false
        entradas.clear()
        destruirPlayers()
    }

    /** Leitura periódica do estado nativo (no-op no Android). */
    internal fun poll() {
        slots.forEach { it.engine.refresh() }
    }

    /** Aplica o som atual a todos os players do pool. */
    internal fun applySound() {
        val mudo = sound.isMuted
        slots.forEach { it.engine.setMuted(mudo) }
    }

    // ---------------------------------------------------------------------------------------

    private fun recalcular() {
        if (liberado || !emPrimeiroPlano) return

        val candidatos = entradas.map { (k, e) -> FeedVideoCandidate(k, e.fraction, e.top) }
        val novoAtivo = if (isPlaybackEnabled) {
            pickFeedVideoToPlay(candidatos, activeKey, config.playThreshold, config.switchMargin)
        } else {
            null
        }
        val desejados = feedVideosToPrepare(candidatos, novoAtivo, config.maxPlayers)
        val novaAtribuicao = assignFeedVideoSlots(
            wanted = desejados,
            current = atribuicao,
            slotSources = slots.map { it.engine.loadedUrl },
            poolSize = config.maxPlayers,
            sourceOf = { entradas.getValue(it).url },
        )

        // O pool cresce sob demanda, até o teto — nunca além.
        val maiorIndice = novaAtribuicao.values.maxOrNull() ?: -1
        while (slots.size <= maiorIndice) {
            slots += Slot(novoPlayer())
        }

        for ((key, indice) in novaAtribuicao) {
            val entrada = entradas.getValue(key)
            val slot = slots[indice]
            val engine = slot.engine
            val recemAtribuido = atribuicao[key] != indice
            // Recarrega só quando precisa: a fonte é outra, ou o item acabou de ganhar este player e
            // o que está nele falhou (voltar à tela é a segunda chance do vídeo que deu erro).
            if (engine.loadedUrl != entrada.url || (recemAtribuido && engine.status is VideoStatus.Error)) {
                engine.load(entrada.url, entrada.kind)
            }
            slot.ultimaChave = key
        }

        // Player sem dono cujo último item nem existe mais: esvazia (solta decodificador e buffer).
        // O que ainda está composto por perto fica carregado — se voltar, retoma sem abrir de novo.
        val emUso = novaAtribuicao.values.toSet()
        slots.forEachIndexed { indice, slot ->
            if (indice !in emUso && slot.ultimaChave != null && slot.ultimaChave !in entradas) {
                slot.engine.clear()
                slot.ultimaChave = null
            }
        }

        if (novaAtribuicao != atribuicao) atribuicao = novaAtribuicao
        if (novoAtivo != activeKey) activeKey = novoAtivo
        aplicarReproducao()
    }

    private fun aplicarReproducao() {
        val indiceAtivo = activeKey?.let { atribuicao[it] }
        slots.forEachIndexed { indice, slot ->
            if (indice == indiceAtivo) slot.engine.play() else slot.engine.pause()
        }
    }

    private fun novoPlayer(): FeedVideoEngine = engineFactory().also { engine ->
        engine.setMuted(sound.isMuted)
        engine.onAudioLost = { perdeuAudio(engine) }
    }

    /**
     * Outro app tomou o áudio com o vídeo tocando com som: o feed volta a **mudo** e o vídeo segue.
     * Ver [FeedVideoEngine.onAudioLost].
     */
    private fun perdeuAudio(engine: FeedVideoEngine) {
        setMuted(true)
        val indiceAtivo = activeKey?.let { atribuicao[it] } ?: return
        if (slots.getOrNull(indiceAtivo)?.engine === engine) engine.play()
    }

    private fun destruirPlayers() {
        slots.forEach {
            it.engine.onAudioLost = null
            it.engine.release()
        }
        slots.clear()
        if (atribuicao.isNotEmpty()) atribuicao = emptyMap()
        if (activeKey != null) activeKey = null
    }
}
