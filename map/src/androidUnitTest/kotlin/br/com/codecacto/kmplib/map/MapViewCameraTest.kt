package br.com.codecacto.kmplib.map

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `CameraPositionState.animateTo` chamado antes de o Maps SDK inicializar derrubava o app com
 * `NullPointerException: CameraUpdateFactory is not initialized`. A JVM deste teste é exatamente
 * esse cenário: não há Maps SDK inicializado — tocar na fábrica aqui falharia do mesmo jeito.
 */
class MapViewCameraTest {

    private val brasilia = CameraPosition(LatLng(-15.7939, -47.8828), zoom = 4f)
    private val cuiaba = CameraPosition(LatLng(-15.6014, -56.0979), zoom = 14f)

    @Test
    fun `animateTo antes de existir mapa nao lanca e guarda o pedido`() {
        val state = CameraPositionState(brasilia)

        state.animateTo(cuiaba)

        assertEquals(cuiaba, state.position)
    }

    @Test
    fun `position escrita antes de existir mapa guarda o pedido`() {
        val state = CameraPositionState(brasilia)

        state.position = cuiaba

        assertEquals(cuiaba, state.position)
    }

    @Test
    fun `mapa ainda nao carregado nunca anima - escreve a posicao sem a fabrica`() {
        assertEquals(
            CameraSync.SET_BEFORE_LOAD,
            cameraSyncFor(requested = cuiaba, applied = brasilia, loaded = false),
        )
    }

    @Test
    fun `mapa carregado anima ate a posicao pedida`() {
        assertEquals(CameraSync.ANIMATE, cameraSyncFor(requested = cuiaba, applied = brasilia, loaded = true))
    }

    @Test
    fun `posicao ja aplicada nao gera comando - nem no carregamento do mapa`() {
        assertEquals(CameraSync.NONE, cameraSyncFor(requested = cuiaba, applied = cuiaba, loaded = false))
        assertEquals(CameraSync.NONE, cameraSyncFor(requested = cuiaba, applied = cuiaba, loaded = true))
    }

    @Test
    fun `mudar so o zoom e um pedido novo`() {
        assertEquals(
            CameraSync.ANIMATE,
            cameraSyncFor(requested = cuiaba.copy(zoom = 16f), applied = cuiaba, loaded = true),
        )
    }
}
