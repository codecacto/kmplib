package br.com.codecacto.kmplib.fiscal

/**
 * O que as telas fiscais fazem contra o backend do produto. Os ViewModels dependem DESTA interface (o teste troca por
 * um dublê); a implementação de produção é [RestFiscalRepository] sobre o [FiscalApi].
 *
 * Registro típico no Koin do app:
 * ```kotlin
 * single<FiscalRepository> { RestFiscalRepository(FiscalApi(get<DomainApiClient>(), "/locaki/v2/fiscal")) }
 * ```
 */
interface FiscalRepository {
    suspend fun status(): FiscalResult<FiscalStatusResposta>
    suspend fun emitter(): FiscalResult<FiscalEmissor?>
    suspend fun saveEmitter(input: FiscalEmissorInput): FiscalResult<FiscalEmissor>
    suspend fun uploadCertificate(bytes: ByteArray, fileName: String, password: String): FiscalResult<FiscalEmissor>
    suspend fun removeCertificate(): FiscalResult<Unit>
    suspend fun municipalParameters(ibge: String): FiscalResult<FiscalParametrosMunicipais>
    suspend fun searchServices(q: String): FiscalResult<List<FiscalCatalogoItem>>
    suspend fun preview(pedido: NfsePedido): FiscalResult<NfsePrevia>
    suspend fun issue(pedido: NfsePedido, idempotencyKey: String): FiscalResult<FiscalDocumentoAceito>
    suspend fun invoices(filter: FiscalDocumentoFiltro): FiscalResult<FiscalDocumentosPagina>
    suspend fun invoice(id: String): FiscalResult<FiscalDocumento>
    suspend fun cancel(id: String, input: FiscalCancelamento): FiscalResult<FiscalDocumento>
    suspend fun substitute(id: String, input: FiscalSubstituicao, idempotencyKey: String): FiscalResult<FiscalDocumentoAceito>
    suspend fun reconsult(id: String): FiscalResult<FiscalDocumento>
    suspend fun pdf(id: String, numeroNfse: String?): FiscalResult<FiscalArquivo>
    suspend fun xml(id: String, numeroNfse: String?): FiscalResult<FiscalArquivo>
    suspend fun suggestion(ref: String): FiscalResult<TomadorSugerido?>
}

/** [FiscalRepository] sobre as rotas do produto. */
class RestFiscalRepository(private val api: FiscalApi) : FiscalRepository {
    override suspend fun status() = api.status()
    override suspend fun emitter() = api.emitter()
    override suspend fun saveEmitter(input: FiscalEmissorInput) = api.saveEmitter(input)
    override suspend fun uploadCertificate(bytes: ByteArray, fileName: String, password: String) = api.uploadCertificate(bytes, fileName, password)
    override suspend fun removeCertificate() = api.removeCertificate()
    override suspend fun municipalParameters(ibge: String) = api.municipalParameters(ibge)
    override suspend fun searchServices(q: String) = api.searchServices(q)
    override suspend fun preview(pedido: NfsePedido) = api.preview(pedido)
    override suspend fun issue(pedido: NfsePedido, idempotencyKey: String) = api.issue(pedido, idempotencyKey)
    override suspend fun invoices(filter: FiscalDocumentoFiltro) = api.invoices(filter)
    override suspend fun invoice(id: String) = api.invoice(id)
    override suspend fun cancel(id: String, input: FiscalCancelamento) = api.cancel(id, input)
    override suspend fun substitute(id: String, input: FiscalSubstituicao, idempotencyKey: String) = api.substitute(id, input, idempotencyKey)
    override suspend fun reconsult(id: String) = api.reconsult(id)
    override suspend fun pdf(id: String, numeroNfse: String?) = api.pdf(id, numeroNfse)
    override suspend fun xml(id: String, numeroNfse: String?) = api.xml(id, numeroNfse)
    override suspend fun suggestion(ref: String) = api.suggestion(ref)
}
