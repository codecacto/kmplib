package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.network.ConnectivityObserver
import br.com.codecacto.kmplib.sync.FakeSyncStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// -- Domínio de teste: uma ROTA (pai) com PASSAGEIROS (filhos) -------------
//
// É o formato do "Todos a Bordo": a rota nasce sem sinal e os passageiros referenciam a rota por FK.
// O backend valida a FK (UUID + FOREIGN KEY): um `rotaId` que não seja um id de rota conhecido é
// recusado com 422 — recusa TERMINAL, que antes deixava o filho `Failed` para sempre.

@Serializable
private data class Rota(val id: String, val nome: String, val encerrada: Boolean = false)

private object RotaCrud : RestCrudEntity<Rota> {
    override val name = "rota"
    override val serializer: KSerializer<Rota> = Rota.serializer()
    override fun idOf(model: Rota) = model.id
    override fun encodeBody(model: Rota) = Json.encodeToString(Rota.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(Rota.serializer(), body)
    override fun decodeList(body: String) = Json.decodeFromString(ListSerializer(Rota.serializer()), body)
    override fun withLocalId(model: Rota, clientId: String) = model.copy(id = clientId)
}

@Serializable
private data class Passageiro(val id: String, val rotaId: String, val nome: String, val embarcado: Boolean = false)

/** Descritor **sem** `remapRefs`: prova que a correção chega ao app sem ele mudar nada. */
private object PassageiroCrud : RestCrudEntity<Passageiro> {
    override val name = "passageiro"
    override val serializer: KSerializer<Passageiro> = Passageiro.serializer()
    override fun idOf(model: Passageiro) = model.id
    override fun encodeBody(model: Passageiro) = Json.encodeToString(Passageiro.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(Passageiro.serializer(), body)
    override fun decodeList(body: String) = Json.decodeFromString(ListSerializer(Passageiro.serializer()), body)
    override fun withLocalId(model: Passageiro, clientId: String) = model.copy(id = clientId)
}

/** Descritor **com** `remapRefs` — o caminho que os apps de hoje já usam. */
private object PassageiroComHookCrud : RestCrudEntity<Passageiro> {
    override val name = "passageiro"
    override val serializer: KSerializer<Passageiro> = Passageiro.serializer()
    override fun idOf(model: Passageiro) = model.id
    override fun encodeBody(model: Passageiro) = Json.encodeToString(Passageiro.serializer(), model)
    override fun decodeModel(body: String) = Json.decodeFromString(Passageiro.serializer(), body)
    override fun decodeList(body: String) = Json.decodeFromString(ListSerializer(Passageiro.serializer()), body)
    override fun withLocalId(model: Passageiro, clientId: String) = model.copy(id = clientId)
    override fun remapRefs(model: Passageiro, remap: Map<String, String>) =
        model.copy(rotaId = remap[model.rotaId] ?: model.rotaId)
}

/**
 * **GAP-KL-M-RESTCRUD-IDMIGRATION** — o id migra; nada pode se perder com ele.
 *
 * Dois defeitos da mesma raiz, ambos pré-existentes:
 * 1. **UI:** a tela aberta com o id local de um registro criado offline ficava vazia assim que o
 *    drain migrava o id (a linha era apagada e reinserida sob o id do servidor, e `client_id` era
 *    sobrescrito, então não sobrava âncora nenhuma).
 * 2. **FK:** o remap `clientId → serverId` só existia dentro de UM ciclo de sync — um filho que
 *    não drenasse junto do pai subia a FK apontando para o id local, o backend recusava e a linha
 *    ficava `Failed` para sempre (perda definitiva de dado).
 */
class RestIdMigrationTest {

    private val jsonHeader = headersOf("Content-Type", "application/json")

    /**
     * Backend com **validação de FK**: só aceita passageiro cujo `rotaId` seja um id de rota que ele
     * conheça. É o que transforma "FK com id local" em recusa terminal — o comportamento real.
     */
    private class Backend {
        var online = true
        var proximoIdRota = "rota-SRV"
        var sequenciaPassageiro = 0
        val rotasConhecidas = mutableSetOf<String>()

