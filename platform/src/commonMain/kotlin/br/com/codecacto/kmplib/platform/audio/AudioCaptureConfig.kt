package br.com.codecacto.kmplib.platform.audio

/**
 * **Ponderação em frequência** — o filtro aplicado ao sinal antes de calcular o nível.
 *
 * - [Z]: sem ponderação nenhuma (*zero weighting*, sinal cru). É o que um afinador ou um
 *   detector de frequência quer: qualquer filtro aqui deformaria a amplitude relativa dos
 *   harmônicos.
 * - [A]: curva A da **IEC 61672-1**, que imita a sensibilidade do ouvido humano — atenua muito os
 *   graves (-39 dB em 31,5 Hz) e um pouco os agudos extremos. É a ponderação que **todo
 *   decibelímetro de referência** usa: sem ela, o número infla nos graves (ar-condicionado,
 *   trânsito distante, vento no microfone) e não bate com o que um medidor comercial mostra.
 *
 * A curva C **não existe aqui de propósito**: ela só serve a pico impulsivo e ruído industrial, e
 * não tem consumidor na fábrica — módulo novo não nasce com opção sem dono.
 */
enum class AudioWeighting {
    /** Sem ponderação: o sinal como o conversor entregou. */
    Z,

    /** Curva A da IEC 61672-1 — o padrão de qualquer medidor de nível sonoro. */
    A,
}

/**
 * **Integração temporal** da IEC 61672-1 — o quanto o número "segura" antes de acompanhar o som.
 *
 * Sem integração, cada janela de ~150 ms é medida isolada e o número **pula** a cada atualização:
 * a tela fica ilegível e a pessoa não consegue ler o valor antes de ele mudar. A norma resolve isso
 * com uma média exponencial de constante de tempo τ:
 *
 * - [FAST] (τ = 125 ms): o default. Acompanha a fala e o evento curto, já estável o bastante para
 *   ler.
 * - [SLOW] (τ = 1 s): para ruído variável (trânsito, ambiente) — o número quase não oscila, ao
 *   custo de reagir devagar a um estouro.
 * - [NONE] (τ = 0): sem suavização, cada janela isolada. Só faz sentido para quem quer o dado cru
 *   e vai suavizar do seu jeito.
 *
 * ⚠️ A suavização é feita **sobre a potência (RMS²), nunca sobre o valor em dB** — média de
 * decibéis não é média de energia, e suavizar em dB devolve número errado em transiente. Quem faz
 * isso é o [TimeWeightingIntegrator], em código comum e testado.
 */
enum class AudioTimeWeighting(val tauSeconds: Double) {
    /** τ = 125 ms — resposta rápida (default da norma e desta lib). */
    FAST(0.125),

    /** τ = 1 s — resposta lenta, número estável. */
    SLOW(1.0),

    /** Sem integração: cada janela isolada. */
    NONE(0.0),
}

/**
 * **Fonte de entrada que a plataforma efetivamente abriu**, para log e diagnóstico.
 *
 * Importa porque o processamento do sistema (ganho automático, supressão de ruído, cancelamento de
 * eco) **destrói** a relação amplitude → SPL: o AGC "conserta" exatamente aquilo que estamos
 * medindo. Duas leituras feitas em fontes diferentes não são comparáveis — e a calibração de
 * [SplCalibration] feita numa **não vale** na outra.
 *
 * - [UNPROCESSED]: Android `MediaRecorder.AudioSource.UNPROCESSED` (o aparelho declara suporte) ou
 *   iOS `AVAudioSession` em `AVAudioSessionModeMeasurement`. É a fonte que se quer.
 * - [VOICE_RECOGNITION]: Android `VOICE_RECOGNITION` — o fallback documentado como o que menos
 *   processa entre as fontes sempre disponíveis (`MIC` costuma vir com AGC/NS em OEM).
 * - [DEFAULT]: a plataforma não deixou escolher; leitura sujeita a processamento do sistema.
 */
