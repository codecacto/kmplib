package br.com.codecacto.kmplib.pix

/**
 * Um campo TLV do **EMV MPM** (*Merchant Presented Mode*), o formato do **BR Code** — o payload que
 * viaja dentro do QR Code de Pix.
 *
 * O formato é uma sequência de `ID(2) + tamanho(2) + valor`, sem separadores, e alguns IDs são
 * **templates**: o valor deles é, por sua vez, outra sequência TLV (ver [isEmvTemplateId]). Exemplo
 * mínimo, com o template da conta do recebedor (`26`) aberto:
 *
 * ```
 * 00 02 01                     -> formato do payload = "01"
 * 26 32 [00 14 br.gov.bcb.pix  -> GUI do arranjo
 *        01 14 12345678901]    -> chave Pix
 * 52 04 0000                   -> MCC
 * 53 03 986                    -> moeda (BRL)
 * 58 02 BR                     -> país
 * 59 08 CODECACTO              -> nome do recebedor
 * 60 09 SAO PAULO              -> cidade
 * 63 04 1D3F                   -> CRC-16
 * ```
 *
 * [value] é o valor **cru**, exatamente como veio no payload (sem `trim`, sem caixa alterada) —
 * mexer nele mudaria o CRC e a identidade da plaquinha. Quem quer o valor apresentável usa os campos
 * já tratados de [BrCode].
 *
 * [children] só é preenchido para template cujo interior foi lido com sucesso. Template ilegível
 * **não invalida** o payload: vira folha, com o [value] preservado (ver `parseEmvTlv`).
 */
data class EmvField(
    val id: String,
    val value: String,
    val children: List<EmvField> = emptyList(),
) {
    /** `true` quando este campo é um template cujo interior foi lido como TLV. */
    val hasChildren: Boolean get() = children.isNotEmpty()

    /** Sub-campo direto de [id], ou `null` se ausente. */
    fun child(id: String): EmvField? = children.firstOrNull { it.id == id }

    /** Valor cru do sub-campo [id], ou `null` se ausente. */
    fun childValue(id: String): String? = child(id)?.value
}

/** Campo de primeiro nível com o [id] pedido, ou `null` se ausente. */
fun List<EmvField>.emvField(id: String): EmvField? = firstOrNull { it.id == id }

/** Valor cru do campo de primeiro nível [id], ou `null` se ausente. */
fun List<EmvField>.emvValue(id: String): String? = emvField(id)?.value

/**
 * Por que um payload EMV foi recusado no **enquadramento** (a estrutura `ID+tamanho+valor` não
 * fecha). Recusa de enquadramento é o que separa "isto não é um BR Code" de "isto é um BR Code
 * adulterado" (esse segundo caso é o CRC — ver [PixCrc]).
 */
enum class EmvTlvError {
    /** Entrada vazia. */
    Blank,

    /** Sobraram caracteres insuficientes para um cabeçalho `ID(2)+tamanho(2)`. */
    Truncated,

    /** O ID não são dois dígitos ASCII (o caso de um payload que nem é EMV: link, texto, vCard). */
    InvalidId,

    /** O campo de tamanho não são dois dígitos ASCII. */
    InvalidLength,

    /** O tamanho declarado extrapola o que resta do payload (truncamento/adulteração). */
    LengthOverflow,
}

/** Resultado do parse TLV. Sucesso traz a árvore de campos; falha diz o quê e **onde**. */
sealed interface EmvTlvResult {

    /** Enquadramento válido: os campos consumiram o payload inteiro, sem sobra. */
    data class Success(val fields: List<EmvField>) : EmvTlvResult

    /** Enquadramento inválido. [position] é o índice no payload onde o problema foi detectado. */
    data class Failure(val error: EmvTlvError, val position: Int) : EmvTlvResult
}

/** Tamanho do cabeçalho de um campo EMV MPM: 2 chars de ID + 2 chars de tamanho. */
private const val EMV_HEADER_LENGTH = 4

/**
 * IDs cujo valor é, por especificação, **outra sequência TLV**:
 *
 * - `26`–`51` — *Merchant Account Information* (é onde o Pix mora; ver [BrCodeTag.PIX_GUI]);
 * - `62` — *Additional Data Field* (carrega o `txid` na sub-tag `05`);
 * - `64` — *Merchant Information — Language Template*;
 * - `80`–`99` — *Unreserved Templates* (uso privado do arranjo).
 *
 * Fora dessa lista o valor é tratado como folha. Descer num campo que não é template produziria
 * "filhos" acidentais a partir de texto comum.
 */
