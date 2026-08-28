package br.com.codecacto.kmplib.pix

/**
 * **Geração** de BR Code Pix estático — a contraparte de [parseBrCode].
 *
 * O módulo `pix` nasceu para **ler** plaquinha (conferir CRC, comparar recebedor, desconfiar de um
 * QR trocado). Faltava o outro lado: o app que precisa **cobrar** — a diarista mandando o QR da
 * própria diária, o prestador anexando o Pix ao orçamento. Sem isto, cada projeto monta a string
 * EMV na mão, e é exatamente o tipo de código em que um detalhe errado produz um QR que **abre no
 * app do banco e falha na confirmação** — o pior desfecho possível, porque parece que funcionou.
 *
 * ## Estático, e só
 * Aqui se gera o caso da **plaquinha**: chave + nome + cidade, com valor opcional. O caso
 * **dinâmico** (tag `01 = 12`, com URL de payload na sub-tag `25`) exige um PSP emitindo a cobrança
 * e devolvendo a URL — não é algo que um cliente monta sozinho, e oferecer a API sugeriria que é.
 * Quem tiver PSP monta o payload com a URL que ele devolver.
 *
 * ## As cinco armadilhas que este arquivo existe para fechar
 *
 * 1. **O CRC cobre `"6304"`.** O cálculo inclui o ID e o tamanho da própria tag de CRC; só os 4
 *    dígitos do valor ficam de fora. Errar isso é o modo clássico de "todo QR dá inválido" — e por
 *    isso a assinatura sai do [PixCrc.sign], nunca concatenada à mão aqui.
 * 2. **O tamanho do TLV é contado em CARACTERES, e o CRC é sobre BYTES UTF-8.** Um nome acentuado
 *    ("Rosângela") faz as duas contas divergirem, e leitores de banco reagem de formas diferentes ao
 *    desencontro. Por isso nome e cidade passam por [sanitizeText]: sem acento, maiúsculas, ASCII.
 *    Não é preferência estética — é o que mantém as duas contagens iguais.
 * 3. **Valor com vírgula, com separador de milhar ou com 3 casas** é recusado pelo banco na hora de
 *    confirmar. [normalizeAmount] aceita as formas que uma tela de app produz e devolve o formato
 *    único do padrão (`"1234.50"`), ou falha ANTES de gerar o QR.
 * 4. **`txid` tem alfabeto restrito** (A–Z, a–z, 0–9) e teto de 25. Um traço ou um espaço vindos de
 *    um "número do pedido" livre passam pelo parser da lib e quebram no PSP.
 * 5. **Chave vazia gera um QR sintaticamente válido e inútil.** Falha explícita, com motivo.
 *
 * ## Devolve resultado, não exceção
 * Mesma disciplina do [parseBrCode]: os motivos de recusa são finitos e conhecidos, e a tela precisa
 * dizer **qual** deles aconteceu ("informe a cidade", "o valor está inválido"). Um `throw` genérico
 * viraria "erro ao gerar o código" para cinco causas diferentes.
 */

/** O que se cobra: quem recebe, quanto e por quê. */
data class PixCharge(
    /** Chave Pix do recebedor — CPF/CNPJ (só dígitos), e-mail, telefone `+55…` ou chave aleatória. */
    val key: String,

    /**
     * Nome de quem recebe (tag `59`), até 25 caracteres depois da normalização.
     *
     * É o texto que aparece na tela do banco de quem paga, então vale o nome que a pessoa
     * reconhece. Acento e minúscula são normalizados — ver [sanitizeText].
     */
    val merchantName: String,

    /** Cidade do recebedor (tag `60`), até 15 caracteres depois da normalização. */
    val merchantCity: String,

    /**
     * Valor da cobrança. `null` = **QR sem valor** (quem paga digita) — o caso legítimo da
     * plaquinha de balcão, e o default do padrão.
     *
     * Aceita `"150"`, `"150,00"`, `"1.234,50"` e `"1234.50"`; sai sempre como `"1234.50"`.
     */
    val amount: String? = null,

    /**
     * Identificador da cobrança (tag `62` → `05`). `null` vira `"***"`, que é como o padrão diz
     * "não informado" — e **não** string vazia, que alguns PSPs recusam.
     */
    val txid: String? = null,

    /** Descrição livre do recebedor (sub-tag `02` da conta). Opcional; muitos leitores ignoram. */
    val description: String? = null,
)

