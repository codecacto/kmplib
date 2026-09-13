package br.com.codecacto.kmplib.video.feed

/**
 * A **posição estável** de cada vídeo no espaço de coordenadas do pré-carregador.
 *
 * ### Por que isto existe (e não é indireção gratuita)
 * O `DefaultPreloadManager` da Media3 identifica cada item por um `rankingData`, e esse campo é
 * **`public final T`**: ele é fixado na construção do `MediaSourceHolder` e **não muda mais**
 * (conferido com `javap` no `media3-exoplayer-1.11.1`). Quem pergunta "quanto pré-carregar deste
 * item?" recebe o valor **congelado no dia em que o item entrou**.
 *
 * Enquanto isso, o índice do item **na janela composta** muda a cada rolagem: a `LazyColumn`
 * compõe uma faixa de 4-5 posts, e o que era o 4º vira o 3º, o 2º, o 1º. Se o `rankingData` for o
 * índice da janela, o item somado na posição 3 pergunta **para sempre** por 3 — enquanto a posição
 * 3 já é de outro vídeo. A escada passa a responder a distância de um item **para outro**: o
 * próximo vídeo pode receber "não pré-carregue" (primeiro quadro lento, exatamente o que a feature
 * existe para evitar) enquanto um que já passou recebe os 3 s. Não trava, não aparece na tela —
 * **gasta o plano de dados no vizinho errado** e entrega menos do que promete.
 *
 * A saída é dar a cada URL uma posição **que não se mexe enquanto ele estiver no manager**, num
 * espaço de coordenadas do feed inteiro (e não da janela). Com isso o `rankingData` congelado
 * continua verdadeiro, e a distância vira **aritmética** (`posição − posiçãoDoQueToca`), sem mapa
 * auxiliar para ficar velho.
 *
 * ### As duas saídas que NÃO servem, e por quê (medido no artefato, não suposto)
 * - **Re-`add` a cada `update`**: `BasePreloadManager.add` cria uma `MediaSource` nova, um
 *   `PreloadMediaSource` novo e um holder novo, e grava com `HashMap.put` — que **substitui sem
 *   liberar** o holder anterior (não há `release()` no caminho). Seria um vazamento por item por
 *   rolagem, jogando fora a fonte já preparada justamente para preparar tudo de novo.
 * - **Chave estável arbitrária** (um id que não seja posição): o `SimpleRankingDataComparator`
 *   ordena por `abs(rankingData − currentPlayingIndex)`. O `rankingData` **é** lido como posição;
 *   um id qualquer manteria o alvo certo e embaralharia a **ordem** em que os vizinhos são
 *   adiantados.
 *
 * Trocar a posição de um item, quando ela realmente muda, é `remove` + `add` — e aí sim o holder
 * antigo é liberado (`remove` chama `releaseMediaSourceHolderInternal`).
 *
 * ### O contrato
 * As posições da janela são **contíguas e na ordem da tela**, então a distância entre vizinhos é 1
 * — que é o que a escada de [feedPreloadTargetFor] espera. Item que sai da janela é **podado**
 * (o mapa não cresce com a rolagem), e ao voltar recebe posição nova.
 */
internal data class FeedPreloadSlot(val url: String, val position: Int)

/**
 * Onde começa o espaço de posições: **no meio do `Int`**, não no zero (2.203.0).
 *
 * Rolar para cima acima da primeira janela faz a base diminuir, e partindo do zero o item logo acima
 * recebia a posição **`-1`** — que é justamente o "ninguém tocando" (`C_INDICE_NENHUM`) do
 * `setCurrentPlayingIndex` da Media3. O vídeo da vez nessa posição fazia o manager entender que não
 * havia vídeo tocando, e a escada inteira se desligava. Com a origem em `Int.MAX_VALUE / 2` a posição
 * só chegaria a negativo depois de ~1 bilhão de itens rolados para cima, e só estouraria para cima
 * depois de ~1 bilhão de itens de feeds novos no mesmo pré-carregador — nenhum dos dois é um feed.
 */
