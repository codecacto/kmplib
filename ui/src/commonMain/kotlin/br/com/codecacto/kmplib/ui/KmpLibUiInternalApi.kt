package br.com.codecacto.kmplib.ui

/**
 * Marca o que é **público só para outro módulo da kmplib** (2.278.0) — hoje, a codificação de foto
 * do seletor (`encodePickedImage` no Android, `NSData.toPickedImage`/`UIImage.toPickedImage` no
 * iOS), que a câmera guiada do `kmplib-camera` reusa para entregar o MESMO [br.com.codecacto.kmplib.ui.components.PickedImage]
 * (JPEG em pé, sem EXIF, com teto de medida).
 *
 * `internal` do Kotlin não atravessa módulo publicado; a saída oficial é esta (a mesma de
 * `@InternalCoroutinesApi`). Usar num app exige `@OptIn(KmpLibUiInternalApi::class)` escrito à
 * mão, e não há compromisso de compatibilidade: pode mudar em qualquer versão.
 */
@RequiresOptIn(
    message = "API interna da kmplib (compartilhada entre os módulos da lib). " +
        "Não é contrato para app: pode mudar sem aviso.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class KmpLibUiInternalApi
