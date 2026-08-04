package br.com.codecacto.kmplib.camera.barcode

/**
 * Aritmética **GTIN** (GS1) — dígito verificador, expansão de UPC-E e normalização de tamanho.
 *
 * `commonMain` puro, sem dependência de plataforma: é a peça que faz Android (ML Kit) e iOS
 * (AVFoundation/Vision) devolverem **a mesma chave** para o mesmo produto, e é o único ponto onde
 * "esse código é plausível?" é decidido. Espelha o papel de [br.com.codecacto.kmplib.camera.extractPlate]
 * no irmão de OCR de placa.
 *
 * O algoritmo do dígito verificador (mod 10, pesos 3-1 a partir da direita) vale para **GTIN-8,
 * GTIN-12 (UPC-A), GTIN-13 (EAN-13) e GTIN-14 (caixa/ITF-14)**.
 */
object Gtin {

    /** Tamanhos válidos de um GTIN. */
    val VALID_LENGTHS: Set<Int> = setOf(8, 12, 13, 14)

    /**
     * Calcula o dígito verificador de [payload] (o código **sem** o último dígito).
     *
     * @return o dígito (0..9), ou `null` se [payload] tiver caractere não numérico ou for vazio.
     */
    fun checkDigit(payload: String): Int? {
        if (payload.isEmpty()) return null
        var sum = 0
        for (index in payload.indices) {
            val char = payload[payload.length - 1 - index]
            val digit = char - '0'
            if (digit !in 0..9) return null
            // Peso 3 no dígito mais à direita do payload, alternando para a esquerda.
            sum += if (index % 2 == 0) digit * 3 else digit
        }
        return (10 - sum % 10) % 10
    }

    /**
     * `true` se [code] é um GTIN completo (8/12/13/14 dígitos) com dígito verificador correto.
     *
     * Zeros à esquerda **não** alteram o dígito verificador (os pesos são ancorados à direita),
     * então `"7891000100103"` e `"07891000100103"` são ambos válidos e representam o mesmo item.
     */
    fun isValid(code: String): Boolean {
        if (code.length !in VALID_LENGTHS) return false
        if (!code.all { it in '0'..'9' }) return false
        val expected = checkDigit(code.dropLast(1)) ?: return false
        return expected == (code.last() - '0')
    }

    /**
     * Acrescenta zeros à esquerda até [length] (normalização GS1 — não altera o dígito
     * verificador nem o produto identificado).
     *
     * @return o código com [length] dígitos, ou `null` se [code] não for numérico ou já for maior
     *   que [length].
     */
    fun pad(code: String, length: Int): String? {
        if (code.isEmpty() || code.length > length) return null
        if (!code.all { it in '0'..'9' }) return null
        return code.padStart(length, '0')
    }

    /**
     * Expande um **UPC-E** (forma comprimida) para o **UPC-A** de 12 dígitos equivalente.
     *
     * Aceita:
     * - **8 dígitos** — `S d1 d2 d3 d4 d5 d6 C` (forma padrão devolvida pelos SDKs);
     * - **6 dígitos** — apenas o corpo `d1..d6`; assume sistema numérico `0` e calcula o
     *   verificador.
     *
     * A expansão segue a tabela GS1, escolhida pelo **último dígito do corpo** (`d6`).
     *
     * @return o UPC-A de 12 dígitos com verificador válido, ou `null` se a entrada não for um
     *   UPC-E plausível (tamanho, caractere não numérico, sistema numérico diferente de 0/1 ou
     *   verificador que não fecha).
     */
    fun expandUpcE(upcE: String): String? {
        val digits = upcE.trim()
        if (!digits.all { it in '0'..'9' }) return null

        val system: Char
        val body: String
        val declaredCheck: Int?
        when (digits.length) {
            8 -> {
                system = digits[0]
                body = digits.substring(1, 7)
                declaredCheck = digits[7] - '0'
            }
            6 -> {
                system = '0'
                body = digits
                declaredCheck = null
            }
            else -> return null
        }
        if (system != '0' && system != '1') return null

        val d = body
        val expandedPayload = when (d[5]) {
            '0', '1', '2' -> "$system${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
            '3' -> "$system${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
            '4' -> "$system${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
            else -> "$system${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
        }
        val check = checkDigit(expandedPayload) ?: return null
        if (declaredCheck != null && declaredCheck != check) return null
        return expandedPayload + check
    }
}
