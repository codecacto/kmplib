@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.fiscal

import br.com.codecacto.kmplib.brdata.BrazilianCities
import br.com.codecacto.kmplib.brdata.equalsIgnoringAccents
import br.com.codecacto.kmplib.validation.CnpjValidator
import br.com.codecacto.kmplib.validation.CpfValidator
import br.com.codecacto.kmplib.validation.EmailValidator
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// Regras puras do módulo fiscal — o espelho do `core.ts` do `/fiscal` da weblib (0.340.0), para o app e o portal
// decidirem igual: regime em palavras ↔ códigos do leiaute, validação da tela, montagem do cadastro e do pedido.
// O servidor confere tudo de novo; a validação daqui só poupa a ida.

// ───────────────────────────── Status ─────────────────────────────

/** Tom do status, para a cor do selo. */
enum class FiscalStatusTone { NEUTRAL, INFO, SUCCESS, WARNING, DANGER }

fun fiscalStatusTone(status: String): FiscalStatusTone = when (status) {
    FiscalStatus.RECEBIDO, FiscalStatus.PROCESSANDO -> FiscalStatusTone.INFO
    FiscalStatus.AUTORIZADO -> FiscalStatusTone.SUCCESS
    FiscalStatus.REJEITADO -> FiscalStatusTone.DANGER
    FiscalStatus.INCERTO, FiscalStatus.ERRO_TEMPORARIO -> FiscalStatusTone.WARNING
    else -> FiscalStatusTone.NEUTRAL
}

/** Ainda andando: a tela continua consultando. */
fun isPendingStatus(status: String): Boolean =
    status == FiscalStatus.RECEBIDO || status == FiscalStatus.PROCESSANDO ||
        status == FiscalStatus.INCERTO || status == FiscalStatus.ERRO_TEMPORARIO

fun canCancelInvoice(status: String): Boolean = status == FiscalStatus.AUTORIZADO
fun canSubstituteInvoice(status: String): Boolean = status == FiscalStatus.AUTORIZADO
fun canReconsultInvoice(status: String): Boolean = status == FiscalStatus.INCERTO || status == FiscalStatus.ERRO_TEMPORARIO
fun hasInvoiceFiles(status: String): Boolean =
    status == FiscalStatus.AUTORIZADO || status == FiscalStatus.CANCELADO || status == FiscalStatus.SUBSTITUIDO

/** O leiaute exige justificativa de cancelamento com 15 caracteres ou mais. */
const val FISCAL_CANCEL_MIN_LENGTH: Int = 15

// ───────────────────────────── Etapas do cadastro ─────────────────────────────

/** As etapas do cadastro fiscal, na ordem da tela (as mesmas da weblib). */
enum class FiscalSetupStep { EMPRESA, REGIME, SERVICO, CERTIFICADO, AMBIENTE }

/** Etapa a que uma pendência pertence, pelo [field] (se houver) ou pelo [code]. */
fun setupStepOf(code: String, field: String? = null): FiscalSetupStep {
    val f = field.orEmpty().lowercase()
    if (f.startsWith("certificado")) return FiscalSetupStep.CERTIFICADO
    if (f.startsWith("regime")) return FiscalSetupStep.REGIME
    if (f.startsWith("padroes")) return FiscalSetupStep.SERVICO
    if (f.startsWith("ambiente")) return FiscalSetupStep.AMBIENTE
    if (f.isNotEmpty()) return FiscalSetupStep.EMPRESA
    val c = code.uppercase()
    return when {
        "CERTIFICADO" in c -> FiscalSetupStep.CERTIFICADO
        "REGIME" in c -> FiscalSetupStep.REGIME
        "SERVICO" in c || "ALIQUOTA" in c || "TRIBUTACAO" in c -> FiscalSetupStep.SERVICO
        "AMBIENTE" in c -> FiscalSetupStep.AMBIENTE
        else -> FiscalSetupStep.EMPRESA
    }
}

// ───────────────────────────── Certificado ─────────────────────────────

/** Teto do arquivo do certificado nas rotas do produto (`maxCertificateBytes` da backlib). */
const val FISCAL_CERTIFICATE_MAX_BYTES: Long = 50L * 1024L

/** Aviso de vencimento: 30 dias ou menos. */
const val CERTIFICATE_WARNING_DAYS: Long = 30

/** O arquivo parece um A1 (`.pfx`/`.p12`)? */
fun isCertificateFile(name: String): Boolean = Regex("\\.(pfx|p12)$", RegexOption.IGNORE_CASE).containsMatchIn(name.trim())

/** Dias inteiros até a validade (negativo = vencido; sem validade legível = `null`). */
fun certificateDaysLeft(validoAte: String?, nowMillis: Long = Clock.System.now().toEpochMilliseconds()): Long? {
    val end = validoAte?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: return null
    val diff = end - nowMillis
    return if (diff >= 0) diff / DAY_MS else -((-diff + DAY_MS - 1) / DAY_MS)
}

private const val DAY_MS = 86_400_000L

// ───────────────────────────── Dinheiro e alíquota ─────────────────────────────

/**
 * "1.250,00" / "1250.00" / "1250" → "1250.00" (arredonda meio para cima em [digits] casas, sem ponto flutuante);
 * vazio ou inválido → `null`.
 */
fun toDecimalString(value: String?, digits: Int = 2): String? {
    var s = value?.trim().orEmpty()
    if (s.isEmpty()) return null
    if (',' in s) s = s.replace(".", "").replace(',', '.')
    if (!Regex("^-?\\d+(\\.\\d+)?$").matches(s)) return null
    val negative = s.startsWith("-")
    if (negative) s = s.substring(1)
    val intPart = s.substringBefore('.')
    val frac = s.substringAfter('.', "")
    var digitsStr = (intPart + frac.padEnd(digits, '0').take(digits)).trimStart('0').ifEmpty { "0" }
    val next = frac.getOrNull(digits)
    if (next != null && next >= '5') digitsStr = incrementDigits(digitsStr)
    val padded = digitsStr.padStart(digits + 1, '0')
    val whole = padded.dropLast(digits).trimStart('0').ifEmpty { "0" }
    val cents = padded.takeLast(digits)
    val out = if (digits == 0) whole else "$whole.$cents"
    val isZero = out.all { it == '0' || it == '.' }
    return if (negative && !isZero) "-$out" else out
}

