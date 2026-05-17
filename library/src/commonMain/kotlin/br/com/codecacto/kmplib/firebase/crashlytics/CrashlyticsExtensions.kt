package br.com.codecacto.kmplib.firebase.crashlytics

import kotlin.coroutines.cancellation.CancellationException

/**
 * Versão de [runCatching] que também reporta a exceção ao Crashlytics.
 *
 * - `CancellationException` é **re-lançada** (cancelamento de coroutine não é erro).
 * - Demais exceções: setam [customKeys] (se houver) e chamam `recordException`.
 *
 * Uso:
 * ```
 * val crashlytics = getCrashlyticsService()
 * val result = crashlytics.runCatchingAndReport(
 *     customKeys = mapOf("user_action" to "save_profile")
 * ) {
 *     repository.saveProfile(profile)
 * }
 * result.onSuccess { ... }.onFailure { ... }
 * ```
 *
 * Para versão suspend, use [runCatchingAndReportSuspend].
 */
inline fun <T> CrashlyticsService.runCatchingAndReport(
    customKeys: Map<String, String> = emptyMap(),
    block: () -> T
): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        customKeys.forEach { (k, v) -> setCustomKey(k, v) }
        recordException(e)
        Result.failure(e)
    }
}

/**
 * Versão suspend de [runCatchingAndReport]. Mesma semântica.
 */
suspend inline fun <T> CrashlyticsService.runCatchingAndReportSuspend(
    customKeys: Map<String, String> = emptyMap(),
    block: suspend () -> T
): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        customKeys.forEach { (k, v) -> setCustomKey(k, v) }
        recordException(e)
        Result.failure(e)
    }
}

/**
 * Reporta uma exceção e propaga (em vez de capturar). Útil em catches existentes.
 *
 * ```
 * try { ... } catch (e: IOException) {
 *     getCrashlyticsService().reportAndRethrow(e, "stage" to "upload")
 * }
 * ```
 */
fun CrashlyticsService.reportAndRethrow(
    exception: Throwable,
    vararg customKeys: Pair<String, String>
): Nothing {
    if (exception is CancellationException) throw exception
    customKeys.forEach { (k, v) -> setCustomKey(k, v) }
    recordException(exception)
    throw exception
}

/**
 * Reporta uma exceção sem propagar. Útil em fluxos onde o erro foi tratado
 * mas você quer registrar no Crashlytics mesmo assim.
 */
fun CrashlyticsService.reportSilently(
    exception: Throwable,
    vararg customKeys: Pair<String, String>
) {
    if (exception is CancellationException) return
    customKeys.forEach { (k, v) -> setCustomKey(k, v) }
    recordException(exception)
}
