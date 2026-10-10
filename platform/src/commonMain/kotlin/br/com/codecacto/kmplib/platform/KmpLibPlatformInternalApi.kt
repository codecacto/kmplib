package br.com.codecacto.kmplib.platform

/**
 * Marca o que é **público só para outro módulo da kmplib** (2.280.0) — hoje, o registro de
 * temporários do Android ([KmpLibTempFiles]), usado pelo visualizador de PDF do `kmplib-pdf`.
 *
 * `internal` do Kotlin não atravessa módulo publicado; a saída oficial é esta (a mesma de
 * `@InternalCoroutinesApi`). Usar num app exige `@OptIn(KmpLibPlatformInternalApi::class)` escrito à
 * mão, e não há compromisso de compatibilidade.
 */
@RequiresOptIn(
    message = "API interna da kmplib (compartilhada entre os módulos da lib). " +
        "Não é contrato para app: pode mudar sem aviso.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class KmpLibPlatformInternalApi