private fun incrementDigits(d: String): String {
    val chars = d.toCharArray()
    var i = chars.size - 1
    while (i >= 0) {
        if (chars[i] == '9') {
            chars[i] = '0'; i--
        } else {
            chars[i] = chars[i] + 1
            return chars.concatToString()
        }
    }
    return "1" + chars.concatToString()
}

/** Compara dois decimais em string já normalizados por [toDecimalString]. */
private fun decimalSign(d: String): Int = when {
    d.startsWith("-") -> -1
    d.all { it == '0' || it == '.' } -> 0
    else -> 1
}

fun isPositiveMoney(value: String?): Boolean = toDecimalString(value)?.let { decimalSign(it) > 0 } == true

/** Alíquota em % entre 0 e 100. */
fun isValidRate(value: String?): Boolean {
    val d = toDecimalString(value, 4) ?: return false
    if (decimalSign(d) < 0) return false
    val whole = d.substringBefore('.').toLongOrNull() ?: return false
    val frac = d.substringAfter('.', "")
    return whole < 100 || (whole == 100L && frac.all { it == '0' })
}

/** "1250.00" → "R$ 1.250,00"; vazio → "—". Nota fiscal brasileira: sempre pt-BR, independe do aparelho. */
fun formatFiscalMoney(value: String?): String {
    val d = toDecimalString(value) ?: return "—"
    val negative = d.startsWith("-")
    val abs = d.removePrefix("-")
    return (if (negative) "-" else "") + "R$ " + groupThousands(abs.substringBefore('.')) + "," + abs.substringAfter('.')
}

/** "2.00" → "2,00"; "2.5000" → "2,50". */
fun formatRate(value: String?): String {
    val d = toDecimalString(value, 4) ?: return "—"
    val whole = d.substringBefore('.')
    var frac = d.substringAfter('.', "").trimEnd('0')
    if (frac.length < 2) frac = frac.padEnd(2, '0')
    return "$whole,$frac"
}

/** "1250.00" → "1250,00" (valor editável em pt-BR, sem milhar). */
fun decimalToInput(value: String?): String = toDecimalString(value)?.replace('.', ',').orEmpty()

