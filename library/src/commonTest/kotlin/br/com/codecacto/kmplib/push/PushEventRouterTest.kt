package br.com.codecacto.kmplib.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Listener de teste que captura cada callback disparado. */
private class RecordingListener : PushNotificationListener {
    var newToken: String? = null
    var clicked: Map<String, String>? = null
    var foreground: Triple<String?, String?, Map<String, String>>? = null
    var plainForeground: Pair<String?, String?>? = null

    override fun onNewToken(token: String) {
        newToken = token
    }

    override fun onNotificationClicked(data: Map<String, String>) {
        clicked = data
    }

    override fun onPushNotificationWithPayloadData(
        title: String?,
        body: String?,
        data: Map<String, String>,
    ) {
        foreground = Triple(title, body, data)
    }

    override fun onPushNotification(title: String?, body: String?) {
        plainForeground = title to body
    }
}

class PushEventRouterTest {

    @Test
    fun foregroundRoutesToPayloadCallbackWithDerivedTitleAndBody() {
        val listener = RecordingListener()
        val data = mapOf("title" to "Agendado", "body" to "Corte às 15h", "route" to "booking/9")

        PushEventRouter.dispatch(listener, data, wasTapped = false)

        val fg = listener.foreground
        assertTrue(fg != null)
        assertEquals("Agendado", fg.first)
        assertEquals("Corte às 15h", fg.second)
        assertEquals(data, fg.third)
        // Foreground NÃO é toque → não dispara deep link.
        assertNull(listener.clicked)
    }

    @Test
    fun backgroundTapRoutesToClickedWithFullPayload() {
        val listener = RecordingListener()
        val data = mapOf("title" to "Agendado", "route" to "booking/9")

        PushEventRouter.dispatch(listener, data, wasTapped = true)

        assertEquals(data, listener.clicked)
        // Toque não deve disparar o callback de foreground.
        assertNull(listener.foreground)
    }

    @Test
    fun coldStartTapUsesSameTapPathAsBackgroundTap() {
        // Cold-start-tap chega pelo MESMO didReceive:response (wasTapped=true) — deep link preservado.
        val listener = RecordingListener()
        val data = mapOf("route" to "promo/summer")

        PushEventRouter.dispatch(listener, data, wasTapped = true)

        assertEquals(data, listener.clicked)
    }

    @Test
    fun foregroundWithoutTextKeysStillRoutesWithNulls() {
        val listener = RecordingListener()
        val data = mapOf("route" to "booking/9")

        PushEventRouter.dispatch(listener, data, wasTapped = false)

        val fg = listener.foreground
        assertTrue(fg != null)
        assertNull(fg.first)
        assertNull(fg.second)
        assertEquals(data, fg.third)
    }
}
