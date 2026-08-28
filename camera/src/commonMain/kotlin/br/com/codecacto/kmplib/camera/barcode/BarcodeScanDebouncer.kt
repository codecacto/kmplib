package br.com.codecacto.kmplib.camera.barcode

/**
 * Regras de **anti-repetição** da leitura contínua.
 *
 * Uma câmera de scanner entrega 15–30 frames por segundo, e o código que continua na mira é
 * reconhecido em **todos** eles. Sem estas regras, apontar para um produto por dois segundos
 * dispararia o callback ~50 vezes — no Controle de Validade, cinquenta lotes cadastrados.
 *
 * As três regras são independentes e resolvem problemas diferentes:
 *
 * | Regra | Problema que resolve |
 * |---|---|
 * | [requiredConsecutiveReads] | leitura instável (gôndola escura, código amassado) virando produto errado |
 * | [anyCodeCooldownMillis] | a mira passar por cima do produto **vizinho** e capturá-lo junto |
 * | [sameCodeCooldownMillis] | o **mesmo** código disparando a cada frame enquanto está na mira |
 *
 * @property sameCodeCooldownMillis janela em que o **mesmo** valor é ignorado após ser aceito.
 *   Passada a janela, o código é aceito de novo — de propósito: ler duas caixas iguais com
 *   validades diferentes é o caso normal do "escanear vários seguidos". `0` desliga a supressão.
 * @property anyCodeCooldownMillis janela mínima entre **duas leituras quaisquer**. Segura a
 *   rajada em que dois códigos aparecem no mesmo enquadramento.
 * @property requiredConsecutiveReads quantas leituras seguidas do mesmo valor são exigidas antes
 *   de aceitar. `1` (default) = aceita na primeira — as simbologias de varejo já vêm com dígito
 *   verificador conferido. Suba para `2`/`3` em ambiente ruim, ao custo de alguns frames.
 */
data class BarcodeScanDebounce(
    val sameCodeCooldownMillis: Long = 2_500L,
    val anyCodeCooldownMillis: Long = 600L,
    val requiredConsecutiveReads: Int = 1,
) {
    init {
        require(sameCodeCooldownMillis >= 0) { "sameCodeCooldownMillis não pode ser negativo" }
        require(anyCodeCooldownMillis >= 0) { "anyCodeCooldownMillis não pode ser negativo" }
        require(requiredConsecutiveReads >= 1) { "requiredConsecutiveReads deve ser >= 1" }
    }

    companion object {
        /**
         * Perfil para o modo **"escanear vários seguidos"**: janela curta do mesmo código, para
         * que o operador possa reler o mesmo produto (outra caixa, outra validade) sem esperar.
         */
        val SEQUENCE = BarcodeScanDebounce(
            sameCodeCooldownMillis = 1_200L,
            anyCodeCooldownMillis = 500L,
        )
    }
}

/**
 * Filtro de leitura contínua — decide **se um código lido agora deve virar um evento** para o app.
 *
 * `commonMain` puro e determinístico (o tempo entra por parâmetro), portanto testável sem device:
 * é aqui que mora a diferença entre "scanner" e "trinta cadastros por segundo". As implementações
 * Android e iOS entregam frames crus; quem aplica esta política é o [BarcodeScannerView], em
 * código comum — assim as duas plataformas se comportam igual.
 *
 * **Não é thread-safe**: use uma instância por tela, e chame sempre da mesma thread (o
 * [BarcodeScannerView] chama da main).
 *
 * ```kotlin
 * val debouncer = remember { BarcodeScanDebouncer(BarcodeScanDebounce.SEQUENCE) }
 * if (debouncer.accept(codigo.value, currentTimeMillis())) onBarcodeScanned(codigo)
 * ```
 */
class BarcodeScanDebouncer(
    private val config: BarcodeScanDebounce = BarcodeScanDebounce(),
) {
    private val acceptedAtByValue = LinkedHashMap<String, Long>()
    private var lastAcceptedAt: Long? = null
    private var pendingValue: String? = null
    private var pendingCount: Int = 0

    /**
     * `true` se [value] deve ser entregue ao app agora.
     *
     * @param nowMillis relógio monotônico do chamador (ex.: `currentTimeMillis()`).
     */
    fun accept(value: String, nowMillis: Long): Boolean {
        if (value.isBlank()) return false

        // 1) Confirmação por leituras consecutivas.
        if (value == pendingValue) pendingCount++ else {
            pendingValue = value
            pendingCount = 1
        }
        if (pendingCount < config.requiredConsecutiveReads) return false

        // 2) Cooldown global (qualquer código).
        val last = lastAcceptedAt
        if (last != null && nowMillis - last < config.anyCodeCooldownMillis) return false

        // 3) Cooldown do MESMO código.
        purgeExpired(nowMillis)
        val sameAt = acceptedAtByValue[value]
        if (sameAt != null && nowMillis - sameAt < config.sameCodeCooldownMillis) return false

        lastAcceptedAt = nowMillis
        acceptedAtByValue.remove(value)
        acceptedAtByValue[value] = nowMillis
        pendingValue = null
        pendingCount = 0
        return true
    }

    /**
     * Esquece tudo o que foi lido — o próximo código, **inclusive o último aceito**, passa na hora.
     *
     * É o que a tela chama depois de gravar o item ("Salvo: Leite Integral 1L"): o operador volta
     * para a gôndola e pode reapontar para o mesmo produto sem esperar o cooldown.
     */
    fun reset() {
        acceptedAtByValue.clear()
        lastAcceptedAt = null
        pendingValue = null
        pendingCount = 0
    }

    private fun purgeExpired(nowMillis: Long) {
        if (acceptedAtByValue.isEmpty()) return
        val iterator = acceptedAtByValue.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value >= config.sameCodeCooldownMillis) iterator.remove()
        }
        // Trava de tamanho: o histórico é uma otimização, não pode virar vazamento numa jornada
        // inteira de "escanear vários seguidos".
        while (acceptedAtByValue.size > MAX_HISTORY) {
            val oldest = acceptedAtByValue.keys.firstOrNull() ?: break
            acceptedAtByValue.remove(oldest)
        }
    }

    private companion object {
        const val MAX_HISTORY = 64
    }
}
