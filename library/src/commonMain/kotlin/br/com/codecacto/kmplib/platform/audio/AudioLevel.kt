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
 *   temporal configurada. É o número que vai para a tela.
 * @param peakDbfs maior amostra do intervalo, **sem** ponderação e **sem** integração — a verdade
 *   crua do conversor. Ponderar antes de medir o pico esconderia a saturação, que é justamente o
 *   que ele existe para denunciar. Vale sempre `peakDbfs >= rmsDbfs`.
 * @param isClipping o sinal encostou no teto do conversor nesta janela (ver [AudioLevelAnalyzer]).
 *   Quando `true`, o número **não é confiável**: o app deve trocá-lo por um aviso de saturação em
 *   vez de exibir um valor exato que já não significa nada.
 * @param sampleRate taxa **efetivamente** aberta pela plataforma (pode não ser a preferida, e pode
 *   mudar no meio da sessão no iOS, quando um fone é plugado).
 * @param weighting ponderação que produziu [rmsDbfs].
 * @param timestampMillis instante da emissão (relógio da plataforma, epoch em ms).
 */
data class AudioLevel(
    val rmsDbfs: Double,
    val peakDbfs: Double,
    val isClipping: Boolean,
    val sampleRate: Int,
    val weighting: AudioWeighting,
    val timestampMillis: Long,
) {
    companion object {
        /**
         * **Piso de silêncio, em dBFS.**
         *
         * Uma janela em silêncio digital tem potência zero, e `log10(0)` é `-Infinity` — que vira
         * `NaN` na primeira animação ou formatação da UI e **some com o número da tela**, sem erro
         * nenhum no log. Por isso todo campo em dB desta classe é grampeado neste piso, e
         * `Infinity`/`NaN` **nunca** são emitidos.
         *
         * -120 dBFS é bem abaixo do ruído próprio de qualquer microfone de celular (que fica na
         * casa de -60 a -80 dBFS), então o piso nunca esconde sinal real.
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