internal const val FEED_PRELOAD_POSITION_ORIGIN: Int = Int.MAX_VALUE / 2

/**
 * O que mudou entre duas janelas.
 *
 * @param slots a janela inteira, na ordem da tela, já sem URL repetida.
 * @param added itens que o manager ainda não conhece → `add`.
 * @param moved itens cuja posição mudou de verdade (reordenação, novo feed) → `remove` + `add`,
 *   porque o `rankingData` de um holder não é editável. Deve ser **raro**: rolar não reordena.
 * @param removed URLs que saíram da janela → `remove`.
 */
internal data class FeedPreloadPositionUpdate(
    val slots: List<FeedPreloadSlot>,
    val added: List<FeedPreloadSlot>,
    val moved: List<FeedPreloadSlot>,
    val removed: List<String>,
)

/**
 * O registro de posições estáveis do feed. Um por pré-carregador; não é thread-safe (é alimentado
 * pelo controller, sempre na thread da UI).
 */
internal class FeedPreloadPositions {

    private val posicoes = LinkedHashMap<String, Int>()

    /**
     * De onde parte uma janela que não tem **nenhum** conhecido (primeira carga, feed trocado).
     *
     * Só cresce: assim uma janela nova nunca reaproveita a posição de um item que acabou de sair,
     * o que evita que um holder ainda não liberado responda no lugar do recém-chegado.
     */
    private var proximaBase = FEED_PRELOAD_POSITION_ORIGIN

    /** Quantas URLs estão registradas agora. É o tamanho da janela — nunca o do feed. */
    val size: Int get() = posicoes.size

    /** A posição de [url] no espaço do feed, ou `null` se ela não está na janela. */
    fun positionOf(url: String): Int? = posicoes[url]

    /**
     * Registra a janela [urls] (de cima para baixo) e devolve o que mudou.
     *
     * A âncora é o **primeiro conhecido**: a base é escolhida de forma que ele mantenha a posição
     * que já tinha, e os demais saem contíguos a partir dela. É isso que faz rolar **não** mexer em
     * quem ficou — inclusive rolando para cima, quando a base fica menor. O que importa é a diferença
     * entre elas, mas o valor absoluto **nunca fica negativo**: ver [FEED_PRELOAD_POSITION_ORIGIN].
     */
    fun update(urls: List<String>): FeedPreloadPositionUpdate {
        val janela = urls.distinct()
        if (janela.isEmpty()) {
            val removidos = posicoes.keys.toList()
            posicoes.clear()
            return FeedPreloadPositionUpdate(emptyList(), emptyList(), emptyList(), removidos)
        }

        val ancora = janela.indexOfFirst { posicoes.containsKey(it) }
        val base = if (ancora >= 0) posicoes.getValue(janela[ancora]) - ancora else proximaBase

        val slots = ArrayList<FeedPreloadSlot>(janela.size)
        val entrando = ArrayList<FeedPreloadSlot>()
        val movidos = ArrayList<FeedPreloadSlot>()
        janela.forEachIndexed { indice, url ->
            val slot = FeedPreloadSlot(url, base + indice)
            slots += slot
            val anterior = posicoes[url]
            when {
                anterior == null -> entrando += slot
                anterior != slot.position -> movidos += slot
            }
        }

        val vivos = janela.toSet()
        val saindo = posicoes.keys.filter { it !in vivos }

        posicoes.clear()
        slots.forEach { posicoes[it.url] = it.position }
        proximaBase = maxOf(proximaBase, base + janela.size)

        return FeedPreloadPositionUpdate(slots, entrando, movidos, saindo)
    }

    /**
     * Esquece a janela. Não devolve as posições ao começo de propósito: o feed volta com
     * coordenadas novas, e nenhuma delas colide com um holder que ainda esteja sendo liberado.
     */
    fun clear() {
        posicoes.clear()
    }
}