/** Por que um [PixCharge] não pôde virar payload. */
enum class PixBrCodeError {
    /** Chave vazia, ou que não sobrou nada depois de aparar. */
    EmptyKey,

    /** Chave longa demais para o template da conta. */
    KeyTooLong,

    /** Nome vazio, ou que ficou vazio depois da normalização (ex.: só emojis). */
    EmptyMerchantName,

    /** Cidade vazia, ou que ficou vazia depois da normalização. */
    EmptyMerchantCity,

    /** Valor não numérico, negativo, zero, com mais de 2 casas ou grande demais. */
    InvalidAmount,

    /** `txid` fora do alfabeto permitido ou maior que 25. */
    InvalidTxid,
}

/** Desfecho da geração. */
sealed interface PixBrCodeResult {

    /** O payload pronto, com CRC assinado. É este texto que vira QR **e** "Pix Copia e Cola". */
    data class Ok(val payload: String) : PixBrCodeResult

    data class Invalid(val error: PixBrCodeError) : PixBrCodeResult

    /** O payload, ou `null` quando a geração falhou — para quem só quer o caminho feliz. */
    val payloadOrNull: String? get() = (this as? Ok)?.payload
}

/**
 * Monta o BR Code estático de [charge].
 *
 * O resultado passa por [parseBrCode] sem ressalva: é EMV MPM válido, com CRC fechado, GUI Pix e a
 * chave no lugar certo — a suíte prova isso gerando e relendo.
 */
@Suppress("ReturnCount")
fun buildPixBrCode(charge: PixCharge): PixBrCodeResult {
    val key = charge.key.trim()
    if (key.isEmpty()) return PixBrCodeResult.Invalid(PixBrCodeError.EmptyKey)
    if (key.length > MAX_KEY) return PixBrCodeResult.Invalid(PixBrCodeError.KeyTooLong)

    val name = sanitizeText(charge.merchantName, MAX_NAME)
    if (name.isEmpty()) return PixBrCodeResult.Invalid(PixBrCodeError.EmptyMerchantName)

    val city = sanitizeText(charge.merchantCity, MAX_CITY)
    if (city.isEmpty()) return PixBrCodeResult.Invalid(PixBrCodeError.EmptyMerchantCity)

    val amount = charge.amount?.let { raw ->
        normalizeAmount(raw) ?: return PixBrCodeResult.Invalid(PixBrCodeError.InvalidAmount)
    }

    val txid = (charge.txid?.trim()?.takeIf { it.isNotEmpty() } ?: TXID_NOT_INFORMED).let { raw ->
        if (raw != TXID_NOT_INFORMED && !raw.isValidTxid()) {
            return PixBrCodeResult.Invalid(PixBrCodeError.InvalidTxid)
        }
        raw
    }

    // A conta inteira (GUI + chave + descrição) é UM valor TLV, e EMV MPM não tem tamanho estendido:
    // acima de 99 caracteres o campo não pode ser escrito. A descrição é o único campo elástico, e
    // por isso é ela que **encolhe** — recusar o QR inteiro por causa de um texto decorativo, que
    // metade dos leitores nem exibe, seria trocar um problema cosmético por um pagamento que não
    // acontece. Chave e GUI cabem sempre: 18 + (4 + 77) = 99 no pior caso.
    val fixo = tlv(BrCodeTag.ACCOUNT_GUI, BrCodeTag.PIX_GUI) + tlv(BrCodeTag.ACCOUNT_KEY, key)
    val espacoParaDescricao = MAX_TLV_VALUE - fixo.length - TLV_OVERHEAD
    val description = charge.description
        ?.let { sanitizeText(it, minOf(MAX_DESCRIPTION, maxOf(0, espacoParaDescricao))) }
        ?.takeIf { it.isNotEmpty() }

    val accountBody = fixo + (description?.let { tlv(BrCodeTag.ACCOUNT_DESCRIPTION, it) } ?: "")

    val body = buildString {
        append(tlv(BrCodeTag.FORMAT_INDICATOR, FORMAT_VERSION))
        append(tlv(BrCodeTag.INITIATION_METHOD, PixInitiationMethod.Static.code))
        append(tlv(BrCodeTag.MERCHANT_ACCOUNT_FIRST, accountBody))
        append(tlv(BrCodeTag.MERCHANT_CATEGORY_CODE, MCC_NOT_INFORMED))
        append(tlv(BrCodeTag.TRANSACTION_CURRENCY, BrCodeTag.CURRENCY_BRL))
        if (amount != null) append(tlv(BrCodeTag.TRANSACTION_AMOUNT, amount))
        append(tlv(BrCodeTag.COUNTRY_CODE, BrCodeTag.COUNTRY_BR))
        append(tlv(BrCodeTag.MERCHANT_NAME, name))
        append(tlv(BrCodeTag.MERCHANT_CITY, city))
        append(tlv(BrCodeTag.ADDITIONAL_DATA, tlv(BrCodeTag.ADDITIONAL_TXID, txid)))
    }

    return PixBrCodeResult.Ok(PixCrc.sign(body))
}

