package br.com.codecacto.kmplib.platform.audio

/**
 * Desktop (2.353.0): captura de microfone com medição (AGC, ponderação, calibração) não tem o motor
 * de medição validado no PC — captura inerte com [AudioCaptureError.DeviceUnavailable], como num
 * aparelho sem microfone. Lacuna registrada no catálogo.
 */
actual fun createAudioCapture(config: AudioCaptureConfig): AudioCapture =
    UnavailableAudioCapture(AudioCaptureError.DeviceUnavailable)
