package br.com.codecacto.kmplib.platform.haptics

import androidx.compose.runtime.Composable
import kotlin.math.roundToInt

/**
 * Um trecho de um [VibrationPattern]: vibrar por um tempo, com uma intensidade — ou ficar parado.
 */
sealed interface HapticSegment {
    /** Duração do trecho, em milissegundos (≥ 0). */
    val durationMillis: Long

    /**
     * Vibra por [durationMillis] com [intensity] de `0f` (exclusivo) a `1f`.
     *
     * No Android sem controle de amplitude, e no fallback do iOS sem Core Haptics, a intensidade é
     * aproximada (liga/desliga no Android; estilo de impacto no iOS).
     */
    data class Vibrate(override val durationMillis: Long, val intensity: Float = 1f) : HapticSegment

    /** Parado por [durationMillis]. */
    data class Pause(override val durationMillis: Long) : HapticSegment
}

/**
 * **Padrão de vibração multiplataforma** (2.273.0) — a sequência de trechos que o [Haptics] toca.
 *
 * Diferente do `LocalHapticFeedback` do Compose (um toque curto de UI, sem duração nem padrão), este
 * é o canal para **avisar**: fim do descanso, cronômetro zerado, volta concluída. É o que chega à
 * pessoa quando o som não chega — o `SoundEffectPlayer` não toca no Silencioso.
 *
 * ```kotlin
 * Haptics.vibrate(VibrationPattern.Alert)                                    // três pulsos
 * Haptics.vibrate(VibrationPattern.pulses(count = 2, onMillis = 120))
 * Haptics.vibrate(VibrationPattern.waveform(0, 400, 200, 400))               // estilo Android
 * ```
 *
 * @throws IllegalArgumentException padrão sem nenhum trecho de vibração com duração, duração
 *   negativa, intensidade fora de `(0f, 1f]` ou total acima de [MAX_TOTAL_MILLIS].
 */
data class VibrationPattern(val segments: List<HapticSegment>) {

    init {
        require(segments.all { it.durationMillis >= 0 }) { "duração negativa no padrão de vibração" }
        require(
            segments.all { it !is HapticSegment.Vibrate || (it.intensity > 0f && it.intensity <= 1f) },
        ) { "intensidade fora de (0, 1]" }
        require(segments.any { it is HapticSegment.Vibrate && it.durationMillis > 0 }) {
            "o padrão precisa de ao menos um trecho de vibração com duração"
        }
        require(totalDurationMillis <= MAX_TOTAL_MILLIS) {
            "padrão de vibração acima de ${MAX_TOTAL_MILLIS / 1000} s"
        }
    }

    /** Soma das durações de todos os trechos. */
    val totalDurationMillis: Long get() = segments.sumOf { it.durationMillis }

    companion object {
        /**
         * Teto do padrão: 30 s. É o limite de um evento contínuo do Core Haptics, e um aviso por
         * vibração mais longo que isso deixa de ser aviso.
         */
        const val MAX_TOTAL_MILLIS: Long = 30_000

        /** Monta a partir dos trechos, na ordem. */
        fun of(vararg segments: HapticSegment): VibrationPattern = VibrationPattern(segments.toList())

        /**
         * Formato do `VibrationEffect.createWaveform(timings, -1)` do Android: os tempos alternam
         * **parado, vibrando, parado, vibrando…**, começando por parado (use `0` para vibrar já).
         */
        fun waveform(vararg timingsMillis: Long, intensity: Float = 1f): VibrationPattern =
            VibrationPattern(
                timingsMillis.mapIndexed { i, t ->
                    if (i % 2 == 0) HapticSegment.Pause(t) else HapticSegment.Vibrate(t, intensity)
                },
            )

        /** [count] pulsos de [onMillis] separados por [gapMillis]. */
        fun pulses(
            count: Int,
            onMillis: Long = 200,
            gapMillis: Long = 150,
            intensity: Float = 1f,
        ): VibrationPattern {
            require(count >= 1) { "count precisa ser ≥ 1" }
            return VibrationPattern(
                buildList {
                    repeat(count) { i ->
                        if (i > 0) add(HapticSegment.Pause(gapMillis))
                        add(HapticSegment.Vibrate(onMillis, intensity))
                    }
                },
            )
        }

        /** Toque curto e leve — confirmação de ação. */
        val Tick: VibrationPattern = of(HapticSegment.Vibrate(30, 0.6f))

        /** Dois pulsos curtos — "deu certo". */
        val Confirm: VibrationPattern = pulses(count = 2, onMillis = 70, gapMillis = 90, intensity = 0.8f)

        /** Três pulsos firmes — o aviso que tem de ser notado (fim do descanso, tempo esgotado). */
        val Alert: VibrationPattern = pulses(count = 3, onMillis = 300, gapMillis = 200)
    }
}

