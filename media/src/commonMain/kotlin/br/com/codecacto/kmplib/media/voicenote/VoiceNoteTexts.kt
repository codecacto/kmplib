package br.com.codecacto.kmplib.media.voicenote

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_busy
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_cancel
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_description
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_loading
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_locked
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_open_settings
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_pause
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_permission
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_play
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_play_error
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_record
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_recording
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_retry
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_send
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_slide_to_cancel
import br.com.codecacto.kmplib.generated.resources.kmplib_voice_note_too_short
import br.com.codecacto.kmplib.media.AudioRecorderError
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Textos da nota de voz (gravador e player). Defaults em pt-BR; [rememberVoiceNoteTexts] traz os 4
 * idiomas da fábrica no idioma da tela — app global não passa nada.
 */
data class VoiceNoteTexts(
    val record: String = "Gravar áudio",
    val slideToCancel: String = "Deslize para cancelar",
    val recording: (elapsed: String) -> String = { "Gravando áudio, $it" },
    val locked: String = "Gravação travada",
    val send: String = "Enviar áudio",
    val cancel: String = "Cancelar gravação",
    val tooShort: String = "Áudio muito curto",
    val permission: String = "Permita o microfone para gravar áudio",
    val openSettings: String = "Abrir configurações",
    val busy: String = "O microfone está em uso por outro app",
    val failed: String = "Não foi possível gravar o áudio",
    val play: String = "Tocar áudio",
    val pause: String = "Pausar áudio",
    val description: (duration: String) -> String = { "Áudio de $it" },
    val loading: String = "Carregando áudio",
    val playError: String = "Não foi possível tocar o áudio",
    val retry: String = "Tentar de novo",
) {
    /** A frase para o erro do gravador (para o snackbar/aviso da tela). */
    fun errorMessage(error: VoiceNoteError): String = when (error) {
        VoiceNoteError.TOO_SHORT -> tooShort
        VoiceNoteError.PERMISSION_DENIED -> permission
        VoiceNoteError.MICROPHONE_BUSY -> busy
        VoiceNoteError.FAILED -> failed
    }
}

/** Os textos da nota de voz no idioma da tela (pt-BR, en, es, pt-PT). */
@Composable
fun rememberVoiceNoteTexts(): VoiceNoteTexts {
    val gravando = kmpStringResource(Res.string.kmplib_voice_note_recording, "\u0001")
    val descricao = kmpStringResource(Res.string.kmplib_voice_note_description, "\u0001")
    return VoiceNoteTexts(
        record = kmpStringResource(Res.string.kmplib_voice_note_record),
        slideToCancel = kmpStringResource(Res.string.kmplib_voice_note_slide_to_cancel),
        recording = { gravando.replace("\u0001", it) },
        locked = kmpStringResource(Res.string.kmplib_voice_note_locked),
        send = kmpStringResource(Res.string.kmplib_voice_note_send),
        cancel = kmpStringResource(Res.string.kmplib_voice_note_cancel),
        tooShort = kmpStringResource(Res.string.kmplib_voice_note_too_short),
        permission = kmpStringResource(Res.string.kmplib_voice_note_permission),
        openSettings = kmpStringResource(Res.string.kmplib_voice_note_open_settings),
        busy = kmpStringResource(Res.string.kmplib_voice_note_busy),
        failed = kmpStringResource(Res.string.kmplib_voice_note_failed),
        play = kmpStringResource(Res.string.kmplib_voice_note_play),
        pause = kmpStringResource(Res.string.kmplib_voice_note_pause),
        description = { descricao.replace("\u0001", it) },
        loading = kmpStringResource(Res.string.kmplib_voice_note_loading),
        playError = kmpStringResource(Res.string.kmplib_voice_note_play_error),
        retry = kmpStringResource(Res.string.kmplib_voice_note_retry),
    )
}

/** O que pode dar errado ao gravar, do ponto de vista da tela. */
enum class VoiceNoteError {
    /** Soltou cedo demais (abaixo do mínimo) — nada foi enviado. */
    TOO_SHORT,

    /** Sem permissão de microfone (a lib já pediu; aqui a saída é abrir as configurações). */
    PERMISSION_DENIED,

    /** Microfone com outro app. */
    MICROPHONE_BUSY,

    /** Falhou ao começar ou no meio. */
    FAILED,
}

internal fun AudioRecorderError.toVoiceNoteError(): VoiceNoteError = when (this) {
    AudioRecorderError.PERMISSION_DENIED -> VoiceNoteError.PERMISSION_DENIED
    AudioRecorderError.MICROPHONE_BUSY -> VoiceNoteError.MICROPHONE_BUSY
    AudioRecorderError.START_FAILED, AudioRecorderError.WRITE_FAILED, AudioRecorderError.INTERRUPTED -> VoiceNoteError.FAILED
}

/** Ids de teste (Maestro) da nota de voz. */
object VoiceNoteTestTags {
    const val MIC = "voz-btn-microfone"
    const val SEND = "voz-btn-enviar"
    const val CANCEL = "voz-btn-cancelar"
    const val RECORDING = "voz-gravando"
    const val PLAYER = "voz-player"
    const val PLAY = "voz-btn-tocar"
}
