package br.com.codecacto.kmplib.media

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.log10

/**
 * Configuração do [AudioRecorder]. Os defaults são os da nota de voz da fábrica: **AAC-LC em MP4
 * (`.m4a`), 64 kbps, mono, 44,1 kHz** — o formato que o servidor aceita sem transcodificar (o
 * `POST /v1/media/audio` do App do Personal guarda AAC/m4a 64 kbps mono) e que toca nativo nas duas
 * plataformas. ≈ 480 KB por minuto.
 *
 * @param maxDurationMillis teto da gravação; ao atingir, **a plataforma para sozinha**
 *   (`MediaRecorder.setMaxDuration` / `AVAudioRecorder.recordForDuration`) e o resultado chega como
 *   [AudioRecordingResult.Recorded] com [RecordedAudio.reachedLimit] = `true`.
 * @param minDurationMillis abaixo disso o [AudioRecorder.stop] devolve [AudioRecordingResult.TooShort]
 *   e apaga o arquivo (toque acidental no microfone não vira mensagem).
 * @param levelIntervalMillis de quanto em quanto tempo o nível é lido (para a onda).
 */
data class AudioRecorderConfig(
    val maxDurationMillis: Long = 180_000L,
    val minDurationMillis: Long = 1_000L,
    val sampleRateHz: Int = 44_100,
    val bitRate: Int = 64_000,
    val channels: Int = 1,
    val levelIntervalMillis: Long = 50L,
) {
    init {
        require(maxDurationMillis in 1_000L..3_600_000L) { "maxDurationMillis fora de 1 s..1 h" }
        require(minDurationMillis in 0L..maxDurationMillis) { "minDurationMillis fora de 0..maxDurationMillis" }
        require(channels == 1 || channels == 2) { "channels: 1 ou 2" }
        require(levelIntervalMillis in 16L..1_000L) { "levelIntervalMillis fora de 16..1000" }
    }
}

/** Estado do [AudioRecorder]. */
sealed interface AudioRecorderState {
    data object Idle : AudioRecorderState

    /**
     * Gravando. [level] é o nível da última leitura (0..1, escala perceptual — ver
     * [audioLevelFromDecibels]); [levels] é a onda acumulada (uma amostra por [AudioRecorderConfig.levelIntervalMillis]).
     */
    data class Recording(val elapsedMillis: Long, val level: Float, val levels: List<Float>) : AudioRecorderState {
        override fun toString(): String = "Recording(elapsedMillis=$elapsedMillis, levels=${levels.size})"
    }

    /**
     * O teto ([AudioRecorderConfig.maxDurationMillis]) parou a gravação **sozinho**. O arquivo está
     * pronto: chame [AudioRecorder.stop] para recebê-lo (com [RecordedAudio.reachedLimit]).
     */
    data class LimitReached(val elapsedMillis: Long, val levels: List<Float>) : AudioRecorderState {
        override fun toString(): String = "LimitReached(elapsedMillis=$elapsedMillis)"
    }

    /** Falhou ao começar ou no meio (a gravação parcial é apagada). */
    data class Failed(val error: AudioRecorderError) : AudioRecorderState
}

/** Por que a gravação não saiu — cada caso com uma saída diferente na tela. */
enum class AudioRecorderError {
    /** Sem permissão de microfone. Saída: pedir/abrir configurações. */
    PERMISSION_DENIED,

    /** O microfone está com outro app (ligação, outro gravador). Saída: tentar depois. */
    MICROPHONE_BUSY,

    /** O sistema recusou começar (sem `initKmpLibMedia`, codec indisponível). */
    START_FAILED,

    /** Não coube/gravou no disco. */
    WRITE_FAILED,

    /** Uma ligação ou a Siri interrompeu — o que foi gravado é descartado. */
    INTERRUPTED,
}

/**
 * Uma nota de voz gravada — arquivo **temporário** em [AUDIO_CAPTURE_TEMP_DIRECTORY][br.com.codecacto.kmplib.platform.AUDIO_CAPTURE_TEMP_DIRECTORY]
 * (varrido por `clearKmpLibTemporaryFiles`). Envie e apague com [deleteFile]; fila durável que precise
 * dele mais tempo deve movê-lo para a área dela.
 *
 * @param levels a onda da gravação (0..1), para desenhar o balão sem decodificar o arquivo.
 * @param reachedLimit `true` se parou sozinha no teto.
 */
class RecordedAudio(
    val path: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val levels: List<Float>,
    val reachedLimit: Boolean,
) {
    /** `audio/mp4` (AAC em contêiner MP4/m4a). */
    val mimeType: String get() = AUDIO_M4A_MIME_TYPE

    /** Duração em segundos inteiros, arredondada para cima — o `durationSeconds` dos contratos. */
    val durationSeconds: Int get() = ((durationMillis + 999) / 1000).toInt()

    /** Os bytes do arquivo (≤ 1,5 MB num teto de 3 min) — para o multipart. `null` se sumiu. */
    fun readBytes(): ByteArray? = readLocalAudioFile(path)

    /** Apaga o arquivo. `true` se apagou ou já não existia. */
    fun deleteFile(): Boolean = deleteLocalAudioFile(path)

    /** Sem caminho: é a voz da pessoa. */
    override fun toString(): String =
        "RecordedAudio(durationMillis=$durationMillis, sizeBytes=$sizeBytes, levels=${levels.size}, reachedLimit=$reachedLimit)"
}

