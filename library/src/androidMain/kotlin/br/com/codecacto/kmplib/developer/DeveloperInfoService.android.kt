package br.com.codecacto.kmplib.developer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal actual suspend fun httpGet(url: String): Result<String> {
    return withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000

            val responseCode = connection.responseCode

            if (responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                connection.disconnect()
                Result.success(body)
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                } catch (_: Exception) { "" }
                connection.disconnect()
                Result.failure(Exception("HTTP $responseCode: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
