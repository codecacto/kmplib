package br.com.codecacto.kmplib.fiscal

import kotlinx.serialization.Serializable

// Contrato de fio das rotas do PRODUTO da `backlib-fiscal` (≥ 0.278.0, `FiscalModels.kt`) — os MESMOS nomes JSON.
// Diferenças de representação, de propósito:
// - dinheiro, alíquota e percentual são STRING decimal com ponto ("1250.00", "2.00") — o servidor nunca recebe número
//   de ponto flutuante (o `BigDecimalSerializer` da backlib lê e escreve string);
// - datas (`competencia`) em `yyyy-MM-dd` e instantes (`criadoEm`…) em ISO-8601, como texto;
// - status do documento é TEXTO ([FiscalStatus]): um status novo do serviço não quebra quem está numa versão anterior.
// Campo nulo no pedido = herda o padrão do emissor (o serviço resolve). A leitura ignora campo desconhecido.

/** Endereço (emissor, tomador). [municipioIbge] = código IBGE de 7 dígitos; [pais] ISO alfa-2 (`null` = Brasil). */
@Serializable
data class FiscalEndereco(
    val cep: String? = null,
    val logradouro: String? = null,
    val numero: String? = null,
    val complemento: String? = null,
    val bairro: String? = null,
    val municipioIbge: String? = null,
    val uf: String? = null,
    val pais: String? = null,
)

/** Regime nos códigos do leiaute (`opSimpNac`, `regApTribSN`, `regEspTrib`) + o [regimeApuracao] (0.278.0). */
@Serializable
data class FiscalRegime(
    val opSimpNac: Int? = null,
    val regApTribSN: Int? = null,
    val regEspTrib: Int? = null,
    val regimeApuracao: RegimeApuracao? = null,
)

/** Regime de apuração do emissor. [PESSOA_FISICA] só para CPF. */
@Serializable
enum class RegimeApuracao { MEI, SIMPLES, SIMPLES_HIBRIDO, LUCRO_PRESUMIDO, LUCRO_REAL, PESSOA_FISICA, IMUNE_ISENTO }

/** Como informar os tributos aproximados da Lei 12.741 (ver a backlib). [VALOR] só no pedido; [VALOR_INFORMADO] só no padrão. */
@Serializable
enum class TipoTotaisAproximados { PERCENTUAL, SIMPLES_NACIONAL, NAO_INFORMAR, VALOR_INFORMADO, VALOR }

/** Tributos aproximados: percentual ("4.50") ou, no tipo [TipoTotaisAproximados.VALOR], R$ ("12.30"). */
@Serializable
data class FiscalTotaisAproximados(
    val tipo: TipoTotaisAproximados,
    val federal: String? = null,
    val estadual: String? = null,
    val municipal: String? = null,
    val simplesNacional: String? = null,
)

/** Padrões tributários do emissor — o que um pedido com campo nulo herda. Alíquota em percentual. */
@Serializable
data class FiscalPadroes(
    val codigoTributacaoNacional: String? = null,
    val codigoTributacaoMunicipal: String? = null,
    val nbs: String? = null,
    val aliquotaIss: String? = null,
    val issRetido: Boolean? = null,
    val tributacaoIss: TributacaoIss? = null,
    val pisCofinsRetidos: Boolean? = null,
    val ibsCbsCst: String? = null,
    val ibsCbsClassificacaoTributaria: String? = null,
    val descricaoServico: String? = null,
    val cstPisCofins: String? = null,
    val totaisAproximados: FiscalTotaisAproximados? = null,
)

/** Ambiente da SEFIN: [PRODUCAO_RESTRITA] = nota de teste, sem valor fiscal; [PRODUCAO] = valendo. */
@Serializable
enum class FiscalAmbiente { PRODUCAO_RESTRITA, PRODUCAO }

/** Pessoa do emissor: física (CPF) ou jurídica (CNPJ). */
@Serializable
enum class FiscalTipoPessoa { PF, PJ }