/**
 * Para que serve a vibração — é o que o sistema usa para aplicar a configuração da pessoa.
 *
 * | Uso | Android | iOS |
 * |---|---|---|
 * | [TOUCH] | `USAGE_TOUCH`; não toca com "vibração ao tocar" desligada | segue "Sons e Tátil → Tátil do Sistema" |
 * | [NOTIFICATION] | `USAGE_NOTIFICATION`; não toca no modo **Silencioso** | idem |
 * | [ALARM] | `USAGE_ALARM`; toca no Silencioso (alarme é o que a pessoa pediu) | idem — o iOS não distingue |
 *
 * No Android 13+ o sistema aplica, por cima, a intensidade que a pessoa escolheu para aquele uso
 * (incluindo desligado) — a lib não consegue ler isso, e devolve [HapticOutcome.PLAYED].
 */
enum class HapticUsage { TOUCH, NOTIFICATION, ALARM }

/** O que aconteceu com o pedido de vibração. */
enum class HapticOutcome {
    /** Entregue ao sistema. Não é garantia de que vibrou (ver [HapticUsage]). */
    PLAYED,

    /** O aparelho não vibra (tablet, simulador, iPad) ou a plataforma não foi inicializada. */
    UNSUPPORTED,

    /** A configuração do sistema não deixa (Silencioso, vibração ao tocar desligada). */
    SUPPRESSED_BY_SYSTEM,

    /** O sistema recusou o pedido (erro da engine). Já foi logado, sem dado da pessoa. */
    FAILED,
}

/**
 * Quem toca um [VibrationPattern]. O app recebe esta interface (para trocar por um dublê no teste) e,
 * em produção, usa o objeto [Haptics].
 */
interface HapticPlayer {
    /** O aparelho tem motor de vibração utilizável. */
    val isSupported: Boolean

    /**
     * Toca [pattern], interrompendo o padrão anterior que ainda estiver tocando. Não bloqueia.
     *
     * **Primeiro plano.** Com o app em segundo plano a vibração NÃO é garantida: o iOS suspende a
     * engine háptica do app, e o Android ignora vibração de app em segundo plano fora de alarme/
     * chamada. Aviso que precisa chegar com o app fechado vai pela **notificação** (que vibra pelo
     * canal do sistema) — este canal é o da tela na frente.
     */
    fun vibrate(pattern: VibrationPattern, usage: HapticUsage = HapticUsage.NOTIFICATION): HapticOutcome

    /** Para o que estiver tocando. Sem efeito se nada toca. */
    fun cancel()
}

/**
 * **Vibração com padrão, nas duas plataformas** (2.273.0, GAP-PT-M08).
 *
 * - **Android:** `VibratorManager.defaultVibrator` (API 31+) / `Vibrator`; `VibrationEffect.createWaveform`
 *   com amplitudes quando o motor tem controle de amplitude (API 26+), liga/desliga quando não tem, e
 *   o `vibrate(long[], -1)` antigo abaixo da API 26. Atributos de uso (`VibrationAttributes` na 33+,
 *   `AudioAttributes` antes) — é por eles que o sistema aplica a configuração da pessoa. A permissão
 *   `VIBRATE` vem no manifesto do `kmplib-platform`; o app não declara nada, mas precisa de
 *   `initKmpLibPlatform(context)`.
 * - **iOS:** **Core Haptics** (`CHHapticEngine` + evento contínuo por trecho, com intensidade) nos
 *   aparelhos que o suportam (iPhone 8+); sem ele, aproxima com `UIImpactFeedbackGenerator` (um
 *   impacto no início de cada trecho e a cada 100 ms dentro dele). iPad e simulador não vibram.
 *   O "Tátil do Sistema" desligado silencia os dois caminhos — o iOS não informa, e o retorno é
 *   [HapticOutcome.PLAYED].
 *
 * Chame da thread principal (no iOS, chamada de outra thread é repassada à principal).
 */
