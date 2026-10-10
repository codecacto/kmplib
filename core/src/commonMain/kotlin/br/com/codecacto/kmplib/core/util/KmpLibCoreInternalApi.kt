package br.com.codecacto.kmplib.core.util

/**
 * Marca o que é **público só para outro módulo da kmplib** (2.277.1) — hoje, os helpers de
 * [redactMediaUrl]/[redactMediaUrlsIn], usados por `kmplib-video`, `kmplib-video-download` e
 * `kmplib-ui`.
 *
 * `internal` do Kotlin não atravessa módulo publicado; a saída oficial é esta (a mesma de
 * `@InternalCoroutinesApi`). Usar num app exige `@OptIn(KmpLibCoreInternalApi::class)` escrito à
 * mão, e não há compromisso de compatibilidade: pode mudar em qualquer versão.
 */
@RequiresOptIn(
    message = "API interna da kmplib (compartilhada entre os módulos da lib). " +
        "Não é contrato para app: pode mudar sem aviso.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class KmpLibCoreInternalApi
