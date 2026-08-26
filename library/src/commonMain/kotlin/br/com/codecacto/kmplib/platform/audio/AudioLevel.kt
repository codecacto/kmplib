package br.com.codecacto.kmplib.platform.audio

/**
 * **Uma leitura de nível**, referente à janela que acabou de ser medida.
 *
 * ⚠️ **A unidade é dBFS, não dB SPL** — e essa distinção é o coração da honestidade deste módulo.
 * dBFS é *decibel relativo ao fundo de escala digital*: `0 dBFS` é o máximo que o conversor do
 * aparelho consegue representar, e todo valor real é **negativo**. É a verdade do que o hardware
 * entregou, e é a única coisa que a lib pode afirmar sem mentir.
 *
 * dB SPL (o número que a pessoa quer ver na tela, "62 dB") depende da **sensibilidade do microfone
 * daquele modelo de aparelho**, que nenhuma API expõe. Converter de um para o outro é somar um
 * deslocamento — ver [SplCalibration], que faz a conta e diz, com todas as letras, por que ela é
 * por aparelho. **Isto não é um instrumento de medição.**
 *
 * @param rmsDbfs nível RMS da janela, **com** a ponderação de [weighting] e **com** a integração
 *   temporal configurada. É o número que vai para a tela. **Sem grampo**: o valor é o que o
 *   conversor entregou (ver [SILENCE_DBFS]).
 * @param peakDbfs maior amostra do intervalo, **sem** ponderação e **sem** integração — a verdade
 *   crua do conversor. Ponderar antes de medir o pico esconderia a saturação, que é justamente o
 *   que ele existe para denunciar. Vale sempre `peakDbfs >= rmsDbfs`.
 * @param noiseFloorDbfs **menor [rmsDbfs] observado nesta sessão** (desde o `start()` ou desde o
 *   último `reset()` do analisador). Enquanto nada foi medido — e em silêncio digital, quando não
 *   há sinal nenhum a observar — vale [SILENCE_DBFS]. É o ruído próprio do conjunto
 *   microfone + pré-amplificador **daquele aparelho**, e existe para o app poder dizer "abaixo
 *   disto seu aparelho não distingue" em vez de apresentar ruído próprio como medição — a
 *   reclamação nº 1 da categoria (ver `references/platform-audio-capture.md`).
 * @param isClipping a janela saturou: a **fração** de amostras no fundo de escala
 *   ([clippedSampleRatio]) passou de [AudioLevelAnalyzer.CLIPPING_RATIO_THRESHOLD]. Quando `true`,
 *   o número **não é confiável**: o app deve trocá-lo por um aviso de saturação em vez de exibir um
 *   valor exato que já não significa nada.
 * @param clippedSampleRatio fração das amostras da janela que encostaram no fundo de escala
 *   (`0f`..`1f`), medida no sinal **cru**, antes de qualquer ponderação. Uma amostra isolada não
 *   satura janela nenhuma — é por isso que a decisão é por fração e não por "houve pico".
 * @param sampleRate taxa **efetivamente** aberta pela plataforma (pode não ser a preferida, e pode
 *   mudar no meio da sessão no iOS, quando um fone é plugado).
 * @param weighting ponderação que produziu [rmsDbfs].
 * @param timestampMillis instante da emissão (relógio da plataforma, epoch em ms).
 */
data class AudioLevel(
    val rmsDbfs: Double,
    val peakDbfs: Double,
    val noiseFloorDbfs: Double,
    val isClipping: Boolean,
    val clippedSampleRatio: Float,
    val sampleRate: Int,
    val weighting: AudioWeighting,
    val timestampMillis: Long,
) {
    companion object {
        /**
         * **Sentinela de silêncio DIGITAL, em dBFS — não um piso de exibição.**
         *
         * Vale exatamente para um caso: a janela não tem sinal nenhum (RMS matematicamente zero —
         * microfone tomado por outro app, permissão negada com o `AudioRecord` aberto, buffer de
         * zeros). `log10(0)` é `-Infinity`, que vira `NaN` na primeira animação ou formatação da UI
         * e **some com o número da tela**, sem erro nenhum no log; este valor existe para que
         * `Infinity`/`NaN` **nunca** sejam emitidos.
         *
         * ⚠️ **Fora desse caso a lib NÃO inventa piso nenhum.** Toda leitura sai como o conversor
         * a entregou, sem grampo inferior: se o cálculo dá -97 dBFS, é -97 que sai. Grampear toda
         * leitura neste valor (como se fazia até a 2.150.0) era materialmente inócuo — nenhum
         * microfone de celular chega perto de -120, o ruído próprio deles fica em -60/-70 dBFS —,
         * mas mentia no **contrato**: quem lia o código concluía que existe um mínimo de exibição.
         * Não existe. O número é o do conversor, e o que houver abaixo do ruído próprio do aparelho
         * é [noiseFloorDbfs], medido, não arbitrado.
         */
        const val SILENCE_DBFS: Double = -120.0
    }
}

/**
 * **Um bloco de amostras cruas**, como saiu do conversor.
 *
 * Só é emitido quando [AudioCaptureConfig.emitFrames] é `true`. Existe para quem precisa **analisar
 * o sinal**, não só o nível: detecção de frequência (afinador por microfone), autocorrelação, FFT.
 * A lib entrega o buffer e para por aí — **não** faz FFT nem detecção de pitch, que são decisão de
 * cada produto.
 *
 * Não é `data class` de propósito: ela carrega um [ShortArray], e `equals`/`hashCode` gerados sobre
 * array comparam **referência**, produzindo uma igualdade que mente. Comparar dois blocos de áudio
 * amostra a amostra também não é operação que alguém queira sem pedir.
 *
 * @param samples PCM 16-bit **mono**, na ordem de captura. O array é exclusivo desta emissão (a
 *   captura já entrega uma cópia); o consumidor pode lê-lo à vontade, mas **não deve guardá-lo**
 *   além do necessário — em 44,1 kHz isto é ~88 KB por segundo.
 * @param sampleRate taxa efetiva destas amostras.
 * @param channelCount canais no buffer. Sempre `1` hoje: a captura é mono, porque nível sonoro não
 *   tem estéreo e o afinador só atrapalharia com dois canais somados.
 * @param timestampMillis instante da emissão (epoch em ms).
 */
class AudioFrame(
    val samples: ShortArray,
    val sampleRate: Int,
    val channelCount: Int,
    val timestampMillis: Long,
) {
    /** Quantidade de amostras do bloco — atalho para não expor `samples.size` em toda chamada. */
    val frameCount: Int get() = samples.size

    /** Duração do bloco em segundos, útil para janelar análise. */
    val durationSeconds: Double
        get() = if (sampleRate > 0) samples.size.toDouble() / sampleRate else 0.0
}