        /** Corpos recebidos, como `"POST /v1/passageiros {json}"`. */
        val recebidos = mutableListOf<String>()

        /** Rejeições por FK inválida (o 422 que matava o registro). */
        var recusasPorFk = 0
    }

    private fun rotaRepo(backend: Backend, store: FakeSyncStore, mode: RestWriteMode = RestWriteMode.LocalFirst) =
        OfflineFirstRestRepository(
            api = api(backend),
            descriptor = RotaCrud,
            store = store,
            collectionPath = "/v1/rotas",
            writeMode = mode,
        )

    private fun passageiroRepo(
        backend: Backend,
        store: FakeSyncStore,
        descriptor: RestCrudEntity<Passageiro> = PassageiroCrud,
        mode: RestWriteMode = RestWriteMode.LocalFirst,
    ) = OfflineFirstRestRepository(
        api = api(backend),
        descriptor = descriptor,
        store = store,
        collectionPath = "/v1/passageiros",
        writeMode = mode,
    )

    private fun api(backend: Backend) = DomainApiClient(
        HttpClient(
            MockEngine { request ->
                if (!backend.online) throw RuntimeException("sem rede")
                val corpo = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
                val rota = "${request.method.value} ${request.url.encodedPath}"
                backend.recebidos += "$rota $corpo"
                when {
                    request.method.value == "GET" -> respond("[]", HttpStatusCode.OK, jsonHeader)
                    request.url.encodedPath.startsWith("/v1/rotas") -> {
                        val comId = corpo.replaceFirst(Regex("\"id\":\"[^\"]*\""), "\"id\":\"${backend.proximoIdRota}\"")
                        backend.rotasConhecidas += backend.proximoIdRota
                        respond(comId, HttpStatusCode.Created, jsonHeader)
                    }
                    else -> {
                        val fk = Regex("\"rotaId\":\"([^\"]*)\"").find(corpo)?.groupValues?.get(1)
                        if (fk != null && fk !in backend.rotasConhecidas) {
                            backend.recusasPorFk++
                            respond("""{"error":"rotaId inválido"}""", HttpStatusCode.UnprocessableEntity, jsonHeader)
                        } else {
                            val comId = corpo.replaceFirst(
                                Regex("\"id\":\"[^\"]*\""),
                                "\"id\":\"pax-SRV-${++backend.sequenciaPassageiro}\"",
                            )
                            respond(comId, HttpStatusCode.Created, jsonHeader)
                        }
                    }
                }
            },
        ),
        DomainTokenProvider { "tok" },
        "https://api.example.com",
    )

    private fun fkEnviada(backend: Backend): List<String> =
        backend.recebidos
            .filter { it.startsWith("POST /v1/passageiros") }
            .mapNotNull { Regex("\"rotaId\":\"([^\"]*)\"").find(it)?.groupValues?.get(1) }

    // =====================================================================
    // 1) FK entre entidades que drenam em CICLOS DIFERENTES
    // =====================================================================

    @Test
    fun `filho que drena em ciclo POSTERIOR ao do pai sobe com a FK do servidor`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { online = false }
        val rotas = rotaRepo(backend, store)
        val passageiros = passageiroRepo(backend, store)

        // Rota e passageiro nascem sem sinal.
        val rotaHandle = (rotas.create(Rota(id = "", nome = "Manhã")) as DomainResult.Success).data.id
        passageiros.create(Passageiro(id = "", rotaId = rotaHandle, nome = "Ana"))

        // CICLO 1: só o PAI drena (o filho ficou de fora — sinal caiu, app fechou, tanto faz).
        backend.online = true
        val remapCiclo1 = rotas.drainOutbox(emptyMap())
        assertEquals("rota-SRV", remapCiclo1[rotaHandle])

        // CICLO 2: o pai não tem mais nada a drenar, então o remap do ciclo chega VAZIO.
        assertTrue(rotas.drainOutbox(emptyMap()).isEmpty())
        passageiros.drainOutbox(emptyMap())

