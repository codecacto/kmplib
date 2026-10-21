package br.com.codecacto.kmplib.push

interface PushNotificationService {
    suspend fun getToken(): String?

    suspend fun deleteToken(): Result<Unit>

    suspend fun subscribeToTopic(topic: String): Result<Unit>

    suspend fun unsubscribeFromTopic(topic: String): Result<Unit>

    /**
     * Tira o ouvinte deste serviço do [PushEvents] (2.314.0). Idempotente; o serviço continua
     * respondendo a `getToken`/tópicos, só deixa de receber eventos. Default: nada a soltar.
     */
    fun release() = Unit
}
