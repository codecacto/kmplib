@file:OptIn(kotlin.time.ExperimentalTime::class)

package br.com.codecacto.kmplib.fiscal

import br.com.codecacto.kmplib.sync.rest.DomainResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FiscalRulesTest {

    // Um CNPJ e um CPF válidos (dígitos verificadores certos).
    private val cnpj = "11222333000181"
    private val cpf = "52998224725"

    @Test
    fun `decimal aceita virgula e ponto e arredonda sem ponto flutuante`() {
        assertEquals("1250.00", toDecimalString("1.250,00"))
        assertEquals("1250.00", toDecimalString("1250"))
        assertEquals("1250.50", toDecimalString("1250.5"))
        assertEquals("0.13", toDecimalString("0,125"))
        assertEquals("100.00", toDecimalString("99,999"))
        assertEquals("2.0000", toDecimalString("2", 4))
        assertEquals("-3.10", toDecimalString("-3,1"))
        assertEquals("0.00", toDecimalString("-0,001"))
        assertNull(toDecimalString(""))
        assertNull(toDecimalString("abc"))
        assertNull(toDecimalString("1,2,3"))
    }

    @Test
    fun `dinheiro e aliquota formatados em pt-BR`() {
        assertEquals("R$ 1.250,00", formatFiscalMoney("1250"))
        assertEquals("R$ 1.234.567,89", formatFiscalMoney("1234567.89"))
        assertEquals("—", formatFiscalMoney(null))
        assertEquals("2,00", formatRate("2"))
        assertEquals("0,65", formatRate("0.65"))
        assertEquals("1250,00", decimalToInput("1250.00"))
    }

    @Test
    fun `valor positivo e aliquota entre zero e cem`() {
        assertTrue(isPositiveMoney("0,01"))
        assertFalse(isPositiveMoney("0"))
        assertFalse(isPositiveMoney("-1"))
        assertTrue(isValidRate("100"))
        assertTrue(isValidRate("0"))
        assertTrue(isValidRate("2,5"))
        assertFalse(isValidRate("100,01"))
        assertFalse(isValidRate("-1"))
    }

    @Test
    fun `status pendente e acoes permitidas`() {
        assertTrue(isPendingStatus(FiscalStatus.INCERTO))
        assertFalse(isPendingStatus(FiscalStatus.AUTORIZADO))
        assertTrue(canCancelInvoice(FiscalStatus.AUTORIZADO))
        assertFalse(canCancelInvoice(FiscalStatus.CANCELADO))
        assertTrue(canReconsultInvoice(FiscalStatus.ERRO_TEMPORARIO))
        assertTrue(hasInvoiceFiles(FiscalStatus.SUBSTITUIDO))
        assertEquals(FiscalStatusTone.SUCCESS, fiscalStatusTone(FiscalStatus.AUTORIZADO))
        assertEquals(FiscalStatusTone.NEUTRAL, fiscalStatusTone("NOVO_STATUS"))
    }

    @Test
    fun `etapa da pendencia pelo campo ou pelo codigo`() {
        assertEquals(FiscalSetupStep.CERTIFICADO, setupStepOf("CERTIFICADO_AUSENTE"))
        assertEquals(FiscalSetupStep.SERVICO, setupStepOf("X", "padroes.aliquotaIss"))
        assertEquals(FiscalSetupStep.EMPRESA, setupStepOf("INSCRICAO_MUNICIPAL_AUSENTE", "inscricaoMunicipal"))
        assertEquals(FiscalSetupStep.REGIME, setupStepOf("REGIME_INCOERENTE"))
    }

    @Test
    fun `regime em palavras vira os codigos do leiaute e volta`() {
        val cases = mapOf(
            RegimeApuracao.MEI to FiscalRegime(2, null, 0, RegimeApuracao.MEI),
            RegimeApuracao.SIMPLES to FiscalRegime(3, 1, 0, RegimeApuracao.SIMPLES),
            RegimeApuracao.LUCRO_REAL to FiscalRegime(1, null, 0, RegimeApuracao.LUCRO_REAL),
        )
        cases.forEach { (r, wire) ->
            assertEquals(wire, regimeToWire(r, FiscalRegimeEspecial.NENHUM))
            assertEquals(r, regimeFromWire(wire).regime)
        }
        val hib = regimeToWire(RegimeApuracao.SIMPLES_HIBRIDO, FiscalRegimeEspecial.PROFISSIONAL_AUTONOMO, FiscalSimplesHibrido.TUDO_FORA)
        assertEquals(FiscalRegime(3, 3, 5, RegimeApuracao.SIMPLES_HIBRIDO), hib)
        val back = regimeFromWire(hib)
        assertEquals(FiscalSimplesHibrido.TUDO_FORA, back.hybrid)
        assertEquals(FiscalRegimeEspecial.PROFISSIONAL_AUTONOMO, back.special)
    }

    @Test
    fun `cadastro antigo sem regimeApuracao e deduzido`() {
        assertEquals(RegimeApuracao.SIMPLES_HIBRIDO, regimeFromWire(FiscalRegime(3, 2, 0)).regime)
        assertEquals(RegimeApuracao.IMUNE_ISENTO, regimeFromWire(FiscalRegime(1), padroes = FiscalPadroes(tributacaoIss = TributacaoIss.IMUNE)).regime)
        assertEquals(RegimeApuracao.PESSOA_FISICA, regimeFromWire(FiscalRegime(1), FiscalTipoPessoa.PF).regime)
        // Presumido ou Real? O dono escolhe.
        assertNull(regimeFromWire(FiscalRegime(1), FiscalTipoPessoa.PJ).regime)
    }

    private fun empresaValida() = FiscalEmitterDraft(
        documento = cnpj,
        razaoSocial = "Locadora Bom Preço LTDA",
        telefone = "65999990000",
        endereco = FiscalAddressDraft(cep = "78000000", logradouro = "Rua A", numero = "10", bairro = "Centro", uf = "MT", cidade = "Cuiabá"),
    )

    @Test
    fun `municipio sai do estado e da cidade`() {
        assertEquals("5103403", ibgeOf("MT", "cuiaba"))
        assertNull(ibgeOf("SP", "Cuiabá"))
        assertEquals("Cuiabá", cityNameOf("5103403"))
    }

    @Test
    fun `validacao da empresa marca o campo`() {
        assertTrue(validateEmitterStep(FiscalSetupStep.EMPRESA, empresaValida()).isEmpty())
        val e = validateEmitterStep(FiscalSetupStep.EMPRESA, empresaValida().copy(documento = "11222333000100", email = "x@", endereco = FiscalAddressDraft()))
        assertEquals(FiscalFieldError.Issue(FiscalIssue.CNPJ_INVALIDO), e["documento"])
        assertEquals(FiscalFieldError.Issue(FiscalIssue.EMAIL_INVALIDO), e["email"])
        assertEquals(FiscalFieldError.Issue(FiscalIssue.MUNICIPIO_OBRIGATORIO), e["endereco.municipioIbge"])
        val pf = validateEmitterStep(FiscalSetupStep.EMPRESA, empresaValida().copy(tipoPessoa = FiscalTipoPessoa.PF, documento = cpf, numeroInicialDps = "0"))
        assertNull(pf["documento"])
        assertEquals(FiscalFieldError.Issue(FiscalIssue.DPS_INVALIDO), pf["numeroInicialDps"])
    }

    @Test
    fun `validacao do servico pede tributos aproximados fora do Simples`() {
        val d = empresaValida().copy(regime = RegimeApuracao.LUCRO_PRESUMIDO, padroes = FiscalDefaultsDraft(cstPisCofins = "77"))
        val e = validateEmitterStep(FiscalSetupStep.SERVICO, d)
        assertEquals(FiscalFieldError.Issue(FiscalIssue.OBRIGATORIO), e["padroes.codigoTributacaoNacional"])
        assertEquals(FiscalFieldError.Issue(FiscalIssue.CST_INVALIDO), e["padroes.cstPisCofins"])
        assertEquals(FiscalFieldError.Issue(FiscalIssue.TOTAIS_OBRIGATORIO), e["padroes.totaisAproximados.federal"])
        val porNota = d.copy(padroes = FiscalDefaultsDraft(codigoTributacaoNacional = "010101", cstPisCofins = "01", totaisValorPorNota = true))
        assertTrue(validateEmitterStep(FiscalSetupStep.SERVICO, porNota).isEmpty())
    }

    @Test
    fun `corpo do cadastro com regime e padroes`() {
        val d = empresaValida().copy(
            regime = RegimeApuracao.LUCRO_PRESUMIDO,
            numeroInicialDps = "000123",
            padroes = FiscalDefaultsDraft(codigoTributacaoNacional = "010101", aliquotaIss = "2,5", totaisFederal = "4,5", totaisMunicipal = "2"),
        )
        val body = toEmitterBody(d)
        assertEquals(cnpj, body.documento)
        assertEquals("65999990000", body.telefone)
        assertEquals("5103403", body.endereco.municipioIbge)
        assertEquals(123L, body.numeroInicialDps)
        assertEquals(1, body.regime.opSimpNac)
        assertEquals("2.50", body.padroes.aliquotaIss)
        assertEquals(DEFAULT_CST_PIS_COFINS, body.padroes.cstPisCofins)
        assertEquals(FiscalTotaisAproximados(TipoTotaisAproximados.PERCENTUAL, federal = "4.50", municipal = "2.00"), body.padroes.totaisAproximados)
        // MEI não leva CST e não informa tributos aproximados.
        val mei = toEmitterBody(d.copy(regime = RegimeApuracao.MEI))
        assertNull(mei.padroes.cstPisCofins)
        assertEquals(TipoTotaisAproximados.NAO_INFORMAR, mei.padroes.totaisAproximados?.tipo)
        // Imune/isento marca a tributação do ISS.
        assertEquals(TributacaoIss.IMUNE, toEmitterBody(d.copy(regime = RegimeApuracao.IMUNE_ISENTO)).padroes.tributacaoIss)
    }

    @Test
    fun `cadastro do servidor vira rascunho e volta igual`() {
        val body = toEmitterBody(empresaValida().copy(regime = RegimeApuracao.SIMPLES, padroes = FiscalDefaultsDraft(codigoTributacaoNacional = "010101", totaisSimples = "6")))
        val server = FiscalEmissor(
            ref = "u1", tipoPessoa = body.tipoPessoa, documento = body.documento, razaoSocial = body.razaoSocial, endereco = body.endereco,
            telefone = body.telefone, regime = body.regime, ambiente = body.ambiente, padroes = body.padroes,
        )
        val draft = emitterToDraft(server)
        assertEquals("Cuiabá", draft.endereco.cidade)
        assertEquals("6,00", draft.padroes.totaisSimples)
        assertEquals(body, toEmitterBody(draft))
    }

    private val emissor = FiscalEmissor(
        ref = "u1",
        padroes = FiscalPadroes(codigoTributacaoNacional = "010101", aliquotaIss = "2.00", descricaoServico = "Locação", issRetido = true),
    )

    @Test
    fun `emissao abre com os padroes e a sugestao do produto`() {
        val s = TomadorSugerido(
            tomador = NfseTomador(TipoTomador.CPF, documento = "529.982.247-25", nome = "Mariana Costa", telefone = "(65) 99999-0000"),
            descricaoServico = "Locação de betoneira",
            valorServico = "350.00",
            competencia = "2026-11-20",
        )
        val d = initialInvoiceDraft(emissor, s, "locacao:1", today = "2026-11-25")
        assertEquals("locacao:1", d.refExterna)
        assertEquals("2026-11-20", d.competencia)
        assertEquals(cpf, d.tomadorDocumento)
        assertEquals("65999990000", d.tomadorTelefone)
        assertEquals("Locação de betoneira", d.descricao)
        assertEquals("350,00", d.valorServico)
        assertEquals("2,00", d.aliquotaIss)
        assertTrue(d.issRetido)
        assertTrue(validateInvoiceDraft(d).isEmpty())
    }

    @Test
    fun `sugestao com CNPJ troca o tipo do tomador`() {
        val d = initialInvoiceDraft(emissor, TomadorSugerido(NfseTomador(TipoTomador.CPF, documento = cnpj, nome = "Obra SA")), today = "2026-11-25")
        assertEquals(TipoTomador.CNPJ, d.tipoTomador)
    }

    @Test
    fun `pedido validado e montado com decimal de ponto`() {
        val base = initialInvoiceDraft(emissor, null, today = "2026-11-25")
        val e = validateInvoiceDraft(base.copy(tomadorDocumento = "123", valorServico = "0", aliquotaIss = "200"))
        assertEquals(FiscalFieldError.Issue(FiscalIssue.CPF_INVALIDO), e["tomador.documento"])
        assertEquals(FiscalFieldError.Issue(FiscalIssue.OBRIGATORIO), e["tomador.nome"])
        assertEquals(FiscalFieldError.Issue(FiscalIssue.VALOR_INVALIDO), e["valores.servico"])
        assertEquals(FiscalFieldError.Issue(FiscalIssue.ALIQUOTA_INVALIDA), e["valores.iss.aliquota"])
        assertTrue(isAdvancedInvoiceField("valores.iss.aliquota"))

        val body = toInvoiceBody(base.copy(tomadorDocumento = cpf, tomadorNome = "Mariana", valorServico = "1.250,5", irrf = "15"))
        assertEquals("1250.50", body.valores.servico)
        assertEquals("15.00", body.valores.federais?.irrf)
        assertNull(body.valores.federais?.pis)
        assertEquals(cpf, body.tomador?.documento)
        assertNull(body.tomador?.endereco)
        assertNull(body.valores.totaisAproximados)
    }

    @Test
    fun `emissor em valor informado pede o valor em cada nota`() {
        val e = emissor.copy(padroes = emissor.padroes!!.copy(totaisAproximados = FiscalTotaisAproximados(TipoTotaisAproximados.VALOR_INFORMADO)))
        val d = initialInvoiceDraft(e, null, today = "2026-11-25").copy(tipoTomador = TipoTomador.NAO_IDENTIFICADO, valorServico = "100")
        assertEquals(FiscalFieldError.Issue(FiscalIssue.TOTAIS_VALOR_OBRIGATORIO), validateInvoiceDraft(d)["valores.totaisAproximados.federal"])
        val body = toInvoiceBody(d.copy(totaisFederal = "12,3"))
        assertEquals(FiscalTotaisAproximados(TipoTotaisAproximados.VALOR, federal = "12.30"), body.valores.totaisAproximados)
        assertEquals(TipoTomador.NAO_IDENTIFICADO, body.tomador?.tipo)
    }

    @Test
    fun `substituta parte do resumo da nota`() {
        val inv = FiscalDocumento(
            id = "n1", status = FiscalStatus.AUTORIZADO, refExterna = "locacao:9",
            valores = FiscalValoresResumo(servico = "500.00", tomadorNome = "Obra SA", tomadorDocumento = cnpj),
        )
        val d = substituteDraft(inv, emissor, today = "2026-11-25")
        assertEquals("locacao:9", d.refExterna)
        assertEquals(TipoTomador.CNPJ, d.tipoTomador)
        assertEquals("500,00", d.valorServico)
    }

    @Test
    fun `mensagens com campo viram erro por campo`() {
        val f = fieldsOfMessages(listOf(FiscalMensagem("E1", "CPF inválido", "tomador.documento"), FiscalMensagem("E2", "outro", "tomador.documento"), FiscalMensagem("E3", "geral")))
        assertEquals(mapOf<String, FiscalFieldError>("tomador.documento" to FiscalFieldError.Text("CPF inválido (E1)")), f)
    }

    @Test
    fun `certificado e arquivo`() {
        assertTrue(isCertificateFile("empresa.PFX"))
        assertTrue(isCertificateFile("a.p12"))
        assertFalse(isCertificateFile("a.pdf"))
        val now = 1_700_000_000_000L
        assertEquals(10L, certificateDaysLeft(kotlin.time.Instant.fromEpochMilliseconds(now + 10 * 86_400_000L + 1000).toString(), now))
        assertEquals(-1L, certificateDaysLeft(kotlin.time.Instant.fromEpochMilliseconds(now - 1000).toString(), now))
        assertNull(certificateDaysLeft("lixo", now))
    }

    @Test
    fun `documento formatado inteiro e nome de arquivo`() {
        assertEquals("529.982.247-25", formatDocument(cpf))
        assertEquals("11.222.333/0001-81", formatDocument(cnpj))
        assertEquals("nfse-123.pdf", invoiceFileName("id", "12-3", "pdf"))
        assertEquals("nfse-id.xml", invoiceFileName("id", null, "xml"))
        assertEquals("25/11/2026", formatFiscalDate("2026-11-25"))
    }

    @Test
    fun `aliquota sugerida do municipio`() {
        val p = FiscalParametrosMunicipais("5103403", conveniado = true, aliquotas = listOf(FiscalAliquotaMunicipal("010101", "3")))
        assertEquals("3.00", suggestedRate(p, "010101"))
        assertNull(suggestedRate(p, "020202"))
    }

    @Test
    fun `erro do cliente vira o tipo da tela`() {
        assertEquals(FiscalErrorKind.DISABLED, fiscalErrorOf(DomainResult.Error(404, "x", serverCode = "NOT_FOUND")).kind)
        assertEquals(FiscalErrorKind.NOT_FOUND, fiscalErrorOf(DomainResult.Error(404, "x", serverCode = FiscalErrorCodes.NOTA_NAO_ENCONTRADA)).kind)
        assertEquals(FiscalErrorKind.UNCERTAIN, fiscalErrorOf(DomainResult.Error(503, "x", serverCode = FiscalErrorCodes.RESULTADO_INCERTO)).kind)
        assertEquals(FiscalErrorKind.UNAVAILABLE, fiscalErrorOf(DomainResult.Error(503, "x")).kind)
        val f = fiscalErrorOf(DomainResult.Error(400, "x", serverMessage = "Dados inválidos", details = mapOf("documento" to "CNPJ inválido")))
        assertEquals(FiscalErrorKind.FIELDS, f.kind)
        assertEquals("CNPJ inválido", f.fields["documento"])
        val icp = fiscalErrorOf(DomainResult.Error(400, "x", serverCode = "FISCAL_CERTIFICADO_FORA_DA_ICP_BRASIL", serverMessage = "fora"))
        assertIs<FiscalError>(icp)
        assertEquals("fora", icp.fields["arquivo"])
        assertEquals(FiscalErrorKind.NETWORK, fiscalErrorOf(DomainResult.Error(DomainResult.OFFLINE_CODE, "x")).kind)
    }
}
