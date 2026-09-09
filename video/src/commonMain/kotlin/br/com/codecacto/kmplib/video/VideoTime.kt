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
