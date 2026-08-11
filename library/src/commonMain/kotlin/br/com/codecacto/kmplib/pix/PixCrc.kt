package br.com.codecacto.kmplib.pix

/**
 * CRC do BR Code — **CRC-16/CCITT-FALSE**, o algoritmo que o EMV MPM (e o Manual do BR Code do
 * Bacen) fixa para a tag `63`.
 *
 * Parâmetros, sem variação permitida: polinômio `0x1021`, valor inicial `0xFFFF`, **sem** reflexão
 * de entrada ou saída, **sem** XOR final. O resultado é escrito em **hex maiúsculo de 4 dígitos**.
 *
 * **O cálculo cobre o payload inteiro incluindo `"6304"`** — ou seja, o ID e o tamanho da própria
 * tag de CRC entram no cálculo; só os 4 dígitos do valor ficam de fora. Errar esse detalhe é o modo
 * clássico de "todo QR dá inválido".
 *
 * É a **primeira linha de defesa** do produto: um payload cujo CRC não fecha foi truncado ou
 * adulterado depois de emitido, e isso é diferente de "não é um BR Code" — ver [BrCodeReading].
 *
 * O CRC é sobre **bytes**, e os bytes são a codificação **UTF-8** do texto (é o que a
 * especificação define e o que todo emissor usa). Isso importa quando o nome do recebedor vem
 * acentuado: contar caracteres em vez de bytes daria um CRC diferente do gravado no QR.
 */
object PixCrc {

    /** ID da tag de CRC no EMV MPM. */
    const val TAG: String = "63"

    /** O valor do CRC tem sempre 4 caracteres. */
    const val VALUE_LENGTH: Int = 4

    /** ID + tamanho da tag de CRC: o sufixo que **entra** no cálculo. */
    const val TAG_WITH_LENGTH: String = "6304"

    private const val POLYNOMIAL = 0x1021
    private const val INITIAL_VALUE = 0xFFFF

    /**
     * CRC-16/CCITT-FALSE do texto, em hex maiúsculo de 4 dígitos.
     *
     * Para validar um BR Code, passe o payload **até e incluindo** `"6304"`
     * (`payload.dropLast(4)`), não o payload inteiro.
     */
    fun compute(data: String): String = compute(data.encodeToByteArray())

    /** CRC-16/CCITT-FALSE dos bytes, em hex maiúsculo de 4 dígitos. */
    fun compute(bytes: ByteArray): String {
        var crc = INITIAL_VALUE
        for (byte in bytes) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    (crc shl 1) xor POLYNOMIAL
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc.toHex4()
    }

    /**
     * Fecha um payload: acrescenta `"6304"` (se ainda não estiver lá) e o CRC calculado.
     *
     * Serve para montar payload de teste/fixture e para qualquer app que precise gerar um BR Code —
     * é a única forma correta de produzir a tag `63`, e evita que cada consumidor reimplemente a
     * regra de "o `6304` entra no cálculo".
     *
     * ```kotlin
     * val payload = PixCrc.sign("00020126...5802BR5913CODECACTO6009SAO PAULO")
     * // -> "...6009SAO PAULO6304XXXX"
     * ```
     */
    fun sign(payloadWithoutCrc: String): String {
        val base = if (payloadWithoutCrc.endsWith(TAG_WITH_LENGTH)) {
            payloadWithoutCrc
        } else {
            payloadWithoutCrc + TAG_WITH_LENGTH
        }
        return base + compute(base)
    }

    /**
     * `true` se o payload termina em `"6304" + CRC` e o CRC fecha.
     *
     * Atalho de conveniência; para saber **por que** um payload foi recusado (CRC errado? nem é
     * EMV?), use `parseBrCode`, que devolve o motivo tipado.
     */
    fun isValid(payload: String?): Boolean {
        val declared = declaredCrcOf(payload) ?: return false
        val computed = compute(payload!!.dropLast(VALUE_LENGTH))
        return declared == computed
    }

    /**
     * O CRC declarado no fim do payload, **normalizado para maiúsculo**, ou `null` se o payload não
     * termina em `"6304" + 4 chars hex`.
     *
     * A comparação é feita em maiúsculo porque o padrão manda escrever assim, mas existe emissor
     * que grava minúsculo — e recusar por causa da caixa reprovaria um QR íntegro. Tolerar a caixa
     * não abre brecha: o CRC prova **integridade**, não autenticidade (quem adultera o payload
     * recalcula o CRC de qualquer jeito; é o cofre de plaquinhas cadastradas que pega a troca).
     */
    fun declaredCrcOf(payload: String?): String? {
        if (payload == null) return null
        if (payload.length < TAG_WITH_LENGTH.length + VALUE_LENGTH) return null
        val crcFieldStart = payload.length - VALUE_LENGTH - TAG_WITH_LENGTH.length
        if (payload.substring(crcFieldStart, crcFieldStart + TAG_WITH_LENGTH.length) != TAG_WITH_LENGTH) {
            return null
        }
        val declared = payload.takeLast(VALUE_LENGTH)
        if (!declared.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return declared.uppercase()
    }

    private fun Int.toHex4(): String {
        val digits = "0123456789ABCDEF"
        val value = this and 0xFFFF
        return buildString(VALUE_LENGTH) {
            append(digits[(value shr 12) and 0xF])
            append(digits[(value shr 8) and 0xF])
            append(digits[(value shr 4) and 0xF])
            append(digits[value and 0xF])
        }
    }
}
