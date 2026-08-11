package br.com.codecacto.kmplib.qr

/**
 * Resposta de "**este payload cabe num QR?**" — com o número que a tela precisa, não só um sim/não.
 *
 * Existe porque a decisão de produto vem **antes** de tentar gerar: a tela de exportar escolhe entre
 * **QR de transferência** e **arquivo** olhando isto. Sem esse helper, o app cairia no comportamento
 * errado (tentar gerar e falhar na cara do usuário) ou reimplementaria a tabela de capacidade do
 * padrão por conta própria.
 */
data class QrCapacityCheck(

    /** `true` se o conteúdo cabe dentro de [maxVersion] no nível pedido. */
    val fits: Boolean,

    /**
     * Menor versão (1–40) capaz de conter o conteúdo, ou `null` se nem [maxVersion] serve.
     *
     * Útil para avisar antes de gerar: uma versão alta significa um QR com muitos módulos, difícil de
     * ler de longe ou em tela pequena — é informação de UX, não só de capacidade.
     */
    val requiredVersion: Int?,

    /** Bits que o conteúdo ocupa (cabeçalho de modo + contador + dados) na versão de referência. */
    val requiredBits: Int,

    /** Bits de dados disponíveis na versão de referência (a exigida, se couber; senão [maxVersion]). */
    val capacityBits: Int,

    /** Modo que a lib escolheria para este conteúdo. */
    val mode: QrMode,

    /** Nível de correção considerado. */
    val errorCorrection: QrErrorCorrection,

    /** Teto de versão considerado na conta. */
    val maxVersion: Int,
) {

    /**
     * Bits ainda livres na versão de referência (0 quando não cabe).
     *
     * O uso prático é "ainda dá para acrescentar mais uma plaquinha neste QR?" — daí existir também
     * [remainingBytes].
     */
    val remainingBits: Int get() = if (fits) capacityBits - requiredBits else 0

    /** Bytes livres aproximados (`remainingBits / 8`) — a conta que o app faz para decidir. */
    val remainingBytes: Int get() = remainingBits / 8

    /** Fração da capacidade já usada (0f–1f), para barra de "quanto do QR está cheio". */
    val usedFraction: Float
        get() = if (capacityBits <= 0) 0f else (requiredBits.toFloat() / capacityBits).coerceIn(0f, 1f)
}

/**
 * **"Este payload cabe num QR escaneável com confiança?"** — sem gerar o símbolo e sem cada app
 * recalcular a tabela de capacidade do padrão.
 *
 * ```kotlin
 * val check = qrCodeFits(cofreJson, QrErrorCorrection.L, maxVersion = 20)
 * if (check.fits) mostrarQr() else mostrarExportarArquivo(check)
 * ```
 *
 * @param maxVersion teto de versão. O default é [QrCode.MAX_VERSION] (40, o limite do padrão), mas
 *   **um teto menor costuma ser a escolha certa de produto**: a versão 40 tem 177×177 módulos e, numa
 *   tela de celular a meio metro de distância, cada módulo fica com menos de um pixel de câmera — o
 *   símbolo é válido e ninguém consegue ler. Para transferência tela↔tela, algo em torno de 15–25 é
 *   um limite honesto.
 */
fun qrCodeFits(
    text: String,
    errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
    maxVersion: Int = QrCode.MAX_VERSION,
    minVersion: Int = QrCode.MIN_VERSION,
): QrCapacityCheck {
    require(minVersion in QrCode.MIN_VERSION..QrCode.MAX_VERSION) { "minVersion inválida: $minVersion" }
    require(maxVersion in minVersion..QrCode.MAX_VERSION) { "maxVersion inválida: $maxVersion" }

    val segment = QrSegment.of(text)

    for (version in minVersion..maxVersion) {
        val capacity = QrTables.dataCapacityBits(version, errorCorrection)
        val required = segment.bitLength(version)
        if (required <= capacity) {
            return QrCapacityCheck(
                fits = true,
                requiredVersion = version,
                requiredBits = required,
                capacityBits = capacity,
                mode = segment.mode,
                errorCorrection = errorCorrection,
                maxVersion = maxVersion,
            )
        }
    }

    return QrCapacityCheck(
        fits = false,
        requiredVersion = null,
        requiredBits = segment.bitLength(maxVersion),
        capacityBits = QrTables.dataCapacityBits(maxVersion, errorCorrection),
        mode = segment.mode,
        errorCorrection = errorCorrection,
        maxVersion = maxVersion,
    )
}

/**
 * Versão booleana de [qrCodeFits], para quando a tela só precisa do sim/não.
 *
 * Prefira [qrCodeFits] quando for exibir algo ao usuário: "não cabe" sem dizer **quanto** falta não
 * permite escrever uma mensagem útil.
 */
fun qrCodeFitsPayload(
    text: String,
    errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
    maxVersion: Int = QrCode.MAX_VERSION,
): Boolean = qrCodeFits(text, errorCorrection, maxVersion).fits

/**
 * Capacidade de dados, **em bytes de modo binário**, de uma versão/nível — a régua para dimensionar
 * um payload antes de montá-lo.
 *
 * Desconta o cabeçalho de modo e o contador de caracteres, então o número é o que de fato dá para
 * gravar (não a capacidade bruta, que costuma ser citada e induz ao erro de "deveria caber").
 */
fun qrByteCapacity(version: Int, errorCorrection: QrErrorCorrection = QrErrorCorrection.M): Int {
    require(version in QrCode.MIN_VERSION..QrCode.MAX_VERSION) { "versão inválida: $version" }
    val overhead = 4 + QrMode.Byte.characterCountBits(version)
    val available = QrTables.dataCapacityBits(version, errorCorrection) - overhead
    return maxOf(0, available / 8)
}