// -- Normalizações ------------------------------------------------------------------------------

/**
 * Deixa o texto no formato que **todo** leitor de banco aceita: sem acento, maiúsculo, ASCII
 * imprimível, espaços colapsados e truncado no limite da tag.
 *
 * ## Por que não basta truncar
 * O tamanho do TLV é contado em **caracteres**; o CRC é calculado sobre **bytes UTF-8**. "Rosângela"
 * tem 9 caracteres e 10 bytes — e emissores divergem sobre qual das duas contagens escrever no
 * campo de tamanho. Normalizar para ASCII faz as duas contas coincidirem e tira a questão da mesa.
 *
 * O truncamento vem **depois** da limpeza: cortar antes deixaria o limite ser gasto por acentos que
 * iam sumir de qualquer forma.
 */
internal fun sanitizeText(raw: String, maxLength: Int): String {
    val semAcento = buildString(raw.length) {
        for (c in raw) {
            val substituto = ACCENT_MAP[c.uppercaseChar()]
            when {
                substituto != null -> append(substituto)
                // Faixa ASCII imprimível: letras, dígitos, pontuação simples e espaço.
                c.code in ASCII_PRINTABLE_MIN..ASCII_PRINTABLE_MAX -> append(c.uppercaseChar())
                // Qualquer outra coisa (emoji, ideograma, controle) vira espaço — some no colapso.
                else -> append(' ')
            }
        }
    }
    return semAcento.split(' ').filter { it.isNotEmpty() }.joinToString(" ").take(maxLength).trim()
}

/**
 * Traduz o que uma tela de app produz para o formato único do padrão (`"1234.50"`).
 *
 * Aceita `"150"`, `"150,00"`, `"1.234,50"`, `"1234.50"` e `"R$ 150,00"`. Devolve `null` — e não um
 * palpite — quando o texto não é dinheiro: gerar um QR com valor errado é pior do que não gerar.
 *
 * **O separador único seguido de exatamente 3 dígitos é RECUSADO, não adivinhado.** `"1.234"` pode
 * ser mil duzentos e trinta e quatro (milhar) ou um vírgula duzentos e trinta e quatro (3 casas, que
 * o padrão nem aceita) — e chutar erra por **mil vezes** em alguma das direções. Um leitor de moeda
 * de tela pode arriscar; aqui se gera um **instrumento de pagamento**, e a recusa alta tem conserto
 * ("escreva 1234,00") enquanto uma cobrança mil vezes maior não tem.
 *
 * Ambíguo é só esse caso. `"1.234,50"` (os dois separadores) e `"1.234.567"` (o mesmo repetido) são
 * inequívocos e passam; a forma canônica `"1234.50"` nunca depende de heurística nenhuma.
 *
 * **Zero é recusado.** Um BR Code com `54` = `"0.00"` não é "sem valor": é uma cobrança de zero
 * real, que o banco recusa na confirmação. Quem quer QR sem valor passa `amount = null`.
 */
