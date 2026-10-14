package br.com.codecacto.kmplib.account

import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.HttpClientOptions
import br.com.codecacto.kmplib.core.network.HttpRetryPolicy
import br.com.codecacto.kmplib.core.network.createHttpClient
import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 2.301.0 — exclusão com carência: leitura do contrato, estado único e o 403 interceptado. */
class AccountPendingDeletionTest {

    // O envelope EXATO da backlib 0.181.0: os campos da exclusão vêm DENTRO de `details`.
    private val pendingBody =
        """{"message":"Conta com exclusão programada","code":"ACCOUNT_PENDING_DELETION",""" +
            """"timestamp":"2026-11-01T12:00:00Z","path":"/v1/auth/me",""" +
            """"details":{"requestedAt":"2026-10-14T12:00:00Z","scheduledFor":"2026-11-13T12:00:00Z","daysRemaining":12}}"""

    // ---------- respostas do contrato ----------

    @Test
    fun status_get_pendente() {
        val s = parseAccountDeletionStatus(
            """{"pending":true,"requestedAt":"2026-10-14T12:00:00Z","scheduledFor":"2026-11-13T12:00:00Z","daysRemaining":30}""",
        )
        assertEquals(AccountDeletionStatus(true, "2026-10-14T12:00:00Z", "2026-11-13T12:00:00Z", 30), s)
    }

    @Test
    fun status_get_naoPendente_e_restore() {
        assertEquals(AccountDeletionStatus.NotPending, parseAccountDeletionStatus("""{"pending":false}"""))
    }

    @Test
    fun status_delete202_peloCampoStatus() {
        val s = parseAccountDeletionStatus(
            """{"status":"PENDING_DELETION","requestedAt":"a","scheduledFor":"2026-11-13T12:00:00Z","daysRemaining":"30"}""",
        )
        assertNotNull(s)
        assertTrue(s.pending)
        assertEquals(30, s.daysRemaining, "número em texto também vale")
    }

    @Test
    fun status_ilegivel_eNull() {
        assertNull(parseAccountDeletionStatus(null))
        assertNull(parseAccountDeletionStatus(""))
        assertNull(parseAccountDeletionStatus("<html>erro</html>"))
        assertNull(parseAccountDeletionStatus("""{"outra":"coisa"}"""))
        assertNull(parseAccountDeletionStatus("""{"pending":"""))
    }

    @Test
    fun status_diasNegativosViramZero() {
        assertEquals(0, parseAccountDeletionStatus("""{"pending":true,"daysRemaining":-3}""")?.daysRemaining)
    }

    // ---------- o 403 ----------

    @Test
    fun pendente403_comDetails() {
        val s = parseAccountPendingDeletion(403, pendingBody)
        assertNotNull(s)
        assertTrue(s.pending)
        assertEquals("2026-11-13T12:00:00Z", s.scheduledFor)
        assertEquals(12, s.daysRemaining)
    }

    @Test
    fun pendente403_camposNaRaiz_e_aninhadoEmError() {
        val raiz = parseAccountPendingDeletion(403, """{"code":"account_pending_deletion","daysRemaining":5}""")
        assertEquals(5, raiz?.daysRemaining, "sem diferenciar caixa, campos na raiz")
        val aninhado = parseAccountPendingDeletion(
            403,
            """{"ok":false,"error":{"code":"ACCOUNT_PENDING_DELETION","details":{"daysRemaining":"7"}}}""",
        )
        assertEquals(7, aninhado?.daysRemaining)
        val semDetalhe = parseAccountPendingDeletion(403, """{"code":"ACCOUNT_PENDING_DELETION"}""")
        assertEquals(AccountDeletionStatus(pending = true), semDetalhe)
    }

