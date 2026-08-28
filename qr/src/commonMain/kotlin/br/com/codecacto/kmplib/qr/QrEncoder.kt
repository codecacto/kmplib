package br.com.codecacto.kmplib.qr

/**
 * Resultado de codificar um texto em QR Code.
 *
 * Tipado em vez de exceção porque "não cabe" é um **estado esperado** do produto, não um bug: um cofre
 * com muitas plaquinhas simplesmente não entra num QR, e a tela precisa oferecer outro caminho
 * (arquivo) em vez de estourar na cara do usuário. Para decidir isso **antes** de tentar, use
 * [qrCodeFitsPayload].
 */
sealed interface QrEncodeResult {

    /** Símbolo pronto para desenhar. */
    data class Success(val qrCode: QrCode) : QrEncodeResult

    /**
     * O texto não cabe: nem a maior versão permitida ([maxVersion]) tem capacidade.
     *
     * [requiredBits] é quanto o conteúdo precisa e [capacityBits] quanto a maior versão oferece —
     * a razão entre os dois é o que a tela usa para dizer "quebre em partes" ou "exporte arquivo".
     */
    data class TooLong(
        val requiredBits: Int,
        val capacityBits: Int,
        val maxVersion: Int,
        val errorCorrection: QrErrorCorrection,
    ) : QrEncodeResult
}

/** O símbolo, ou `null` quando não cabe. */
val QrEncodeResult.qrCodeOrNull: QrCode?
    get() = (this as? QrEncodeResult.Success)?.qrCode

/**
 * Codifica [text] em um QR Code (ISO/IEC 18004).
 *
 * **Nunca lança** para conteúdo grande: devolve [QrEncodeResult.TooLong] (ver o motivo no KDoc de
 * [QrEncodeResult]). Argumento inválido de programação — versão fora de 1–40, quiet zone negativa,
 * máscara fora de 0–7 — **lança** `IllegalArgumentException`, porque é erro de quem chama, não do
 * usuário.
 *
 * O **modo** é escolhido automaticamente (o mais econômico que cubra o texto: numérico →
 * alfanumérico → bytes UTF-8) e a **versão** é a menor que couber, a partir de [minVersion]. A
 * **máscara** é a de menor penalidade, salvo [forcedMask].
 *
 * ```kotlin
 * val qr = encodeQr(cofreJson, QrErrorCorrection.L).qrCodeOrNull   // L: leitura tela→tela
 *     ?: return exportarArquivo()                                   // não cabe: outro caminho
 * QrCodeView(qr)
 * ```
 *
 * @param errorCorrection nível de correção — ver [QrErrorCorrection] para o trade-off
 *   capacidade × robustez (para QR lido de tela, `L` é a escolha correta e a que mais cabe).
 * @param quietZone margem clara em módulos. **Não reduza abaixo de 4**: é exigência do padrão, e o
 *   valor é clampado nesse mínimo justamente porque "economizar" aqui é o caminho conhecido para um
 *   QR que só lê em alguns aparelhos.
 * @param minVersion versão mínima (útil para manter tamanho estável entre conteúdos diferentes).
 * @param maxVersion versão máxima aceitável.
 * @param forcedMask máscara fixa (0–7) em vez da escolha automática — para teste/reprodutibilidade.
 */
fun encodeQr(
    text: String,
    errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
    quietZone: Int = QrCode.QUIET_ZONE,
    minVersion: Int = QrCode.MIN_VERSION,
    maxVersion: Int = QrCode.MAX_VERSION,
    forcedMask: Int? = null,
): QrEncodeResult {
    require(minVersion in QrCode.MIN_VERSION..QrCode.MAX_VERSION) { "minVersion inválida: $minVersion" }
    require(maxVersion in minVersion..QrCode.MAX_VERSION) { "maxVersion inválida: $maxVersion" }
    require(quietZone >= 0) { "quietZone não pode ser negativa: $quietZone" }
    require(forcedMask == null || forcedMask in 0 until QrMask.COUNT) { "máscara inválida: $forcedMask" }

    val effectiveQuietZone = maxOf(QrCode.QUIET_ZONE, quietZone)
    val segment = QrSegment.of(text)

    var version = minVersion
    var dataCodewords = 0
    while (true) {
        dataCodewords = QrTables.dataCodewords(version, errorCorrection)
        if (segment.bitLength(version) <= dataCodewords * 8) break
        if (version >= maxVersion) {
            return QrEncodeResult.TooLong(
                requiredBits = segment.bitLength(maxVersion),
                capacityBits = QrTables.dataCapacityBits(maxVersion, errorCorrection),
                maxVersion = maxVersion,
                errorCorrection = errorCorrection,
            )
        }
        version++
    }

    // Cabeçalho (modo + contador) e dados, fechados em codewords com terminador e padding.
    val buffer = QrBitBuffer(dataCodewords * 8)
    buffer.appendBits(segment.mode.modeBits, 4)
    buffer.appendBits(segment.characterCount, segment.mode.characterCountBits(version))
    buffer.appendAll(segment.data)
    val data = buffer.toDataCodewords(dataCodewords)

    val codewords = addErrorCorrectionAndInterleave(data, version, errorCorrection)
    val built = QrMatrixBuilder.build(version, errorCorrection, codewords, forcedMask)

    return QrEncodeResult.Success(
        QrCode(
            version = version,
            errorCorrection = errorCorrection,
            mode = segment.mode,
            mask = built.mask,
            quietZone = effectiveQuietZone,
            symbolSize = built.size,
            modules = withQuietZone(built.dark, built.size, effectiveQuietZone),
        ),
    )
}

