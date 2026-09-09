package br.com.codecacto.kmplib.video

/**
 * Leitor de **WebVTT** e **SubRip (.srt)** — os dois formatos que um arquivo de legenda solto tem
 * no mundo real.
 *
 * ### Por que a lib interpreta a legenda em vez de entregá-la ao player
 *
 * A **embutida no HLS** é da plataforma: o ExoPlayer e o AVPlayer a descobrem no manifesto e a
 * desenham sozinhos, e é assim que tem de ser.
 *
 * A **externa** é outra história, e é onde as duas plataformas divergem: o ExoPlayer aceita um
 * arquivo lateral (`MediaItem.SubtitleConfiguration`), e o iOS **não tem API pública** para
 * acrescentar uma faixa a um HLS remoto — o caminho oficial da Apple é um
 * `AVAssetResourceLoaderDelegate` que reescreve o manifesto no ar, o que é um subsistema inteiro,
 * não um parâmetro. Deixar cada plataforma com o seu caminho daria legenda no Android e nenhuma no
 * iOS, com build verde nos dois.
 *
 * A saída correta é interpretar o arquivo **uma vez, em `commonMain`**, e desenhar a fala como
 * texto sobre o vídeo nas duas plataformas — determinístico, idêntico, e coberto por teste (que é
 * o que nem o ExoPlayer nem o AVPlayer permitiriam aqui).
 *
 * ### O que o leitor cobre
 * - cabeçalho `WEBVTT`, comentários `NOTE`, blocos `STYLE`/`REGION` (ignorados);
 * - numeração de bloco do `.srt`;
 * - carimbos `hh:mm:ss,mmm` (SRT) e `hh:mm:ss.mmm` / `mm:ss.mmm` (VTT);
 * - *cue settings* depois do `-->` (`align:start position:10%`), descartadas;
 * - as marcações inline mais comuns do VTT/SRT (`<i>`, `<b>`, `<v Fulano>`, `<c.classe>`), tiradas
 *   do texto — o overlay é um `Text`, não um renderizador de rich text.
 *
 * Bloco malformado é **pulado**, nunca lançado: legenda quebrada não pode derrubar a aula.
 */
fun parseSubtitles(content: String): List<SubtitleCue> {
    val cues = ArrayList<SubtitleCue>()
    // \r\n do Windows e \r solto do macOS clássico: sem isto o `\r` fica colado no carimbo e a
    // conversão para número falha em todo bloco de um arquivo inteiro.
    val linhas = content.replace("\r\n", "\n").replace('\r', '\n').split('\n')

    var i = 0
    while (i < linhas.size) {
        val linha = linhas[i].trim()
        if (!linha.contains("-->")) {
            i++
            continue
        }

        val tempos = linha.split("-->")
        if (tempos.size != 2) {
            i++
            continue
        }
        val inicio = parseSubtitleTimestamp(tempos[0].trim())
        // O fim vem grudado nas *cue settings* do VTT: "00:00:04.000 align:start position:10%".
        val fim = parseSubtitleTimestamp(tempos[1].trim().substringBefore(' '))
        if (inicio == null || fim == null || fim <= inicio) {
            i++
            continue
        }

        val texto = StringBuilder()
        i++
        while (i < linhas.size && linhas[i].isNotBlank()) {
            if (texto.isNotEmpty()) texto.append('\n')
            texto.append(linhas[i].trim())
            i++
        }

        val limpo = stripSubtitleMarkup(texto.toString())
        if (limpo.isNotEmpty()) cues += SubtitleCue(inicio, fim, limpo)
    }

    // Ordenar é pré-condição da busca binária do `cueAt`, e arquivo gerado por ferramenta de
    // terceiro nem sempre vem em ordem.
    return cues.sortedBy { it.startMillis }
}

/**
 * `hh:mm:ss,mmm` · `hh:mm:ss.mmm` · `mm:ss.mmm` → milissegundos. `null` quando não é carimbo.
 *
 * Os milissegundos são normalizados por comprimento (`.5` = 500 ms, `.05` = 50 ms): truncar em três
 * dígitos sem completar faria `.5` virar 5 ms e a fala piscar.
 */
fun parseSubtitleTimestamp(raw: String): Long? {
    val texto = raw.trim().replace(',', '.')
    if (texto.isEmpty()) return null

    val partes = texto.split(':')
    if (partes.size !in 2..3) return null

    val horas: Long
    val minutos: Long
    val segundosParte: String
    if (partes.size == 3) {
        horas = partes[0].toLongOrNull() ?: return null
        minutos = partes[1].toLongOrNull() ?: return null
        segundosParte = partes[2]
    } else {
        horas = 0
        minutos = partes[0].toLongOrNull() ?: return null
        segundosParte = partes[1]
    }
    if (minutos !in 0..59) return null

    val segundos = segundosParte.substringBefore('.').toLongOrNull() ?: return null
    if (segundos !in 0..59) return null

    val fracao = segundosParte.substringAfter('.', "")
    val millis = when {
        fracao.isEmpty() -> 0L
        !fracao.all { it.isDigit() } -> return null
        else -> (fracao.take(3).padEnd(3, '0')).toLong()
    }

    return ((horas * 60 + minutos) * 60 + segundos) * 1000 + millis
}

/** Tira as marcações `<i>`, `<b>`, `<v Fulano>`, `<c.classe>` e as entidades básicas do VTT. */
fun stripSubtitleMarkup(text: String): String {
    val sb = StringBuilder(text.length)
    var dentroDeTag = false
    for (c in text) {
        when {
            c == '<' -> dentroDeTag = true
            c == '>' -> dentroDeTag = false
            !dentroDeTag -> sb.append(c)
        }
    }
    return sb.toString()
        .replace("&lrm;", "")
        .replace("&rlm;", "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()
}
