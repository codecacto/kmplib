package br.com.codecacto.kmplib.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PushNotificationServiceTest {
    @Test
    fun fakeServiceTracksTokenOperationsAndTopics() = runTest {
        val service = FakePushNotificationService(token = "token-1")

        assertEquals("token-1", service.getToken())
        assertTrue(service.deleteToken().isSuccess)
        assertTrue(service.subscribeToTopic("updates").isSuccess)
        assertTrue(service.unsubscribeFromTopic("legacy").isSuccess)

        assertEquals(1, service.deleteTokenCalls)
        assertEquals(listOf("updates"), service.subscribedTopics)
        assertEquals(listOf("legacy"), service.unsubscribedTopics)
    }
}
