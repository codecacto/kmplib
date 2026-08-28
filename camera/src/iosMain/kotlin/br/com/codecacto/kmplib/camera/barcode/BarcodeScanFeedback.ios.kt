package br.com.codecacto.kmplib.camera.barcode

import platform.AudioToolbox.AudioServicesPlaySystemSound

/**
 * Bipe de confirmação no iOS — som do sistema via `AudioServicesPlaySystemSound`.
 *
 * A Apple não expõe um gerador de tom; o caminho oficial para um retorno sonoro curto é um
 * *system sound*. `1057` ("Tink") é curto e não se confunde com notificação.
 *
 * Respeita o interruptor de silencioso do aparelho — que é o comportamento correto: quem colocou o
 * celular no mudo não quer o bipe.
 */
private const val SCAN_BEEP_SOUND_ID: UInt = 1057u

internal actual fun playBarcodeScanBeep() {
    AudioServicesPlaySystemSound(SCAN_BEEP_SOUND_ID)
}
