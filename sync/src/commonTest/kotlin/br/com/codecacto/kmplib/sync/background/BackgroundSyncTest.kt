package br.com.codecacto.kmplib.sync.background

import br.com.codecacto.kmplib.core.network.NetworkType
import br.com.codecacto.kmplib.sync.SyncNetworkPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundSyncTest {

    @AfterTest
    fun limpar() = BackgroundSyncWork.clear()

    @Test
    fun `texto sobe em qualquer rede nos dois automaticos e nunca no manual`() {
        for (rede in listOf(NetworkType.METERED, NetworkType.UNMETERED)) {
            assertTrue(SyncNetworkPolicy.AUTOMATIC_MEDIA_ON_UNMETERED.allowsAutomaticData(rede))
            assertTrue(SyncNetworkPolicy.AUTOMATIC_ANY_NETWORK.allowsAutomaticData(rede))
            assertFalse(SyncNetworkPolicy.MANUAL_ONLY.allowsAutomaticData(rede))
        }
        SyncNetworkPolicy.entries.forEach { assertFalse(it.allowsAutomaticData(NetworkType.NONE)) }
    }

    @Test
    fun `a politica segura so a midia`() {
        assertTrue(SyncNetworkPolicy.AUTOMATIC_MEDIA_ON_UNMETERED.mediaOnlyOnUnmetered)
        assertFalse(SyncNetworkPolicy.AUTOMATIC_ANY_NETWORK.mediaOnlyOnUnmetered)
        assertFalse(SyncNetworkPolicy.MANUAL_ONLY.mediaOnlyOnUnmetered)
        assertFalse(SyncNetworkPolicy.AUTOMATIC_MEDIA_ON_UNMETERED.allowsAutomaticMedia(NetworkType.METERED))
        assertTrue(SyncNetworkPolicy.AUTOMATIC_MEDIA_ON_UNMETERED.allowsAutomaticMedia(NetworkType.UNMETERED))
        assertTrue(SyncNetworkPolicy.AUTOMATIC_ANY_NETWORK.allowsAutomaticMedia(NetworkType.METERED))
        assertFalse(SyncNetworkPolicy.MANUAL_ONLY.allowsAutomaticMedia(NetworkType.UNMETERED))
    }

    @Test
    fun `persistencia pelo wire com default seguro`() {
        SyncNetworkPolicy.entries.forEach { assertEquals(it, SyncNetworkPolicy.fromWire(it.wire)) }
        assertEquals(SyncNetworkPolicy.MANUAL_ONLY, SyncNetworkPolicy.fromWire("MANUAL_ONLY"))
        assertEquals(SyncNetworkPolicy.DEFAULT, SyncNetworkPolicy.fromWire(null))
        assertEquals(SyncNetworkPolicy.AUTOMATIC_MEDIA_ON_UNMETERED, SyncNetworkPolicy.fromWire("lixo"))
    }

    @Test
    fun `sem trabalho registrado devolve null`() = runTest {
        assertFalse(BackgroundSyncWork.isRegistered)
        assertNull(BackgroundSyncWork.run())
    }

    @Test
    fun `roda o trabalho registrado e excecao vira RETRY`() = runTest {
        var rodadas = 0
        BackgroundSyncWork.register { rodadas++; BackgroundSyncResult.SUCCESS }
        assertEquals(BackgroundSyncResult.SUCCESS, BackgroundSyncWork.run())
        assertEquals(1, rodadas)
        BackgroundSyncWork.register { error("servidor fora") }
        assertEquals(BackgroundSyncResult.RETRY, BackgroundSyncWork.run())
    }

    @Test
    fun `config recusa periodo abaixo do minimo do WorkManager`() {
        assertFailsWith<IllegalArgumentException> { BackgroundSyncConfig(periodMinutes = 5) }
        assertEquals(15, BackgroundSyncConfig().periodMinutes)
    }
}