    @Test
    fun pendente403_naoConfundeOutrosCasos() {
        assertNull(parseAccountPendingDeletion(401, pendingBody), "outro status")
        assertNull(parseAccountPendingDeletion(403, """{"code":"FORBIDDEN"}"""), "outro 403")
        assertNull(parseAccountPendingDeletion(403, "Forbidden"), "não-JSON")
        assertNull(parseAccountPendingDeletion(403, null))
    }

    // ---------- dias ----------

    @Test
    fun dias_doServidorVencem() {
        val s = AccountDeletionStatus(pending = true, scheduledFor = "2026-11-13T12:00:00Z", daysRemaining = 3)
        assertEquals(3, s.daysRemainingAt(0L))
    }

    @Test
    fun dias_calculadosDaData_arredondaParaCima() {
        val alvo = AccountDeletionStatus(pending = true, scheduledFor = "2026-11-13T12:00:00Z")
        val alvoMs = alvo.scheduledForEpochMillis!!
        val dia = 24L * 60 * 60 * 1000
        assertEquals(1, alvo.daysRemainingAt(alvoMs - 3_600_000), "falta 1 h: ainda é 1 dia")
        assertEquals(2, alvo.daysRemainingAt(alvoMs - dia - 1))
        assertEquals(1, alvo.daysRemainingAt(alvoMs - dia))
        assertEquals(0, alvo.daysRemainingAt(alvoMs + 10), "passou: hoje")
        assertNull(AccountDeletionStatus(pending = true).daysRemainingAt(0L))
        assertNull(AccountDeletionStatus(pending = true, scheduledFor = "lixo").daysRemainingAt(0L))
    }

    // ---------- estado ----------

    @Test
    fun estado_reportClear() {
        val st = AccountDeletionState()
        assertFalse(st.isPending)
        st.report(AccountDeletionStatus.NotPending)
        assertFalse(st.isPending, "não pendente não cobre")
        assertTrue(st.reportIfPending(403, pendingBody))
        assertEquals(12, st.pending.value?.daysRemaining)
        assertFalse(st.reportIfPending(403, """{"code":"FORBIDDEN"}"""))
        assertTrue(st.isPending, "outro 403 não tira")
        st.report(AccountDeletionStatus.NotPending)
        assertFalse(st.isPending, "não pendente limpa")
        st.report(AccountDeletionStatus(pending = true))
        st.clear()
        assertNull(st.pending.value)
    }

    // ---------- detector no HttpClient ----------

    private fun client(
        state: AccountDeletionState?,
        contentType: String = "application/json",
        hosts: Set<String>? = null,
        handler: (path: String, host: String) -> Pair<HttpStatusCode, String>,
    ): HttpClient = createHttpClient(
        MockEngine { req ->
            val (status, body) = handler(req.url.encodedPath, req.url.host)
            respond(body, status, headersOf("Content-Type", contentType))
        },
        HttpClientOptions(
            enableLogging = false,
            retry = HttpRetryPolicy.Disabled,
            accountDeletion = state,
            accountDeletionHosts = hosts,
        ),
    )

    @Test
    fun detector_ligadoPorDefault_noEstadoDoProcesso() {
        assertSame(AccountDeletionState.Shared, HttpClientOptions().accountDeletion)
    }

    @Test
    fun detector_registraO403_eOChamadorAindaLeOCorpo() = runTest {
        val st = AccountDeletionState()
        val http = client(st) { _, _ -> HttpStatusCode.Forbidden to pendingBody }
        val resp = http.get("https://api.x.com/v1/itens")
        assertEquals(pendingBody, resp.bodyAsText(), "o detector não consome o corpo de quem chamou")
        assertEquals(12, st.pending.value?.daysRemaining)
    }

    @Test
    fun detector_desligadoComNull() = runTest {
        val http = client(null) { _, _ -> HttpStatusCode.Forbidden to pendingBody }
        http.get("https://api.x.com/v1/itens")
        // Nada para conferir além de não lançar: sem estado, sem detector.
    }

