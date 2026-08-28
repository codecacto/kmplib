package br.com.codecacto.kmplib.push

interface PushNotificationService {
    suspend fun getToken(): String?

    suspend fun deleteToken(): Result<Unit>

    suspend fun subscribeToTopic(topic: String): Result<Unit>

    suspend fun unsubscribeFromTopic(topic: String): Result<Unit>
}
