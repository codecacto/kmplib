package br.com.codecacto.kmplib.platform.haptics

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import br.com.codecacto.kmplib.core.util.AppLogger
import java.lang.ref.WeakReference

private const val TAG = "Haptics"

/** Guarda o `Context` da aplicação para o [Haptics]. Preenchido por `initKmpLibPlatform`. */
internal object HapticsHolder {
    private var ref: WeakReference<Context>? = null

    fun init(context: Context) {
        ref = WeakReference(context.applicationContext)
    }

    fun context(): Context? = ref?.get()
}

internal actual fun createPlatformHapticPlayer(): HapticPlayer = AndroidHapticPlayer

/**
 * **Padrão-ouro do Android:** `VibratorManager.defaultVibrator` (31+) ou `Vibrator`, efeito por
 * `VibrationEffect.createWaveform` e **atributos de uso** — sem eles o sistema não sabe aplicar a
 * intensidade que a pessoa escolheu para notificação/toque/alarme (Android 13+).
 */
internal object AndroidHapticPlayer : HapticPlayer {

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override val isSupported: Boolean
        get() = HapticsHolder.context()?.let { vibrator(it)?.hasVibrator() } == true

    override fun vibrate(pattern: VibrationPattern, usage: HapticUsage): HapticOutcome {
        val context = HapticsHolder.context() ?: run {
            AppLogger.e(TAG, "initKmpLibPlatform(context) não foi chamado — vibração indisponível")
            return HapticOutcome.UNSUPPORTED
        }
        val vib = vibrator(context)
        if (vib == null || !vib.hasVibrator()) return HapticOutcome.UNSUPPORTED
        if (!systemAllowsVibration(usage, ringerMode(context), touchHapticsEnabled(context))) {
            return HapticOutcome.SUPPRESSED_BY_SYSTEM
        }
        return try {
            vib.cancel()
            play(vib, pattern, usage)
            HapticOutcome.PLAYED
        } catch (e: Exception) {
            // SecurityException (manifesto sem VIBRATE após merge com tools:node="remove") ou
            // IllegalArgumentException do fabricante. Sem o padrão no log: não é dado útil.
            AppLogger.e(TAG, "o sistema recusou a vibração", e)
            HapticOutcome.FAILED
        }
    }

    override fun cancel() {
        HapticsHolder.context()?.let { vibrator(it) }?.cancel()
    }

    private fun play(vib: Vibrator, pattern: VibrationPattern, usage: HapticUsage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern.toOnOffTimings(), -1, audioAttributes(usage))
            return
        }
        val effect = if (vib.hasAmplitudeControl()) {
            val w = pattern.toAmplitudeWaveform()
            VibrationEffect.createWaveform(w.timings, w.amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(pattern.toOnOffTimings(), -1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vib.vibrate(effect, VibrationAttributes.createForUsage(vibrationUsage(usage)))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(effect, audioAttributes(usage))
        }
    }

    private fun vibrationUsage(usage: HapticUsage): Int = when (usage) {
        HapticUsage.TOUCH -> VibrationAttributes.USAGE_TOUCH
        HapticUsage.NOTIFICATION -> VibrationAttributes.USAGE_NOTIFICATION
        HapticUsage.ALARM -> VibrationAttributes.USAGE_ALARM
    }

    private fun audioAttributes(usage: HapticUsage): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(
                when (usage) {
                    HapticUsage.TOUCH -> AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
                    HapticUsage.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
                    HapticUsage.ALARM -> AudioAttributes.USAGE_ALARM
                },
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun ringerMode(context: Context): RingerMode {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return RingerMode.NORMAL
        return when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else -> RingerMode.NORMAL
        }
    }

    /** Na 33+ a chave foi aposentada: o `USAGE_TOUCH` já faz o sistema aplicar a configuração. */
    private fun touchHapticsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return true
        return runCatching {
            @Suppress("DEPRECATION")
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        }.getOrDefault(true)
    }
}
