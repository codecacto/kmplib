package br.com.codecacto.kmplib.fiscal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dublê do repositório: respostas programáveis e o registro do que a tela pediu. */
private class FakeFiscalRepository : FiscalRepository {
    var statusResult: FiscalResult<FiscalStatusResposta> = FiscalResult.Ok(FiscalStatusResposta(emissor = FiscalEmissor("u1", pronto = true), pronto = true))
    var emitterResult: FiscalResult<FiscalEmissor?> = FiscalResult.Ok(null)
    var saveResult: (FiscalEmissorInput) -> FiscalResult<FiscalEmissor> = { FiscalResult.Ok(FiscalEmissor("u1", razaoSocial = it.razaoSocial, documento = it.documento, regime = it.regime, endereco = it.endereco)) }
    var issueResults = ArrayDeque<FiscalResult<FiscalDocumentoAceito>>()
    var invoiceResult: (String) -> FiscalResult<FiscalDocumento> = { FiscalResult.Ok(FiscalDocumento(it, status = FiscalStatus.AUTORIZADO, numeroNfse = "7")) }
    var invoicesResult: (FiscalDocumentoFiltro) -> FiscalResult<FiscalDocumentosPagina> = { FiscalResult.Ok(FiscalDocumentosPagina(listOf(FiscalDocumento("a", status = FiscalStatus.AUTORIZADO)))) }
    var suggestionResult: FiscalResult<TomadorSugerido?> = FiscalResult.Ok(null)
    var cancelResult: FiscalResult<FiscalDocumento> = FiscalResult.Ok(FiscalDocumento("d1", status = FiscalStatus.CANCELADO))

    val saved = mutableListOf<FiscalEmissorInput>()
    val issuedKeys = mutableListOf<String>()
    val filters = mutableListOf<FiscalDocumentoFiltro>()
    val uploads = mutableListOf<Pair<String, String>>()
    var cancelled: FiscalCancelamento? = null

    override suspend fun status() = statusResult
    override suspend fun emitter() = emitterResult
    override suspend fun saveEmitter(input: FiscalEmissorInput): FiscalResult<FiscalEmissor> { saved += input; return saveResult(input) }
    override suspend fun uploadCertificate(bytes: ByteArray, fileName: String, password: String): FiscalResult<FiscalEmissor> {
        uploads += fileName to password
        return FiscalResult.Ok(FiscalEmissor("u1", certificado = FiscalCertificado(titularNome = "X"), pronto = true))
    }
    override suspend fun removeCertificate() = FiscalResult.Ok(Unit)
    override suspend fun municipalParameters(ibge: String) = FiscalResult.Ok(FiscalParametrosMunicipais(ibge, conveniado = true))
    override suspend fun searchServices(q: String) = FiscalResult.Ok(listOf(FiscalCatalogoItem("010101", "Locação")))
    override suspend fun preview(pedido: NfsePedido) = FiscalResult.Ok(NfsePrevia(valido = true, valores = NfseValoresCalculados(servico = pedido.valores.servico)))
    override suspend fun issue(pedido: NfsePedido, idempotencyKey: String): FiscalResult<FiscalDocumentoAceito> {
        issuedKeys += idempotencyKey
        return issueResults.removeFirstOrNull() ?: FiscalResult.Ok(FiscalDocumentoAceito("d1"))
    }
    override suspend fun invoices(filter: FiscalDocumentoFiltro): FiscalResult<FiscalDocumentosPagina> { filters += filter; return invoicesResult(filter) }
    override suspend fun invoice(id: String) = invoiceResult(id)
    override suspend fun cancel(id: String, input: FiscalCancelamento): FiscalResult<FiscalDocumento> { cancelled = input; return cancelResult }
    override suspend fun substitute(id: String, input: FiscalSubstituicao, idempotencyKey: String) = FiscalResult.Ok(FiscalDocumentoAceito("d2"))
    override suspend fun reconsult(id: String) = invoiceResult(id)
    override suspend fun pdf(id: String, numeroNfse: String?) = FiscalResult.Ok(FiscalArquivo(byteArrayOf(1), "nfse.pdf", "application/pdf"))
    override suspend fun xml(id: String, numeroNfse: String?) = FiscalResult.Ok(FiscalArquivo(byteArrayOf(1), "nfse.xml", "application/xml"))
    override suspend fun suggestion(ref: String) = suggestionResult
}

class FiscalViewModelsTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val cpf = "52998224725"

    private fun issueVm(repo: FakeFiscalRepository, ref: String? = null, substitute: String? = null): FiscalIssueViewModel {
        var n = 0
        return FiscalIssueViewModel(repo, ref, substitute, pollIntervalMillis = 0, newKey = { "chave-${++n}" })
    }

    private fun FiscalIssueViewModel.fillValid() = onAction(
        FiscalIssueAction.Edit { it.copy(tomadorDocumento = cpf, tomadorNome = "Mariana", codigoTributacaoNacional = "010101", descricao = "Locação", valorServico = "100") },
    )

    @Test
    fun `emissao abre com a sugestao da locacao`() {
        val repo = FakeFiscalRepository().apply {
            suggestionResult = FiscalResult.Ok(TomadorSugerido(NfseTomador(TipoTomador.CPF, documento = cpf, nome = "Mariana"), valorServico = "350.00"))
        }
        val vm = issueVm(repo, ref = "locacao:1")
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals("Mariana", s.draft.tomadorNome)
        assertEquals("350,00", s.draft.valorServico)
        assertEquals("locacao:1", s.draft.refExterna)
        assertFalse(s.dirty)
    }

    @Test
    fun `emissao invalida nao chama o servidor e marca o campo`() {
        val repo = FakeFiscalRepository()
        val vm = issueVm(repo)
        vm.onAction(FiscalIssueAction.Submit)
        assertTrue(repo.issuedKeys.isEmpty())
        assertEquals(FiscalFieldError.Issue(FiscalIssue.VALOR_INVALIDO), vm.state.value.errors["valores.servico"])
        assertTrue(vm.state.value.dirty.not())
    }

    @Test
    fun `reenvio depois de falha usa a MESMA chave e a correcao de recusa usa outra`() {
        val repo = FakeFiscalRepository().apply {
            issueResults.add(FiscalResult.Failure(FiscalError(FiscalErrorKind.UNCERTAIN, FiscalErrorCodes.RESULTADO_INCERTO)))
        }
        repo.invoiceResult = { FiscalResult.Ok(FiscalDocumento(it, status = FiscalStatus.REJEITADO)) }
        val vm = issueVm(repo)
        vm.fillValid()
        assertTrue(vm.state.value.dirty)
        vm.onAction(FiscalIssueAction.Submit)
        assertEquals(FiscalErrorKind.UNCERTAIN, vm.state.value.banner?.kind)
        vm.onAction(FiscalIssueAction.Submit)
        assertEquals(listOf("chave-1", "chave-1"), repo.issuedKeys)
        val phase = vm.state.value.phase
        assertIs<FiscalIssuePhase.Progress>(phase)
        assertEquals(FiscalStatus.REJEITADO, phase.invoice?.status)
        assertFalse(vm.state.value.dirty)

        vm.onAction(FiscalIssueAction.FixAfterRejection)
        assertEquals(FiscalIssuePhase.Form, vm.state.value.phase)
        vm.onAction(FiscalIssueAction.Submit)
        assertEquals("chave-2", repo.issuedKeys.last())
    }

    @Test
    fun `recusa com campo do servidor fica no campo e abre o avancado`() {
        val repo = FakeFiscalRepository().apply {
            issueResults.add(FiscalResult.Failure(FiscalError(FiscalErrorKind.FIELDS, fields = mapOf("valores.iss.aliquota" to "Alíquota fora da faixa"))))
        }
        val vm = issueVm(repo)
        vm.fillValid()
        vm.onAction(FiscalIssueAction.Submit)
        assertEquals(FiscalFieldError.Text("Alíquota fora da faixa"), vm.state.value.errors["valores.iss.aliquota"])
        assertTrue(vm.state.value.advancedOpen)
        // Digitar no campo limpa o erro dele.
        vm.onAction(FiscalIssueAction.Edit { it.copy(aliquotaIss = "2") })
        assertNull(vm.state.value.errors["valores.iss.aliquota"])
    }

    @Test
    fun `previa guarda os valores calculados`() {
        val vm = issueVm(FakeFiscalRepository())
        vm.fillValid()
        vm.onAction(FiscalIssueAction.Preview)
        assertEquals("100.00", vm.state.value.preview?.valores?.servico)
    }

    @Test
    fun `modulo desligado vira erro de carga`() {
        val repo = FakeFiscalRepository().apply { statusResult = FiscalResult.Ok(FiscalStatusResposta.DESABILITADO) }
        assertEquals(FiscalErrorKind.DISABLED, issueVm(repo).state.value.loadError?.kind)
    }

    @Test
    fun `substituicao exige o motivo`() {
        val repo = FakeFiscalRepository()
        repo.invoiceResult = { FiscalDocumento(it, status = FiscalStatus.AUTORIZADO, valores = FiscalValoresResumo(servico = "80.00", tomadorDocumento = cpf, tomadorNome = "M")).let { d -> FiscalResult.Ok(d) } }
        val vm = issueVm(repo, substitute = "n1")
        assertTrue(vm.state.value.isSubstitution)
        assertEquals("80,00", vm.state.value.draft.valorServico)
        vm.onAction(FiscalIssueAction.Edit { it.copy(codigoTributacaoNacional = "010101", descricao = "x") })
        vm.onAction(FiscalIssueAction.Submit)
        assertEquals(FiscalFieldError.Issue(FiscalIssue.OBRIGATORIO), vm.state.value.errors[FiscalIssueViewModel.REASON_FIELD])
        vm.onAction(FiscalIssueAction.SetReasonText("Valor errado"))
        vm.onAction(FiscalIssueAction.Submit)
        assertIs<FiscalIssuePhase.Progress>(vm.state.value.phase)
    }

    @Test
    fun `cadastro novo abre na empresa e salva so com a etapa valida`() {
        val repo = FakeFiscalRepository()
        val vm = FiscalSettingsViewModel(repo, searchDebounceMillis = 0)
        assertEquals(FiscalSetupStep.EMPRESA, vm.state.value.step)
        vm.onAction(FiscalSettingsAction.SaveStep)
        assertTrue(repo.saved.isEmpty())
        assertEquals(FiscalFieldError.Issue(FiscalIssue.OBRIGATORIO), vm.state.value.errors["documento"])

        vm.onAction(
            FiscalSettingsAction.Edit {
                it.copy(
                    documento = "11222333000181", razaoSocial = "Locadora LTDA",
                    endereco = FiscalAddressDraft(cep = "78000000", logradouro = "Rua A", numero = "1", bairro = "Centro", uf = "MT", cidade = "Cuiabá"),
                )
            },
        )
        assertTrue(vm.state.value.dirty)
        vm.onAction(FiscalSettingsAction.SaveStep)
        assertEquals(1, repo.saved.size)
        assertEquals("5103403", repo.saved.single().endereco.municipioIbge)
        assertEquals(FiscalSetupStep.REGIME, vm.state.value.step)
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun `recusa do servidor leva a etapa do campo`() {
        val repo = FakeFiscalRepository().apply {
            emitterResult = FiscalResult.Ok(FiscalEmissor("u1", tipoPessoa = FiscalTipoPessoa.PJ, documento = "11222333000181", razaoSocial = "L", regime = FiscalRegime(3, 1, 0, RegimeApuracao.SIMPLES),
                endereco = FiscalEndereco(cep = "78000000", logradouro = "R", numero = "1", bairro = "C", municipioIbge = "5103403", uf = "MT"),
                padroes = FiscalPadroes(codigoTributacaoNacional = "010101")))
            saveResult = { FiscalResult.Failure(FiscalError(FiscalErrorKind.FIELDS, fields = mapOf("inscricaoMunicipal" to "Obrigatória neste município"))) }
        }
        val vm = FiscalSettingsViewModel(repo)
        vm.onAction(FiscalSettingsAction.GoToStep(FiscalSetupStep.SERVICO))
        vm.onAction(FiscalSettingsAction.SaveStep)
        assertEquals(FiscalSetupStep.EMPRESA, vm.state.value.step)
        assertEquals(FiscalFieldError.Text("Obrigatória neste município"), vm.state.value.errors["inscricaoMunicipal"])
    }

    @Test
    fun `certificado exige arquivo e senha e envia em seguida`() {
        val repo = FakeFiscalRepository().apply { emitterResult = FiscalResult.Ok(FiscalEmissor("u1")) }
        val vm = FiscalSettingsViewModel(repo)
        vm.onAction(FiscalSettingsAction.GoToStep(FiscalSetupStep.CERTIFICADO))
        vm.onAction(FiscalSettingsAction.UploadCertificate)
        assertEquals(FiscalFieldError.Issue(FiscalIssue.CERTIFICADO_OBRIGATORIO), vm.state.value.errors["arquivo"])
        vm.onAction(FiscalSettingsAction.CertificatePicked("empresa.pfx", byteArrayOf(1, 2)))
        vm.onAction(FiscalSettingsAction.SetCertificatePassword("segredo"))
        assertTrue(vm.state.value.dirty)
        vm.onAction(FiscalSettingsAction.SaveStep)
        assertEquals(listOf("empresa.pfx" to "segredo"), repo.uploads)
        assertEquals("", vm.state.value.certificatePassword)
        assertEquals(FiscalSetupStep.AMBIENTE, vm.state.value.step)
        assertFalse(vm.state.value.toString().contains("segredo"))
    }

    @Test
    fun `passar a valer grava o ambiente de producao`() {
        val repo = FakeFiscalRepository().apply {
            emitterResult = FiscalResult.Ok(FiscalEmissor("u1", ambiente = FiscalAmbiente.PRODUCAO_RESTRITA))
        }
        val vm = FiscalSettingsViewModel(repo)
        vm.onAction(FiscalSettingsAction.AskProduction)
        assertTrue(vm.state.value.confirmProduction)
        vm.onAction(FiscalSettingsAction.ConfirmProduction)
        assertEquals(FiscalAmbiente.PRODUCAO, repo.saved.single().ambiente)
        assertFalse(vm.state.value.confirmProduction)
    }

    @Test
    fun `busca de servico escolhe o codigo`() {
        val vm = FiscalSettingsViewModel(FakeFiscalRepository(), searchDebounceMillis = 0)
        vm.onAction(FiscalSettingsAction.SearchServices("loca"))
        assertEquals("010101", vm.state.value.serviceResults.single().codigo)
        vm.onAction(FiscalSettingsAction.PickService(vm.state.value.serviceResults.single()))
        assertEquals("010101", vm.state.value.draft.padroes.codigoTributacaoNacional)
        assertTrue(vm.state.value.serviceResults.isEmpty())
    }

    @Test
    fun `lista busca filtra e pagina`() {
        val repo = FakeFiscalRepository()
        repo.invoicesResult = { f -> FiscalResult.Ok(FiscalDocumentosPagina(listOf(FiscalDocumento("n${f.pagina}", status = FiscalStatus.AUTORIZADO)), pagina = f.pagina, temMais = f.pagina < 2)) }
        val vm = FiscalInvoiceListViewModel(repo, ref = "locacao:1", searchDebounceMillis = 0)
        vm.onAction(FiscalInvoiceListAction.Load)
        assertEquals(listOf("n1"), vm.state.value.items.map { it.id })
        assertTrue(vm.state.value.hasMore)
        vm.onAction(FiscalInvoiceListAction.LoadMore)
        assertEquals(listOf("n1", "n2"), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.hasMore)
        vm.onAction(FiscalInvoiceListAction.Search("mari"))
        vm.onAction(FiscalInvoiceListAction.FilterStatus(FiscalStatus.CANCELADO))
        val last = repo.filters.last()
        assertEquals("mari", last.q)
        assertEquals(FiscalStatus.CANCELADO, last.status)
        assertEquals("locacao:1", last.ref)
        assertEquals(1, last.pagina)
    }

    @Test
    fun `lista mostra o erro e o refresh mantem os itens`() {
        val repo = FakeFiscalRepository()
        val vm = FiscalInvoiceListViewModel(repo)
        vm.onAction(FiscalInvoiceListAction.Load)
        repo.invoicesResult = { FiscalResult.Failure(FiscalError(FiscalErrorKind.NETWORK)) }
        vm.onAction(FiscalInvoiceListAction.Refresh)
        assertEquals(1, vm.state.value.items.size)
        assertEquals(FiscalErrorKind.NETWORK, vm.state.value.error?.kind)
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `detalhe cancela com justificativa de 15 caracteres`() {
        val repo = FakeFiscalRepository()
        val vm = FiscalInvoiceDetailViewModel(repo, "d1", pollIntervalMillis = 0)
        assertEquals("7", vm.state.value.invoice?.numeroNfse)
        vm.onAction(FiscalInvoiceDetailAction.AskCancel)
        vm.onAction(FiscalInvoiceDetailAction.SetCancelText("curto"))
        vm.onAction(FiscalInvoiceDetailAction.ConfirmCancel)
        assertNull(repo.cancelled)
        assertEquals(FiscalFieldError.Issue(FiscalIssue.DESCRICAO_CURTA), vm.state.value.cancelError)
        vm.onAction(FiscalInvoiceDetailAction.SetCancelReason(MotivoCancelamento.SERVICO_NAO_PRESTADO))
        vm.onAction(FiscalInvoiceDetailAction.SetCancelText("Cliente desistiu da locação"))
        vm.onAction(FiscalInvoiceDetailAction.ConfirmCancel)
        assertEquals(FiscalCancelamento(MotivoCancelamento.SERVICO_NAO_PRESTADO, "Cliente desistiu da locação"), repo.cancelled)
        assertEquals(FiscalStatus.CANCELADO, vm.state.value.invoice?.status)
        assertFalse(vm.state.value.showCancel)
    }

    @Test
    fun `detalhe nao encontrado vira erro`() {
        val repo = FakeFiscalRepository().apply { invoiceResult = { FiscalResult.Failure(FiscalError(FiscalErrorKind.NOT_FOUND)) } }
        val vm = FiscalInvoiceDetailViewModel(repo, "x", pollIntervalMillis = 0)
        assertEquals(FiscalErrorKind.NOT_FOUND, vm.state.value.error?.kind)
    }
}
