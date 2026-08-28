package br.com.codecacto.kmplib.torch

/**
 * **O que a lanterna deste aparelho de fato faz** — consultável ANTES de desenhar a tela.
 *
 * Existe para que o app não descubra no toque que o slider de intensidade não tem efeito: com
 * [supportsIntensity] `false` o slider simplesmente **não aparece** (e a tela explica em uma linha),
 * em vez de virar um controle que mexe e não muda nada.
 *
 * A capacidade é **de runtime**, nunca deduzida só da versão do SO: no Android a intensidade
 * variável exige API 33+ **e** um aparelho cuja câmera declare mais de um nível de força — as duas
 * coisas juntas. Ver [androidTorchCapabilities].
 */
data class TorchCapabilities(
    /** `true` se existe unidade de flash utilizável (câmera traseira com LED). */
    val hasTorch: Boolean,
    /**
     * `true` se o hardware aceita **nível de força real**. `false` ⇒ liga/desliga simples.
     *
     * Quando `false`, é **PROIBIDO** simular intensidade piscando o LED (PWM): aquece e desgasta o
     * componente. A capacidade é reportada como ausente e ponto.
     */
    val supportsIntensity: Boolean,
    /**
     * Quantos níveis discretos o hardware oferece.
     *
     * - [CONTINUOUS] (`0`) — faixa **contínua** (iOS: `setTorchModeOn(level:)`, 0..1).
     * - `1` — só liga/desliga (não há intensidade).
     * - `n > 1` — `n` degraus (Android: `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`).
     */
    val levelCount: Int,
) {

    /** `true` quando a intensidade é uma faixa contínua, sem degraus (iOS). */
    val isContinuous: Boolean get() = levelCount == CONTINUOUS

    /**
     * `steps` para um `Slider` do Compose: `0` quando a faixa é contínua (ou não há intensidade),
     * `levelCount - 1` quando é discreta — evita que cada app refaça essa conta.
     */
    val sliderSteps: Int get() = if (!supportsIntensity || isContinuous) 0 else levelCount - 1

    companion object {
        /** Faixa contínua de intensidade (sem degraus). Valor de [levelCount] no iOS. */
        const val CONTINUOUS: Int = 0

        /** Aparelho sem lanterna — o estado inicial de quem não conseguiu abrir a câmera. */
        val NONE: TorchCapabilities = TorchCapabilities(
            hasTorch = false,
            supportsIntensity = false,
            levelCount = 1,
        )

        /**
         * Decide a capacidade a partir dos três fatos que a plataforma informa. **Ponto único** da
         * regra — os dois `actual` passam por aqui, então Android e iOS concordam no critério.
         *
         * @param hasTorch existe unidade de flash.
         * @param levelCount níveis declarados pelo hardware ([CONTINUOUS] para faixa contínua).
         * @param platformSupportsIntensity a API de intensidade existe nesta versão do SO.
         */
        fun resolve(
            hasTorch: Boolean,
            levelCount: Int,
            platformSupportsIntensity: Boolean,
        ): TorchCapabilities {
            if (!hasTorch) return NONE
            val normalized = if (levelCount <= CONTINUOUS) CONTINUOUS else levelCount
            // Teto 1 = um nível só: o hardware "suporta" a API mas não há o que variar.
            val hasRoomToVary = normalized == CONTINUOUS || normalized > 1
            return TorchCapabilities(
                hasTorch = true,
                supportsIntensity = platformSupportsIntensity && hasRoomToVary,
                levelCount = if (normalized == CONTINUOUS) CONTINUOUS else normalized,
            )
        }
    }
}

/**
 * Primeira versão do Android com `CameraManager.turnOnTorchWithStrengthLevel` — **Android 13
 * (API 33)**. Abaixo disso a lanterna é liga/desliga, por mais níveis que o LED tenha.
 */
internal const val ANDROID_VARIABLE_TORCH_MIN_SDK: Int = 33

/**
 * Capacidade no **Android**: intensidade só existe com API 33+ **e** teto
 * (`FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`) maior que 1.
 *
 * Mora em `commonMain` de propósito: é a regra que mais erra na prática ("é Android 13, então tem
 * slider"), e aqui ela é coberta por teste sem precisar de aparelho.
 *
 * @param maxLevel teto declarado pela câmera; `0`/ausente é tratado como `1` (sem variação).
 */
internal fun androidTorchCapabilities(
    hasFlashUnit: Boolean,
    sdkInt: Int,
    maxLevel: Int,
): TorchCapabilities = TorchCapabilities.resolve(
    hasTorch = hasFlashUnit,
    levelCount = if (maxLevel < 1) 1 else maxLevel,
    platformSupportsIntensity = sdkInt >= ANDROID_VARIABLE_TORCH_MIN_SDK,
)

/**
 * Capacidade no **iOS**: `AVCaptureDevice.setTorchModeOn(level:)` aceita uma faixa **contínua**
 * (0..1) em todas as versões suportadas — não há corte por versão de SO, só a existência do LED.
 */
internal fun iosTorchCapabilities(hasTorch: Boolean): TorchCapabilities =
    TorchCapabilities.resolve(
        hasTorch = hasTorch,
        levelCount = TorchCapabilities.CONTINUOUS,
        platformSupportsIntensity = hasTorch,
    )
