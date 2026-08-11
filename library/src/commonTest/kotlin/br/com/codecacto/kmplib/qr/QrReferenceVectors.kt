package br.com.codecacto.kmplib.qr

/**
 * Vetores de referência de QR Code gerados por uma implementação **INDEPENDENTE**: a biblioteca
 * JavaScript `node-qrcode` 1.5.4 (usada em produção por milhares de projetos, e derivada da
 * implementação de referência de Kazuhiko Arase). Nenhum destes valores saiu do encoder da kmplib.
 *
 * É o que dá base ao teste `QrReferenceMatrixTest`: se qualquer etapa do nosso encoder divergir
 * (bitstream, padding, Reed-Solomon, intercalamento de blocos, padrões funcionais, informação de
 * formato/versão ou aplicação da máscara), a matriz sai diferente e o teste falha apontando a
 * coordenada exata.
 *
 * **A máscara é FORÇADA** em cada vetor, de propósito: a regra de *escolha* de máscara é uma
 * heurística de qualidade em que implementações divergem legitimamente (ver `QrMask.penaltyRule4`),
 * enquanto tudo o mais é determinado pelo padrão. Forçar a máscara isola a estrutura — que é o que
 * precisa bater bit a bit.
 *
 * Gerado por script; não editar à mão. Matriz **sem** quiet zone (`rows[y][x]`, '1' = escuro).
 */
internal object QrReferenceVectors {

    internal data class Vector(
        val name: String,
        val text: String,
        val level: QrErrorCorrection,
        val mask: Int,
        val version: Int,
        val size: Int,
        /** Matriz completa quando disponível (símbolos pequenos), senão vazia. */
        val rows: List<String> = emptyList(),
        /** Quantidade de módulos escuros — sinal independente do hash. */
        val darkCount: Int,
        /** FNV-1a (32 bits, hex) da matriz concatenada linha a linha. */
        val fingerprint: String,
    )

    internal val vectors: List<Vector> = listOf(
        Vector(
            name = "numerico_v1_L",
            text = "01234567",
            level = QrErrorCorrection.L,
            mask = 2,
            version = 1,
            size = 21,
            rows = listOf(
                "111111100100101111111",
                "100000101001001000001",
                "101110100100001011101",
                "101110101001001011101",
                "101110100011101011101",
                "100000101110101000001",
                "111111101010101111111",
                "000000000011100000000",
                "111110111100110101010",
                "111100011000100101100",
                "101110110001010011111",
                "000100001100000111100",
                "100100110011010010000",
                "000000001001111001100",
                "111111101000101100000",
                "100000100001111000101",
                "101110101000100101100",
                "101110101110100100000",
                "101110101001010010100",
                "100000101000000110110",
                "111111101011010010100",
            ),
            darkCount = 214,
            fingerprint = "C86538E9",
        ),
        Vector(
            name = "alfanum_HELLO",
            text = "HELLO WORLD",
            level = QrErrorCorrection.Q,
            mask = 4,
            version = 1,
            size = 21,
            rows = listOf(
                "111111100110001111111",
                "100000100100101000001",
                "101110101100001011101",
                "101110100010001011101",
                "101110101000001011101",
                "100000101111101000001",
                "111111101010101111111",
                "000000000010000000000",
                "010010101010110110100",
                "111101000010101111100",
                "100000111011100110101",
                "001001000001000111100",
                "110000111001111100111",
                "000000001000100101000",
                "111111100111101000001",
                "100000100111111111010",
                "101110101000011101101",
                "101110100000111001111",
                "101110100100110000100",
                "100000101001100011001",
                "111111100011001110011",
            ),
            darkCount = 220,
            fingerprint = "01C42903",
        ),
        Vector(
            name = "byte_url",
            text = "https://codecacto.com.br",
            level = QrErrorCorrection.M,
            mask = 3,
            version = 2,
            size = 25,
            rows = listOf(
                "1111111010000100001111111",
                "1000001010101100101000001",
                "1011101001101101001011101",
                "1011101011111110001011101",
                "1011101001110011101011101",
                "1000001001100000101000001",
                "1111111010101010101111111",
                "0000000011100000100000000",
                "1011011101010010001001011",
                "0100000110001100100100010",
                "1100011011011100101100000",
                "1111010001010111111101100",
                "0000011111100111011010111",
                "0101110100010111111110001",
                "0100111011001110010010110",
                "1001100011101011001110001",
                "0000011001010110111111111",
                "0000000010101000100010101",
                "1111111010111110101010111",
                "1000001011001011100010010",
                "1011101001110010111111001",
                "1011101010101000111011111",
                "1011101011110110011010110",
                "1000001001010100010010100",
                "1111111010100110011111111",
            ),
            darkCount = 334,
            fingerprint = "9A361F53",
        ),
        Vector(
            name = "byte_acento",
            text = "Cofre do Café — 12 plaquinhas",
            level = QrErrorCorrection.M,
            mask = 5,
            version = 3,
            size = 29,
            rows = listOf(
                "11111110011100011100001111111",
                "10000010111001101010001000001",
                "10111010101111010010001011101",
                "10111010101110001100101011101",
                "10111010011100011111001011101",
                "10000010000010010000001000001",
                "11111110101010101010101111111",
                "00000000110110111011000000000",
                "10000010100100001100111001110",
                "01001101010011001011101100110",
                "11111010000011111010110000100",
                "11010100111011000000011111001",
                "11011110000101011001010101001",
                "01000000100000001001001111001",
                "00001010100110000010011111000",
                "10100100111110010000111010110",
                "10100111001100000100010000101",
                "11000001000101100001001110001",
                "11111010100010010101111001101",
                "10110001000011010000100011011",
                "10111011110000111100111110110",
                "00000000111010110111100011101",
                "11111110000110011010101010000",
                "10000010010000100000100011000",
                "10111010001111111000111111011",
                "10111010001010111110011011101",
                "10111010011111110100011111110",
                "10000010010111001010101010101",
                "11111110110100000100111010100",
            ),
            darkCount = 419,
            fingerprint = "9BF4C1BC",
        ),
        Vector(
            name = "byte_v7_versioninfo",
            text = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            level = QrErrorCorrection.M,
            mask = 0,
            version = 7,
            size = 45,
            rows = listOf(
                "111111100110101111011001011011101000101111111",
                "100000101101110101110100001110111101001000001",
                "101110100000100000100110100100010001001011101",
                "101110100101110100001011110001000001101011101",
                "101110101110100001011111111011101111101011101",
                "100000100100010000111000101110111000001000001",
                "111111101010101010101010101010101010101111111",
                "000000000111010001111000111011101111100000000",
                "101010100110011011001111101110111011000010010",
                "110001010000001111011110100100010000000101101",
                "011110111110001110001011110001000100110000111",
                "001000010110110001010001011011101111111010010",
                "111110101001111001101100001110111011001111000",
                "111111010010101110111110100100010000000101101",
                "100100111010110010010011110001000100110000111",
                "110000001010110111000001011011101111111010010",
                "111000111011110101101100001110111011001111000",
                "000100000010101100111110100100010000000101101",
                "010010101010101110010011110001000100110000111",
                "001100011010111001000001011011101111111010010",
                "010011111010110001101111101110111011111111000",
                "011110001010111110111000100100010000100011101",
                "011010101101011110101010110001000101101010111",
                "100110001011101010001000111011101111100010010",
                "110011111011001000111111101110111010111111000",
                "111101000000000110001011100100010001010001101",
                "011000110100111110001001010001000101011010111",
                "100111010101101011010100011011101110101110010",
                "110011100000001100010110101110111010100101000",
                "111001011100000110101011100100010001010001101",
                "001100111100001111101001010001000101011010111",
                "011111011101011001010100011011101110101110010",
                "011001100000001110110110101110111010100101000",
                "101001011110000000101011100100010001010001101",
                "000010111100000001101001010001000101011010111",
                "011110011101000111010100011011101110101110010",
                "100110110000010110111111101110111010111111000",
                "000000001111110100101000100100010001100011101",
                "111111100111000010111010110001000100101010111",
                "100000100100110011101000111011101111100010010",
                "101110101101000111001111101110111011111111000",
                "101110100000110101010001000100010000111011101",
                "101110101110100010000100010001000101101110111",
                "100000100110010011101110111011101111000100010",
                "111111101100100111011011101110111010010001011",
            ),
            darkCount = 1056,
            fingerprint = "3E322FEF",
        ),
        Vector(
            name = "byte_v10_multiblock",
            text = "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy",
            level = QrErrorCorrection.Q,
            mask = 1,
            version = 14,
            size = 73,
            darkCount = 2775,
            fingerprint = "A24DF8AE",
        ),
        Vector(
            name = "byte_v27_high",
            text = "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
            level = QrErrorCorrection.L,
            mask = 6,
            version = 24,
            size = 113,
            darkCount = 5976,
            fingerprint = "DC1D9FC9",
        ),
        Vector(
            name = "numerico_longo",
            text = "999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999",
            level = QrErrorCorrection.H,
            mask = 7,
            version = 11,
            size = 61,
            darkCount = 1938,
            fingerprint = "76BFC487",
        ),
    )

