package br.com.codecacto.kmplib.platform.audio

/**
 * **A conversão de dBFS para dB SPL — a conta trivial que não pode ser reinventada por app.**
 *
 * O microfone entrega [dBFS][AudioLevel.rmsDbfs] (relativo ao fundo de escala digital, sempre
 * negativo). O que a pessoa quer ver é **dB SPL** ("62 dB"), que depende da **sensibilidade do
 * microfone daquele modelo de aparelho** — informação que nenhuma API de Android ou iOS expõe. A
 * ponte entre os dois é uma soma:
 *
 * ```
 * dB SPL ≈ dBFS + offset
 * ```
 *
 * A aritmética é de uma linha; o que **não** é trivial é a constante. Ela é conhecimento
 * compartilhado — deixar cada app escolher a sua é exatamente como a fórmula se perde, e como dois
 * produtos da mesma fábrica passam a mostrar números diferentes para o mesmo som.
 *
 * ⚠️ **O offset é POR APARELHO e POR FONTE DE ENTRADA.**
 * - Por aparelho, porque a sensibilidade do microfone muda de modelo para modelo (e até entre
 *   unidades do mesmo modelo).
 * - Por fonte, porque trocar de [AudioInputSource.UNPROCESSED] para
 *   [AudioInputSource.VOICE_RECOGNITION] muda o ganho do caminho de captura — a calibração feita
 *   numa **não vale** na outra, e a leitura sai deslocada sem nada avisar.
 *
 * ⚠️ **Isto não é um instrumento de medição.** Mesmo calibrado contra uma referência, o resultado é
 * uma **aproximação**: o microfone de celular satura por volta de 90-105 dB SPL (ver
 * [AudioLevel.isClipping]), tem resposta em frequência própria e não certificada, e nada disto
 * substitui um decibelímetro. Nenhuma superfície deve apresentar o número como laudo, prova ou
 * medição oficial.
 *
 * **A lib faz a conta; o app guarda o valor.** Persistir o offset e desenhar a tela de calibração
 * é do produto (é ele que decide onde guarda e como pede), e por isso esta classe não tem
 * dependência nenhuma — é um `data class` com uma função.
 */
data class SplCalibration(
    /**
     * Deslocamento em dB somado à leitura. Positivo sobe o número exibido, negativo desce.
     * Zero-com-[DEFAULT_OFFSET_DB] não existe: "sem ajuste" para o **usuário** significa o offset
     * default, e é o app que decide como apresentar isso.
     */
    val offsetDb: Double = DEFAULT_OFFSET_DB,
) {

    /** Converte uma leitura em dBFS para dB SPL aproximado. */
    fun toSpl(dbfs: Double): Double = dbfs + offsetDb

    /**
     * Converte uma leitura já em dB SPL de volta para dBFS — o caminho que a tela de calibração
     * usa para descobrir o offset a partir de uma referência ("o medidor ao lado marca 74").
     */
    fun toDbfs(spl: Double): Double = spl - offsetDb

    companion object {
        /**
         * **Ponto de partida típico para microfone de celular, NÃO uma verdade absoluta.**
         *
         * Com 90, uma leitura de -30 dBFS aparece como 60 dB SPL — a ordem de grandeza certa para
         * uma conversa em sala fechada, que é o que faz o número parecer plausível na primeira
         * abertura do app. O valor real do aparelho pode estar 10 dB acima ou abaixo disto; é para
         * isso que existe a tela de calibração.
         */
        const val DEFAULT_OFFSET_DB: Double = 90.0

        /** Calibração neutra: a leitura sai em dBFS puro, sem deslocamento nenhum. */
        val NONE: SplCalibration = SplCalibration(offsetDb = 0.0)

        /** O ponto de partida da lib, para quem só quer um número plausível na tela. */
        val DEFAULT: SplCalibration = SplCalibration()
    }
}
