package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.network.ConnectivityObserver
import br.com.codecacto.kmplib.sync.SyncState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestCrudSyncEngineTest {

    private class FakeParticipant(
        val name: String,
        val produces: Map<String, String> = emptyMap(),
        val refreshOk: Boolean = true,
        val log: MutableList<String>,
    ) : RestCrudSyncParticipant {
        var receivedRemap: Map<String, String> = emptyMap()
        override suspend fun drainOutbox(parentRemap: Map<String, String>): Map<String, String> {
            receivedRemap = parentRemap
            log += "drain:$name"
            return produces
        }
        override suspend fun refresh(): Boolean {
            log += "refresh:$name"
            return refreshOk
        }
    }

    @Test
    fun `syncNow drena na ordem e propaga remap acumulado aos seguintes`() = runTest {
        val log = mutableListOf<String>()
        val pai = FakeParticipant("pai", produces = mapOf("local-1" to "srv-1"), log = log)
        val filho = FakeParticipant("filho", log = log)
        val engine = RestCrudSyncEngine(listOf(pai, filho), ConnectivityObserver())

        val ok = engine.syncNow()

        assertTrue(ok)
        // Push acontece antes do pull, e na ordem pai→filho.
        assertEquals(listOf("drain:pai", "drain:filho", "refresh:pai", "refresh:filho"), log)
        // O filho recebeu o remap gerado pelo pai.
        assertEquals("srv-1", filho.receivedRemap["local-1"])
        assertEquals(SyncState.Idle, engine.state.value)
    }

    @Test
    fun `refresh parcial deixa estado de erro mas nao lanca`() = runTest {
        val log = mutableListOf<String>()
        val a = FakeParticipant("a", refreshOk = false, log = log)
        val engine = RestCrudSyncEngine(listOf(a), ConnectivityObserver())
        val ok = engine.syncNow()
        assertTrue(!ok)
        assertTrue(engine.state.value is SyncState.Error)
    }
}