@Suppress("ReturnCount")
internal fun normalizeAmount(raw: String): String? {
    val limpo = raw.filter { it.isDigit() || it == ',' || it == '.' }
    if (limpo.isEmpty()) return null

    val virgulas = limpo.count { it == ',' }
    val pontos = limpo.count { it == '.' }
    val corte = maxOf(limpo.lastIndexOf(','), limpo.lastIndexOf('.'))

    val inteiros: String
    val centavos: String
    when {
        // Sem separador: só inteiros. "150" → 150,00.
        corte < 0 -> {
            inteiros = limpo
            centavos = ""
        }

        // Os DOIS separadores presentes: o último é o decimal, por definição. "1.234,50" e
        // "1,234.50" caem aqui, e nenhum dos dois é ambíguo.
        virgulas > 0 && pontos > 0 -> {
            inteiros = limpo.substring(0, corte).filter { it.isDigit() }
            centavos = limpo.substring(corte + 1).filter { it.isDigit() }
        }

        // O MESMO separador repetido só pode ser milhar: "1.234.567".
        virgulas > 1 || pontos > 1 -> {
            inteiros = limpo.filter { it.isDigit() }
            centavos = ""
        }

        else -> {
            val depois = limpo.substring(corte + 1).filter { it.isDigit() }
            val antes = limpo.substring(0, corte).filter { it.isDigit() }
            when (depois.length) {
                1, 2 -> {
                    inteiros = antes
                    centavos = depois
                }

                // AMBÍGUO, e por isso RECUSADO. "1.234" é mil duzentos e trinta e quatro; "150.005"
                // poderia ser cento e cinquenta mil e cinco — ou um valor de 3 casas, que o padrão
                // nem aceita. Chutar erra por MIL VEZES em alguma das direções, e isto aqui gera um
                // instrumento de pagamento, não um rótulo de tela. A recusa é alta e tem conserto:
                // a pessoa escreve "1234,00" e segue.
                GROUP_DIGITS -> return null

                else -> return null
            }
        }
    }

    if (centavos.length > MAX_DECIMALS) return null
    if (inteiros.isEmpty() && centavos.isEmpty()) return null
    if (inteiros.length > MAX_INTEGER_DIGITS) return null

    val parteInteira = inteiros.trimStart('0').ifEmpty { "0" }
    val parteDecimal = centavos.padEnd(MAX_DECIMALS, '0')
    if (parteInteira == "0" && parteDecimal == "00") return null

    return "$parteInteira.$parteDecimal"
}

/** `txid` do padrão: alfanumérico ASCII, 1..25. */
private fun String.isValidTxid(): Boolean =
    length in 1..MAX_TXID && all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }

/** Um campo TLV: `ID(2) + tamanho(2) + valor`. */
private fun tlv(id: String, value: String): String =
    id + value.length.toString().padStart(2, '0') + value

// -- Constantes ---------------------------------------------------------------------------------

/** Versão do payload (tag `00`). Hoje o padrão só define `"01"`. */
private const val FORMAT_VERSION = "01"

/** MCC "não informado" — o que se usa quando quem recebe é pessoa física. */
private const val MCC_NOT_INFORMED = "0000"

/** `txid` ausente. O padrão define `"***"`; string vazia é recusada por parte dos PSPs. */
internal const val TXID_NOT_INFORMED: String = "***"

private const val MAX_TLV_VALUE = 99
private const val MAX_KEY = 77
private const val MAX_NAME = 25
private const val MAX_CITY = 15
private const val MAX_TXID = 25
private const val MAX_DESCRIPTION = 40
private const val MAX_DECIMALS = 2

/** `ID(2) + tamanho(2)` — o custo fixo de qualquer campo TLV. */
private const val TLV_OVERHEAD = 4

/** Um grupo de milhar tem 3 dígitos. Ver [normalizeAmount]. */
private const val GROUP_DIGITS = 3

/** Tag `54` tem 13 caracteres no total; `"9999999999.99"` já ocupa os 13. */
private const val MAX_INTEGER_DIGITS = 10

private const val ASCII_PRINTABLE_MIN = 0x20
private const val ASCII_PRINTABLE_MAX = 0x7E

/**
 * Acentos que o português usa, em maiúscula (o texto já é convertido antes da consulta).
 *
 * Tabela explícita, e não `Normalizer` da JVM: `kotlinx` não tem normalização Unicode em
 * `commonMain`, e depender de `java.text` mataria o iOS. A lista cobre o alfabeto português; o que
 * escapar dela vira espaço, que é degradação visível e não corrupção silenciosa.
 */
private val ACCENT_MAP: Map<Char, Char> = mapOf(
    'Á' to 'A', 'À' to 'A', 'Ã' to 'A', 'Â' to 'A', 'Ä' to 'A',
    'É' to 'E', 'È' to 'E', 'Ê' to 'E', 'Ë' to 'E',
    'Í' to 'I', 'Ì' to 'I', 'Î' to 'I', 'Ï' to 'I',
    'Ó' to 'O', 'Ò' to 'O', 'Õ' to 'O', 'Ô' to 'O', 'Ö' to 'O',
    'Ú' to 'U', 'Ù' to 'U', 'Û' to 'U', 'Ü' to 'U',
    'Ç' to 'C', 'Ñ' to 'N',
)
