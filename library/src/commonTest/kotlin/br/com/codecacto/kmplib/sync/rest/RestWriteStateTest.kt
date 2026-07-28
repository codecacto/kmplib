package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.sync.SyncOpType
import br.com.codecacto.kmplib.sync.db.Synced_entity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Núcleo puro da correção do GAP-KL-M-RESTCRUD-LOCALFIRST: **classificar a falha** (retentar × parar
 * e mostrar) e **derivar o estado da linha**. Errar aqui é perder a escrita do usuário ou retentar
 * para sempre algo que nunca será aceito.
 */
class RestWriteStateTest {

    @Test
    fun `falha de transporte e retentavel e marcada como offline`() {
        assertEquals(RestFailureClass.Offline, classifyRestFailure(DomainResult.OFFLINE_CODE))
        assertTrue(classifyRestFailure(DomainResult.OFFLINE_CODE).isRetryable)
    }

    @Test
    fun `5xx com rede presente e RETENTAVEL — era o caso que perdia a escrita`() {
        listOf(500, 502, 503, 504).forEach { code ->
            assertEquals(RestFailureClass.Retryable, classifyRestFailure(code), "código $code")
            assertTrue(classifyRestFailure(code).isRetryable)
        }
    }

    @Test
    fun `timeout 408, rate limit 429 e sessao expirada 401 sao retentaveis`() {
        // 401 chega aqui só depois de o DomainApiClient já ter renovado o token e tentado de novo:
        // a escrita espera o novo login em vez de ser jogada fora.
        listOf(408, 429, 401).forEach { assertTrue(classifyRestFailure(it).isRetryable, "código $it") }
    }

    @Test
    fun `4xx de validacao e permissao e TERMINAL — retentar em loop nunca converge`() {
        listOf(400, 403, 404, 409, 422).forEach {
            assertEquals(RestFailureClass.Terminal, classifyRestFailure(it), "código $it")
            assertFalse(classifyRestFailure(it).isRetryable)
        }
    }

    @Test
    fun `402 tem classe propria (cota) e nao e retentavel sozinho`() {
        assertEquals(RestFailureClass.Quota, classifyRestFailure(402))
        assertFalse(classifyRestFailure(402).isRetryable)
    }

    @Test
    fun `resposta ilegivel e terminal — repetir o POST duplicaria o registro`() {
        assertEquals(
            RestFailureClass.Terminal,
            classifyRestFailure(OfflineFirstRestRepository.INVALID_RESPONSE_CODE),
        )
        assertEquals(
            RestFailureClass.Terminal,
            classifyRestFailure(OfflineFirstRestRepository.INVALID_PAYLOAD_CODE),
        )
    }

    // -- Máquina de estados da outbox (resolveOutboxOp) ---------------------

    @Test
    fun `update sobre linha SEM server_id continua sendo CREATE — o registro nunca existiu la`() {
        assertEquals(
            SyncOpType.CREATE,
            resolveOutboxOp(SyncOpType.UPDATE, knownLocally = true, hasServerId = false),
        )
        // Idempotente: vários updates seguidos não escapam do CREATE.
        assertEquals(
            SyncOpType.CREATE,
            resolveOutboxOp(SyncOpType.CREATE, knownLocally = true, hasServerId = false),
        )
    }

    @Test
    fun `delete sobre linha SEM server_id nao envia nada (nulo) — resolve local, sem 404`() {
        assertNull(resolveOutboxOp(SyncOpType.DELETE, knownLocally = true, hasServerId = false))
    }

    @Test
    fun `linha ja confirmada pelo servidor mantem a operacao pedida`() {
        assertEquals(
            SyncOpType.UPDATE,
            resolveOutboxOp(SyncOpType.UPDATE, knownLocally = true, hasServerId = true),
        )
        assertEquals(
            SyncOpType.DELETE,
            resolveOutboxOp(SyncOpType.DELETE, knownLocally = true, hasServerId = true),
        )
    }

    @Test
    fun `create sobre linha que JA tem server_id vira update — repetir o POST duplicaria`() {
        assertEquals(
            SyncOpType.UPDATE,
            resolveOutboxOp(SyncOpType.CREATE, knownLocally = true, hasServerId = true),
        )
    }

    @Test
    fun `linha desconhecida no espelho respeita o pedido — inferir viraria POST duplicado`() {
        // Ex.: update de um item que ainda não foi baixado: o id veio do app e é do servidor.
        SyncOpType.entries.forEach { op ->
            assertEquals(op, resolveOutboxOp(op, knownLocally = false, hasServerId = false), op.wire)
        }
    }

    private fun row(
        dirty: Long,
        op: String?,
        failed: Long = 0L,
        code: Long? = null,
        error: String? = null,
        attempts: Long = 0L,
    ) = Synced_entity(
        account_id = "acc",
        entity = "e",
        local_id = "1",
        server_id = null,
        client_id = "1",
        payload_json = "{}",
        updated_at = null,
        dirty = dirty,
        pending_op = op,
        deleted = 0L,
        base_updated_at = null,
        last_error = error,
        failed = failed,
        fail_code = code,
        attempts = attempts,
    )

    @Test
    fun `linha limpa e Synced`() {
        assertEquals(RestRowState.Synced, row(dirty = 0L, op = null).toRestRowState())
    }

    @Test
    fun `linha suja sem recusa e Pending com a operacao e as tentativas`() {
        val state = row(dirty = 1L, op = SyncOpType.CREATE.wire, attempts = 2L, error = "500").toRestRowState()
        assertEquals(RestRowState.Pending(SyncOpType.CREATE, attempts = 2, lastError = "500"), state)
        assertTrue(state.isPending)
        assertFalse(state.isFailed)
    }

    @Test
    fun `linha recusada e Failed preservando codigo e mensagem`() {
        val state = row(
            dirty = 1L,
            op = SyncOpType.UPDATE.wire,
            failed = 1L,
            code = 422L,
            error = "CPF inválido",
            attempts = 1L,
        ).toRestRowState()
        assertEquals(RestRowState.Failed(SyncOpType.UPDATE, 422, "CPF inválido", 1), state)
        assertFalse((state as RestRowState.Failed).isQuota)
    }

    @Test
    fun `Failed com 402 se identifica como cota (caminho do Paywall, nao do retry)`() {
        val state = row(dirty = 1L, op = SyncOpType.CREATE.wire, failed = 1L, code = 402L).toRestRowState()
        assertTrue((state as RestRowState.Failed).isQuota)
    }
}
