package br.com.codecacto.kmplib.developer

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.setHTTPMethod
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual suspend fun httpGet(url: String): Result<String> {
    return suspendCancellableCoroutine { continuation ->
        val nsUrl = NSURL.URLWithString(url) ?: run {
            continuation.resume(Result.failure(Exception("URL inválida: $url")))
            return@suspendCancellableCoroutine
        }

        val request = NSMutableURLRequest(nsUrl).apply {
            setHTTPMethod("GET")
        }

        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            if (error != null) {
                continuation.resume(Result.failure(Exception(error.localizedDescription)))
                return@dataTaskWithRequest
            }

            val httpResponse = response as? NSHTTPURLResponse
            val statusCode = httpResponse?.statusCode?.toInt() ?: -1

            if (statusCode in 200..299 && data != null) {
                val body = (NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?) ?: ""
                continuation.resume(Result.success(body))
            } else {
                continuation.resume(Result.failure(Exception("HTTP $statusCode")))
            }
        }

        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}