        // A FK subiu com o id do SERVIDOR — o backend aceitou e nada ficou recusado.
        assertEquals(listOf("rota-SRV"), fkEnviada(backend))
        assertEquals(0, backend.recusasPorFk)
        assertTrue(passageiros.failedRows().isEmpty())
        assertTrue(store.getDirty("passageiro").isEmpty())
    }

    @Test
    fun `remap durável funciona com o hook remapRefs do app (caminho dos apps de hoje)`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { online = false }
        val rotas = rotaRepo(backend, store)
        val passageiros = passageiroRepo(backend, store, PassageiroComHookCrud)

        val rotaHandle = (rotas.create(Rota(id = "", nome = "Tarde")) as DomainResult.Success).data.id
        passageiros.create(Passageiro(id = "", rotaId = rotaHandle, nome = "Bia"))

        backend.online = true
        rotas.drainOutbox(emptyMap())
        passageiros.drainOutbox(emptyMap()) // ciclo posterior, remap do ciclo vazio

        assertEquals(listOf("rota-SRV"), fkEnviada(backend))
        assertEquals(0, backend.recusasPorFk)
    }

    @Test
    fun `drain interrompido no meio - o filho que sobrou converge no ciclo seguinte`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { online = false }
        val rotas = rotaRepo(backend, store)
        val passageiros = passageiroRepo(backend, store)

        val rotaHandle = (rotas.create(Rota(id = "", nome = "Escolar")) as DomainResult.Success).data.id
        passageiros.create(Passageiro(id = "", rotaId = rotaHandle, nome = "Ana"))
        passageiros.create(Passageiro(id = "", rotaId = rotaHandle, nome = "Caio"))

        // Ciclo 1: o pai sobe e o sinal cai antes de o filho drenar.
        backend.online = true
        rotas.drainOutbox(emptyMap())
        backend.online = false
        passageiros.drainOutbox(emptyMap())
        assertEquals(2, store.getDirty("passageiro").size) // outbox preservada, nada recusado
        assertTrue(passageiros.failedRows().isEmpty())

        // Ciclo 2: rede de volta, sem nada no pai para drenar.
        backend.online = true
        passageiros.drainOutbox(emptyMap())