/** Cadastro fiscal que o app ENVIA (`PUT {base}/emissor`). */
@Serializable
data class FiscalEmissorInput(
    val tipoPessoa: FiscalTipoPessoa,
    val documento: String,
    val inscricaoMunicipal: String? = null,
    val razaoSocial: String,
    val nomeFantasia: String? = null,
    val endereco: FiscalEndereco,
    val email: String? = null,
    val telefone: String? = null,
    val regime: FiscalRegime,
    val ambiente: FiscalAmbiente = FiscalAmbiente.PRODUCAO_RESTRITA,
    val serie: String? = null,
    val padroes: FiscalPadroes = FiscalPadroes(),
    /** Número da próxima DPS (só sobe; 1..999.999.999.999.999). */
    val numeroInicialDps: Long? = null,
)

/** Metadados do certificado A1 guardado — nunca o arquivo nem a senha. */
@Serializable
data class FiscalCertificado(
    val titularNome: String? = null,
    val titularDocumento: String? = null,
    val validoDe: String? = null,
    val validoAte: String? = null,
    val impressaoDigital: String? = null,
    val enviadoEm: String? = null,
)

/** O que falta para o emissor poder emitir. [field] aponta o campo do cadastro. */
@Serializable
data class FiscalPendencia(
    val code: String,
    val message: String,
    val field: String? = null,
)

/** Cadastro fiscal como o servidor DEVOLVE. */
@Serializable
data class FiscalEmissor(
    val ref: String = "",
    val tipoPessoa: FiscalTipoPessoa? = null,
    val documento: String? = null,
    val inscricaoMunicipal: String? = null,
    val razaoSocial: String? = null,
    val nomeFantasia: String? = null,
    val endereco: FiscalEndereco? = null,
    val email: String? = null,
    val telefone: String? = null,
    val regime: FiscalRegime? = null,
    val ambiente: FiscalAmbiente? = null,
    val serie: String? = null,
    val padroes: FiscalPadroes? = null,
    val ativo: Boolean = true,
    val proximoNumeroDps: Long? = null,
    val certificado: FiscalCertificado? = null,
    val pronto: Boolean = false,
    val pendencias: List<FiscalPendencia> = emptyList(),
    val criadoEm: String? = null,
    val atualizadoEm: String? = null,
)

/** `GET {base}/status`. Sem o módulo a rota responde 404 — o [FiscalApi] devolve [FiscalStatusResposta.DESABILITADO]. */
@Serializable
data class FiscalStatusResposta(
    val habilitado: Boolean = true,
    val emissor: FiscalEmissor? = null,
    val pronto: Boolean = false,
    val pendencias: List<FiscalPendencia> = emptyList(),
) {
    companion object {
        val DESABILITADO = FiscalStatusResposta(habilitado = false)
    }
}

/** Quem é o tomador. [NAO_IDENTIFICADO] = sem tomador (quando o município permite). */
@Serializable
enum class TipoTomador { CNPJ, CPF, ESTRANGEIRO, NAO_IDENTIFICADO }

/** Tomador ou intermediário. Estrangeiro: [nif] ou [motivoSemNif]; nacional: [documento] (só dígitos). */
@Serializable
data class NfseTomador(
    val tipo: TipoTomador,
    val documento: String? = null,
    val nif: String? = null,
    val motivoSemNif: String? = null,
    val nome: String? = null,
    val email: String? = null,
    val telefone: String? = null,
    val endereco: FiscalEndereco? = null,
)

/** Serviço prestado. Código nacional (6 dígitos, LC 116) herdado do emissor quando nulo. */
@Serializable
data class NfseServico(
    val codigoTributacaoNacional: String? = null,
    val codigoTributacaoMunicipal: String? = null,
    val nbs: String? = null,
    val descricao: String? = null,
    val municipioPrestacaoIbge: String? = null,
    val paisPrestacao: String? = null,
    val informacoesComplementares: String? = null,
)