/** Desfecho do [AudioRecorder.stop]. */
sealed interface AudioRecordingResult {
    data class Recorded(val audio: RecordedAudio) : AudioRecordingResult

    /** Mais curta que [AudioRecorderConfig.minDurationMillis] — descartada. */
    data object TooShort : AudioRecordingResult

    /** Não estava gravando. */
    data object NotRecording : AudioRecordingResult

    data class Failed(val error: AudioRecorderError) : AudioRecordingResult
}

/**
 * **Gravador de áudio para arquivo** (2.287.0 — GAP-PT-M06): nota de voz curta em AAC/m4a, com nível
 * para a onda, teto que para sozinho e cancelar que apaga.
 *
 * - **Android:** `MediaRecorder` (`MPEG_4` + `AAC`, `setMaxDuration`), nível por `getMaxAmplitude()`
 *   — serve para **desenhar a onda**; para medir som (dB SPL) o caminho é o `AudioCapture` do
 *   `kmplib-platform`, não este.
 * - **iOS:** `AVAudioRecorder` (`kAudioFormatMPEG4AAC`, `recordForDuration`, `meteringEnabled`) com a
 *   sessão `playAndRecord`; ligação/Siri que interrompe descarta a gravação ([AudioRecorderError.INTERRUPTED]).
 *
 * A **permissão** de microfone é do app (`AppPermission.MICROPHONE`) — o [VoiceNoteRecorderButton][br.com.codecacto.kmplib.media.voicenote.VoiceNoteRecorderButton]
 * já a pede. Uma instância grava uma coisa por vez; chame [release] ao sair da tela.
 */
interface AudioRecorder {
    val state: StateFlow<AudioRecorderState>

    /** Começa a gravar. `false` se não começou (o motivo fica em [state] como [AudioRecorderState.Failed]). */
    suspend fun start(): Boolean

    /**
     * Para e entrega o arquivo. Se o teto já tinha parado a gravação, devolve o que foi gravado
     * (com [RecordedAudio.reachedLimit]). Abaixo do mínimo: [AudioRecordingResult.TooShort].
     */
    suspend fun stop(): AudioRecordingResult

    /** Para e **apaga** o que estava sendo gravado. */
    fun cancel()

    /** Libera o microfone e a sessão de áudio (cancela se estiver gravando). */
    fun release()
}

/** O gravador da plataforma (ver [AudioRecorder]). */
expect fun createAudioRecorder(config: AudioRecorderConfig = AudioRecorderConfig()): AudioRecorder

/** MIME da nota de voz: AAC em contêiner MP4. */
const val AUDIO_M4A_MIME_TYPE: String = "audio/mp4"

internal expect fun readLocalAudioFile(path: String): ByteArray?

internal expect fun deleteLocalAudioFile(path: String): Boolean

/** Piso da escala de nível: abaixo de −50 dBFS a onda fica no mínimo (ruído de fundo). */
const val AUDIO_LEVEL_FLOOR_DB: Float = -50f

/**
 * dBFS (0 = fundo de escala, negativo abaixo) → nível 0..1 **para desenhar**. Linear em dB entre
 * [floorDb] e 0: o ouvido percebe volume em escala logarítmica, então uma onda linear em amplitude
 * ficaria quase achatada na fala normal.
 */
fun audioLevelFromDecibels(db: Float, floorDb: Float = AUDIO_LEVEL_FLOOR_DB): Float {
    if (db.isNaN()) return 0f
    if (db >= 0f) return 1f
    if (db <= floorDb) return 0f
    return ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
}

/** Amplitude de pico 16 bits (0..32767, o `getMaxAmplitude` do Android) → nível 0..1. */
fun audioLevelFromAmplitude(maxAmplitude: Int): Float {
    if (maxAmplitude <= 0) return 0f
    val db = 20f * log10(maxAmplitude.coerceAtMost(32_767) / 32_767f)
    return audioLevelFromDecibels(db)
}

/**
 * Reduz (ou estica) a onda para [bars] barras: cada barra é o **pico** do seu trecho (a média
 * achataria as sílabas). Lista vazia ou [bars] ≤ 0 → vazia.
 */
fun resampleWaveform(levels: List<Float>, bars: Int): List<Float> {
    if (levels.isEmpty() || bars <= 0) return emptyList()
    if (levels.size == bars) return levels.map { it.coerceIn(0f, 1f) }
    return List(bars) { i ->
        val inicio = (i.toLong() * levels.size / bars).toInt()
        val fim = maxOf(inicio + 1, ((i + 1).toLong() * levels.size / bars).toInt()).coerceAtMost(levels.size)
        var pico = 0f
        for (j in inicio until fim) if (levels[j] > pico) pico = levels[j]
        pico.coerceIn(0f, 1f)
    }
}

/** `0:07`, `1:05`, `12:00` — duração de nota de voz (minutos sem zero à esquerda). */
fun formatVoiceNoteDuration(millis: Long): String {
    val total = (millis.coerceAtLeast(0L) / 1000L)
    val m = total / 60
    val s = total % 60
    return "$m:" + s.toString().padStart(2, '0')
}
