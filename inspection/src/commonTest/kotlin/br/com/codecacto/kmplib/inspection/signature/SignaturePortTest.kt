package br.com.codecacto.kmplib.inspection.signature

import br.com.codecacto.kmplib.inspection.capture.InspectionGeo
import br.com.codecacto.kmplib.inspection.data.FakeInspectionServer
import br.com.codecacto.kmplib.inspection.data.InspectionError
import br.com.codecacto.kmplib.inspection.data.InspectionFixtures
import br.com.codecacto.kmplib.inspection.data.InspectionJson
import br.com.codecacto.kmplib.inspection.data.InspectionResult
import br.com.codecacto.kmplib.inspection.data.OtpChannelKind
import br.com.codecacto.kmplib.inspection.data.Reply
import br.com.codecacto.kmplib.inspection.data.SignatureDto
import br.com.codecacto.kmplib.inspection.data.SignerDto
import br.com.codecacto.kmplib.inspection.data.SignerRole
import br.com.codecacto.kmplib.inspection.model.OtpChannel
import br.com.codecacto.kmplib.inspection.model.SignatureState
import br.com.codecacto.kmplib.inspection.model.SignatureStatus
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignaturePortTest {

    private val id = InspectionFixtures.INSPECTION_ID
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64) { it.toByte() }

    private val assinatura = """{"id":"sig1","signerId":"s1","role":"LOCATARIO","name":"Mariana Costa","method":"PRESENCIAL","otpChannel":"SMS",
        "signedAt":"2026-10-22T13:58:00Z","signedHash":"cccc","hasTrace":true}"""

    @Test
    fun `envia o codigo pelo canal escolhido`() = runTest {
        val s = FakeInspectionServer()
        s.on("POST", "/inspections/$id/signatures/otp", Reply(body = """{"channel":"WHATSAPP","destination":"(65) *****-0240","expiresAt":"2026-10-22T14:10:00Z","resendAfterSeconds":60}"""))
        val r = RestSignaturePort(s.api(), id).sendCode("s1", OtpChannel.WHATSAPP)
        val sent = assertIs<InspectionResult.Ok<OtpSentDto>>(r).value
        assertEquals(OtpChannelKind.WHATSAPP, sent.channel)
        assertFalse("0240" in sent.toString(), "destino fora do log")
        assertEquals("""{"signerId":"s1","channel":"WHATSAPP"}""", s.received.single().body)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `assina com codigo normalizado traco em base64 e gps`() = runTest {
        val s = FakeInspectionServer()
        s.on("POST", "/inspections/$id/signatures", Reply(201, assinatura))
        val r = RestSignaturePort(s.api(), id).sign(SignatureRequest("s1", " 123-456 ", accepted = true, strokePng = png, location = InspectionGeo(-15.6, -56.1, 5f)))
        assertIs<InspectionResult.Ok<SignatureDto>>(r)
        val enviado = InspectionJson.decodeFromString(SignInput.serializer(), s.received.single().body)
        assertEquals("123456", enviado.otp)
        assertTrue(enviado.accepted)
        assertEquals(-15.6, enviado.lat)
        assertEquals(5.0, enviado.accuracyM)
        assertTrue(Base64.Default.decode(enviado.tracePngBase64!!).contentEquals(png))
        assertFalse("123456" in enviado.toString(), "código fora do log")
        assertFalse(enviado.tracePngBase64 in enviado.toString())
    }

    @Test
    fun `vistoriador sem codigo vai sem otp`() = runTest {
        val s = FakeInspectionServer()
        s.on("POST", "/inspections/$id/signatures", Reply(201, assinatura))
        RestSignaturePort(s.api(), id).sign(SignatureRequest("s1", code = null, accepted = true))
        assertNull(InspectionJson.decodeFromString(SignInput.serializer(), s.received.single().body).otp)
    }

    @Test
    fun `forma invalida e recusada sem gastar tentativa no servidor`() = runTest {
        val s = FakeInspectionServer()
        val port = RestSignaturePort(s.api(), id)
        assertEquals(SignatureErrorCodes.TERMS_NOT_ACCEPTED, port.sign(SignatureRequest("s1", "123456", accepted = false)).errorOrNull()?.serverCode)
        assertEquals(SignatureErrorCodes.OTP_INVALID, port.sign(SignatureRequest("s1", "12345", accepted = true)).errorOrNull()?.serverCode)
        assertEquals(SignatureErrorCodes.OTP_INVALID, port.sign(SignatureRequest("s1", "12a456", accepted = true)).errorOrNull()?.serverCode)
        assertEquals(SignatureErrorCodes.TRACE_INVALID, port.sign(SignatureRequest("s1", "123456", true, strokePng = byteArrayOf(1, 2, 3))).errorOrNull()?.serverCode)
        val grande = png + ByteArray(RestSignaturePort.MAX_TRACE_BYTES)
        assertEquals(SignatureErrorCodes.TRACE_INVALID, port.sign(SignatureRequest("s1", "123456", true, strokePng = grande)).errorOrNull()?.serverCode)
        assertTrue(s.received.isEmpty())
    }

    @Test
    fun `teto do traco cabe no corpo da rota de 256 KB em base64`() {
        val base64 = (RestSignaturePort.MAX_TRACE_BYTES + 2) / 3 * 4
        assertTrue(base64 + 1024 < 256 * 1024, "base64=$base64")
    }

    @Test
    fun `classifica as recusas do servidor`() = runTest {
        val s = FakeInspectionServer()
        s.onSeq(
            "POST", "/inspections/$id/signatures",
            Reply(422, """{"message":"Código incorreto","code":"OTP_INVALID","details":{"attemptsLeft":"2"}}"""),
            Reply(422, """{"message":"Código vencido; peça outro","code":"OTP_EXPIRED"}"""),
            Reply(409, """{"message":"Esta parte já assinou esta versão","code":"ALREADY_SIGNED"}"""),
            Reply(422, """{"message":"Limite","code":"OTP_EXHAUSTED"}"""),
            Reply(offline = true),
        )
        val port = RestSignaturePort(s.api(), id)
        suspend fun falha() = port.sign(SignatureRequest("s1", "123456", true)).errorOrNull()!!.toSignatureFailure()
        assertEquals(SignatureFailure.InvalidCode(2, "Código incorreto"), falha())
        assertEquals(SignatureFailure.Expired, falha())
        assertEquals(SignatureFailure.AlreadySigned, falha())
        assertEquals(SignatureFailure.Exhausted, falha())
        assertEquals(SignatureFailure.Offline, falha())
        val rl = InspectionError.RateLimited("OTP_RESEND_TOO_SOON", "Aguarde", "2026-10-22T14:01:00Z", 30)
        assertEquals(SignatureFailure.ResendTooSoon("2026-10-22T14:01:00Z", 30), rl.toSignatureFailure())
        assertEquals(SignatureFailure.CannotSend("OTP_DELIVERY_FAILED"), InspectionError.Unavailable("OTP_DELIVERY_FAILED", "x").toSignatureFailure())
    }

    @Test
    fun `estado da tela nunca vira assinado sem o servidor`() {
        val signer = SignerDto("s1", SignerRole.LOCATARIO, "Mariana Costa")
        var st: SignatureState = SignatureStateMapping.initial(signer, "Locatária", "Li o laudo e concordo.")
        assertEquals(SignatureStatus.PENDING, st.status)
        st = SignatureStateMapping.codeSent(st, OtpSentDto(OtpChannelKind.EMAIL, "m***@g***.com", "2026-10-22T14:10:00Z", 45))
        assertEquals(SignatureStatus.AWAITING_CODE, st.status)
        assertEquals(OtpChannel.EMAIL, st.channel)
        assertEquals(45, st.resendInSeconds)
        st = SignatureStateMapping.submitting(st.copy(code = "111111"))
        assertTrue(st.isSubmitting)
        st = SignatureStateMapping.failed(st, SignatureFailure.InvalidCode(2, "Código incorreto"))
        assertEquals("", st.code)
        assertEquals(2, st.attemptsLeft)
        st = SignatureStateMapping.failed(st, SignatureFailure.Offline)
        assertTrue(st.offline)
        assertEquals(SignatureStatus.AWAITING_CODE, st.status)
        st = SignatureStateMapping.failed(st, SignatureFailure.Expired)
        assertTrue(st.codeExpired)
        st = SignatureStateMapping.signed(st, InspectionJson.decodeFromString(SignatureDto.serializer(), assinatura), "s1")
        assertEquals(SignatureStatus.SIGNED, st.status)
        assertFalse(st.isSubmitting)
    }

    @Test
    fun `canais vao e voltam`() {
        OtpChannel.entries.forEach { assertEquals(it, it.toKind().toChannel()) }
        assertNull(OtpChannelKind.NONE.toChannel())
    }

    // ── Revisão de segurança (2.321.0) ──────────────────────────────────────────────────────────

    private fun aguardando(): SignatureState = SignatureStateMapping.codeSent(
        SignatureStateMapping.initial(SignerDto("s1", SignerRole.LOCATARIO, "Mariana Costa"), "Locatária", "Li e concordo."),
        OtpSentDto(OtpChannelKind.SMS, "x", "2026-10-22T14:10:00Z", 60),
    )

    @Test
    fun `codigo ja usado e laudo mudou nao inventam tentativas e mostram a frase do servidor`() = runTest {
        val s = FakeInspectionServer()
        s.onSeq(
            "POST", "/inspections/$id/signatures",
            Reply(422, """{"message":"Código já usado; peça outro","code":"OTP_INVALID"}"""),
            Reply(422, """{"message":"O laudo mudou; peça um código novo","code":"OTP_INVALID"}"""),
        )
        val port = RestSignaturePort(s.api(), id)
        for (frase in listOf("Código já usado; peça outro", "O laudo mudou; peça um código novo")) {
            val f = port.sign(SignatureRequest("s1", "123456", true)).errorOrNull()!!.toSignatureFailure()
            assertEquals(SignatureFailure.InvalidCode(null, frase), f)
            val st = SignatureStateMapping.failed(aguardando().copy(code = "123456"), f)
            assertNull(st.attemptsLeft, "sem contagem do servidor não existe '0 tentativas'")
            assertEquals(frase, st.errorMessage)
            assertEquals("", st.code)
        }
    }

    @Test
    fun `recusa local de formato vira mensagem sem contagem`() = runTest {
        val s = FakeInspectionServer()
        val f = RestSignaturePort(s.api(), id).sign(SignatureRequest("s1", "12a", true)).errorOrNull()!!.toSignatureFailure()
        assertEquals(SignatureFailure.InvalidCode(null, "O código tem 6 dígitos."), f)
        val st = SignatureStateMapping.failed(aguardando(), f)
        assertNull(st.attemptsLeft)
        assertEquals("O código tem 6 dígitos.", st.errorMessage)
        assertTrue(s.received.isEmpty())
        // Com contagem, a frase não aparece (o kit mostra "restam N").
        val com = SignatureStateMapping.failed(aguardando(), SignatureFailure.InvalidCode(2, "Código incorreto"))
        assertEquals(2, com.attemptsLeft)
        assertNull(com.errorMessage)
    }

    @Test
    fun `assinado so com a resposta desta parte e com prova`() {
        val boa = InspectionJson.decodeFromString(SignatureDto.serializer(), assinatura)
        val base = SignatureStateMapping.submitting(aguardando())
        assertEquals(SignatureStatus.SIGNED, SignatureStateMapping.signed(base, boa, "s1").status)
        for ((dto, esperado) in listOf(
            boa.copy(signerId = "s2") to "s1",
            boa.copy(signedHash = " ") to "s1",
            boa.copy(signedAt = "") to "s1",
            boa to "",
        )) {
            val st = SignatureStateMapping.signed(base, dto, esperado)
            assertEquals(SignatureStatus.AWAITING_CODE, st.status)
            assertFalse(st.isSubmitting)
            assertEquals(InspectionError.INVALID_RESPONSE_MESSAGE, st.errorMessage)
        }
    }

    @Test
    fun `429 do reenvio le o Retry-After e ajusta a contagem`() = runTest {
        val s = FakeInspectionServer()
        s.on(
            "POST", "/inspections/$id/signatures/otp",
            Reply(429, """{"message":"Aguarde para pedir outro código","code":"OTP_RESEND_TOO_SOON"}""", headers = mapOf("Retry-After" to "37")),
        )
        val f = RestSignaturePort(s.api(), id).sendCode("s1").errorOrNull()!!.toSignatureFailure()
        assertEquals(SignatureFailure.ResendTooSoon(null, 37), f)
        assertEquals(37, SignatureStateMapping.failed(aguardando(), f).resendInSeconds)
        val muitos = SignatureStateMapping.failed(aguardando(), SignatureFailure.TooManyCodes(1200))
        assertEquals(1200, muitos.resendInSeconds)
        assertEquals(60, SignatureStateMapping.failed(aguardando(), SignatureFailure.TooManyCodes(null)).resendInSeconds)
    }

    @Test
    fun `parte em branco e 404 como no servidor`() = runTest {
        val port = RestSignaturePort(FakeInspectionServer().api(), id)
        for (e in listOf(port.sendCode(" ").errorOrNull(), port.sign(SignatureRequest("", "123456", true)).errorOrNull())) {
            assertIs<InspectionError.NotFound>(e)
            assertEquals(404, e.httpStatus)
            assertEquals(SignatureErrorCodes.SIGNER_NOT_FOUND, e.serverCode)
        }
    }
}