object Haptics : HapticPlayer {
    private val platform: HapticPlayer by lazy { createPlatformHapticPlayer() }

    override val isSupported: Boolean get() = platform.isSupported

    override fun vibrate(pattern: VibrationPattern, usage: HapticUsage): HapticOutcome =
        platform.vibrate(pattern, usage)

    override fun cancel() = platform.cancel()
}

/** O [Haptics] dentro de um composable — mesma instância, para ler como os outros `remember…`. */
@Composable
fun rememberHaptics(): HapticPlayer = Haptics

internal expect fun createPlatformHapticPlayer(): HapticPlayer

// ---- Regras puras (testadas em commonTest) -------------------------------------------------------

/** Modo de toque do Android (`AudioManager.RINGER_MODE_*`), sem depender do SDK. */
internal enum class RingerMode { NORMAL, VIBRATE, SILENT }

/** A configuração do sistema deixa vibrar para este uso? (Android) */
internal fun systemAllowsVibration(usage: HapticUsage, ringer: RingerMode, touchHapticsEnabled: Boolean): Boolean =
    when (usage) {
        HapticUsage.TOUCH -> touchHapticsEnabled
        HapticUsage.NOTIFICATION -> ringer != RingerMode.SILENT
        HapticUsage.ALARM -> true
    }

/** Par `timings`/`amplitudes` do `VibrationEffect.createWaveform(timings, amplitudes, -1)`. */
internal class AmplitudeWaveform(val timings: LongArray, val amplitudes: IntArray)

/** Trechos de duração zero saem; amplitude `1..255` (0 = parado). */
internal fun VibrationPattern.toAmplitudeWaveform(): AmplitudeWaveform {
    val vivos = segments.filter { it.durationMillis > 0 }
    return AmplitudeWaveform(
        timings = LongArray(vivos.size) { vivos[it].durationMillis },
        amplitudes = IntArray(vivos.size) {
            when (val s = vivos[it]) {
                is HapticSegment.Pause -> 0
                is HapticSegment.Vibrate -> (s.intensity * 255f).roundToInt().coerceIn(1, 255)
            }
        },
    )
}

/**
 * Tempos liga/desliga do `createWaveform(timings, -1)` (motor sem amplitude): alternam parado,
 * vibrando…, começando por parado. Trechos vizinhos do mesmo tipo se somam.
 */
internal fun VibrationPattern.toOnOffTimings(): LongArray {
    val out = mutableListOf(0L)
    var vibrando = false
    segments.filter { it.durationMillis > 0 }.forEach { s ->
        val eVibra = s is HapticSegment.Vibrate
        if (eVibra == vibrando) {
            out[out.lastIndex] += s.durationMillis
        } else {
            out += s.durationMillis
            vibrando = eVibra
        }
    }
    return out.toLongArray()
}

/** Um trecho de vibração posicionado no tempo — o evento contínuo do Core Haptics. */
internal data class TimedVibration(val startMillis: Long, val durationMillis: Long, val intensity: Float)

internal fun VibrationPattern.timedVibrations(): List<TimedVibration> {
    var t = 0L
    return buildList {
        segments.forEach { s ->
            if (s is HapticSegment.Vibrate && s.durationMillis > 0) {
                add(TimedVibration(t, s.durationMillis, s.intensity))
            }
            t += s.durationMillis
        }
    }
}

/** Intervalo entre impactos no fallback do iOS sem Core Haptics. */
internal const val IMPACT_REPEAT_MILLIS: Long = 100

/** Instantes (e intensidade) dos impactos que aproximam o padrão com `UIImpactFeedbackGenerator`. */
internal fun VibrationPattern.impactSchedule(): List<Pair<Long, Float>> =
    timedVibrations().flatMap { v ->
        generateSequence(v.startMillis) { it + IMPACT_REPEAT_MILLIS }
            .takeWhile { it < v.startMillis + v.durationMillis }
            .map { it to v.intensity }
            .toList()
    }
