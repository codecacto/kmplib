package br.com.codecacto.kmplib.fiscal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.fiscal.generated.resources.*
import br.com.codecacto.kmplib.mask.CepInputTransformation
import br.com.codecacto.kmplib.mask.CepOutputTransformation
import br.com.codecacto.kmplib.mask.CnpjInputTransformation
import br.com.codecacto.kmplib.mask.CnpjOutputTransformation
import br.com.codecacto.kmplib.mask.CpfInputTransformation
import br.com.codecacto.kmplib.mask.CpfOutputTransformation
import br.com.codecacto.kmplib.mask.PhoneBrOutputTransformation
import br.com.codecacto.kmplib.mask.PhoneInputTransformation
import br.com.codecacto.kmplib.ui.components.StatusBadge
import br.com.codecacto.kmplib.ui.components.StatusTone
import br.com.codecacto.kmplib.ui.components.rememberSyncedTextFieldState
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import org.jetbrains.compose.resources.StringResource

// Peças de tela comuns às telas fiscais: textos de status/regime, a frase do erro, o selo e os campos mascarados.

/** Ids de automação das telas fiscais (Maestro). */
object FiscalTestTags {
    const val CONFIG = "fiscal-config"
    const val CONFIG_SALVAR = "fiscal-config-btn-salvar"
    const val CONFIG_ETAPA = "fiscal-config-etapa"
    const val CERTIFICADO_ESCOLHER = "fiscal-certificado-btn-escolher"
    const val CERTIFICADO_SENHA = "fiscal-certificado-input-senha"
    const val CERTIFICADO_ENVIAR = "fiscal-certificado-btn-enviar"
    const val LISTA = "fiscal-notas"
    const val LISTA_BUSCA = "fiscal-notas-busca"
    const val LISTA_NOVA = "fiscal-notas-btn-nova"
    const val LISTA_ITEM = "fiscal-nota-item"
    const val DETALHE = "fiscal-nota-detalhe"
    const val DETALHE_PDF = "fiscal-nota-btn-pdf"
    const val DETALHE_XML = "fiscal-nota-btn-xml"
    const val DETALHE_CANCELAR = "fiscal-nota-btn-cancelar"
    const val DETALHE_SUBSTITUIR = "fiscal-nota-btn-substituir"
    const val DETALHE_RECONSULTAR = "fiscal-nota-btn-reconsultar"
    const val EMITIR = "fiscal-emitir"
    const val EMITIR_PREVIA = "fiscal-emitir-btn-previa"
    const val EMITIR_ENVIAR = "fiscal-emitir-btn-enviar"
    const val EMITIR_ANDAMENTO = "fiscal-emitir-andamento"
}

@Composable
internal fun fiscalString(res: StringResource): String = kmpStringResource(res)

@Composable
internal fun fiscalString(res: StringResource, vararg args: Any): String = kmpStringResource(res, *args)

@Composable
fun fiscalStatusLabel(status: String): String = when (status) {
    FiscalStatus.RECEBIDO -> fiscalString(Res.string.fiscal_status_recebido)
    FiscalStatus.PROCESSANDO -> fiscalString(Res.string.fiscal_status_processando)
    FiscalStatus.AUTORIZADO -> fiscalString(Res.string.fiscal_status_autorizado)
    FiscalStatus.REJEITADO -> fiscalString(Res.string.fiscal_status_rejeitado)
    FiscalStatus.INCERTO -> fiscalString(Res.string.fiscal_status_incerto)
    FiscalStatus.ERRO_TEMPORARIO -> fiscalString(Res.string.fiscal_status_erro_temporario)
    FiscalStatus.CANCELADO -> fiscalString(Res.string.fiscal_status_cancelado)
    FiscalStatus.SUBSTITUIDO -> fiscalString(Res.string.fiscal_status_substituido)
    else -> status
}

internal fun FiscalStatusTone.toStatusTone(): StatusTone = when (this) {
    FiscalStatusTone.NEUTRAL -> StatusTone.NEUTRAL
    FiscalStatusTone.INFO -> StatusTone.INFO
    FiscalStatusTone.SUCCESS -> StatusTone.SUCCESS
    FiscalStatusTone.WARNING -> StatusTone.WARNING
    FiscalStatusTone.DANGER -> StatusTone.DANGER
}

