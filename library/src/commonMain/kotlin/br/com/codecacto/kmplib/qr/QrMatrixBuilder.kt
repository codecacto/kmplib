package br.com.codecacto.kmplib.qr

/**
 * Montagem da matriz de módulos de um símbolo QR (ISO/IEC 18004 §6.3 e §8).
 *
 * Ordem das etapas — cada uma existe por um motivo, e trocar a ordem quebra o símbolo em silêncio:
 *
 * 1. **Padrões funcionais**: localização (3 cantos), separadores, sincronismo, alinhamento, o
 *    **módulo escuro fixo** e as áreas *reservadas* de informação de formato/versão.
 * 2. **Dados+EC** preenchidos em ziguezague de colunas duplas, da direita para a esquerda, pulando a
 *    coluna de sincronismo.
 * 3. **Máscara** aplicada **só** nos módulos que não são funcionais.
 * 4. **Informação de formato** (BCH 15,5) e, da versão 7 em diante, **de versão** (BCH 18,6) —
 *    escritas por último, porque elas **não** são mascaradas.
 */
internal object QrMatrixBuilder {

    /** Gerador BCH(15,5) da informação de formato. */
    private const val FORMAT_GENERATOR = 0x537

    /** Máscara XOR aplicada à informação de formato (evita o padrão todo-zero). */
    private const val FORMAT_XOR = 0x5412

    /** Gerador BCH(18,6) da informação de versão. */
    private const val VERSION_GENERATOR = 0x1F25

    /**
     * Monta o símbolo completo.
     *
     * @param codewords sequência final de *codewords* (dados e EC já **intercalados**).
     * @param forcedMask máscara a usar; `null` escolhe a de menor penalidade (as 8 são avaliadas).
     * @return matriz do símbolo (sem quiet zone) e a máscara usada.
     */
    fun build(
        version: Int,
        level: QrErrorCorrection,
        codewords: ByteArray,
        forcedMask: Int? = null,
    ): BuiltMatrix {
        val size = QrCode.symbolSizeOf(version)
        val dark = BooleanArray(size * size)
        val function = BooleanArray(size * size)

        drawFunctionPatterns(version, size, dark, function)
        drawCodewords(size, function, dark, codewords)

        val mask = forcedMask ?: chooseMask(version, level, size, dark, function)
        require(mask in 0 until QrMask.COUNT) { "máscara inválida: $mask" }

        applyMask(size, dark, function, mask)
        drawFormatInformation(size, dark, level, mask)
        drawVersionInformation(version, size, dark)

        return BuiltMatrix(dark = dark, size = size, mask = mask)
    }

    internal data class BuiltMatrix(val dark: BooleanArray, val size: Int, val mask: Int)

    // -------------------------------------------------------------------------------------------
    // Padrões funcionais
    // -------------------------------------------------------------------------------------------

    private fun drawFunctionPatterns(
        version: Int,
        size: Int,
        dark: BooleanArray,
        function: BooleanArray,
    ) {
        // Sincronismo (linha e coluna 6), alternando escuro/claro a partir das bordas.
        for (i in 0 until size) {
            setFunction(size, dark, function, 6, i, i % 2 == 0)
            setFunction(size, dark, function, i, 6, i % 2 == 0)
        }

        // Localização + separador nos três cantos (o quarto canto é onde vive o alinhamento).
        drawFinderPattern(size, dark, function, 3, 3)
        drawFinderPattern(size, dark, function, size - 4, 3)
        drawFinderPattern(size, dark, function, 3, size - 4)

        // Alinhamento — em todas as combinações de centros, menos as três que colidem com a localização.
        val positions = QrTables.alignmentPatternPositions(version)
        for (i in positions.indices) {
            for (j in positions.indices) {
                val isFinderCorner = (i == 0 && j == 0) ||
                    (i == 0 && j == positions.size - 1) ||
                    (i == positions.size - 1 && j == 0)
                if (!isFinderCorner) {
                    drawAlignmentPattern(size, dark, function, positions[i], positions[j])
                }
            }
        }

        reserveFormatAndVersionAreas(version, size, dark, function)
    }

