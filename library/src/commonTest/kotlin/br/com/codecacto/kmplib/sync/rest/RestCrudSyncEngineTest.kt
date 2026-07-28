package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.network.ConnectivityObserver
import br.com.codecacto.kmplib.sync.FakeSyncStore
import br.com.codecacto.kmplib.sync.SyncState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `sem escopo de conta declarado, nenhum ciclo roda (nao sobe outbox de dono desconhecido)`() = runTest {
        val log = mutableListOf<String>()
        val p = FakeParticipant("p", log = log)
        val scope = MutableStateFlow("")
        val engine = RestCrudSyncEngine(listOf(p), ConnectivityObserver(), accountScope = scope)

        assertTrue(!engine.syncNow())
        assertTrue(log.isEmpty())

        // Declarado o titular, o ciclo passa a rodar normalmente.
        scope.value = "conta-1"
        assertTrue(engine.syncNow())
        assertEquals(listOf("drain:p", "refresh:p"), log)
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

    // -- Integridade do titular DURANTE o ciclo (2.94.0 — GAP-KL-M-SYNC-SCOPERACE) ----
    //
    // O escopo era conferido uma vez, no início. Um ciclo em voo atravessava um `setAccountScope`
    // e misturava Bearer e bucket: o PUSH subia a outbox do titular anterior com o token de quem
    // acabou de entrar — vazamento de dado entre contas.

    /** Participante que "faz login de outro usuário" no meio da própria drenagem. */
    private class TrocaDeTitular(
        val name: String,
        val scope: MutableStateFlow<String>,
        val novoTitular: String,
        val log: MutableList<String>,
        val trocaNoDrain: Boolean = true,
    ) : RestCrudSyncParticipant {
        override suspend fun drainOutbox(parentRemap: Map<String, String>): Map<String, String> {
            log += "drain:$name"
            if (trocaNoDrain) scope.value = novoTitular
            return emptyMap()
        }
        override suspend fun refresh(): Boolean {
            log += "refresh:$name"
            if (!trocaNoDrain) scope.value = novoTitular
            return true
        }
    }

    @Test
    fun `titular que muda no meio do PUSH aborta o ciclo (outbox de A nao sobe com o token de B)`() = runTest {
        val log = mutableListOf<String>()
        val scope = MutableStateFlow("motorista-A")
        val pai = TrocaDeTitular("pai", scope, "motorista-B", log)
        val filho = FakeParticipant("filho", log = log)
        val engine = RestCrudSyncEngine(listOf(pai, filho), ConnectivityObserver(), accountScope = scope)

        assertFalse(engine.syncNow())

        // O filho nem chegou a ser drenado, e ninguém reconciliou sob o titular novo.
        assertEquals(listOf("drain:pai"), log)
        // Abortar por troca de titular não é "falha de sincronização" — o ciclo de B vai rodar.
        assertEquals(SyncState.Idle, engine.state.value)
    }

    @Test
    fun `titular que muda entre o PUSH e o PULL aborta antes de reconciliar`() = runTest {
        val log = mutableListOf<String>()
        val scope = MutableStateFlow("motorista-A")
        val p = TrocaDeTitular("p", scope, "motorista-B", log)
        val engine = RestCrudSyncEngine(listOf(p), ConnectivityObserver(), accountScope = scope)

        assertFalse(engine.syncNow())

        // O `refresh` gravaria o dado lido sob A dentro do bucket de B.
        assertEquals(listOf("drain:p"), log)
    }

    @Test
    fun `titular inalterado durante o ciclo continua rodando tudo (sem falso positivo)`() = runTest {
        val log = mutableListOf<String>()
        val scope = MutableStateFlow("motorista-A")
        val a = FakeParticipant("a", log = log)
        val b = FakeParticipant("b", log = log)
        val engine = RestCrudSyncEngine(listOf(a, b), ConnectivityObserver(), accountScope = scope)

        assertTrue(engine.syncNow())
        assertEquals(listOf("drain:a", "drain:b", "refresh:a", "refresh:b"), log)
    }

    /** Participante que trava no drain até o teste liberar — simula um ciclo "em voo". */
    private class ParticipanteBloqueado(
        val portao: CompletableDeferred<Unit>,
        val store: FakeSyncStore,
        val log: MutableList<String>,
    ) : RestCrudSyncParticipant {
        override suspend fun drainOutbox(parentRemap: Map<String, String>): Map<String, String> {
            log += "drain-inicio:${store.accountScope.value}"
            portao.await()
            log += "drain-fim:${store.accountScope.value}"
            return emptyMap()
        }
        override suspend fun refresh(): Boolean {
            log += "refresh:${store.accountScope.value}"
            return true
        }
    }

    @Test
    fun `setAccountScope ESPERA o ciclo em execucao terminar (fecha ate a requisicao em voo)`() = runTest {
        val store = FakeSyncStore()
        store.setAccountScope("motorista-A")
        val log = mutableListOf<String>()
        val portao = CompletableDeferred<Unit>()
        val engine = RestCrudSyncEngine(
            participants = listOf(ParticipanteBloqueado(portao, store, log)),
            connectivity = ConnectivityObserver(),
            store = store,
        )

        val ciclo = launch { engine.syncNow() }
        runCurrent() // o ciclo entra no drain e fica preso no portão

        val troca = launch { engine.setAccountScope("motorista-B") }
        runCurrent()
        // A troca NÃO se interpõe: o ciclo em voo continua inteiramente sob o titular anterior.
        assertEquals("motorista-A", store.accountScope.value)

        portao.complete(Unit)
        ciclo.join()
        troca.join()

        assertEquals("motorista-B", store.accountScope.value)
        assertEquals(
            listOf("drain-inicio:motorista-A", "drain-fim:motorista-A", "refresh:motorista-A"),
            log,
        )
    }

    @Test
    fun `com store informado o motor deriva o escopo sozinho (nao roda sem titular)`() = runTest {
        val store = FakeSyncStore()
        val log = mutableListOf<String>()
        val p = FakeParticipant("p", log = log)
        val engine = RestCrudSyncEngine(listOf(p), ConnectivityObserver(), store = store)

        assertFalse(engine.syncNow()) // espelho ainda sem titular
        assertTrue(log.isEmpty())

        engine.setAccountScope("motorista-A")
        assertTrue(engine.syncNow())
        assertEquals(listOf("drain:p", "refresh:p"), log)
    }
}
