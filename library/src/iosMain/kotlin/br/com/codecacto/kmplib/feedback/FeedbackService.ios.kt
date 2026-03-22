package br.com.codecacto.kmplib.feedback

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
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume

internal actual val currentPlatform: String = "ios"
internal actual val feedbackApiKey: String = "AIzaSyCRSX0fO8k1yhFbUhGscyjZDx2_vrWUdZA"

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual suspend fun httpPost(url: String, body: String): Result<Unit> {
    return suspendCancellableCoroutine { continuation ->
        val nsUrl = NSURL.URLWithString(url) ?: run {
            continuation.resume(Result.failure(Exception("URL inválida: $url")))
            return@suspendCancellableCoroutine
        }

        val nsBody = NSString.create(string = body)

        val request = NSMutableURLRequest(nsUrl).apply {
            setHTTPMethod("POST")
            setValue("application/json", forHTTPHeaderField = "Content-Type")
            setHTTPBody(nsBody.dataUsingEncoding(NSUTF8StringEncoding))
        }

        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { _, response, error ->
            if (error != null) {
                continuation.resume(Result.failure(Exception(error.localizedDescription)))
                return@dataTaskWithRequest
            }

            val httpResponse = response as? NSHTTPURLResponse
            val statusCode = httpResponse?.statusCode?.toInt() ?: -1

            if (statusCode in 200..299) {
                continuation.resume(Result.success(Unit))
            } else {
                continuation.resume(Result.failure(Exception("HTTP $statusCode")))
            }
        }

        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}
