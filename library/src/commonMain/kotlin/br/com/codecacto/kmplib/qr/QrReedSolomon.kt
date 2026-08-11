package br.com.codecacto.kmplib.qr

/**
 * Correção de erro **Reed-Solomon em GF(256)** — a redundância que faz um QR sujo, dobrado ou
 * parcialmente coberto ainda ser lido.
 *
 * O corpo finito é o do padrão QR: **GF(2⁸) com polinômio primitivo `0x11D`**
 * (`x⁸ + x⁴ + x³ + x² + 1`) e gerador `α = 0x02`. Trocar o polinômio produz *codewords* de EC
 * plausíveis e completamente inúteis — o leitor tenta corrigir, falha, e descarta o símbolo.
 *
 * Multiplicação por tabela de log/antilog (o mesmo que o padrão descreve), calculada uma vez.
 */
internal object QrReedSolomon {

    /** Polinômio primitivo do GF(256) usado pelo QR Code. */
    const val PRIMITIVE_POLYNOMIAL: Int = 0x11D

    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            log[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor PRIMITIVE_POLYNOMIAL
        }
        // Espelha a tabela para permitir soma de expoentes sem módulo explícito.
        for (i in 255 until 512) exp[i] = exp[i - 255]
    }

    /** Multiplicação no corpo. `0` é absorvente (não existe log de zero). */
    fun multiply(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return exp[log[a and 0xFF] + log[b and 0xFF]]
    }

    /**
     * Polinômio gerador de grau [degree] — `(x - α⁰)(x - α¹)…(x - α^(degree-1))`.
     *
     * Devolvido do coeficiente de maior grau para o menor, sem o termo líder (que é sempre 1).
     */
    fun generatorPolynomial(degree: Int): IntArray {
        require(degree in 1..255) { "grau inválido para o gerador: $degree" }
        // Começa em "1" e multiplica por (x - α^i) a cada passo.
        var result = intArrayOf(1)
        var root = 1
        repeat(degree) {
            val next = IntArray(result.size + 1)
            for (i in result.indices) {
                next[i] = next[i] xor result[i]
                next[i + 1] = next[i + 1] xor multiply(result[i], root)
            }
            result = next
            root = multiply(root, 0x02)
        }
        // O termo líder (grau máximo) é 1 e não é transmitido.
        return result.copyOfRange(1, result.size)
    }

    /**
     * *Codewords* de correção de erro de um bloco: o **resto** da divisão do polinômio de dados pelo
     * gerador de grau [eccCount].
     */
    fun errorCorrectionCodewords(data: ByteArray, eccCount: Int): ByteArray {
        val generator = generatorPolynomial(eccCount)
        val remainder = IntArray(eccCount)

        for (byte in data) {
            val factor = (byte.toInt() and 0xFF) xor remainder[0]
            // Desloca o resto uma posição e acumula factor * gerador.
            for (i in 0 until eccCount - 1) remainder[i] = remainder[i + 1]
            remainder[eccCount - 1] = 0
            for (i in 0 until eccCount) {
                remainder[i] = remainder[i] xor multiply(generator[i], factor)
            }
        }

        return ByteArray(eccCount) { remainder[it].toByte() }
    }
}
