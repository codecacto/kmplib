package br.com.codecacto.kmplib.inspection.data

import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.monetization.entitlement.QuotaExceeded
import br.com.codecacto.kmplib.sync.rest.parseServerErrorEnvelope
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O espelho do contrato v1 lê o que o servidor escreve e escreve o que ele lê. */
class InspectionContractTest {

    @Test
    fun `le a vistoria real com campo e valor de enum desconhecidos`() {
        val dto = InspectionFixtures.entryDraft()
        assertEquals(InspectionStatus.DRAFT, dto.status)
        assertEquals("Apto 302", dto.objectTitle)
        assertEquals(2, dto.groups.size)
        val piso = dto.groups[0].items[0]
        assertEquals(ItemState.GOOD, piso.state)
        assertEquals(MediaStatus.CONFIRMED, piso.media.single().status)
        val parede = dto.groups[0].items[1]
        assertEquals(ItemState.FAIR, parede.state)
        assertEquals(ItemFunctioning.NOT_TESTED, parede.functioning)
        // Valor que este app não conhece vira UNKNOWN, sem derrubar a leitura.
        assertEquals(MediaStatus.UNKNOWN, dto.groups[1].items[1].media.single().status)
        val tarefa = dto.groups[1].items[0].mediaTasks.single()
        assertEquals(MeterUnit.M3, tarefa.reading)
        assertEquals(3, tarefa.reading?.maxDecimals)
    }

    @Test
    fun `le a saida assinada com o item da entrada e as versoes do laudo`() {
        val dto = InspectionFixtures.exitSigned()
        assertEquals(InspectionContext.EXIT, dto.context)
        assertEquals(InspectionStatus.SIGNED, dto.status)
        val item = dto.groups.single().items.single()
        assertEquals(ItemResult.NON_CONFORM, item.result)
        assertEquals(ItemState.GOOD, item.entry?.state)
        assertEquals(2, dto.reports.size)
        assertEquals(BilateralBasis.SIGNATURE, dto.reports[1].bilateralBasis)
        assertEquals(OtpChannelKind.SMS, dto.reports[1].signatures.single().otpChannel)
    }

    @Test
    fun `patch omite o que nao muda e manda a lista de limpeza`() {
        val json = InspectionJson.encodeToString(
            ItemPatch.serializer(),
            ItemPatch(id = "i1", state = ItemState.POOR, clear = listOf(ItemClearFields.OBSERVATION)),
        )
        val obj = InspectionJson.parseToJsonElement(json).jsonObject
        assertEquals(JsonPrimitive("POOR"), obj["state"])
        assertFalse("result" in obj, "campo nulo não vai no fio (ausente = não mexe)")
        assertFalse("observation" in obj)
        assertEquals("[\"observation\"]", obj["clear"].toString())
    }

    @Test
    fun `enums escrevem o nome exato do servidor`() {
        assertEquals("\"NOT_TESTED\"", InspectionJson.encodeToString(ItemFunctioning.serializer(), ItemFunctioning.NOT_TESTED))
        assertEquals("\"NATURAL_WEAR\"", InspectionJson.encodeToString(ItemDivergence.serializer(), ItemDivergence.NATURAL_WEAR))
        assertEquals("\"CONFORM_WITH_REMARK\"", InspectionJson.encodeToString(ItemResult.serializer(), ItemResult.CONFORM_WITH_REMARK))
        for (s in ItemState.entries.filter { it != ItemState.UNKNOWN }) {
            assertEquals(s, InspectionJson.decodeFromString(ItemState.serializer(), "\"${s.name}\""))
        }
        // UNKNOWN nunca é lido de um valor conhecido, nem do próprio nome.
        assertEquals(ItemState.UNKNOWN, InspectionJson.decodeFromString(ItemState.serializer(), "\"UNKNOWN\""))
    }

    @Test
    fun `toString nao vaza gps endereco observacao nem url`() {
        val dto = InspectionFixtures.entryDraft()
        val texto = dto.toString() + dto.groups[0].items[0].media[0] + dto.groups[0].items[1]
        assertFalse("Rua das Flores" in texto)
        assertFalse("-15.6" in texto)
        assertFalse("Mancha" in texto)
        val alvo = UploadTargetDto(url = "https://r2/x?X-Amz-Signature=segredo", expiresAt = "2099-01-01T00:00:00Z")
        assertFalse("segredo" in alvo.toString())
        assertFalse("segredo" in MediaUrlDto("https://r2/x?sig=segredo", "x").toString())
    }