/** Atalho: o símbolo, ou `null` se o conteúdo não couber. */
fun encodeQrOrNull(
    text: String,
    errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
    quietZone: Int = QrCode.QUIET_ZONE,
    minVersion: Int = QrCode.MIN_VERSION,
    maxVersion: Int = QrCode.MAX_VERSION,
    forcedMask: Int? = null,
): QrCode? = encodeQr(text, errorCorrection, quietZone, minVersion, maxVersion, forcedMask).qrCodeOrNull

/**
 * Divide os dados em blocos, calcula a EC de cada um e **intercala** tudo na ordem de transmissão.
 *
 * O intercalamento existe para espalhar um dano físico entre vários blocos (um arranhão vertical tira
 * poucos *codewords* de cada bloco, em vez de destruir um bloco inteiro) — e é justamente por isso que
 * concatenar em vez de intercalar produz um símbolo que **só falha quando sujo**, o pior modo de
 * falhar. Blocos "curtos" e "longos" convivem: os longos têm 1 *codeword* de dados a mais, e o padrão
 * define que o *codeword* extra entra depois de todos os curtos.
 */
private fun addErrorCorrectionAndInterleave(
    data: ByteArray,
    version: Int,
    level: QrErrorCorrection,
): ByteArray {
    val blockCount = QrTables.errorCorrectionBlocks(version, level)
    val eccPerBlock = QrTables.eccCodewordsPerBlock(version, level)
    val totalCodewords = QrTables.totalCodewords(version)
    require(data.size == QrTables.dataCodewords(version, level)) { "tamanho de dados inconsistente" }

    val shortBlockCount = blockCount - totalCodewords % blockCount
    val shortBlockLength = totalCodewords / blockCount
    val shortDataLength = shortBlockLength - eccPerBlock

    val blocks = ArrayList<ByteArray>(blockCount)
    var offset = 0
    for (index in 0 until blockCount) {
        val dataLength = shortDataLength + if (index < shortBlockCount) 0 else 1
        val blockData = data.copyOfRange(offset, offset + dataLength)
        offset += dataLength
        // O bloco fica com o tamanho do maior (curto + 1); a EC vai sempre no fim.
        val block = blockData.copyOf(shortBlockLength + 1)
        val ecc = QrReedSolomon.errorCorrectionCodewords(blockData, eccPerBlock)
        ecc.copyInto(block, destinationOffset = block.size - eccPerBlock)
        blocks += block
    }

    val result = ByteArray(totalCodewords)
    var target = 0
    for (position in 0 until blocks[0].size) {
        for (blockIndex in blocks.indices) {
            // Salta a posição de preenchimento que os blocos curtos não têm.
            if (position != shortDataLength || blockIndex >= shortBlockCount) {
                result[target++] = blocks[blockIndex][position]
            }
        }
    }
    return result
}

/** Envolve a matriz do símbolo com a quiet zone (área clara). */
private fun withQuietZone(symbol: BooleanArray, symbolSize: Int, quietZone: Int): BooleanArray {
    val size = symbolSize + quietZone * 2
    val result = BooleanArray(size * size)
    for (y in 0 until symbolSize) {
        for (x in 0 until symbolSize) {
            if (symbol[y * symbolSize + x]) {
                result[(y + quietZone) * size + (x + quietZone)] = true
            }
        }
    }
    return result
}
