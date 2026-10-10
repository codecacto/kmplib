package br.com.codecacto.kmplib.video

/**
 * Milissegundos → o relógio que o player mostra: `"4:07"` até uma hora, `"1:04:07"` a partir dela.
 *
 * A hora só aparece quando existe, como em todo player conhecido — `"0:04:07"` numa aula de quatro
 * minutos é ruído. Valor negativo ou desconhecido vira `"0:00"`, e nunca `"-1:-1"`: enquanto a
 * duração não chega (HLS ao vivo, manifesto ainda abrindo) ela é `0`, e a barra tem de desenhar
 * alguma coisa.
 */
fun formatVideoTime(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val segundos = total % 60
    val minutos = (total / 60) % 60
    val horas = total / 3600
    val ss = if (segundos < 10) "0$segundos" else "$segundos"
    return if (horas > 0) {
        val mm = if (minutos < 10) "0$minutos" else "$minutos"
        "$horas:$mm:$ss"
    } else {
        "$minutos:$ss"
    }
}

/**
 * Onde o botão de ±10 s deve parar.
 *
 * Grampeia nas duas pontas: sem isto, "voltar 10 s" nos primeiros cinco segundos manda o player
 * para uma posição negativa (o ExoPlayer aceita e o AVPlayer reclama), e "avançar 10 s" perto do
 * fim dispara o `Ended` antes da hora.
 *
 * Duração `0` (ainda desconhecida) faz o alvo ser só grampeado por baixo — é o que permite avançar
 * num vídeo cujo manifesto ainda não disse quanto dura.
 */
fun seekTargetOf(positionMillis: Long, deltaMillis: Long, durationMillis: Long): Long {
    val alvo = positionMillis + deltaMillis
    if (alvo < 0) return 0
    if (durationMillis <= 0) return alvo
    return alvo.coerceAtMost(durationMillis)
}

/**
 * A fração `0f..1f` que a barra de progresso desenha. Duração desconhecida ou zero → `0f` (barra
 * vazia), nunca `NaN` — que em Compose vira uma barra que some sem erro nenhum.
 */
fun videoProgressOf(positionMillis: Long, durationMillis: Long): Float {
    if (durationMillis <= 0L) return 0f
    return (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
}

/**
 * A taxa de quadros usada pelo [VideoPlayerState.stepFrame] enquanto a plataforma não informa a do
 * vídeo: 30 fps, a do perfil de gravação/compressão da lib (`VideoTranscodeProfile.H264_720P`).
 */
const val DEFAULT_VIDEO_FRAME_RATE: Float = 30f

/**
 * A duração de um quadro, em milissegundos (`1000 / fps`). Taxa ausente, zero, negativa ou absurda
 * (> 240) cai em [DEFAULT_VIDEO_FRAME_RATE] — o formato de alguns arquivos informa `-1`.
 */
fun frameDurationMillisOf(frameRate: Float?): Double = 1000.0 / effectiveFrameRate(frameRate)

/** A taxa que vale: a informada se plausível (0 < fps ≤ 240), senão [DEFAULT_VIDEO_FRAME_RATE]. */
fun effectiveFrameRate(frameRate: Float?): Float =
    if (frameRate == null || frameRate.isNaN() || frameRate <= 0f || frameRate > 240f) DEFAULT_VIDEO_FRAME_RATE else frameRate

/**
 * Onde parar ao andar [frames] quadros a partir de [positionMillis] (2.286.0).
 *
 * A conta é por **índice de quadro**, não por soma de milissegundos: o quadro atual é
 * `floor((posição + 1 ms) × fps)` — o milissegundo de folga absorve a posição que a plataforma
 * TRUNCA (o iOS informa 33 ms para o quadro que começa em 33,33 ms; sem a folga, ele pareceria o
 * quadro anterior e o próximo passo cairia no mesmo lugar) —, o alvo é esse índice + [frames], e o tempo devolvido é o **início** do
 * quadro alvo arredondado **para cima** ao milissegundo. Para cima, porque a busca exata mostra o
 * quadro cujo instante é ≤ ao pedido: pedir 33 ms num vídeo de 30 fps (quadro 1 começa em 33,33 ms)
 * mostraria ainda o quadro 0, e o botão "não faria nada".
 *
 * Somar `1000/fps` à posição acumularia o erro do arredondamento — depois de dez passos o vídeo
 * estaria num quadro diferente do que a pessoa contou.
 *
 * Grampeia em `0` e, com duração conhecida (> 0), no início do último quadro.
 */
fun frameStepTargetOf(positionMillis: Long, frames: Int, frameRate: Float?, durationMillis: Long): Long {
    val fps = effectiveFrameRate(frameRate).toDouble()
    val atual = kotlin.math.floor((positionMillis.coerceAtLeast(0L) + 1) * fps / 1000.0).toLong()
    var alvo = (atual + frames).coerceAtLeast(0L)
    if (durationMillis > 0L) {
        val ultimo = kotlin.math.floor((durationMillis - 1).coerceAtLeast(0L) * fps / 1000.0).toLong()
        alvo = alvo.coerceAtMost(ultimo)
    }
    return kotlin.math.ceil(alvo * 1000.0 / fps - FRAME_EPSILON).toLong()
}

/** Folga numérica da conta de quadro: absorve o `33.333…` que vira `33.33299…` em ponto flutuante. */
private const val FRAME_EPSILON = 1e-6
