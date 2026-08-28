package br.com.codecacto.kmplib.platform

/**
 * Capacidade de plataforma **nomeada** — a forma tipada de perguntar ao [PlatformCapabilities]
 * "isto existe aqui?" e, sobretudo, de **atrelar uma feature vendida a uma capacidade real**.
 *
 * Existe para o caso ChamadaFacil: o app anunciava "Exportar PDF" como destaque do plano **Pro**,
 * mas no iOS o gerador é stub (`OsPdfNotSupportedException`) — vendia o que não tem. Com
 * [CapabilityFeature] o destaque some da lista de venda no alvo em que a feature não existe, sem
 * o app precisar lembrar de escrever um `if (isAndroid)`.
 */
enum class PlatformCapability {

    /** `camera/CameraView` com preview + OCR + captura de JPEG. iOS: placeholder estático. */
    CameraCapture,

    /** Os 9 geradores de PDF de `pdf/`. iOS: lançam exceção (`renderPdfPagesToImages` não é afetado). */
    PdfGeneration;

    /** `true` se a plataforma corrente entrega a capacidade de verdade (constante de build). */
    val isAvailable: Boolean
        get() = when (this) {
            CameraCapture -> PlatformCapabilities.cameraCapture
            PdfGeneration -> PlatformCapabilities.pdfGeneration
        }
}

/**
 * Um item (destaque de paywall, entrada de menu, item de onboarding) **condicionado** a uma
 * capacidade de plataforma. [requires] `null` = sempre disponível.
 */
data class CapabilityFeature<T>(
    val value: T,
    val requires: PlatformCapability? = null,
)

/** Item sempre disponível (não depende de capacidade de plataforma). */
fun <T> T.alwaysAvailable(): CapabilityFeature<T> = CapabilityFeature(this, null)

/** Item disponível apenas onde [capability] existe: `"Exportar PDF" requiring PdfGeneration`. */
infix fun <T> T.requiring(capability: PlatformCapability): CapabilityFeature<T> =
    CapabilityFeature(this, capability)

/**
 * Filtra a lista mantendo só o que a plataforma corrente **de fato entrega**.
 *
 * Use para montar `PaywallPlan.highlights`, itens de menu, banners de feature — assim o app
 * **não consegue vender o que não tem**:
 *
 * ```kotlin
 * val highlights = listOf(
 *     "Chamada ilimitada".alwaysAvailable(),
 *     "Exportar PDF" requiring PlatformCapability.PdfGeneration,   // some no iOS
 *     "Foto do veículo" requiring PlatformCapability.CameraCapture // some no iOS
 * ).availableValues()
 *
 * PaywallPlan(id = ..., highlights = highlights, ...)
 * ```
 *
 * @param isAvailable resolvedor da capacidade (default: a plataforma corrente). Parametrizado para
 *   permitir teste determinístico dos dois alvos a partir de qualquer host.
 */
fun <T> List<CapabilityFeature<T>>.availableValues(
    isAvailable: (PlatformCapability) -> Boolean = { it.isAvailable },
): List<T> = mapNotNull { feature ->
    val capability = feature.requires
    if (capability == null || isAvailable(capability)) feature.value else null
}
