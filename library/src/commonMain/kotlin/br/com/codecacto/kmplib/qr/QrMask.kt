package br.com.codecacto.kmplib.qr

import kotlin.math.abs

/**
 * As **8 máscaras** do padrão e a **pontuação de penalidade** que escolhe a melhor
 * (ISO/IEC 18004 §8.8, Tabelas 10 e 11).
 *
 * É aqui que quase todo encoder escrito à mão erra — e o sintoma **não** é "não gera": é gerar um
 * símbolo que *parece* certo e que **alguns leitores recusam**, porque a distribuição de módulos ficou
 * ruim (faixas longas de uma cor, blocos maciços, ou um trecho parecido com o padrão de localização,
 * que confunde o algoritmo de detecção do leitor).
 *
 * A máscara é aplicada **só na área de dados** (padrões funcionais e as áreas de informação de
 * formato/versão ficam de fora) e a escolhida é a de **menor penalidade total** das quatro regras.
 *
 * **Importante para calibrar expectativa:** a penalidade é uma heurística de *qualidade de leitura*,
 * não de correção. Qualquer uma das 8 máscaras produz um símbolo **válido e decodificável**; escolher
 * uma máscara ligeiramente pior nunca gera QR ilegível. O que gera ilegível é errar a *aplicação* da
 * máscara, a informação de formato, o intercalamento ou a EC.
 */
internal object QrMask {

    /** Quantidade de padrões de máscara definidos pelo padrão. */
    const val COUNT: Int = 8

    /** Penalidade N1 — sequência de 5+ módulos da mesma cor: `3 + (tamanho - 5)`. */
    const val PENALTY_N1: Int = 3

    /** Penalidade N2 — bloco 2×2 da mesma cor. */
    const val PENALTY_N2: Int = 3

    /** Penalidade N3 — trecho parecido com o padrão de localização (`1:1:3:1:1` + 4 módulos claros). */
    const val PENALTY_N3: Int = 40

    /** Penalidade N4 — cada 5% de desvio além da faixa 45%–55% de módulos escuros. */
    const val PENALTY_N4: Int = 10

    /** `1011101` seguido de `0000` — a janela de 11 bits da regra 3. */
    private const val FINDER_THEN_LIGHT = 0b10111010000

    /** `0000` seguido de `1011101`. */
    private const val LIGHT_THEN_FINDER = 0b00001011101

    /**
     * Se o módulo ([x], [y]) é invertido pela máscara [mask].
     *
     * As expressões são as da Tabela 10 do padrão. Nas coordenadas do padrão `i` é a **linha** e `j` a
     * **coluna** — trocar os dois é o erro silencioso mais fácil de cometer aqui, porque as máscaras
     * 0, 3, 5, 6 e 7 são simétricas e **só as máscaras 1, 2 e 4 revelam a troca**.
     */
    fun isMasked(mask: Int, x: Int, y: Int): Boolean {
        val i = y
        val j = x
        return when (mask) {
            0 -> (i + j) % 2 == 0
            1 -> i % 2 == 0
            2 -> j % 3 == 0
            3 -> (i + j) % 3 == 0
            4 -> (i / 2 + j / 3) % 2 == 0
            5 -> (i * j) % 2 + (i * j) % 3 == 0
            6 -> ((i * j) % 2 + (i * j) % 3) % 2 == 0
            7 -> ((i + j) % 2 + (i * j) % 3) % 2 == 0
            else -> throw IllegalArgumentException("máscara inválida: $mask")
        }
    }

    /**
     * Penalidade total de um símbolo (soma das 4 regras). Menor é melhor.
     *
     * [size] é o lado do símbolo **sem** quiet zone; [dark] é indexado `y * size + x`.
     */
    fun penalty(dark: BooleanArray, size: Int): Int =
        penaltyRule1(dark, size) +
            penaltyRule2(dark, size) +
            penaltyRule3(dark, size) +
            penaltyRule4(dark, size)

