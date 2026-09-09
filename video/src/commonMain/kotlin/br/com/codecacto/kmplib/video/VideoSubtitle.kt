package br.com.codecacto.kmplib.video

/**
 * Uma faixa de legenda em **arquivo externo** (`.vtt` ou `.srt`), fora do manifesto.
 *
 * @param label o que o usuário lê no menu ("Português", "English").
 * @param language a etiqueta BCP-47 (`pt-BR`, `en`). Serve para casar com a faixa embutida de mesma
 *   língua e para a preferência inicial.
 * @param url de onde baixar. Pode ser assinada como a do vídeo.
 * @param content o arquivo já em mãos, quando o app o embarca ou o baixou antes. Tendo [content],
 *   a [url] não é usada.
 */
data class VideoSubtitleTrack(
    val label: String,
    val language: String,
    val url: String? = null,
    val content: String? = null,
)

/**
 * Uma faixa **disponível para escolher** no menu de legendas do player, já misturando as duas
 * origens.
 *
 * @param id identidade estável dentro da sessão. Para a embutida é o índice/id que a plataforma
 *   deu; para a externa, a URL (ou o rótulo).
 * @param embedded `true` quando a faixa veio dentro do manifesto (HLS) e quem a desenha é a
 *   plataforma; `false` quando é arquivo externo, desenhado por nós — ver [SubtitleCue].
 */
data class VideoSubtitleOption(
    val id: String,
    val label: String,
    val language: String,
    val embedded: Boolean,
)

/** Uma fala: de [startMillis] a [endMillis], o texto [text] (pode ter mais de uma linha). */
data class SubtitleCue(
    val startMillis: Long,
    val endMillis: Long,
    val text: String,
)

/**
 * A fala que vale em [positionMillis], ou `null` no silêncio entre duas.
 *
 * Busca **binária** sobre a lista ordenada: um arquivo de aula de 40 minutos passa de mil falas, e
 * isto roda a cada tique do relógio de progresso (4×/s). Varredura linear ali é trabalho inútil em
 * cada quadro.
 *
 * Pré-condição: [cues] ordenada por [SubtitleCue.startMillis] — é o que [parseSubtitles] devolve.
 */
fun cueAt(cues: List<SubtitleCue>, positionMillis: Long): SubtitleCue? {
    var baixo = 0
    var alto = cues.lastIndex
    var candidato: SubtitleCue? = null
    while (baixo <= alto) {
        val meio = (baixo + alto) / 2
        val cue = cues[meio]
        when {
            positionMillis < cue.startMillis -> alto = meio - 1
            else -> {
                candidato = cue
                baixo = meio + 1
            }
        }
    }
    // O candidato é a última fala que já começou; ela só vale se ainda não terminou.
    return candidato?.takeIf { positionMillis < it.endMillis }
}