private fun groupThousands(digits: String): String {
    val sb = StringBuilder()
    digits.reversed().forEachIndexed { i, c ->
        if (i > 0 && i % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return sb.reverse().toString()
}

// ───────────────────────────── Documento ─────────────────────────────

/** CPF/CNPJ só com dígitos (e as letras maiúsculas do CNPJ alfanumérico). */
fun documentDigits(value: String?): String = value.orEmpty().uppercase().filter { it in '0'..'9' || it in 'A'..'Z' }

/** Tipo do tomador pelo tamanho do documento (11 = CPF, 14 = CNPJ). */
fun takerTypeOfDocument(doc: String?): TipoTomador? = when (documentDigits(doc).length) {
    11 -> TipoTomador.CPF
    14 -> TipoTomador.CNPJ
    else -> null
}

/** CPF inteiro, como a fábrica mostra: "123.456.789-09"; CNPJ "12.345.678/0001-90". */
fun formatDocument(value: String?): String {
    val d = documentDigits(value)
    return when (d.length) {
        11 -> "${d.take(3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.takeLast(2)}"
        14 -> "${d.take(2)}.${d.substring(2, 5)}.${d.substring(5, 8)}/${d.substring(8, 12)}-${d.takeLast(2)}"
        else -> value.orEmpty()
    }
}

/** Telefone BR com DDD: 10 ou 11 dígitos. */
fun isValidBrPhone(value: String?): Boolean = value.orEmpty().filter { it.isDigit() }.length in 10..11

fun isValidCep(value: String?): Boolean = value.orEmpty().filter { it.isDigit() }.length == 8

// ───────────────────────────── Município (IBGE) ─────────────────────────────

/** Código IBGE do município pela UF + nome (acento e caixa não contam). `null` se não achar. */
fun ibgeOf(uf: String?, cidade: String?): String? {
    val u = uf?.trim().orEmpty()
    val c = cidade?.trim().orEmpty()
    if (u.isEmpty() || c.isEmpty()) return null
    return BrazilianCities.getByState(u).firstOrNull { it.name.equalsIgnoringAccents(c) }?.ibgeCode
}

/** Nome do município pelo código IBGE. */
fun cityNameOf(ibge: String?): String? = ibge?.takeIf { it.isNotBlank() }?.let { BrazilianCities.findByCode(it.trim())?.name }

// ───────────────────────────── Regime ─────────────────────────────

/** Regime especial municipal (`regEspTrib`), em palavras. */
enum class FiscalRegimeEspecial(val code: Int) {
    NENHUM(0), ATO_COOPERADO(1), ESTIMATIVA(2), MICROEMPRESA_MUNICIPAL(3), NOTARIO_REGISTRADOR(4),
    PROFISSIONAL_AUTONOMO(5), SOCIEDADE_PROFISSIONAIS(6), OUTROS(9),
}

/** Optante do Simples acima do sublimite: o que fica fora do DAS. */
enum class FiscalSimplesHibrido { ISS_FORA, TUDO_FORA }

fun isNonOptantRegime(r: RegimeApuracao?): Boolean =
    r == RegimeApuracao.LUCRO_PRESUMIDO || r == RegimeApuracao.LUCRO_REAL || r == RegimeApuracao.PESSOA_FISICA || r == RegimeApuracao.IMUNE_ISENTO

fun isSimplesRegime(r: RegimeApuracao?): Boolean = r == RegimeApuracao.SIMPLES || r == RegimeApuracao.SIMPLES_HIBRIDO

/** Presumido e Real: a nota leva o grupo PIS/COFINS pelo CST padrão. */
fun usesPisCofinsDefaults(r: RegimeApuracao?): Boolean = r == RegimeApuracao.LUCRO_PRESUMIDO || r == RegimeApuracao.LUCRO_REAL

/** CST de PIS/COFINS padrão: `01`, tributável com a alíquota básica do regime. */
const val DEFAULT_CST_PIS_COFINS: String = "01"

/** Domínio do CST de PIS/COFINS no leiaute (NT 007) — o mesmo do serviço. */
val FISCAL_CST_PIS_COFINS: Set<String> = buildSet {
    add("00"); (1..9).forEach { add("0$it") }; add("49")
    (50..56).forEach { add("$it") }; (60..67).forEach { add("$it") }; (70..75).forEach { add("$it") }
    add("98"); add("99")
}

/** Alíquotas básicas que o serviço aplica com CST `01` (%). */
fun pisCofinsBasicRates(r: RegimeApuracao?): Pair<String, String>? = when (r) {
    RegimeApuracao.LUCRO_PRESUMIDO -> "0.65" to "3.00"
    RegimeApuracao.LUCRO_REAL -> "1.65" to "7.60"
    else -> null
}

/** Regime em palavras → códigos do leiaute + `regimeApuracao`. */
fun regimeToWire(regime: RegimeApuracao?, special: FiscalRegimeEspecial, hybrid: FiscalSimplesHibrido = FiscalSimplesHibrido.ISS_FORA): FiscalRegime {
    val esp = special.code
    return when (regime) {
        RegimeApuracao.MEI -> FiscalRegime(opSimpNac = 2, regApTribSN = null, regEspTrib = esp, regimeApuracao = regime)
        RegimeApuracao.SIMPLES -> FiscalRegime(opSimpNac = 3, regApTribSN = 1, regEspTrib = esp, regimeApuracao = regime)
        RegimeApuracao.SIMPLES_HIBRIDO -> FiscalRegime(
            opSimpNac = 3, regApTribSN = if (hybrid == FiscalSimplesHibrido.TUDO_FORA) 3 else 2, regEspTrib = esp, regimeApuracao = regime,
        )
        RegimeApuracao.LUCRO_PRESUMIDO, RegimeApuracao.LUCRO_REAL, RegimeApuracao.PESSOA_FISICA, RegimeApuracao.IMUNE_ISENTO ->
            FiscalRegime(opSimpNac = 1, regApTribSN = null, regEspTrib = esp, regimeApuracao = regime)
        null -> FiscalRegime(regEspTrib = esp)
    }
}

/** O inverso de [regimeToWire]. `regimeApuracao` vence; cadastro antigo (só códigos) é deduzido. */
data class FiscalRegimeEmPalavras(val regime: RegimeApuracao?, val special: FiscalRegimeEspecial, val hybrid: FiscalSimplesHibrido)

fun regimeFromWire(regime: FiscalRegime?, tipoPessoa: FiscalTipoPessoa? = null, padroes: FiscalPadroes? = null): FiscalRegimeEmPalavras {
    val special = FiscalRegimeEspecial.entries.firstOrNull { it.code == regime?.regEspTrib } ?: FiscalRegimeEspecial.NENHUM
    val hybrid = if (regime?.regApTribSN == 3) FiscalSimplesHibrido.TUDO_FORA else FiscalSimplesHibrido.ISS_FORA
    regime?.regimeApuracao?.let { return FiscalRegimeEmPalavras(it, special, hybrid) }
    val r = when (regime?.opSimpNac) {
        2 -> RegimeApuracao.MEI
        3 -> if (regime.regApTribSN == 2 || regime.regApTribSN == 3) RegimeApuracao.SIMPLES_HIBRIDO else RegimeApuracao.SIMPLES
        1 -> when {
            padroes?.tributacaoIss == TributacaoIss.IMUNE -> RegimeApuracao.IMUNE_ISENTO
            tipoPessoa == FiscalTipoPessoa.PF -> RegimeApuracao.PESSOA_FISICA
            else -> null // Presumido ou Real? O dono escolhe.
        }
        else -> null
    }
    return FiscalRegimeEmPalavras(r, special, hybrid)
}

// ───────────────────────────── Rascunhos (o que a tela edita) ─────────────────────────────

/** Endereço editável: UF + cidade em seletores; o IBGE sai da cidade. */
data class FiscalAddressDraft(
    val cep: String = "",
    val logradouro: String = "",
    val numero: String = "",
    val complemento: String = "",
    val bairro: String = "",
    val uf: String = "",
    val cidade: String = "",
) {
    val municipioIbge: String? get() = ibgeOf(uf, cidade)
}

/** Padrões tributários editáveis (alíquotas/percentuais como digitados, com vírgula). */
data class FiscalDefaultsDraft(
    val codigoTributacaoNacional: String = "",
    /** Só da tela: a descrição do serviço escolhido no catálogo. */
    val descricaoTributacaoNacional: String = "",
    val codigoTributacaoMunicipal: String = "",
    val nbs: String = "",
    val aliquotaIss: String = "",
    val issRetido: Boolean = false,
    val pisCofinsRetidos: Boolean = false,
    val descricaoServico: String = "",
    val cstPisCofins: String = "",
    /** `VALOR_INFORMADO` = o valor vai em cada nota; senão, percentual padrão. */
    val totaisValorPorNota: Boolean = false,
    val totaisFederal: String = "",
    val totaisEstadual: String = "",
    val totaisMunicipal: String = "",
    val totaisSimples: String = "",
    /** Preservados do servidor (a tela não edita). */
    val tributacaoIss: TributacaoIss? = null,
    val ibsCbsCst: String? = null,
    val ibsCbsClassificacaoTributaria: String? = null,
)

/** O cadastro fiscal como a tela o edita. */
data class FiscalEmitterDraft(
    val tipoPessoa: FiscalTipoPessoa = FiscalTipoPessoa.PJ,
    val documento: String = "",
    val inscricaoMunicipal: String = "",
    val razaoSocial: String = "",
    val nomeFantasia: String = "",
    val email: String = "",
    val telefone: String = "",
    val endereco: FiscalAddressDraft = FiscalAddressDraft(),
    val regime: RegimeApuracao? = null,
    val regimeEspecial: FiscalRegimeEspecial = FiscalRegimeEspecial.NENHUM,
    val simplesHibrido: FiscalSimplesHibrido = FiscalSimplesHibrido.ISS_FORA,
    val numeroInicialDps: String = "",
    /** Cadastro novo começa no TESTE: produção é decisão do dono, com confirmação. */
    val ambiente: FiscalAmbiente = FiscalAmbiente.PRODUCAO_RESTRITA,
    val serie: String? = null,
    val padroes: FiscalDefaultsDraft = FiscalDefaultsDraft(),
)

/** Ajuste dos padrões ao TROCAR de regime: CST `01` aparece em Presumido/Real se estava vazio. */
fun defaultsForRegime(regime: RegimeApuracao?, padroes: FiscalDefaultsDraft): FiscalDefaultsDraft =
    if (usesPisCofinsDefaults(regime) && padroes.cstPisCofins.isBlank()) padroes.copy(cstPisCofins = DEFAULT_CST_PIS_COFINS) else padroes

/** O cadastro do servidor → rascunho editável. */
fun emitterToDraft(e: FiscalEmissor?): FiscalEmitterDraft {
    if (e == null) return FiscalEmitterDraft()
    val words = regimeFromWire(e.regime, e.tipoPessoa, e.padroes)
    val p = e.padroes
    val tot = p?.totaisAproximados
    val end = e.endereco
    return FiscalEmitterDraft(
        tipoPessoa = e.tipoPessoa ?: FiscalTipoPessoa.PJ,
        documento = documentDigits(e.documento),
        inscricaoMunicipal = e.inscricaoMunicipal.orEmpty(),
        razaoSocial = e.razaoSocial.orEmpty(),
        nomeFantasia = e.nomeFantasia.orEmpty(),
        email = e.email.orEmpty(),
        telefone = e.telefone.orEmpty().filter { it.isDigit() },
        endereco = FiscalAddressDraft(
            cep = end?.cep.orEmpty().filter { it.isDigit() },
            logradouro = end?.logradouro.orEmpty(),
            numero = end?.numero.orEmpty(),
            complemento = end?.complemento.orEmpty(),
            bairro = end?.bairro.orEmpty(),
            uf = end?.uf.orEmpty(),
            cidade = cityNameOf(end?.municipioIbge).orEmpty(),
        ),
        regime = words.regime,
        regimeEspecial = words.special,
        simplesHibrido = words.hybrid,
        ambiente = e.ambiente ?: FiscalAmbiente.PRODUCAO_RESTRITA,
        serie = e.serie,
        padroes = defaultsForRegime(
            words.regime,
            FiscalDefaultsDraft(
                codigoTributacaoNacional = p?.codigoTributacaoNacional.orEmpty(),
                codigoTributacaoMunicipal = p?.codigoTributacaoMunicipal.orEmpty(),
                nbs = p?.nbs.orEmpty(),
                aliquotaIss = decimalToInput(p?.aliquotaIss),
                issRetido = p?.issRetido == true,
                pisCofinsRetidos = p?.pisCofinsRetidos == true,
                descricaoServico = p?.descricaoServico.orEmpty(),
                cstPisCofins = p?.cstPisCofins.orEmpty(),
                totaisValorPorNota = tot?.tipo == TipoTotaisAproximados.VALOR_INFORMADO,
                totaisFederal = decimalToInput(tot?.federal),
                totaisEstadual = decimalToInput(tot?.estadual),
                totaisMunicipal = decimalToInput(tot?.municipal),
                totaisSimples = decimalToInput(tot?.simplesNacional),
                tributacaoIss = p?.tributacaoIss,
                ibsCbsCst = p?.ibsCbsCst,
                ibsCbsClassificacaoTributaria = p?.ibsCbsClassificacaoTributaria,
            ),
        ),
    )
}

/**
 * Tributos aproximados que o regime manda nos padrões: MEI = `NAO_INFORMAR`; Simples = `SIMPLES_NACIONAL` com o
 * percentual, se informado; fora do Simples = `PERCENTUAL` ou `VALOR_INFORMADO`. `null` = não mexe (o serviço mostra a
 * pendência).
 */
fun approxTaxesForRegime(regime: RegimeApuracao?, p: FiscalDefaultsDraft): FiscalTotaisAproximados? = when {
    regime == RegimeApuracao.MEI -> FiscalTotaisAproximados(TipoTotaisAproximados.NAO_INFORMAR)
    isSimplesRegime(regime) -> toDecimalString(p.totaisSimples)?.let {
        FiscalTotaisAproximados(TipoTotaisAproximados.SIMPLES_NACIONAL, simplesNacional = it)
    }
    isNonOptantRegime(regime) -> {
        val f = toDecimalString(p.totaisFederal)
        val es = toDecimalString(p.totaisEstadual)
        val m = toDecimalString(p.totaisMunicipal)
        when {
            p.totaisValorPorNota -> FiscalTotaisAproximados(TipoTotaisAproximados.VALOR_INFORMADO)
            f != null || es != null || m != null -> FiscalTotaisAproximados(TipoTotaisAproximados.PERCENTUAL, federal = f, estadual = es, municipal = m)
            else -> null
        }
    }
    else -> null
}

/** Teto do número de DPS no leiaute (15 dígitos). */
const val FISCAL_DPS_NUMBER_MAX: Long = 999_999_999_999_999L

/** "000123" → 123; vazio, zero, não número ou acima do teto → `null`. */
fun parseDpsNumber(value: String?): Long? {
    val s = value?.trim().orEmpty()
    if (!Regex("^\\d{1,15}$").matches(s)) return null
    val n = s.toLongOrNull() ?: return null
    return n.takeIf { it in 1..FISCAL_DPS_NUMBER_MAX }
}

private fun String.orNullIfBlank(): String? = trim().takeIf { it.isNotEmpty() }

/** Rascunho → corpo do `PUT /emissor`: dígitos, decimal com ponto, vazio vira `null`, regime em códigos. */
fun toEmitterBody(d: FiscalEmitterDraft): FiscalEmissorInput {
    val p = d.padroes
    val tributacaoIss = when {
        d.regime == RegimeApuracao.IMUNE_ISENTO -> TributacaoIss.IMUNE
        p.tributacaoIss == TributacaoIss.IMUNE -> null
        else -> p.tributacaoIss
    }
    return FiscalEmissorInput(
        tipoPessoa = d.tipoPessoa,
        documento = documentDigits(d.documento),
        inscricaoMunicipal = d.inscricaoMunicipal.orNullIfBlank(),
        razaoSocial = d.razaoSocial.trim(),
        nomeFantasia = d.nomeFantasia.orNullIfBlank(),
        endereco = d.endereco.toWire() ?: FiscalEndereco(),
        email = d.email.orNullIfBlank(),
        telefone = d.telefone.filter { it.isDigit() }.orNullIfBlank(),
        regime = regimeToWire(d.regime, d.regimeEspecial, d.simplesHibrido),
        ambiente = d.ambiente,
        serie = d.serie?.orNullIfBlank(),
        numeroInicialDps = parseDpsNumber(d.numeroInicialDps),
        padroes = FiscalPadroes(
            codigoTributacaoNacional = p.codigoTributacaoNacional.orNullIfBlank(),
            codigoTributacaoMunicipal = p.codigoTributacaoMunicipal.orNullIfBlank(),
            nbs = p.nbs.orNullIfBlank(),
            aliquotaIss = toDecimalString(p.aliquotaIss),
            issRetido = p.issRetido,
            tributacaoIss = tributacaoIss,
            pisCofinsRetidos = p.pisCofinsRetidos,
            ibsCbsCst = p.ibsCbsCst,
            ibsCbsClassificacaoTributaria = p.ibsCbsClassificacaoTributaria,
            descricaoServico = p.descricaoServico.orNullIfBlank(),
            // MEI recusa CST; fora de Presumido/Real o grupo PIS/COFINS não vai por padrão.
            cstPisCofins = if (usesPisCofinsDefaults(d.regime)) p.cstPisCofins.orNullIfBlank() ?: DEFAULT_CST_PIS_COFINS else null,
            totaisAproximados = approxTaxesForRegime(d.regime, p),
        ),
    )
}

/** Endereço do rascunho no fio; tudo em branco → `null`. */
fun FiscalAddressDraft.toWire(pais: String? = null): FiscalEndereco? {
    val out = FiscalEndereco(
        cep = cep.filter { it.isDigit() }.orNullIfBlank(),
        logradouro = logradouro.orNullIfBlank(),
        numero = numero.orNullIfBlank(),
        complemento = complemento.orNullIfBlank(),
        bairro = bairro.orNullIfBlank(),
        municipioIbge = municipioIbge,
        uf = uf.orNullIfBlank(),
        pais = pais?.orNullIfBlank()?.uppercase()?.takeIf { it != "BR" },
    )
    return out.takeIf { listOf(it.cep, it.logradouro, it.numero, it.complemento, it.bairro, it.municipioIbge, it.uf, it.pais).any { v -> v != null } }
}

fun FiscalEndereco?.toDraft(): FiscalAddressDraft = FiscalAddressDraft(
    cep = this?.cep.orEmpty().filter { it.isDigit() },
    logradouro = this?.logradouro.orEmpty(),
    numero = this?.numero.orEmpty(),
    complemento = this?.complemento.orEmpty(),
    bairro = this?.bairro.orEmpty(),
    uf = this?.uf.orEmpty(),
    cidade = cityNameOf(this?.municipioIbge).orEmpty(),
)

// ───────────────────────────── Validação ─────────────────────────────

/** O que a validação local acusa num campo (a tela traduz em frase). */
enum class FiscalIssue {
    OBRIGATORIO, CPF_INVALIDO, CNPJ_INVALIDO, EMAIL_INVALIDO, CEP_INVALIDO, TELEFONE_INVALIDO, VALOR_INVALIDO,
    ALIQUOTA_INVALIDA, MUNICIPIO_OBRIGATORIO, DPS_INVALIDO, CST_INVALIDO, TOTAIS_OBRIGATORIO, TOTAIS_VALOR_OBRIGATORIO,
    DESCRICAO_CURTA, CERTIFICADO_FORMATO, CERTIFICADO_GRANDE, CERTIFICADO_OBRIGATORIO,
}

/** Erro de um campo: o da validação local ([Issue]) ou a frase do servidor ([Text]). */
sealed interface FiscalFieldError {
    data class Issue(val issue: FiscalIssue) : FiscalFieldError
    data class Text(val text: String) : FiscalFieldError
}

/** Chave = caminho no cadastro/pedido (as mesmas do `fields` do servidor: `tomador.documento`, `padroes.aliquotaIss`). */
typealias FiscalFieldErrors = Map<String, FiscalFieldError>

private fun MutableMap<String, FiscalFieldError>.issue(key: String, issue: FiscalIssue) { put(key, FiscalFieldError.Issue(issue)) }

private fun validateAddress(prefix: String, a: FiscalAddressDraft, required: Boolean, out: MutableMap<String, FiscalFieldError>) {
    if (a.cep.isNotBlank() && !isValidCep(a.cep)) out.issue("$prefix.cep", FiscalIssue.CEP_INVALIDO)
    if (!required) return
    if (a.cep.isBlank()) out.issue("$prefix.cep", FiscalIssue.OBRIGATORIO)
    if (a.logradouro.isBlank()) out.issue("$prefix.logradouro", FiscalIssue.OBRIGATORIO)
    if (a.numero.isBlank()) out.issue("$prefix.numero", FiscalIssue.OBRIGATORIO)
    if (a.bairro.isBlank()) out.issue("$prefix.bairro", FiscalIssue.OBRIGATORIO)
    if (a.municipioIbge == null) out.issue("$prefix.municipioIbge", FiscalIssue.MUNICIPIO_OBRIGATORIO)
}

/** Valida uma etapa do cadastro fiscal. */
fun validateEmitterStep(step: FiscalSetupStep, d: FiscalEmitterDraft): FiscalFieldErrors {
    val out = linkedMapOf<String, FiscalFieldError>()
    when (step) {
        FiscalSetupStep.EMPRESA -> {
            val doc = documentDigits(d.documento)
            when {
                doc.isEmpty() -> out.issue("documento", FiscalIssue.OBRIGATORIO)
                d.tipoPessoa == FiscalTipoPessoa.PF && !CpfValidator.isValid(doc) -> out.issue("documento", FiscalIssue.CPF_INVALIDO)
                d.tipoPessoa == FiscalTipoPessoa.PJ && !CnpjValidator.isValid(doc) -> out.issue("documento", FiscalIssue.CNPJ_INVALIDO)
            }
            if (d.razaoSocial.isBlank()) out.issue("razaoSocial", FiscalIssue.OBRIGATORIO)
            if (d.email.isNotBlank() && !EmailValidator.isValid(d.email.trim())) out.issue("email", FiscalIssue.EMAIL_INVALIDO)
            if (d.telefone.isNotBlank() && !isValidBrPhone(d.telefone)) out.issue("telefone", FiscalIssue.TELEFONE_INVALIDO)
            validateAddress("endereco", d.endereco, required = true, out = out)
            if (d.numeroInicialDps.isNotBlank() && parseDpsNumber(d.numeroInicialDps) == null) out.issue("numeroInicialDps", FiscalIssue.DPS_INVALIDO)
        }
        FiscalSetupStep.REGIME -> if (d.regime == null) out.issue("regime", FiscalIssue.OBRIGATORIO)
        FiscalSetupStep.SERVICO -> {
            val p = d.padroes
            if (p.codigoTributacaoNacional.isBlank()) out.issue("padroes.codigoTributacaoNacional", FiscalIssue.OBRIGATORIO)
            if (p.aliquotaIss.isNotBlank() && !isValidRate(p.aliquotaIss)) out.issue("padroes.aliquotaIss", FiscalIssue.ALIQUOTA_INVALIDA)
            if (usesPisCofinsDefaults(d.regime) && p.cstPisCofins.isNotBlank() && p.cstPisCofins.trim() !in FISCAL_CST_PIS_COFINS) {
                out.issue("padroes.cstPisCofins", FiscalIssue.CST_INVALIDO)
            }
            val key = "padroes.totaisAproximados"
            if (isNonOptantRegime(d.regime) && !p.totaisValorPorNota) {
                val parts = listOf("federal" to p.totaisFederal, "estadual" to p.totaisEstadual, "municipal" to p.totaisMunicipal)
                parts.forEach { (k, v) -> if (v.isNotBlank() && !isValidRate(v)) out.issue("$key.$k", FiscalIssue.ALIQUOTA_INVALIDA) }
                if (parts.all { it.second.isBlank() }) out.issue("$key.federal", FiscalIssue.TOTAIS_OBRIGATORIO)
            }
            if (isSimplesRegime(d.regime) && p.totaisSimples.isNotBlank() && !isValidRate(p.totaisSimples)) {
                out.issue("$key.simplesNacional", FiscalIssue.ALIQUOTA_INVALIDA)
            }
        }
        FiscalSetupStep.CERTIFICADO, FiscalSetupStep.AMBIENTE -> Unit
    }
    return out
}

// ───────────────────────────── Pedido (emissão) ─────────────────────────────

/** O pedido como a tela o edita (valores como digitados, com vírgula). */
data class FiscalInvoiceDraft(
    val refExterna: String? = null,
    /** `yyyy-MM-dd`. */
    val competencia: String = "",
    val tipoTomador: TipoTomador = TipoTomador.CPF,
    val tomadorDocumento: String = "",
    val tomadorNif: String = "",
    val tomadorPais: String = "",
    val tomadorNome: String = "",
    val tomadorEmail: String = "",
    val tomadorTelefone: String = "",
    val tomadorEndereco: FiscalAddressDraft = FiscalAddressDraft(),
    val codigoTributacaoNacional: String = "",
    val codigoTributacaoMunicipal: String = "",
    val nbs: String = "",
    val descricao: String = "",
    val informacoesComplementares: String = "",
    val valorServico: String = "",
    val descontoIncondicionado: String = "",
    val descontoCondicionado: String = "",
    val deducoes: String = "",
    val tributacaoIss: TributacaoIss = TributacaoIss.TRIBUTAVEL,
    val aliquotaIss: String = "",
    val issRetido: Boolean = false,
    val pis: String = "",
    val cofins: String = "",
    val csll: String = "",
    val irrf: String = "",
    val cp: String = "",
    val pisCofinsRetidos: Boolean = false,
    /** O emissor está em `VALOR_INFORMADO`: a nota pede os valores. */
    val pedeTotaisEmValor: Boolean = false,
    val totaisFederal: String = "",
    val totaisEstadual: String = "",
    val totaisMunicipal: String = "",
    val enviarParaTomador: Boolean = true,
)

/** Data de hoje (`yyyy-MM-dd`) no relógio de quem emite. */
fun todayIso(nowMillis: Long = Clock.System.now().toEpochMilliseconds(), zone: TimeZone = TimeZone.currentSystemDefault()): String =
    Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date.toString()

/** O emissor informa os tributos aproximados nota a nota (padrão `VALOR_INFORMADO`). */
fun emitterRequiresApproxTaxValues(e: FiscalEmissor?): Boolean = e?.padroes?.totaisAproximados?.tipo == TipoTotaisAproximados.VALOR_INFORMADO

/**
 * Rascunho inicial da emissão: padrões do emissor → sugestão do produto (tomador, descrição, valor, competência). O
 * último vence. [refExterna] liga a nota ao registro do produto (`locacao:123`).
 */
fun initialInvoiceDraft(
    emitter: FiscalEmissor?,
    suggestion: TomadorSugerido?,
    refExterna: String? = null,
    today: String = todayIso(),
): FiscalInvoiceDraft {
    val p = emitter?.padroes
    var d = FiscalInvoiceDraft(
        refExterna = refExterna,
        competencia = today,
        codigoTributacaoNacional = p?.codigoTributacaoNacional.orEmpty(),
        codigoTributacaoMunicipal = p?.codigoTributacaoMunicipal.orEmpty(),
        nbs = p?.nbs.orEmpty(),
        descricao = p?.descricaoServico.orEmpty(),
        tributacaoIss = p?.tributacaoIss ?: TributacaoIss.TRIBUTAVEL,
        aliquotaIss = decimalToInput(p?.aliquotaIss),
        issRetido = p?.issRetido == true,
        pisCofinsRetidos = p?.pisCofinsRetidos == true,
        pedeTotaisEmValor = emitterRequiresApproxTaxValues(emitter),
    )
    if (suggestion != null) d = d.withTaker(suggestion.tomador).let { s ->
        s.copy(
            competencia = suggestion.competencia?.takeIf { it.isNotBlank() } ?: s.competencia,
            descricao = suggestion.descricaoServico?.takeIf { it.isNotBlank() } ?: s.descricao,
            valorServico = suggestion.valorServico?.let { decimalToInput(it) }?.takeIf { it.isNotEmpty() } ?: s.valorServico,
        )
    }
    return d
}

/** Põe o tomador no rascunho (sugestão ou nota substituída). */
fun FiscalInvoiceDraft.withTaker(t: NfseTomador): FiscalInvoiceDraft {
    val tipo = if (t.tipo == TipoTomador.CPF || t.tipo == TipoTomador.CNPJ) takerTypeOfDocument(t.documento) ?: t.tipo else t.tipo
    return copy(
        tipoTomador = tipo,
        tomadorDocumento = documentDigits(t.documento),
        tomadorNif = t.nif.orEmpty(),
        tomadorPais = t.endereco?.pais.orEmpty(),
        tomadorNome = t.nome.orEmpty(),
        tomadorEmail = t.email.orEmpty(),
        tomadorTelefone = if (tipo == TipoTomador.ESTRANGEIRO) t.telefone.orEmpty() else t.telefone.orEmpty().filter { it.isDigit() },
        tomadorEndereco = t.endereco.toDraft(),
    )
}

/** O que dá para tirar de uma nota emitida para abrir a substituta (o documento não traz o pedido inteiro). */
fun substituteDraft(inv: FiscalDocumento, emitter: FiscalEmissor?, today: String = todayIso()): FiscalInvoiceDraft {
    val base = initialInvoiceDraft(emitter, null, inv.refExterna, today)
    val doc = documentDigits(inv.valores?.tomadorDocumento)
    val tipo = takerTypeOfDocument(doc)
    val withTaker = when {
        tipo != null -> base.withTaker(NfseTomador(tipo, documento = doc, nome = inv.valores?.tomadorNome))
        !inv.valores?.tomadorNome.isNullOrBlank() -> base.withTaker(NfseTomador(TipoTomador.ESTRANGEIRO, nome = inv.valores.tomadorNome))
        else -> base
    }
    return withTaker.copy(valorServico = decimalToInput(inv.valores?.servico))
}

/** Valida o pedido antes da prévia/emissão. */
fun validateInvoiceDraft(d: FiscalInvoiceDraft): FiscalFieldErrors {
    val out = linkedMapOf<String, FiscalFieldError>()
    if (d.tipoTomador != TipoTomador.NAO_IDENTIFICADO) {
        if (d.tipoTomador == TipoTomador.CPF || d.tipoTomador == TipoTomador.CNPJ) {
            val doc = documentDigits(d.tomadorDocumento)
            when {
                doc.isEmpty() -> out.issue("tomador.documento", FiscalIssue.OBRIGATORIO)
                d.tipoTomador == TipoTomador.CPF && !CpfValidator.isValid(doc) -> out.issue("tomador.documento", FiscalIssue.CPF_INVALIDO)
                d.tipoTomador == TipoTomador.CNPJ && !CnpjValidator.isValid(doc) -> out.issue("tomador.documento", FiscalIssue.CNPJ_INVALIDO)
            }
        }
        if (d.tipoTomador == TipoTomador.ESTRANGEIRO && d.tomadorPais.isBlank()) out.issue("tomador.endereco.pais", FiscalIssue.OBRIGATORIO)
        if (d.tomadorNome.isBlank()) out.issue("tomador.nome", FiscalIssue.OBRIGATORIO)
        if (d.tomadorEmail.isNotBlank() && !EmailValidator.isValid(d.tomadorEmail.trim())) out.issue("tomador.email", FiscalIssue.EMAIL_INVALIDO)
        if (d.tomadorTelefone.isNotBlank() && d.tipoTomador != TipoTomador.ESTRANGEIRO && !isValidBrPhone(d.tomadorTelefone)) {
            out.issue("tomador.telefone", FiscalIssue.TELEFONE_INVALIDO)
        }
        if (d.tipoTomador != TipoTomador.ESTRANGEIRO) validateAddress("tomador.endereco", d.tomadorEndereco, required = false, out = out)
    }
    if (d.competencia.isBlank()) out.issue("competencia", FiscalIssue.OBRIGATORIO)
    if (d.codigoTributacaoNacional.isBlank()) out.issue("servico.codigoTributacaoNacional", FiscalIssue.OBRIGATORIO)
    if (d.descricao.isBlank()) out.issue("servico.descricao", FiscalIssue.OBRIGATORIO)
    if (!isPositiveMoney(d.valorServico)) out.issue("valores.servico", FiscalIssue.VALOR_INVALIDO)
    if (d.aliquotaIss.isNotBlank() && !isValidRate(d.aliquotaIss)) out.issue("valores.iss.aliquota", FiscalIssue.ALIQUOTA_INVALIDA)
    if (d.pedeTotaisEmValor && listOf(d.totaisFederal, d.totaisEstadual, d.totaisMunicipal).all { it.isBlank() }) {
        out.issue("valores.totaisAproximados.federal", FiscalIssue.TOTAIS_VALOR_OBRIGATORIO)
    }
    return out
}

/** Rascunho → corpo do `POST`: dígitos, decimais com ponto, vazio vira `null` (herda o padrão). */
fun toInvoiceBody(d: FiscalInvoiceDraft): NfsePedido {
    val tomador = when (d.tipoTomador) {
        TipoTomador.NAO_IDENTIFICADO -> NfseTomador(TipoTomador.NAO_IDENTIFICADO)
        TipoTomador.ESTRANGEIRO -> NfseTomador(
            tipo = TipoTomador.ESTRANGEIRO,
            nif = d.tomadorNif.orNullIfBlank(),
            nome = d.tomadorNome.orNullIfBlank(),
            email = d.tomadorEmail.orNullIfBlank(),
            telefone = d.tomadorTelefone.filter { it.isDigit() || it == '+' }.orNullIfBlank(),
            endereco = d.tomadorEndereco.toWire(pais = d.tomadorPais) ?: d.tomadorPais.orNullIfBlank()?.let { FiscalEndereco(pais = it.uppercase()) },
        )
        else -> NfseTomador(
            tipo = d.tipoTomador,
            documento = documentDigits(d.tomadorDocumento).orNullIfBlank(),
            nome = d.tomadorNome.orNullIfBlank(),
            email = d.tomadorEmail.orNullIfBlank(),
            telefone = d.tomadorTelefone.filter { it.isDigit() }.orNullIfBlank(),
            endereco = d.tomadorEndereco.toWire(),
        )
    }
    val totais = if (d.pedeTotaisEmValor) {
        val f = toDecimalString(d.totaisFederal)
        val es = toDecimalString(d.totaisEstadual)
        val m = toDecimalString(d.totaisMunicipal)
        if (f == null && es == null && m == null) null else FiscalTotaisAproximados(TipoTotaisAproximados.VALOR, federal = f, estadual = es, municipal = m)
    } else null
    return NfsePedido(
        refExterna = d.refExterna?.orNullIfBlank(),
        competencia = d.competencia.orNullIfBlank(),
        tomador = tomador,
        servico = NfseServico(
            codigoTributacaoNacional = d.codigoTributacaoNacional.orNullIfBlank(),
            codigoTributacaoMunicipal = d.codigoTributacaoMunicipal.orNullIfBlank(),
            nbs = d.nbs.orNullIfBlank(),
            descricao = d.descricao.orNullIfBlank(),
            informacoesComplementares = d.informacoesComplementares.orNullIfBlank(),
        ),
        valores = NfseValores(
            servico = toDecimalString(d.valorServico) ?: "0.00",
            descontoIncondicionado = toDecimalString(d.descontoIncondicionado),
            descontoCondicionado = toDecimalString(d.descontoCondicionado),
            deducoes = toDecimalString(d.deducoes),
            iss = NfseIss(tributacao = d.tributacaoIss, aliquota = toDecimalString(d.aliquotaIss), retido = d.issRetido),
            federais = NfseFederais(
                pis = toDecimalString(d.pis),
                cofins = toDecimalString(d.cofins),
                csll = toDecimalString(d.csll),
                irrf = toDecimalString(d.irrf),
                cp = toDecimalString(d.cp),
                pisCofinsRetidos = d.pisCofinsRetidos,
            ),
            totaisAproximados = totais,
        ),
        enviarParaTomador = d.enviarParaTomador,
    )
}

/** Campos da emissão que moram no bloco "avançado" (recolhido): erro neles abre o bloco. */
fun isAdvancedInvoiceField(key: String): Boolean =
    key.startsWith("valores.iss") || key.startsWith("valores.federais") || key.startsWith("valores.desconto") ||
        key.startsWith("valores.deducoes") || key == "servico.nbs" || key == "servico.codigoTributacaoMunicipal" ||
        key == "servico.informacoesComplementares"

/** Mensagens com `field` → erro por campo (a primeira de cada campo vence). */
fun fieldsOfMessages(messages: List<FiscalMensagem>): FiscalFieldErrors {
    val out = linkedMapOf<String, FiscalFieldError>()
    for (m in messages) {
        val f = m.field ?: continue
        if (f !in out) out[f] = FiscalFieldError.Text(messageText(m))
    }
    return out
}

/** O que mostrar de uma mensagem: a frase, com o código entre parênteses quando há. */
fun messageText(m: FiscalMensagem): String {
    val text = m.message.ifBlank { m.code }
    return if (m.code.isNotBlank() && text != m.code) "$text (${m.code})" else text
}

/** A alíquota que o município informa para o serviço, quando há. */
fun suggestedRate(params: FiscalParametrosMunicipais?, serviceCode: String?): String? {
    if (params == null || serviceCode.isNullOrBlank()) return null
    return toDecimalString(params.aliquotas.firstOrNull { it.codigoTributacaoNacional == serviceCode }?.aliquota)
}

/** Nome do arquivo baixado quando o servidor não diz (mesma regra da backlib). */
fun invoiceFileName(id: String, numeroNfse: String?, ext: String): String {
    val num = numeroNfse.orEmpty().filter { it.isLetterOrDigit() && it.code < 128 }.take(40)
    return "nfse-${num.ifEmpty { id }}.$ext"
}

/** Instante ISO → "dd/MM/yyyy HH:mm" no fuso do aparelho; ilegível → `null`. */
fun formatFiscalDateTime(iso: String?, zone: TimeZone = TimeZone.currentSystemDefault()): String? {
    val i = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    val t = i.toLocalDateTime(zone)
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${two(t.day)}/${two(t.month.number)}/${t.year} ${two(t.hour)}:${two(t.minute)}"
}

/** `yyyy-MM-dd` → "dd/MM/yyyy". */
fun formatFiscalDate(iso: String?): String? {
    val d = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${two(d.day)}/${two(d.month.number)}/${d.year}"
}
