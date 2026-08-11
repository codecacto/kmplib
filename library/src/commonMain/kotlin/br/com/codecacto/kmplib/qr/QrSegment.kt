package br.com.codecacto.kmplib.qr

/**
 * Acumulador de bits (MSB primeiro), a ordem em que o QR grava tudo.
 */
internal class QrBitBuffer(initialCapacity: Int = 128) {

    private var bytes = ByteArray((initialCapacity + 7) / 8)

    /** Quantidade de bits já escritos. */
    var bitLength: Int = 0
        private set

    /** Escreve os [count] bits menos significativos de [value], do mais significativo ao menos. */
    fun appendBits(value: Int, count: Int) {
        require(count in 0..31) { "count fora de faixa: $count" }
        require(count == 31 || value ushr count == 0) { "valor $value não cabe em $count bits" }
        ensureCapacity(bitLength + count)
        for (i in count - 1 downTo 0) {
            val bit = (value ushr i) and 1
            if (bit == 1) {
                val index = bitLength / 8
                bytes[index] = (bytes[index].toInt() or (0x80 ushr (bitLength % 8))).toByte()
            }
            bitLength++
        }
    }

    /** O bit em [index] (0 = primeiro escrito). */
    fun bitAt(index: Int): Boolean {
        require(index in 0 until bitLength) { "bit fora de faixa: $index" }
        return (bytes[index / 8].toInt() ushr (7 - index % 8)) and 1 == 1
    }

    /** Concatena todos os bits de [other] neste buffer, preservando a ordem. */
    fun appendAll(other: QrBitBuffer) {
        ensureCapacity(bitLength + other.bitLength)
        for (i in 0 until other.bitLength) appendBits(if (other.bitAt(i)) 1 else 0, 1)
    }

    /**
     * Fecha o fluxo em *codewords* de 8 bits, completando com o **terminador**, o alinhamento de byte
     * e os bytes de preenchimento `0xEC`/`0x11` alternados, até [dataCodewords].
     *
     * Os três passos são do padrão e nenhum é opcional: sem terminador o leitor não sabe onde os
     * dados acabam; sem alinhamento o último *codeword* fica deslocado; sem o preenchimento
     * alternado (que é **especificado**, não arbitrário) o bloco fica com bytes indefinidos.
     */
    fun toDataCodewords(dataCodewords: Int): ByteArray {
        val capacityBits = dataCodewords * 8
        require(bitLength <= capacityBits) {
            "dados ($bitLength bits) excedem a capacidade ($capacityBits bits)"
        }

        // Terminador: até 4 bits zero (menos, se não couberem).
        appendBits(0, minOf(4, capacityBits - bitLength))
        // Alinhamento de byte.
        appendBits(0, (8 - bitLength % 8) % 8)

        val result = ByteArray(dataCodewords)
        bytes.copyInto(result, endIndex = minOf(bytes.size, bitLength / 8))

        // Preenchimento alternado 11101100 / 00010001.
        var index = bitLength / 8
        var useFirst = true
        while (index < dataCodewords) {
            result[index] = if (useFirst) 0xEC.toByte() else 0x11.toByte()
            useFirst = !useFirst
            index++
        }
        return result
    }

    private fun ensureCapacity(bits: Int) {
        val needed = (bits + 7) / 8
        if (needed <= bytes.size) return
        var newSize = maxOf(bytes.size * 2, 8)
        while (newSize < needed) newSize *= 2
        bytes = bytes.copyOf(newSize)
    }
}

/**
 * Um segmento de dados: o modo escolhido e o conteúdo já convertido em bits.
 *
 * A lib produz **um único segmento** por símbolo (o modo mais econômico que cubra o texto inteiro).
 * Modo misto — alternar Numeric/Alphanumeric/Byte dentro do mesmo símbolo para economizar mais alguns
 * bits — é uma otimização deliberadamente **fora** de escopo: ganha pouco, multiplica os caminhos de
 * codificação e torna `qrCodeFitsPayload` imprevisível para quem chama.
 */
internal class QrSegment private constructor(
    val mode: QrMode,
    val characterCount: Int,
    val data: QrBitBuffer,
) {

    /** Bits totais deste segmento numa dada versão (cabeçalho de modo + contador + dados). */
    fun bitLength(version: Int): Int =
        4 + mode.characterCountBits(version) + data.bitLength

    companion object {

        /** Os 45 caracteres do modo alfanumérico, na ordem em que valem 0..44 no padrão. */
        const val ALPHANUMERIC_CHARSET: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:"

        /**
         * Escolhe o modo mais econômico capaz de representar [text] inteiro e monta o segmento.
         *
         * Texto vazio é um segmento válido de 0 caracteres (gera o menor QR possível) — quem não quer
         * isso confere antes; a lib não inventa conteúdo.
         */
        fun of(text: String): QrSegment = when {
            text.all { it in '0'..'9' } -> numeric(text)
            text.all { it in ALPHANUMERIC_CHARSET } -> alphanumeric(text)
            else -> byte(text.encodeToByteArray())
        }

        /** Segmento numérico: 3 dígitos → 10 bits, 2 → 7, 1 → 4. */
        fun numeric(digits: String): QrSegment {
            require(digits.all { it in '0'..'9' }) { "modo numérico aceita só dígitos" }
            val buffer = QrBitBuffer(digits.length * 4)
            var index = 0
            while (index < digits.length) {
                val take = minOf(3, digits.length - index)
                val chunk = digits.substring(index, index + take).toInt()
                buffer.appendBits(chunk, take * 3 + 1)
                index += take
            }
            return QrSegment(QrMode.Numeric, digits.length, buffer)
        }

        /** Segmento alfanumérico: pares → 11 bits (`a * 45 + b`), sobra → 6 bits. */
        fun alphanumeric(text: String): QrSegment {
            val buffer = QrBitBuffer(text.length * 6)
            var index = 0
            while (index + 1 < text.length) {
                val first = ALPHANUMERIC_CHARSET.indexOf(text[index])
                val second = ALPHANUMERIC_CHARSET.indexOf(text[index + 1])
                require(first >= 0 && second >= 0) { "caractere fora do conjunto alfanumérico" }
                buffer.appendBits(first * 45 + second, 11)
                index += 2
            }
            if (index < text.length) {
                val last = ALPHANUMERIC_CHARSET.indexOf(text[index])
                require(last >= 0) { "caractere fora do conjunto alfanumérico" }
                buffer.appendBits(last, 6)
            }
            return QrSegment(QrMode.Alphanumeric, text.length, buffer)
        }

        /**
         * Segmento de bytes — 8 bits cada.
         *
         * O texto entra em **UTF-8** (`String.encodeToByteArray`), **sem cabeçalho ECI**. O padrão
         * prevê o ECI 26 para declarar UTF-8, mas o de-facto do mercado é gravar UTF-8 direto: é o
         * que praticamente todo gerador faz e todo leitor de celular espera. Emitir ECI aqui daria um
         * símbolo formalmente mais correto e **pior na prática** (leitores antigos exibem o
         * indicador como lixo no começo do texto).
         */
        fun byte(data: ByteArray): QrSegment {
            val buffer = QrBitBuffer(data.size * 8)
            for (b in data) buffer.appendBits(b.toInt() and 0xFF, 8)
            return QrSegment(QrMode.Byte, data.size, buffer)
        }
    }
}