/** Selo da situação da nota (cor pelo tema, nunca hex). */
@Composable
fun FiscalStatusBadge(status: String, modifier: Modifier = Modifier) {
    StatusBadge(text = fiscalStatusLabel(status), tone = fiscalStatusTone(status).toStatusTone(), modifier = modifier)
}

@Composable
internal fun regimeLabel(r: RegimeApuracao): String = fiscalString(
    when (r) {
        RegimeApuracao.MEI -> Res.string.fiscal_regime_mei
        RegimeApuracao.SIMPLES -> Res.string.fiscal_regime_simples
        RegimeApuracao.SIMPLES_HIBRIDO -> Res.string.fiscal_regime_simples_hibrido
        RegimeApuracao.LUCRO_PRESUMIDO -> Res.string.fiscal_regime_presumido
        RegimeApuracao.LUCRO_REAL -> Res.string.fiscal_regime_real
        RegimeApuracao.PESSOA_FISICA -> Res.string.fiscal_regime_pf
        RegimeApuracao.IMUNE_ISENTO -> Res.string.fiscal_regime_imune
    },
)

@Composable
internal fun regimeDescription(r: RegimeApuracao): String = fiscalString(
    when (r) {
        RegimeApuracao.MEI -> Res.string.fiscal_regime_mei_desc
        RegimeApuracao.SIMPLES -> Res.string.fiscal_regime_simples_desc
        RegimeApuracao.SIMPLES_HIBRIDO -> Res.string.fiscal_regime_simples_hibrido_desc
        RegimeApuracao.LUCRO_PRESUMIDO -> Res.string.fiscal_regime_presumido_desc
        RegimeApuracao.LUCRO_REAL -> Res.string.fiscal_regime_real_desc
        RegimeApuracao.PESSOA_FISICA -> Res.string.fiscal_regime_pf_desc
        RegimeApuracao.IMUNE_ISENTO -> Res.string.fiscal_regime_imune_desc
    },
)

@Composable
internal fun specialLabel(s: FiscalRegimeEspecial): String = fiscalString(
    when (s) {
        FiscalRegimeEspecial.NENHUM -> Res.string.fiscal_esp_nenhum
        FiscalRegimeEspecial.ATO_COOPERADO -> Res.string.fiscal_esp_cooperado
        FiscalRegimeEspecial.ESTIMATIVA -> Res.string.fiscal_esp_estimativa
        FiscalRegimeEspecial.MICROEMPRESA_MUNICIPAL -> Res.string.fiscal_esp_me_municipal
        FiscalRegimeEspecial.NOTARIO_REGISTRADOR -> Res.string.fiscal_esp_notario
        FiscalRegimeEspecial.PROFISSIONAL_AUTONOMO -> Res.string.fiscal_esp_autonomo
        FiscalRegimeEspecial.SOCIEDADE_PROFISSIONAIS -> Res.string.fiscal_esp_sociedade
        FiscalRegimeEspecial.OUTROS -> Res.string.fiscal_esp_outros
    },
)

@Composable
internal fun takerTypeLabel(t: TipoTomador): String = fiscalString(
    when (t) {
        TipoTomador.CPF -> Res.string.fiscal_tomador_cpf
        TipoTomador.CNPJ -> Res.string.fiscal_tomador_cnpj
        TipoTomador.ESTRANGEIRO -> Res.string.fiscal_tomador_estrangeiro
        TipoTomador.NAO_IDENTIFICADO -> Res.string.fiscal_tomador_nao_identificado
    },
)

@Composable
internal fun issTaxationLabel(t: TributacaoIss): String = fiscalString(
    when (t) {
        TributacaoIss.TRIBUTAVEL -> Res.string.fiscal_iss_tributavel
        TributacaoIss.IMUNE -> Res.string.fiscal_iss_imune
        TributacaoIss.EXPORTACAO -> Res.string.fiscal_iss_exportacao
        TributacaoIss.NAO_INCIDENCIA -> Res.string.fiscal_iss_nao_incidencia
    },
)