    /**
     * Máscara e versão que a implementação de referência escolhe **automaticamente**, para medir a
     * concordância da nossa heurística (ver `QrMaskChoiceTest`). Divergência aqui **não** é defeito:
     * qualquer máscara produz símbolo válido, e a kmplib segue a Tabela 11 do ISO enquanto a referência
     * usa uma variante da regra 4.
     */
    internal data class AutoChoice(
        val name: String,
        val text: String,
        val level: QrErrorCorrection,
        val version: Int,
        val mask: Int,
    )

    internal val autoChoices: List<AutoChoice> = listOf(
        AutoChoice("auto1", "01234567", QrErrorCorrection.L, 1, 3),
        AutoChoice("auto2", "HELLO WORLD", QrErrorCorrection.Q, 1, 6),
        AutoChoice("auto3", "https://codecacto.com.br", QrErrorCorrection.M, 2, 2),
        AutoChoice("auto4", "Cofre do Café — 12 plaquinhas", QrErrorCorrection.M, 3, 1),
        AutoChoice("auto5", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", QrErrorCorrection.M, 7, 2),
        AutoChoice("auto6", "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy", QrErrorCorrection.Q, 14, 6),
        AutoChoice("auto7", "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", QrErrorCorrection.L, 24, 1),
        AutoChoice("auto8", "999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999", QrErrorCorrection.H, 11, 3),
        AutoChoice("auto9", "A", QrErrorCorrection.L, 1, 0),
        AutoChoice("auto10", "confere-qr:v1:abababababababababababababababababababababababababababababababababababababababababababababababababababababababababababab", QrErrorCorrection.L, 6, 4),
    )

    /** FNV-1a de 32 bits, em hex maiúsculo — o mesmo algoritmo usado ao gerar os vetores. */
    internal fun fingerprintOf(flat: String): String {
        var hash = 0x811c9dc5.toInt()
        for (char in flat) {
            hash = hash xor char.code
            hash *= 0x01000193
        }
        val unsigned = hash.toUInt().toString(16).uppercase()
        return unsigned.padStart(8, '0')
    }
}
