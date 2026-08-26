package br.com.codecacto.kmplib.platform.audio

/**
 * **Por que a captura não está medindo.** Erro tipado, para o app decidir o que mostrar sem ter de
 * ler mensagem de texto.
 */
sealed interface AudioCaptureError {

    /**
     * A permissão de microfone não está concedida.
     *
     * ⚠️ **A lib não pede a permissão** — ela só confere e recusa. Pedir é do app, via
     * `PermissionManager` + `AppPermission.MICROPHONE`, depois da tela de contexto que explica por
     * que o microfone é preciso. A conferência aqui existe porque, sem ela, o Android **abre** o
     * `AudioRecord` normalmente e entrega **silêncio digital**: o app mostraria -120 dB para
     * sempre, sem exceção, sem log e sem nenhuma pista da causa.
     */
    data object PermissionDenied : AudioCaptureError

    /**
     * O microfone existe mas não pôde ser aberto agora: outro app o tomou, uma ligação está em
     * curso, o recurso nativo recusou a inicialização.
     */
    data object DeviceUnavailable : AudioCaptureError

    /**
     * Nenhuma configuração de captura foi aceita pelo aparelho (nem a preferida, nem os
     * fallbacks de taxa).
     */
    data object UnsupportedConfiguration : AudioCaptureError

    /** Falha que não se encaixa nas acima; [message] é para log, não para a tela. */
    data class Unknown(val message: String) : AudioCaptureError
}

/**
 * **Estado da captura.** É `StateFlow`, então a tela sempre tem um valor atual — inclusive depois
 * de uma falha, que não é evento perdido no meio do caminho.
 */
sealed interface AudioCaptureState {

    /** Criada e ainda não iniciada, ou parada por [AudioCapture.stop]. Pode receber `start()`. */
    data object Idle : AudioCaptureState

    /** `start()` foi chamado e o recurso nativo está sendo aberto. */
    data object Starting : AudioCaptureState

    /**
     * Medindo. [source] e [sampleRate] são o que a plataforma **de fato** entregou — não o que foi
     * pedido.
     */
    data class Running(
        val source: AudioInputSource,
        val sampleRate: Int,
    ) : AudioCaptureState

    /**
     * Interrompida **pelo sistema**, não pelo app: ligação recebida, Siri, outro app tomando a
     * sessão de áudio. É um estado do iOS (`AVAudioSessionInterruptionNotification`); no Android a
     * captura de outro app simplesmente não interrompe a nossa.
     *
     * A captura volta sozinha quando o sistema avisa que pode retomar. O app usa este estado para
     * dizer "pausado por uma ligação" em vez de deixar o número congelado na tela — que é o que
     * acontecia sem tratar isto.
     */
    data class Interrupted(val reason: String) : AudioCaptureState

    /** Falhou. A causa está em [error]. */
    data class Failed(val error: AudioCaptureError) : AudioCaptureState

    /**
     * Terminal: [AudioCapture.release] soltou o recurso nativo. Um objeto liberado **não** volta a
     * medir — é preciso um novo [createAudioCapture].
     */
    data object Released : AudioCaptureState
}