/** Tributação do ISSQN (`tribISSQN`). */
@Serializable
enum class TributacaoIss { TRIBUTAVEL, IMUNE, EXPORTACAO, NAO_INCIDENCIA }

/** ISSQN. [aliquota] em percentual; `null` = a do emissor ou a do município. */
@Serializable
data class NfseIss(
    val tributacao: TributacaoIss? = null,
    val aliquota: String? = null,
    val retido: Boolean? = null,
)

/** Tributos federais retidos, em R$. */
@Serializable
data class NfseFederais(
    val pis: String? = null,
    val cofins: String? = null,
    val csll: String? = null,
    val irrf: String? = null,
    val cp: String? = null,
    val pisCofinsRetidos: Boolean? = null,
    val cstPisCofins: String? = null,
    val aliquotaPis: String? = null,
    val aliquotaCofins: String? = null,
)

/** Valores da nota. [servico] é o único obrigatório. */
@Serializable
data class NfseValores(
    val servico: String,
    val descontoIncondicionado: String? = null,
    val descontoCondicionado: String? = null,
    val deducoes: String? = null,
    val iss: NfseIss? = null,
    val federais: NfseFederais? = null,
    val totalTributosAproximado: String? = null,
    val totaisAproximados: FiscalTotaisAproximados? = null,
)

/** O pedido de NFS-e (`POST {base}/notas`, `POST {base}/notas/previa`). */
@Serializable
data class NfsePedido(
    val refExterna: String? = null,
    /** `yyyy-MM-dd`. */
    val competencia: String? = null,
    val tomador: NfseTomador? = null,
    val servico: NfseServico = NfseServico(),
    val valores: NfseValores,
    val enviarParaTomador: Boolean? = null,
)

/** Mensagem de erro/aviso traduzida (rejeição, schema, validação). [field] = caminho no pedido. */
@Serializable
data class FiscalMensagem(
    val code: String = "",
    val message: String = "",
    val field: String? = null,
)

/** Valores como o serviço os calculou. */
@Serializable
data class NfseValoresCalculados(
    val servico: String? = null,
    val descontos: String? = null,
    val deducoes: String? = null,
    val baseCalculo: String? = null,
    val aliquotaIss: String? = null,
    val iss: String? = null,
    val issRetido: Boolean? = null,
    val retencoesFederais: String? = null,
    val liquido: String? = null,
    val totalTributosAproximado: String? = null,
)

/** Prévia: monta, calcula e valida sem enviar. */
@Serializable
data class NfsePrevia(
    val valido: Boolean = true,
    val valores: NfseValoresCalculados? = null,
    val erros: List<FiscalMensagem> = emptyList(),
)

/** 202 da emissão/substituição: o documento entrou na fila. */
@Serializable
data class FiscalDocumentoAceito(
    val id: String,
    val status: String = FiscalStatus.RECEBIDO,
)

/** Status do documento no serviço (texto). */
object FiscalStatus {
    const val RECEBIDO = "RECEBIDO"
    const val PROCESSANDO = "PROCESSANDO"
    const val AUTORIZADO = "AUTORIZADO"
    const val REJEITADO = "REJEITADO"
    const val INCERTO = "INCERTO"
    const val ERRO_TEMPORARIO = "ERRO_TEMPORARIO"
    const val CANCELADO = "CANCELADO"
    const val SUBSTITUIDO = "SUBSTITUIDO"

    /** Os status, na ordem do filtro da lista. */
    val ALL: List<String> = listOf(AUTORIZADO, PROCESSANDO, RECEBIDO, INCERTO, ERRO_TEMPORARIO, REJEITADO, CANCELADO, SUBSTITUIDO)
}

/** Resumo de valores do documento, para a lista. */
@Serializable
data class FiscalValoresResumo(
    val servico: String? = null,
    val liquido: String? = null,
    val iss: String? = null,
    val tomadorNome: String? = null,
    val tomadorDocumento: String? = null,
)

