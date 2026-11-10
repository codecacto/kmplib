package br.com.codecacto.kmplib.platform.haptics

/** Desktop: computador não vibra. */
internal actual fun createPlatformHapticPlayer(): HapticPlayer = object : HapticPlayer {
    override val isSupported: Boolean = false
    override fun vibrate(pattern: VibrationPattern, usage: HapticUsage): HapticOutcome = HapticOutcome.UNSUPPORTED
    override fun cancel() = Unit
}