enum class AudioInputSource {
    UNPROCESSED,
    VOICE_RECOGNITION,
    DEFAULT,
}

/**
 * Configuração de uma sessão de captura.
 *
 * ⚠️ **Nem tudo muda a quente.** [preferredSampleRate] e [emitFrames] são decididos na abertura do
 * recurso nativo: trocá-los exige **um novo [createAudioCapture]** (o anterior deve ser
 * [AudioCapture.release]ado). O que muda com a captura rodando é só o que está em
 * [AudioCapture.updateProcessing] — [weighting], [timeWeighting] e [emitIntervalMillis] —, que é o
 * necessário para uma tela de Configurações não precisar reabrir o microfone a cada toque.
 *
 * @param preferredSampleRate taxa **preferida**, em Hz. A plataforma pode abrir noutra; a efetiva
 *   vai em [AudioCaptureState.Running.sampleRate] e em cada [AudioLevel.sampleRate].
 * @param weighting ponderação em frequência (default [AudioWeighting.A], como num decibelímetro).
 * @param timeWeighting integração temporal (default [AudioTimeWeighting.FAST]).
 * @param emitIntervalMillis cadência de emissão de [AudioLevel]. O default de 150 ms fica no meio
 *   da faixa que a tela precisa para parecer "tempo real" sem piscar.
 * @param emitFrames liga [AudioCapture.frames]. **Opt-in**: copiar o buffer a cada bloco custa
 *   alocação e cópia, e só quem faz análise do sinal (detecção de frequência, afinador) precisa —
 *   um medidor de nível não paga esse preço.
 */
data class AudioCaptureConfig(
    val preferredSampleRate: Int = DEFAULT_SAMPLE_RATE,
    val weighting: AudioWeighting = AudioWeighting.A,
    val timeWeighting: AudioTimeWeighting = AudioTimeWeighting.FAST,
    val emitIntervalMillis: Long = DEFAULT_EMIT_INTERVAL_MILLIS,
    val emitFrames: Boolean = false,
) {
    init {
        require(preferredSampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) {
            "preferredSampleRate deve estar entre $MIN_SAMPLE_RATE e $MAX_SAMPLE_RATE Hz"
        }
        require(emitIntervalMillis in MIN_EMIT_INTERVAL_MILLIS..MAX_EMIT_INTERVAL_MILLIS) {
            "emitIntervalMillis deve estar entre $MIN_EMIT_INTERVAL_MILLIS e " +
                "$MAX_EMIT_INTERVAL_MILLIS ms"
        }
    }

    companion object {
        /** 44,1 kHz — a taxa que todo aparelho abre, e a de melhor resolução na curva A. */
        const val DEFAULT_SAMPLE_RATE: Int = 44_100

        /** Segunda tentativa quando o aparelho recusa a preferida (muito comum no Android). */
        const val FALLBACK_SAMPLE_RATE: Int = 48_000

        /** Último recurso: banda de voz. Ainda mede, com menos resolução acima de 8 kHz. */
        const val LAST_RESORT_SAMPLE_RATE: Int = 16_000

        /** Abaixo disto a curva A não tem onde existir (1 kHz precisa caber com folga). */
        const val MIN_SAMPLE_RATE: Int = 8_000

        /** Teto sanitário: nenhum microfone de celular passa disto. */
        const val MAX_SAMPLE_RATE: Int = 192_000

        /** Cadência default de emissão (ms). */
        const val DEFAULT_EMIT_INTERVAL_MILLIS: Long = 150L

        /** Emitir mais rápido que isto não é perceptível e só gasta bateria. */
        const val MIN_EMIT_INTERVAL_MILLIS: Long = 20L

        /** Acima disto a tela deixa de parecer tempo real. */
        const val MAX_EMIT_INTERVAL_MILLIS: Long = 5_000L
    }
}