    /** Localização: 7×7 com anel escuro, anel claro e núcleo 3×3, mais o separador claro em volta. */
    private fun drawFinderPattern(
        size: Int,
        dark: BooleanArray,
        function: BooleanArray,
        centerX: Int,
        centerY: Int,
    ) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val x = centerX + dx
                val y = centerY + dy
                if (x !in 0 until size || y !in 0 until size) continue
                val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                // distância 4 = separador (claro); 2 = anel claro; o resto é escuro.
                setFunction(size, dark, function, x, y, distance != 2 && distance != 4)
            }
        }
    }

    /** Alinhamento: 5×5 com anel escuro, anel claro e um módulo escuro no centro. */
    private fun drawAlignmentPattern(
        size: Int,
        dark: BooleanArray,
        function: BooleanArray,
        centerX: Int,
        centerY: Int,
    ) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setFunction(size, dark, function, centerX + dx, centerY + dy, distance != 1)
            }
        }
    }

    /**
     * Reserva (marca como funcional, ainda sem valor) as áreas de informação de formato e de versão, e
     * grava o **módulo escuro fixo** em `(8, size - 8)` — exigência do padrão, e um dos esquecimentos
     * que produzem símbolo que não lê.
     */
    private fun reserveFormatAndVersionAreas(
        version: Int,
        size: Int,
        dark: BooleanArray,
        function: BooleanArray,
    ) {
        // Informação de formato: EXATAMENTE as posições que ela ocupa (as duas cópias).
        //
        // Reservar "a faixa inteira" (coluna 8 de 0 a 8, linha 8 de 0 a 8) parece equivalente e não é:
        // (8, 6) e (6, 8) pertencem ao padrão de SINCRONISMO, não ao formato. Apagá-los produz um
        // símbolo que difere de qualquer leitor de referência — e foi exatamente o que o teste contra
        // a implementação independente pegou.
        forEachFormatPosition(size) { x, y, _ ->
            setFunction(size, dark, function, x, y, false)
        }
        // Módulo escuro fixo.
        setFunction(size, dark, function, 8, size - 8, true)

        // Informação de versão (7 em diante): dois blocos 3×6.
        if (version >= 7) {
            for (i in 0 until 18) {
                val a = i / 3
                val b = i % 3
                setFunction(size, dark, function, size - 11 + b, a, false)
                setFunction(size, dark, function, a, size - 11 + b, false)
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Dados
    // -------------------------------------------------------------------------------------------

    /**
     * Preenche os *codewords* em ziguezague: colunas de duas em duas, da direita para a esquerda,
     * subindo e descendo alternadamente, **pulando a coluna 6** (sincronismo vertical).
     *
     * Bits do *codeword* entram do mais significativo para o menos. Sobrando módulos no fim
     * (*remainder bits*), ficam claros — o padrão os define como não usados.
     */
    private fun drawCodewords(
        size: Int,
        function: BooleanArray,
        dark: BooleanArray,
        codewords: ByteArray,
    ) {
        var bitIndex = 0
        val totalBits = codewords.size * 8
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5 // a coluna de sincronismo não recebe dados
            for (vertical in 0 until size) {
                for (column in 0 until 2) {
                    val x = right - column
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vertical else vertical
                    val index = y * size + x
                    if (function[index] || bitIndex >= totalBits) continue
                    val bit = (codewords[bitIndex / 8].toInt() ushr (7 - bitIndex % 8)) and 1
                    dark[index] = bit == 1
                    bitIndex++
                }
            }
            right -= 2
        }
    }

    // -------------------------------------------------------------------------------------------
    // Máscara
    // -------------------------------------------------------------------------------------------

    private fun applyMask(size: Int, dark: BooleanArray, function: BooleanArray, mask: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val index = y * size + x
                if (function[index]) continue
                if (QrMask.isMasked(mask, x, y)) dark[index] = !dark[index]
            }
        }
    }

    /**
     * Avalia as 8 máscaras e devolve a de **menor penalidade**.
     *
     * A avaliação é feita sobre o símbolo **completo** (com a informação de formato daquela máscara
     * escrita), como o padrão manda — avaliar sem ela dá pontuação de um símbolo que não existe.
     */
    private fun chooseMask(
        version: Int,
        level: QrErrorCorrection,
        size: Int,
        dark: BooleanArray,
        function: BooleanArray,
    ): Int {
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0 until QrMask.COUNT) {
            val candidate = dark.copyOf()
            applyMask(size, candidate, function, mask)
            drawFormatInformation(size, candidate, level, mask)
            drawVersionInformation(version, size, candidate)
            val penalty = QrMask.penalty(candidate, size)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
            }
        }
        return bestMask
    }

    // -------------------------------------------------------------------------------------------
    // Informação de formato e de versão
    // -------------------------------------------------------------------------------------------

    /**
     * Informação de formato: 5 bits (nível + máscara) protegidos por **BCH(15,5)** e mascarados com
     * `0x5412`, gravados **duas vezes** no símbolo (redundância exigida — o leitor precisa achar o
     * formato mesmo com um dos cantos danificado).
     */
    fun formatBits(level: QrErrorCorrection, mask: Int): Int {
        val data = (level.formatBits shl 3) or mask
        var remainder = data
        repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * FORMAT_GENERATOR) }
        return ((data shl 10) or remainder) xor FORMAT_XOR
    }

    private fun drawFormatInformation(
        size: Int,
        dark: BooleanArray,
        level: QrErrorCorrection,
        mask: Int,
    ) {
        val bits = formatBits(level, mask)
        forEachFormatPosition(size) { x, y, bitIndex ->
            set(size, dark, x, y, bitAt(bits, bitIndex))
        }
    }

    /**
     * Fonte **única** das posições da informação de formato: as duas cópias, com o índice do bit que
     * cada módulo carrega.
     *
     * Reserva e desenho passam por aqui de propósito — quando as duas listas viviam separadas, uma
     * incluía (8, 6) e (6, 8) (que são sincronismo) e a outra não.
     */
    private inline fun forEachFormatPosition(size: Int, set: (x: Int, y: Int, bitIndex: Int) -> Unit) {
        // Primeira cópia: em volta do canto superior esquerdo, saltando a linha/coluna 6.
        for (i in 0..5) set(8, i, i)
        set(8, 7, 6)
        set(8, 8, 7)
        set(7, 8, 8)
        for (i in 9..14) set(14 - i, 8, i)

        // Segunda cópia: metade no canto superior direito, metade no inferior esquerdo.
        for (i in 0..7) set(size - 1 - i, 8, i)
        for (i in 8..14) set(8, size - 15 + i, i)
    }

    /**
     * Informação de versão (só da versão 7 em diante): 6 bits protegidos por **BCH(18,6)**, gravados
     * em dois blocos 3×6. Sem ela, o leitor não descobre o tamanho do símbolo grande.
     */
    fun versionBits(version: Int): Int {
        var remainder = version
        repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * VERSION_GENERATOR) }
        return (version shl 12) or remainder
    }

    private fun drawVersionInformation(version: Int, size: Int, dark: BooleanArray) {
        if (version < 7) return
        val bits = versionBits(version)
        for (i in 0 until 18) {
            val bit = bitAt(bits, i)
            val a = size - 11 + i % 3
            val b = i / 3
            set(size, dark, a, b, bit)
            set(size, dark, b, a, bit)
        }
    }

    // -------------------------------------------------------------------------------------------
    // Utilitários
    // -------------------------------------------------------------------------------------------

    private fun bitAt(value: Int, index: Int): Boolean = ((value ushr index) and 1) != 0

    private fun set(size: Int, dark: BooleanArray, x: Int, y: Int, value: Boolean) {
        if (x !in 0 until size || y !in 0 until size) return
        dark[y * size + x] = value
    }

    private fun setFunction(
        size: Int,
        dark: BooleanArray,
        function: BooleanArray,
        x: Int,
        y: Int,
        value: Boolean,
    ) {
        if (x !in 0 until size || y !in 0 until size) return
        val index = y * size + x
        dark[index] = value
        function[index] = true
    }
}
