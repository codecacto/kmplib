package br.com.codecacto.kmplib.torch

/**
 * **A luz que está acesa agora** — o estado observável que a UI espelha.
 *
 * [isOn] reflete o **hardware**, não a última intenção do app: quando o SO apaga a lanterna sozinho
 * (outro app pegou a câmera, superaquecimento, Central de Controle do iOS), o callback da
 * plataforma atualiza este estado e o botão da tela volta sozinho para "apagado". Botão preso em
 * "aceso" com o LED apagado é o defeito clássico dos apps de lanterna.
 */
data class TorchState(
    /** `true` quando o LED está fisicamente aceso. */
    val isOn: Boolean = false,
    /**
     * Intensidade desejada/aplicada, de `0f` a `1f`. Sem suporte de intensidade, é sempre
     * [TorchLevel.MAX]. Alterar com a luz apagada guarda o valor para o próximo acender.
     */
    val level: Float = TorchLevel.MAX,
    /** O que este aparelho suporta. Ver [TorchCapabilities]. */
    val capabilities: TorchCapabilities = TorchCapabilities.NONE,
    /** Último erro, `null` depois de um comando bem-sucedido. */
    val error: TorchError? = null,
) {
    /** `true` se dá para mostrar o slider de intensidade nesta tela. */
    val canAdjustIntensity: Boolean get() = capabilities.supportsIntensity
}
