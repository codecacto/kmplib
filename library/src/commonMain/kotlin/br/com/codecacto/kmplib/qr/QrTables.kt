package br.com.codecacto.kmplib.qr

/**
 * Tabelas e geometria do padrão **ISO/IEC 18004**.
 *
 * Os números aqui são **dados da especificação** (Tabelas 13–22 do padrão: quantidade de blocos de
 * correção e *codewords* de EC por bloco, para cada versão × nível). Não há espaço para escolha:
 * qualquer valor diferente produz um símbolo que nenhum leitor decodifica.
 *
 * O que **não** é tabela aqui é calculado pela fórmula do padrão (número de módulos de dados,
 * posições dos padrões de alinhamento) — é menos código para errar do que 40 linhas copiadas.
 */
internal object QrTables {

    /**
     * *Codewords* de correção de erro **por bloco**, indexado `[nível][versão]` (versão 1–40 no
     * índice 1–40; índice 0 não usado).
     */
    private val ECC_CODEWORDS_PER_BLOCK: Map<QrErrorCorrection, IntArray> = mapOf(
        QrErrorCorrection.L to intArrayOf(
            -1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28,
            28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
        ),
        QrErrorCorrection.M to intArrayOf(
            -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
            26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28,
        ),
        QrErrorCorrection.Q to intArrayOf(
            -1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30,
            28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
        ),
        QrErrorCorrection.H to intArrayOf(
            -1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28,
            30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30,
        ),
    )

    /** Número de blocos de correção de erro, indexado `[nível][versão]`. */
    private val ERROR_CORRECTION_BLOCKS: Map<QrErrorCorrection, IntArray> = mapOf(
        QrErrorCorrection.L to intArrayOf(
            -1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8,
            8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25,
        ),
        QrErrorCorrection.M to intArrayOf(
            -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
            17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49,
        ),
        QrErrorCorrection.Q to intArrayOf(
            -1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20,
            23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68,
        ),
        QrErrorCorrection.H to intArrayOf(
            -1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25,
            25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81,
        ),
    )

    /** *Codewords* de EC por bloco nesta versão/nível. */
    fun eccCodewordsPerBlock(version: Int, level: QrErrorCorrection): Int =
        ECC_CODEWORDS_PER_BLOCK.getValue(level)[version]

    /** Número de blocos de EC nesta versão/nível. */
    fun errorCorrectionBlocks(version: Int, level: QrErrorCorrection): Int =
        ERROR_CORRECTION_BLOCKS.getValue(level)[version]

    /**
     * Módulos disponíveis para dados+EC nesta versão, já descontados os padrões funcionais
     * (localização, separadores, sincronismo, alinhamento, informação de formato e de versão).
     *
     * Fórmula do padrão — preferida a uma tabela de 40 linhas justamente porque é conferível.
     */
    fun rawDataModules(version: Int): Int {
        require(version in QrCode.MIN_VERSION..QrCode.MAX_VERSION) { "versão inválida: $version" }
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val alignmentCount = version / 7 + 2
            result -= (25 * alignmentCount - 10) * alignmentCount - 55
            if (version >= 7) result -= 36 // informação de versão (2 blocos de 18 bits)
        }
        return result
    }

    /** Total de *codewords* (dados + EC) da versão. */
    fun totalCodewords(version: Int): Int = rawDataModules(version) / 8

    /** *Codewords* de DADOS disponíveis nesta versão/nível (o que sobra depois da EC). */
    fun dataCodewords(version: Int, level: QrErrorCorrection): Int =
        totalCodewords(version) - eccCodewordsPerBlock(version, level) * errorCorrectionBlocks(version, level)

    /** Capacidade de dados em **bits** nesta versão/nível. */
    fun dataCapacityBits(version: Int, level: QrErrorCorrection): Int =
        dataCodewords(version, level) * 8

    /**
     * Coordenadas dos **centros** dos padrões de alinhamento desta versão (vazio na versão 1).
     *
     * Calculado pela regra do padrão em vez de tabelado: o primeiro centro é sempre 6, o último é
     * `size - 7`, e os intermediários são espaçados uniformemente com passo par. A versão **32** é a
     * exceção conhecida da fórmula geral (passo 26), e está explícita.
     */
    fun alignmentPatternPositions(version: Int): IntArray {
        if (version == 1) return IntArray(0)
        val count = version / 7 + 2
        val size = QrCode.symbolSizeOf(version)
        val step = if (version == 32) {
            26
        } else {
            // Passo par, arredondado para cima (regra do padrão).
            (version * 4 + count * 2 + 1) / (count * 2 - 2) * 2
        }
        val positions = IntArray(count)
        positions[0] = 6
        var pos = size - 7
        for (i in count - 1 downTo 1) {
            positions[i] = pos
            pos -= step
        }
        return positions
    }
}