@Composable
internal fun cancelReasonLabel(m: MotivoCancelamento): String = fiscalString(
    when (m) {
        MotivoCancelamento.ERRO_EMISSAO -> Res.string.fiscal_motivo_erro_emissao
        MotivoCancelamento.SERVICO_NAO_PRESTADO -> Res.string.fiscal_motivo_servico_nao_prestado
        MotivoCancelamento.OUTROS -> Res.string.fiscal_motivo_outros
    },
)

@Composable
internal fun stepLabel(s: FiscalSetupStep): String = fiscalString(
    when (s) {
        FiscalSetupStep.EMPRESA -> Res.string.fiscal_etapa_empresa
        FiscalSetupStep.REGIME -> Res.string.fiscal_etapa_regime
        FiscalSetupStep.SERVICO -> Res.string.fiscal_etapa_servico
        FiscalSetupStep.CERTIFICADO -> Res.string.fiscal_etapa_certificado
        FiscalSetupStep.AMBIENTE -> Res.string.fiscal_etapa_ambiente
    },
)

@Composable
internal fun issueText(i: FiscalIssue): String = fiscalString(
    when (i) {
        FiscalIssue.OBRIGATORIO -> Res.string.fiscal_erro_obrigatorio
        FiscalIssue.CPF_INVALIDO -> Res.string.fiscal_erro_cpf
        FiscalIssue.CNPJ_INVALIDO -> Res.string.fiscal_erro_cnpj
        FiscalIssue.EMAIL_INVALIDO -> Res.string.fiscal_erro_email
        FiscalIssue.CEP_INVALIDO -> Res.string.fiscal_erro_cep
        FiscalIssue.TELEFONE_INVALIDO -> Res.string.fiscal_erro_telefone
        FiscalIssue.VALOR_INVALIDO -> Res.string.fiscal_erro_valor
        FiscalIssue.ALIQUOTA_INVALIDA -> Res.string.fiscal_erro_aliquota
        FiscalIssue.MUNICIPIO_OBRIGATORIO -> Res.string.fiscal_erro_municipio
        FiscalIssue.DPS_INVALIDO -> Res.string.fiscal_erro_dps
        FiscalIssue.CST_INVALIDO -> Res.string.fiscal_erro_cst
        FiscalIssue.TOTAIS_OBRIGATORIO -> Res.string.fiscal_erro_totais
        FiscalIssue.TOTAIS_VALOR_OBRIGATORIO -> Res.string.fiscal_erro_totais_valor
        FiscalIssue.DESCRICAO_CURTA -> Res.string.fiscal_erro_descricao_curta
        FiscalIssue.CERTIFICADO_FORMATO -> Res.string.fiscal_erro_certificado_formato
        FiscalIssue.CERTIFICADO_GRANDE -> Res.string.fiscal_erro_certificado_grande
        FiscalIssue.CERTIFICADO_OBRIGATORIO -> Res.string.fiscal_erro_certificado_obrigatorio
    },
)

/** Frase do erro de um campo (`null` sem erro). */
@Composable
fun FiscalFieldErrors.textOf(key: String): String? = when (val e = this[key]) {
    null -> null
    is FiscalFieldError.Issue -> issueText(e.issue)
    is FiscalFieldError.Text -> e.text
}

