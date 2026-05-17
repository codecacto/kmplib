package br.com.codecacto.kmplib.push

class FakePushNotificationService(
    var token: String? = "test-fcm-token",
) : PushNotificationService {
    var deleteTokenCalls: Int = 0
        private set

    private val _subscribedTopics = mutableListOf<String>()
    val subscribedTopics: List<String> get() = _subscribedTopics

    private val _unsubscribedTopics = mutableListOf<String>()
    val unsubscribedTopics: List<String> get() = _unsubscribedTopics

    override suspend fun getToken(): String? = token

    override suspend fun deleteToken(): Result<Unit> {
        deleteTokenCalls++
        return Result.success(Unit)
    }

    override suspend fun subscribeToTopic(topic: String): Result<Unit> {
        _subscribedTopics.add(topic)
        return Result.success(Unit)
    }

    override suspend fun unsubscribeFromTopic(topic: String): Result<Unit> {
        _unsubscribedTopics.add(topic)
        return Result.success(Unit)
    }

    fun reset() {
        token = "test-fcm-token"
        deleteTokenCalls = 0
        _subscribedTopics.clear()
        _unsubscribedTopics.clear()
    }
}
