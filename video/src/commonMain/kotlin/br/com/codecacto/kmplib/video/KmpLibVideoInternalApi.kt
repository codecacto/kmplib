package br.com.codecacto.kmplib.video

/**
 * Marca o que é **público só para outro módulo da kmplib** — hoje, o `kmplib-video-download`, que
 * constrói o `DownloadManager` sobre o cache do player (`Media3Cache`) e precisa do `Context`
 * registrado por `initKmpLibVideo`.
 *
 * `internal` do Kotlin não atravessa módulo publicado; a alternativa oficial é esta — a mesma de
 * `@InternalCoroutinesApi` e `@InternalComposeApi`. Usar isto num app exige
 * `@OptIn(KmpLibVideoInternalApi::class)` escrito à mão, e o nome já diz que não há compromisso de
 * compatibilidade: pode mudar em qualquer versão.
 */
@RequiresOptIn(
    message = "API interna da kmplib (usada entre kmplib-video e kmplib-video-download). " +
        "Não é contrato para app: pode mudar sem aviso.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class KmpLibVideoInternalApi
