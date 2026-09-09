package br.com.codecacto.kmplib.video

/**
 * As velocidades de reprodução da fábrica — **as seis, nesta ordem**.
 *
 * Lista fechada de propósito: velocidade contínua num *slider* parece flexível e, na prática, faz o
 * aluno parar em 1,13× sem querer. O conjunto é o mesmo que o YouTube e as plataformas de curso
 * usam, e a ordem é a que o menu mostra.
 */
val VIDEO_SPEEDS: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** A velocidade normal. */
const val VIDEO_SPEED_NORMAL: Float = 1f

/**
 * A próxima velocidade do ciclo, para quem prefere um botão só a um menu. Depois de 2× volta a
 * 0,5×. Velocidade fora da lista cai na mais próxima antes de avançar.
 */
fun nextVideoSpeed(current: Float): Float {
    val indice = VIDEO_SPEEDS.indexOfFirst { it >= current - TOLERANCIA }
    val proximo = if (indice < 0) VIDEO_SPEEDS.lastIndex else indice
    return VIDEO_SPEEDS[(proximo + 1) % VIDEO_SPEEDS.size]
}

/**
 * A velocidade escrita como o Brasil escreve: **vírgula decimal, sem zero à toa e com o `×`**.
 * `1f` → `"1×"`, `0.5f` → `"0,5×"`, `1.25f` → `"1,25×"`.
 *
 * É função e não `String.format` porque `format` não existe em `commonMain` — e porque assim o
 * arredondamento fica coberto por teste em vez de depender da plataforma.
 */
fun formatVideoSpeed(speed: Float): String {
    // Centésimos: nenhuma das seis velocidades precisa de mais, e arredondar aqui mata o
    // 1.2499999 que vem de aritmética de ponto flutuante.
    val centesimos = ((speed * 100).toDouble() + 0.5).toLong()
    val inteiro = centesimos / 100
    val resto = (centesimos % 100).toInt()
    val decimal = when {
        resto == 0 -> ""
        resto % 10 == 0 -> ",${resto / 10}"
        else -> ",${if (resto < 10) "0$resto" else "$resto"}"
    }
    return "$inteiro$decimal×"
}

private const val TOLERANCIA = 0.001f