/** A frase do erro de uma chamada, como a tela mostra (mesma régua da weblib). */
@Composable
fun fiscalErrorMessage(e: FiscalError): String {
    if (e.code == FISCAL_CODE_CERTIFICADO_ANTES_DA_EMPRESA) return fiscalString(Res.string.fiscal_certificado_antes_empresa)
    return when {
        FiscalErrorCodes.matches(e.code, FiscalErrorCodes.CERTIFICADO_FORA_DA_ICP_BRASIL) -> fiscalString(Res.string.fiscal_erro_icp_brasil)
        FiscalErrorCodes.matches(e.code, FiscalErrorCodes.TOKEN_TENTATIVAS_EXCEDIDAS) -> fiscalString(Res.string.fiscal_erro_token_tentativas)
        FiscalErrorCodes.matches(e.code, FiscalErrorCodes.DOCUMENTO_JA_EM_SUBSTITUICAO) -> fiscalString(Res.string.fiscal_erro_em_substituicao)
        e.code == FiscalErrorCodes.CREDENCIAL_RECUSADA -> fiscalString(Res.string.fiscal_erro_credencial)
        e.code == FiscalErrorCodes.CERTIFICADO_GRANDE -> fiscalString(Res.string.fiscal_erro_certificado_grande)
        else -> when (e.kind) {
            FiscalErrorKind.UNCERTAIN -> fiscalString(Res.string.fiscal_erro_incerto)
            FiscalErrorKind.UNAVAILABLE -> fiscalString(Res.string.fiscal_erro_fora_do_ar)
            FiscalErrorKind.RATE_LIMIT -> fiscalString(Res.string.fiscal_erro_muitas_tentativas)
            FiscalErrorKind.DISABLED -> fiscalString(Res.string.fiscal_erro_nao_habilitado)
            FiscalErrorKind.NETWORK -> fiscalString(Res.string.fiscal_erro_carregar)
            FiscalErrorKind.FORBIDDEN -> e.serverMessage ?: fiscalString(Res.string.fiscal_erro_sem_permissao)
            FiscalErrorKind.CONFLICT -> e.serverMessage ?: fiscalString(Res.string.fiscal_erro_conflito)
            FiscalErrorKind.NOT_FOUND -> e.serverMessage ?: fiscalString(Res.string.fiscal_erro_nao_encontrada)
            FiscalErrorKind.FIELDS, FiscalErrorKind.SERVER -> e.serverMessage ?: fiscalString(Res.string.fiscal_erro_salvar)
        }
    }
}

/** Máscara de um campo de texto com estado (o texto mora no campo — o iPhone não perde letra). */
enum class FiscalMask { NONE, CPF, CNPJ, PHONE, CEP, DIGITS }

private object DigitsOnly : InputTransformation {
    override fun androidx.compose.foundation.text.input.TextFieldBuffer.transformInput() {
        val clean = asCharSequence().filter { it.isDigit() }.toString()
        if (clean != asCharSequence().toString()) replace(0, length, clean)
    }
}

/**
 * Campo com máscara de digitação (`TextFieldState` + `InputTransformation`/`OutputTransformation` da `kmplib-mask`):
 * o valor que chega em [onValueChange] é cru (só dígitos); a tela mostra formatado — `(65) 99999-9999`, `00000-000`,
 * CPF/CNPJ. Erro no campo ([error]): borda vermelha, `error()` na semântica e a frase embaixo.
 */
@Composable
fun FiscalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    mask: FiscalMask = FiscalMask.NONE,
    error: String? = null,
    helper: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val state = rememberSyncedTextFieldState(value, onValueChange, saveable = false)
    val input: InputTransformation? = when (mask) {
        FiscalMask.CPF -> CpfInputTransformation
        FiscalMask.CNPJ -> CnpjInputTransformation
        FiscalMask.PHONE -> PhoneInputTransformation
        FiscalMask.CEP -> CepInputTransformation
        FiscalMask.DIGITS -> DigitsOnly
        FiscalMask.NONE -> null
    }
    val output: OutputTransformation? = when (mask) {
        FiscalMask.CPF -> CpfOutputTransformation
        FiscalMask.CNPJ -> CnpjOutputTransformation
        FiscalMask.PHONE -> PhoneBrOutputTransformation
        FiscalMask.CEP -> CepOutputTransformation
        else -> null
    }
    val kb = when (mask) {
        FiscalMask.CPF, FiscalMask.CEP, FiscalMask.DIGITS -> KeyboardType.Number
        FiscalMask.PHONE -> KeyboardType.Phone
        else -> keyboardType
    }
    OutlinedTextField(
        state = state,
        modifier = modifier.fillMaxWidth().then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { if (error != null) error(error) },
        label = { Text(label) },
        inputTransformation = input,
        outputTransformation = output,
        keyboardOptions = KeyboardOptions(keyboardType = kb, imeAction = ImeAction.Next),
        isError = error != null,
        enabled = enabled,
        supportingText = (error ?: helper)?.let { t -> { Text(t) } },
        lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
    )
}

/** Título de seção dos formulários fiscais. */
@Composable
internal fun FiscalSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 8.dp))
}

/** Linha "rótulo — valor" do detalhe e da prévia. */
@Composable
internal fun FiscalInfoRow(label: String, value: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f),
        )
    }
}

/** Bloco de mensagens (pendências, recusa da Receita). */
@Composable
internal fun FiscalMessages(messages: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        messages.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}