    @Test
    fun `status editavel e aceita midia`() {
        assertTrue(InspectionStatus.DRAFT.isEditable)
        assertFalse(InspectionStatus.FINISHED_LOCAL.isEditable)
        assertTrue(InspectionStatus.FINISHED_LOCAL.acceptsMedia)
        assertFalse(InspectionStatus.SIGNED.acceptsMedia)
    }

    // ── Erros ────────────────────────────────────────────────────────────────────────────────────

    private fun erro(code: Int, body: String?): DomainResult.Error {
        val env = parseServerErrorEnvelope(body)
        return DomainResult.Error(code, "local", env?.code, env?.message, env?.details.orEmpty(), env?.detailsJson ?: kotlinx.serialization.json.JsonObject(emptyMap()))
    }

    @Test
    fun `422 incompleto traz as pendencias legiveis e descarta a ilegivel`() {
        val e = erro(422, InspectionFixtures.INCOMPLETE_BODY).toInspectionError()
        assertIs<InspectionError.Incomplete>(e)
        assertEquals(3, e.issues.size)
        assertEquals(FinishIssueReasons.MIN_PHOTOS, e.issues[0].reason)
        assertEquals(2, e.issues[0].required)
        assertEquals("medidor_luz", e.issues[2].headerField)
        assertEquals("Faltam itens obrigatórios ou fotos", e.userMessage)
    }

    @Test
    fun `409 tipado com o id da midia ja registrada`() {
        val e = erro(409, """{"message":"Este arquivo já está registrado","code":"MEDIA_ALREADY_REGISTERED","details":{"mediaId":"m9"}}""")
            .toInspectionError()
        assertIs<InspectionError.Conflict>(e)
        assertEquals("m9", e.mediaId)
        assertFalse(e.isNotEditable)
        assertFalse(e.isRetryable)
        val ne = erro(409, """{"message":"x","code":"INSPECTION_NOT_EDITABLE","details":{"status":"SIGNED"}}""").toInspectionError()
        assertIs<InspectionError.Conflict>(ne)
        assertTrue(ne.isNotEditable)
        assertEquals("SIGNED", ne.currentStatus)
    }

    @Test
    fun `410 404 400 429 503 offline e quota`() {
        assertIs<InspectionError.Gone>(erro(410, """{"message":"Link indisponível","code":"LINK_UNAVAILABLE"}""").toInspectionError())
        assertIs<InspectionError.NotFound>(erro(404, null).toInspectionError())
        val v = erro(400, """{"message":"Inválido","code":"MEDIA_SHA_INVALID","details":{"sha256":"SHA-256 em 64 hexadecimais"}}""").toInspectionError()
        assertIs<InspectionError.Validation>(v)
        assertEquals("SHA-256 em 64 hexadecimais", v.fieldError("sha256"))
        val rl = erro(429, """{"message":"Aguarde","code":"OTP_RESEND_TOO_SOON","details":{"blockedUntil":"2026-10-22T12:01:00Z"}}""").toInspectionError()
        assertIs<InspectionError.RateLimited>(rl)
        assertEquals("2026-10-22T12:01:00Z", rl.blockedUntil)
        assertTrue(rl.isRetryable)
        assertTrue(erro(503, null).toInspectionError().let { it is InspectionError.Unavailable && it.isRetryable })
        val off = DomainResult.Error(DomainResult.OFFLINE_CODE, "Sem conexão").toInspectionError()
        assertIs<InspectionError.Offline>(off)
        assertTrue(off.isRetryable)
        val q = DomainResult.Quota(QuotaExceeded(feature = "vistorias", limite = 5, contagem = 5)).let { it as DomainResult<String> }
            .toInspectionResult { it }
        assertIs<InspectionError.QuotaExceeded>(q.errorOrNull())
        assertEquals(402, q.errorOrNull()?.httpStatus)
    }

    @Test
    fun `2xx com corpo que nao e o DTO vira resposta ilegivel`() {
        val r = DomainResult.Success("{\"x\":1}").toInspectionResult { InspectionJson.decodeFromString(InspectionDto.serializer(), it) }
        assertIs<InspectionError.InvalidResponse>(r.errorOrNull())
        assertNull(r.getOrNull())
    }
}