    /**
     * **Regra 1** — faixas de 5 ou mais módulos da mesma cor, em cada linha e cada coluna.
     *
     * Pontua `3 + (tamanho - 5)`: faixa de 5 vale 3, de 6 vale 4, e assim em diante. Faixa longa é o
     * que faz o leitor perder a grade de módulos.
     */
    fun penaltyRule1(dark: BooleanArray, size: Int): Int {
        var result = 0
        for (line in 0 until size) {
            result += lineRunPenalty(size) { i -> dark[line * size + i] }
            result += lineRunPenalty(size) { i -> dark[i * size + line] }
        }
        return result
    }

    /**
     * **Regra 2** — blocos 2×2 de uma só cor, valendo 3 cada.
     *
     * Contam-se **todos** os quadrados 2×2 sobrepostos (uma área 3×3 uniforme vale 4 blocos), como o
     * padrão define — contar só blocos disjuntos subestima a penalidade e muda a máscara escolhida.
     */
    fun penaltyRule2(dark: BooleanArray, size: Int): Int {
        var blocks = 0
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val value = dark[y * size + x]
                if (value == dark[y * size + x + 1] &&
                    value == dark[(y + 1) * size + x] &&
                    value == dark[(y + 1) * size + x + 1]
                ) {
                    blocks++
                }
            }
        }
        return blocks * PENALTY_N2
    }

    /**
     * **Regra 3** — o padrão `1:1:3:1:1` (escuro-claro-escuro³-claro-escuro) **precedido ou seguido
     * por 4 módulos claros**, em linhas e colunas. Cada ocorrência vale 40.
     *
     * É a regra que protege o *finder pattern*: um trecho de dados parecido com ele faz o leitor
     * procurar o símbolo no lugar errado. Implementada como janela deslizante de 11 bits comparada com
     * os dois arranjos possíveis — a leitura literal do padrão, e a razão de um trecho com área clara
     * dos **dois** lados ser contado duas vezes (são duas ocorrências distintas do arranjo).
     */
    fun penaltyRule3(dark: BooleanArray, size: Int): Int {
        var occurrences = 0
        for (line in 0 until size) {
            occurrences += finderLikeOccurrences(size) { i -> dark[line * size + i] }
            occurrences += finderLikeOccurrences(size) { i -> dark[i * size + line] }
        }
        return occurrences * PENALTY_N3
    }

    /**
     * **Regra 4** — desequilíbrio entre claro e escuro: 10 pontos por cada 5% de desvio **além** da
     * faixa 45%–55%.
     *
     * Símbolo muito escuro ou muito claro reduz a margem do limiar de binarização da câmera.
     *
     * A fórmula é a da Tabela 11 do padrão (`45%–55% ⇒ k = 0`, `40%–60% ⇒ k = 1`, …), e não a
     * variante `|ceil(p/5) − 10|` que aparece em algumas bibliotecas — aquela cobra 10 pontos já em
     * 55,0%, que o padrão considera equilibrado. A diferença muda **apenas qual máscara é escolhida**
     * (ambas produzem símbolo legível), e a lib segue o padrão.
     */
    fun penaltyRule4(dark: BooleanArray, size: Int): Int {
        val total = size * size
        val darkCount = dark.count { it }
        // k = ceil(|proporção − 50%| / 5%) − 1, com piso em 0 — em inteiros, sem ponto flutuante.
        val deviationTimes20 = abs(darkCount * 20 - total * 10)
        val steps = (deviationTimes20 + total - 1) / total - 1
        return maxOf(0, steps) * PENALTY_N4
    }

    private inline fun lineRunPenalty(size: Int, at: (Int) -> Boolean): Int {
        var result = 0
        var runLength = 1
        var previous = at(0)
        for (i in 1 until size) {
            val current = at(i)
            if (current == previous) {
                runLength++
            } else {
                if (runLength >= 5) result += PENALTY_N1 + (runLength - 5)
                previous = current
                runLength = 1
            }
        }
        if (runLength >= 5) result += PENALTY_N1 + (runLength - 5)
        return result
    }

    private inline fun finderLikeOccurrences(size: Int, at: (Int) -> Boolean): Int {
        var occurrences = 0
        var window = 0
        for (i in 0 until size) {
            window = ((window shl 1) and 0x7FF) or (if (at(i)) 1 else 0)
            if (i >= 10 && (window == FINDER_THEN_LIGHT || window == LIGHT_THEN_FINDER)) {
                occurrences++
            }
        }
        return occurrences
    }
}