        assertEquals(listOf("rota-SRV", "rota-SRV"), fkEnviada(backend))
        assertEquals(0, backend.recusasPorFk)
        assertTrue(store.getDirty("passageiro").isEmpty())
    }

    @Test
    fun `reinicio de processo entre o POST do pai e o do filho preserva a traducao da FK`() = runTest {
        val store = FakeSyncStore() // o espelho é o que sobrevive ao processo
        val backend = Backend().apply { online = false }

        val rotaHandle = run {
            val rotas = rotaRepo(backend, store)
            val passageiros = passageiroRepo(backend, store)
            val handle = (rotas.create(Rota(id = "", nome = "Manhã")) as DomainResult.Success).data.id
            passageiros.create(Passageiro(id = "", rotaId = handle, nome = "Ana"))
            backend.online = true
            rotas.drainOutbox(emptyMap()) // o pai sobe...
            handle
        }

        // ...e o app morre aqui. Instâncias NOVAS de repositório/engine, sobre o mesmo espelho.
        val rotasNovo = rotaRepo(backend, store)
        val passageirosNovo = passageiroRepo(backend, store)
        RestCrudSyncEngine(
            participants = listOf(rotasNovo, passageirosNovo),
            connectivity = ConnectivityObserver(),
        ).syncNow()

        assertEquals(listOf("rota-SRV"), fkEnviada(backend))
        assertEquals(0, backend.recusasPorFk)
        assertTrue(passageirosNovo.failedRows().isEmpty())
        // E o handle do pai continua resolvendo, mesmo em processo novo.
        assertEquals("rota-SRV", rotasNovo.canonicalId(rotaHandle))
    }

    // =====================================================================
    // 2) O handle da UI sobrevive à migração do id
    // =====================================================================

    @Test
    fun `consulta por handle continua devolvendo a linha depois da migracao de id`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { online = false }
        val rotas = rotaRepo(backend, store)

        // A UI navega com este id e ele congela no back stack.
        val handle = (rotas.create(Rota(id = "", nome = "Manhã")) as DomainResult.Success).data.id
        assertTrue(handle.startsWith("local-"))
        assertEquals("Manhã", rotas.getCached(handle)?.nome)

        // O sinal volta no meio do trajeto e o id migra.
        backend.online = true
        rotas.drainOutbox(emptyMap())

        // Tudo que a tela faz com o handle antigo continua funcionando.
        assertEquals("rota-SRV", rotas.getCached(handle)?.id)
        assertEquals("rota-SRV", rotas.observeById(handle).first()?.id)
        assertEquals("rota-SRV", rotas.canonicalId(handle))
        assertTrue(rotas.stateOf(handle).isSynced)
        assertNotNull(rotas.observeByIdWithState(handle).first())
        assertEquals("rota-SRV", (rotas.getById(handle) as DomainResult.Success).data?.id)
    }

    @Test
    fun `update e delete pelo handle antigo falam com o id do servidor (nada de PUT em local-)`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { online = false }
        val rotas = rotaRepo(backend, store)
        val handle = (rotas.create(Rota(id = "", nome = "Manhã")) as DomainResult.Success).data.id

        backend.online = true
        rotas.drainOutbox(emptyMap())
        backend.recebidos.clear()

        // O ViewModel ainda tem em mãos o modelo antigo, com o id local.
        val res = rotas.update(Rota(id = handle, nome = "Manhã", encerrada = true))
        assertTrue(res is DomainResult.Success)
        assertTrue(backend.recebidos.any { it.startsWith("PUT /v1/rotas/rota-SRV ") })
        assertFalse(backend.recebidos.any { it.contains("/v1/rotas/local-") })

        backend.recebidos.clear()
        assertTrue(rotas.delete(handle) is DomainResult.Success)
        assertTrue(backend.recebidos.any { it.startsWith("DELETE /v1/rotas/rota-SRV") })
        assertNull(rotas.getCached(handle))
    }

    @Test
    fun `observeCanonicalId emite o id do servidor assim que ele migra`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { online = false }
        val rotas = rotaRepo(backend, store)
        val handle = (rotas.create(Rota(id = "", nome = "Manhã")) as DomainResult.Success).data.id

        assertEquals(handle, rotas.observeCanonicalId(handle).first())

        backend.online = true
        rotas.drainOutbox(emptyMap())

        assertEquals("rota-SRV", rotas.observeCanonicalId(handle).first())
    }

    @Test
    fun `RestIdResolver same reconhece o mesmo registro pelos dois ids`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { online = false }
        val rotas = rotaRepo(backend, store)
        val handle = (rotas.create(Rota(id = "", nome = "Manhã")) as DomainResult.Success).data.id

        // Antes de migrar, só o próprio id é o mesmo registro.
        assertTrue(rotas.ids.same(handle, handle))
        assertFalse(rotas.ids.same(handle, "rota-SRV"))
        assertFalse(rotas.ids.isMigrated(handle))

        backend.online = true
        rotas.drainOutbox(emptyMap())

        assertTrue(rotas.ids.same(handle, "rota-SRV"))
        assertTrue(rotas.ids.same("rota-SRV", handle))
        assertTrue(rotas.ids.isMigrated(handle))
        assertEquals(handle, rotas.ids.clientIdOf("rota-SRV"))
        assertFalse(rotas.ids.same(handle, "outra-rota"))
        assertFalse(rotas.ids.same(null, handle))
    }

    // =====================================================================
    // 3) `client_id` é âncora permanente (não é sobrescrito por ninguém)
    // =====================================================================

    @Test
    fun `client_id sobrevive a markSynced, a refresh e a confirm`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { online = false }
        val rotas = rotaRepo(backend, store)
        val handle = (rotas.create(Rota(id = "", nome = "Manhã")) as DomainResult.Success).data.id

        backend.online = true
        rotas.drainOutbox(emptyMap())
        assertEquals(handle, store.getByLocalId("rota", "rota-SRV")?.client_id)

        // A reconciliação por GET de lista (refresh) NÃO pode apagar a âncora.
        rotas.mirror.reconcile(listOf(Rota(id = "rota-SRV", nome = "Manhã (servidor)")))
        assertEquals(handle, store.getByLocalId("rota", "rota-SRV")?.client_id)
        assertEquals("Manhã (servidor)", rotas.getCached(handle)?.nome)

        // Nem a confirmação de um update.
        rotas.mirror.confirm(Rota(id = "rota-SRV", nome = "Manhã", encerrada = true))
        assertEquals(handle, store.getByLocalId("rota", "rota-SRV")?.client_id)
        assertEquals(true, rotas.getCached(handle)?.encerrada)
    }

    @Test
    fun `putClean de endpoint custom migra a linha local e registra o remap duravel`() = runTest {
        val store = FakeSyncStore()
        val backend = Backend().apply { online = false }
        val rotas = rotaRepo(backend, store)
        val handle = (rotas.create(Rota(id = "", nome = "Manhã")) as DomainResult.Success).data.id

        // O app confirmou a criação por um endpoint próprio e reconciliou o espelho na mão.
        rotas.mirror.putClean(Rota(id = "rota-CUSTOM", nome = "Manhã"), replacingHandle = handle)

        assertEquals("rota-CUSTOM", rotas.canonicalId(handle))
        assertEquals("rota-CUSTOM", rotas.getCached(handle)?.id)
        assertEquals(1, store.getVisible("rota").size) // migrou, não duplicou
        assertTrue(rotas.ids.same(handle, "rota-CUSTOM"))
    }

    @Test
    fun `escopo de conta isola o remap duravel`() = runTest {
        val store = FakeSyncStore()
        store.setAccountScope("motorista-A")
        val backend = Backend().apply { online = false }
        val rotas = rotaRepo(backend, store)
        val handle = (rotas.create(Rota(id = "", nome = "Manhã")) as DomainResult.Success).data.id
        backend.online = true
        rotas.drainOutbox(emptyMap())
        assertEquals("rota-SRV", rotas.canonicalId(handle))

        store.setAccountScope("motorista-B")
        assertNull(store.resolveServerId(handle)) // o mapeamento de A não vaza para B
        assertEquals(0L, store.countIdRemap())

        store.setAccountScope("motorista-A")
        assertEquals("rota-SRV", rotas.canonicalId(handle))
    }

    // =====================================================================
    // 4) Varredura genérica do corpo — sem tocar em texto livre
    // =====================================================================

    @Test
    fun `a varredura generica so troca ids conhecidos, nunca texto livre`() {
        val json = restMirrorJson
        val corpo = """{"id":"local-1","rotaId":"local-9","obs":"combinei local-9 com a escola","n":7}"""
        val resultado = RestPayloadRemap.applyToBody(corpo, json) { if (it == "local-9") "rota-SRV" else null }

        // A FK foi traduzida...
        assertTrue(resultado.contains("\"rotaId\":\"rota-SRV\""))
        // ...e o id da própria linha (que não migrou) e o número ficaram intactos.
        assertTrue(resultado.contains("\"id\":\"local-1\""))
        assertTrue(resultado.contains("\"n\":7"))
        // Texto livre que MENCIONA o id não é reescrito (só valores string iguais ao id inteiro).
        assertTrue(resultado.contains("combinei local-9 com a escola"))

        // Nada conhecido ⇒ o corpo volta idêntico (sem reserializar).
        assertEquals(corpo, RestPayloadRemap.applyToBody(corpo, json) { null })
        // Corpo que não é JSON passa intacto, sem lançar.
        assertEquals("não é json", RestPayloadRemap.applyToBody("não é json", json) { "x" })
    }

    @Test
    fun `a varredura generica desce em objetos e listas aninhados`() {
        val json = restMirrorJson
        val corpo = """{"itens":[{"rotaId":"local-9"},{"rotaId":"local-8"}],"meta":{"rotaId":"local-9"}}"""
        val resultado = RestPayloadRemap.applyToBody(corpo, json) {
            when (it) {
                "local-9" -> "rota-A"
                "local-8" -> "rota-B"
                else -> null
            }
        }
        assertFalse(resultado.contains("local-"))
        assertEquals(2, Regex("rota-A").findAll(resultado).count())
        assertTrue(resultado.contains("rota-B"))
    }
}