/** Evento do documento (cancelamento, substituição). */
@Serializable
data class FiscalEvento(
    val id: String? = null,
    val tipo: String = "",
    val motivo: String? = null,
    val descricao: String? = null,
    val status: String? = null,
    val erros: List<FiscalMensagem> = emptyList(),
    val criadoEm: String? = null,
)

/** Documento fiscal (detalhe e item da lista). */
@Serializable
data class FiscalDocumento(
    val id: String,
    val emissorRef: String = "",
    val refExterna: String? = null,
    val tipo: String = "NFSE",
    val ambiente: FiscalAmbiente? = null,
    val status: String,
    val serie: String? = null,
    val numeroDps: Long? = null,
    val idDps: String? = null,
    val chave: String? = null,
    val numeroNfse: String? = null,
    val valores: FiscalValoresResumo? = null,
    val erros: List<FiscalMensagem> = emptyList(),
    val eventos: List<FiscalEvento> = emptyList(),
    val substituiId: String? = null,
    val substituidoPorId: String? = null,
    val criadoEm: String? = null,
    val atualizadoEm: String? = null,
    val autorizadoEm: String? = null,
)

/** Página de documentos. [pagina] começa em 1. */
@Serializable
data class FiscalDocumentosPagina(
    val items: List<FiscalDocumento> = emptyList(),
    val pagina: Int = 1,
    val tamanho: Int = 0,
    val total: Long? = null,
    val temMais: Boolean = false,
)

/** Filtro da lista (`GET {base}/notas`). Datas `yyyy-MM-dd`, inclusivas. [q] busca por partes. */
data class FiscalDocumentoFiltro(
    val status: String? = null,
    val ref: String? = null,
    val de: String? = null,
    val ate: String? = null,
    val q: String? = null,
    val pagina: Int = 1,
    val tamanho: Int = 20,
)

/** Motivo do cancelamento (e da substituição). */
@Serializable
enum class MotivoCancelamento { ERRO_EMISSAO, SERVICO_NAO_PRESTADO, OUTROS }

/** Corpo de `POST {base}/notas/{id}/cancelar`. [descricao] com 15+ caracteres. */
@Serializable
data class FiscalCancelamento(
    val motivo: MotivoCancelamento,
    val descricao: String,
)

/** Corpo de `POST {base}/notas/{id}/substituir`: a nota nova + o motivo. */
@Serializable
data class FiscalSubstituicao(
    val pedido: NfsePedido,
    val motivo: String,
    val descricao: String? = null,
)

/** Item de catálogo (lista nacional de serviços; NBS). */
@Serializable
data class FiscalCatalogoItem(
    val codigo: String = "",
    val descricao: String = "",
)

@Serializable
internal data class FiscalCatalogo(val items: List<FiscalCatalogoItem> = emptyList())

/** Alíquota do município para um serviço. */
@Serializable
data class FiscalAliquotaMunicipal(
    val codigoTributacaoNacional: String,
    val aliquota: String? = null,
    val descricao: String? = null,
)

/** Parâmetros municipais (convênio com a NFS-e Nacional, alíquotas por serviço). */
@Serializable
data class FiscalParametrosMunicipais(
    val municipioIbge: String = "",
    val nome: String? = null,
    val uf: String? = null,
    val conveniado: Boolean = false,
    val aliquotas: List<FiscalAliquotaMunicipal> = emptyList(),
    val regimesEspeciais: List<String> = emptyList(),
)

/** Sugestão para abrir a emissão já preenchida a partir de um registro do produto (ex.: a locação). */
@Serializable
data class TomadorSugerido(
    val tomador: NfseTomador,
    val descricaoServico: String? = null,
    val valorServico: String? = null,
    val competencia: String? = null,
)

/** Arquivo baixado (PDF/XML da nota). */
class FiscalArquivo(val bytes: ByteArray, val nome: String, val mimeType: String) {
    override fun toString(): String = "FiscalArquivo(${bytes.size} bytes, $mimeType)"
}
