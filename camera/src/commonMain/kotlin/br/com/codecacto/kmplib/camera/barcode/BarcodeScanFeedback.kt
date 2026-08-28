package br.com.codecacto.kmplib.camera.barcode

/**
 * Como o app **confirma ao operador** que um código foi lido.
 *
 * Padrão da casa: **vibração ligada, som desligado**.
 * - **Vibração ligada** porque é o mesmo princípio já adotado no
 *   [br.com.codecacto.kmplib.ui.components.ChecklistItem] e no
 *   [br.com.codecacto.kmplib.ui.components.CommunicationTile]: confirma o acerto sem exigir que a
 *   pessoa olhe a tela — e quem escaneia está com uma mão só, de pé na gôndola.
 * - **Som desligado** porque o beep é do *ambiente*, não do app: uma loja silenciosa, um depósito
 *   de madrugada ou o celular no bolso do cliente não pediram por ele. Quem quer o "bip de caixa
 *   de supermercado" liga explicitamente.
 *
 * @property haptic vibração curta a cada leitura aceita.
 * @property sound bipe curto do sistema a cada leitura aceita.
 */
data class BarcodeScanFeedback(
    val haptic: Boolean = true,
    val sound: Boolean = false,
) {
    companion object {
        /** Nenhum retorno físico (ex.: leitura em segundo plano, quiosque com som próprio). */
        val NONE = BarcodeScanFeedback(haptic = false, sound = false)

        /** Vibração **e** bipe — o "caixa de supermercado", quando o app quiser. */
        val FULL = BarcodeScanFeedback(haptic = true, sound = true)
    }
}

/**
 * Bipe curto do sistema, tocado quando [BarcodeScanFeedback.sound] está ligado.
 *
 * Android: `ToneGenerator` no stream de mídia. iOS: `AudioServicesPlaySystemSound`.
 * **Best-effort:** nunca lança — falha de áudio não pode derrubar a leitura.
 */
internal expect fun playBarcodeScanBeep()