    @Test
    fun detector_ignora403NaoJson_eOutro403() = runTest {
        val st = AccountDeletionState()
        val midia = client(st, contentType = "image/jpeg") { _, _ -> HttpStatusCode.Forbidden to pendingBody }
        midia.get("https://cdn.x.com/foto.jpg")
        assertFalse(st.isPending, "403 de mídia não é lido")
        val outro = client(st) { _, _ -> HttpStatusCode.Forbidden to """{"code":"FORBIDDEN"}""" }
        outro.get("https://api.x.com/v1/admin")
        assertFalse(st.isPending)
    }

    @Test
    fun detector_2xxDeRotaProtegidaDoMesmoHost_tira_eNeutroNao() = runTest {
        val st = AccountDeletionState()
        var restaurada = false
        val http = client(st) { path, host ->
            when {
                host != "api.x.com" -> HttpStatusCode.OK to "{}"
                path.startsWith("/v1/itens") && !restaurada -> HttpStatusCode.Forbidden to pendingBody
                else -> HttpStatusCode.OK to "{}"
            }
        }
        suspend fun autenticado(url: String) = http.get(url) { header(HttpHeaders.Authorization, "Bearer tok") }
        autenticado("https://api.x.com/v1/itens")
        assertTrue(st.isPending)
        autenticado("https://api.x.com/auth/refresh")
        autenticado("https://api.x.com/app/v1/me/data/deletion")
        autenticado("https://api.x.com/public/x")
        autenticado("https://outro.com/v1/itens")
        autenticado("https://api.x.com/feedback/v1")
        assertTrue(st.isPending, "login, a própria consulta, rota pública, outro servidor e outro serviço não provam")
        restaurada = true
        http.get("https://api.x.com/v1/itens")
        assertTrue(st.isPending, "2xx SEM Authorization não prova nada sobre a conta")
        autenticado("https://api.x.com/v1/itens")
        assertFalse(st.isPending, "restaurada por outro caminho")
    }

    @Test
    fun detector_comHosts_soObservaAApiDoProjeto() = runTest {
        val st = AccountDeletionState()
        val http = client(st, hosts = setOf("api.x.com")) { _, _ -> HttpStatusCode.Forbidden to pendingBody }
        http.get("https://terceiro.com/v1/coisa")
        assertFalse(st.isPending, "403 de outro servidor não cobre a tela")
        http.get("https://api.x.com/v1/auth/me")
        assertTrue(st.isPending, "o 403 do /auth/me da API do projeto cobre")
        assertEquals("2026-10-14T12:00:00Z", st.pending.value?.requestedAt, "campos lidos de details")
    }

    // ---------- tipos de erro ----------

    @Test
    fun erros_isAccountPendingDeletion() {
        assertTrue(ApiResult.Error(code = 403, message = "x", serverCode = "ACCOUNT_PENDING_DELETION").isAccountPendingDeletion)
        assertFalse(ApiResult.Error(code = 403, message = "x", serverCode = "FORBIDDEN").isAccountPendingDeletion)
        assertFalse(ApiResult.Error(code = 401, message = "x", serverCode = "ACCOUNT_PENDING_DELETION").isAccountPendingDeletion)
        assertTrue(DomainResult.Error(code = 403, message = "x", serverCode = "ACCOUNT_PENDING_DELETION").isAccountPendingDeletion)
        assertFalse(DomainResult.Error(code = 403, message = "x").isAccountPendingDeletion)
    }

    @Test
    fun domainApiClient_403TrazOCodigo_eODetectorRegistra() = runTest {
        val st = AccountDeletionState()
        val http = client(st) { _, _ -> HttpStatusCode.Forbidden to pendingBody }
        val api = DomainApiClient(http, DomainTokenProvider { "tok" }, "https://api.x.com")
        val r = api.getJson("/v1/itens")
        assertTrue(r is DomainResult.Error && r.isAccountPendingDeletion)
        assertEquals("12", r.details["daysRemaining"])
        assertTrue(st.isPending)
    }
}