fun isEmvTemplateId(id: String): Boolean {
    val numeric = id.toTwoDigitIntOrNull() ?: return false
    return numeric in 26..51 || numeric == 62 || numeric == 64 || numeric in 80..99
}

/**
 * Faz o parse TLV do EMV MPM.
 *
 * **Estrito no enquadramento, tolerante com IDs desconhecidos** — essa assimetria é deliberada:
 *
 * - **Estrito:** tamanho declarado que não cabe, tamanho/ID não-numérico ou sobra de caracteres
 *   fazem o payload inteiro ser recusado ([EmvTlvResult.Failure]). Enquadramento que não fecha é
 *   payload corrompido, e ler "o que der" produziria campos inventados a partir de lixo.
 * - **Tolerante:** ID que esta versão da lib não conhece é **preservado** como campo, não recusado.
 *   O padrão reserva faixas para uso futuro e privado; recusar o que não se conhece faria o app
 *   rejeitar QR legítimo de um PSP novo — falso alarme de fraude no balcão do cliente.
 *
 * Template cujo interior não é TLV válido também é tolerado: vira folha com o valor preservado
 * (`children` vazio), em vez de derrubar o payload todo.
 *
 * O tamanho é contado em **caracteres** do texto (é assim que o padrão define o campo de 2 dígitos,
 * e é o que todo emissor faz). Em payload cujo nome do recebedor tenha acento gravado em UTF-8, um
 * emissor que conte *bytes* produziria enquadramento que não fecha — caso patológico, que aparece
 * aqui como [EmvTlvError.LengthOverflow] em vez de virar campo silenciosamente errado.
 *
 * Nunca lança.
 *
 * @param payload texto cru lido do QR (sem `trim` — ver `parseBrCode`, que normaliza as bordas).
 * @param descendIntoTemplates `false` lê só o primeiro nível (diagnóstico).
 */
fun parseEmvTlv(payload: String, descendIntoTemplates: Boolean = true): EmvTlvResult {
    if (payload.isEmpty()) return EmvTlvResult.Failure(EmvTlvError.Blank, 0)

    val fields = mutableListOf<EmvField>()
    var index = 0

    while (index < payload.length) {
        if (payload.length - index < EMV_HEADER_LENGTH) {
            return EmvTlvResult.Failure(EmvTlvError.Truncated, index)
        }

        val id = payload.substring(index, index + 2)
        if (id.toTwoDigitIntOrNull() == null) {
            return EmvTlvResult.Failure(EmvTlvError.InvalidId, index)
        }

        val declaredLength = payload.substring(index + 2, index + 4).toTwoDigitIntOrNull()
            ?: return EmvTlvResult.Failure(EmvTlvError.InvalidLength, index + 2)

        val valueStart = index + EMV_HEADER_LENGTH
        val valueEnd = valueStart + declaredLength
        if (valueEnd > payload.length) {
            return EmvTlvResult.Failure(EmvTlvError.LengthOverflow, index)
        }

        val value = payload.substring(valueStart, valueEnd)
        val children = if (descendIntoTemplates && isEmvTemplateId(id)) {
            when (val inner = parseEmvTlv(value, descendIntoTemplates = true)) {
                is EmvTlvResult.Success -> inner.fields
                // Template ilegível não derruba o payload: fica como folha, valor preservado.
                is EmvTlvResult.Failure -> emptyList()
            }
        } else {
            emptyList()
        }

        fields += EmvField(id = id, value = value, children = children)
        index = valueEnd
    }

    return EmvTlvResult.Success(fields)
}

/**
 * Dois dígitos **ASCII** (`'0'..'9'`) como `Int`, ou `null`.
 *
 * `Char.isDigit()` do Kotlin aceita dígito de qualquer alfabeto Unicode (ex.: `'٣'`), e
 * `String.toIntOrNull()` também os converteria — o que faria um payload com dígito não-ASCII
 * atravessar o parser com tamanhos que não correspondem ao que o emissor escreveu.
 */
private fun String.toTwoDigitIntOrNull(): Int? {
    if (length != 2) return null
    val tens = this[0]
    val units = this[1]
    if (tens !in '0'..'9' || units !in '0'..'9') return null
    return (tens - '0') * 10 + (units - '0')
}
