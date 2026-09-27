package br.com.codecacto.kmplib.validation

/**
 * Um telefone decomposto: DDI + número nacional significativo (sem o prefixo de tronco).
 *
 * @property region a região ISO 3166-1 do número quando dá para saber — no `+1` (EUA, Canadá, 20
 *   ilhas do Caribe) e em outros DDIs compartilhados, é a região padrão informada, se ela usa o
 *   mesmo DDI, ou a principal do DDI.
 */
data class PhoneNumberParts(
    val callingCode: Int,
    val nationalNumber: String,
    val region: String?,
) {
    /** Forma **E.164** (`+5565999998888`) — a que vai para o servidor e para o banco. */
    val e164: String get() = "+$callingCode$nationalNumber"
}

/**
 * # Telefone INTERNACIONAL — E.164 com país
 *
 * O caminho brasileiro ([PhoneValidator], `filterPhoneInput`/`PhoneVisualTransformation` do
 * `kmplib-mask`) continua **exatamente como era**: DDD + 8/9 dígitos, sem DDI. Este objeto é o
 * caminho para o app que atende gente de fora — um médico em Lisboa não tem DDD, e o formulário que
 * exige "11 dígitos" simplesmente não o deixa enviar.
 *
 * ## O que é validado, e o que não é
 *
 * - **Estrutura E.164 (ITU-T E.164):** `+`, DDI de 1 a 3 dígitos que exista, no máximo 15 dígitos
 *   no total.
 * - **Comprimento do número nacional POR PAÍS** — mínimo e máximo tirados dos metadados do
 *   **libphonenumber** (Google), a referência de fato do assunto ([PHONE_REGION_DATA], gerado).
 * - **Brasil (`+55`)**: a regra completa de sempre (DDD válido, celular com 9) — [PhoneValidator].
 *
 * **Não** é validado o plano de numeração fino de cada país (quais prefixos são móveis na Alemanha,
 * por exemplo). Isso exigiria embarcar o libphonenumber inteiro no app — não existe versão oficial
 * multiplataforma, e o metadado completo pesa ~500 KB. O servidor, que é quem de fato liga ou manda
 * a mensagem, é o lugar dessa validação fina; aqui o objetivo é **não aceitar lixo** e **não recusar
 * número de verdade**.
 *
 * ## Como a pessoa digita
 *
 * - Com `+`: o DDI vem no próprio número (`+351 912 345 678`) — vale em qualquer região.
 * - Sem `+`: o número é **nacional** da [defaultRegion][parse] (normalmente `deviceRegion()`), e o
 *   prefixo de tronco que se disca dentro do país sai sozinho (`0` de `07911 123456` no Reino Unido,
 *   `1` de `1 415 555 0100` nos EUA).
 */
object InternationalPhone {

    /** Teto de dígitos de um número E.164, DDI incluído (ITU-T E.164 §6). */
    const val MAX_DIGITS: Int = 15

    /** O DDI da região (`"BR"` → 55, `"PT"` → 351), ou `null` se a região não for conhecida. */
    fun callingCodeFor(region: String): Int? = PHONE_REGION_DATA[region.trim().uppercase()]?.callingCode

    /**
     * As regiões que usam o DDI [callingCode], a **principal primeiro** (`1` → US, CA, …; `44` → GB,
     * GG, IM, JE). Vazio para DDI inexistente.
     */
    fun regionsFor(callingCode: Int): List<String> {
        val regioes = PHONE_REGION_DATA.filterValues { it.callingCode == callingCode }.keys.sorted()
        val principal = MAIN_REGION_FOR_SHARED_CODE[callingCode] ?: return regioes
        return listOf(principal) + (regioes - principal)
    }

    /** Todas as regiões conhecidas (ISO 3166-1 alfa-2), em ordem alfabética do código. */
    val supportedRegions: List<String> get() = PHONE_REGION_DATA.keys.sorted()

    /**
     * Filtra o que a pessoa digita: `+` **só no começo**, dígitos e nada mais, no máximo
     * [MAX_DIGITS] dígitos. Espaço, traço e parêntese colados de outro app saem.
     */
    fun filterInput(input: String): String {
        val temMais = input.trimStart().startsWith('+')
        val digitos = input.filter { it in '0'..'9' }.take(MAX_DIGITS)
        return if (temMais) "+$digitos" else digitos
    }

    /**
     * Decompõe [input] em DDI + número nacional, ou `null` se não der para saber o país (sem `+` e
     * sem [defaultRegion] conhecida) ou se o DDI não existir. **Não valida o comprimento** — para
     * isso, [isValid].
     */
    fun parse(input: String, defaultRegion: String?): PhoneNumberParts? {
        val digitos = input.filter { it in '0'..'9' }
        if (digitos.isEmpty()) return null
        val regiaoPadrao = defaultRegion?.trim()?.uppercase()?.takeIf { it in PHONE_REGION_DATA }

        if (input.trimStart().startsWith('+')) {
            // Os DDIs são livres de prefixo (nenhum é começo de outro): no máximo um casa.
            val ddi = (1..3).asSequence()
                .filter { it <= digitos.length }
                .map { digitos.take(it).toInt() }
                .firstOrNull { regionsFor(it).isNotEmpty() }
                ?: return null
            val nacional = digitos.drop(ddi.toString().length)
            val regiao = regiaoPadrao?.takeIf { PHONE_REGION_DATA.getValue(it).callingCode == ddi }
                ?: regionsFor(ddi).first()
            return PhoneNumberParts(ddi, nacional, regiao)
        }

        val regiao = regiaoPadrao ?: return null
        val dados = PHONE_REGION_DATA.getValue(regiao)
        return PhoneNumberParts(dados.callingCode, stripNationalPrefix(digitos, dados), regiao)
    }

    /** [input] na forma E.164 (`+5565999998888`), ou `null` se não for um telefone válido. */
    fun toE164(input: String, defaultRegion: String?): String? =
        parse(input, defaultRegion)?.takeIf { isValidParts(it) }?.e164

    /**
     * `true` se [input] é um telefone plausível: DDI existente, número nacional no comprimento que o
     * país usa, no máximo [MAX_DIGITS] dígitos — e, no Brasil, a regra completa do [PhoneValidator].
     */
    fun isValid(input: String, defaultRegion: String?): Boolean =
        parse(input, defaultRegion)?.let(::isValidParts) ?: false

    /**
     * Forma de exibição: `+55 (65) 99999-8888`, `+1 415-555-0100`, `+351 912 345 678`. Devolve
     * [input] como veio quando não dá para decompor.
     */
    fun format(input: String, defaultRegion: String?): String {
        val partes = parse(input, defaultRegion) ?: return input
        return "+${partes.callingCode} ${formatNational(partes.callingCode, partes.nationalNumber)}".trimEnd()
    }

    /**
     * Formata **enquanto a pessoa digita**, sem nunca tirar nem reordenar um caractere de [raw]
     * (a saída de [filterInput]) — só insere espaço, traço e parêntese. É o que a
     * `VisualTransformation` do `kmplib-mask` precisa para mapear o cursor.
     */
    fun formatAsYouType(raw: String, defaultRegion: String?): String {
        if (raw.isEmpty()) return ""
        if (raw.startsWith('+')) {
            val digitos = raw.drop(1)
            val ddi = (1..3).asSequence()
                .filter { it <= digitos.length }
                .map { digitos.take(it) }
                .firstOrNull { regionsFor(it.toInt()).isNotEmpty() }
                ?: return raw
            val resto = digitos.drop(ddi.length)
            return if (resto.isEmpty()) "+$ddi" else "+$ddi ${formatNational(ddi.toInt(), resto)}"
        }
        val regiao = defaultRegion?.trim()?.uppercase()
        val ddi = regiao?.let { PHONE_REGION_DATA[it]?.callingCode } ?: return groupDigits(raw)
        return formatNational(ddi, raw)
    }

    private fun isValidParts(partes: PhoneNumberParts): Boolean {
        val nacional = partes.nationalNumber
        if (nacional.isEmpty()) return false
        if (partes.callingCode.toString().length + nacional.length > MAX_DIGITS) return false
        if (partes.callingCode == BRAZIL_CALLING_CODE) return PhoneValidator.isValid(nacional)
        val planos = PHONE_REGION_DATA.values.filter { it.callingCode == partes.callingCode }
        return planos.any { nacional.length in it.minNationalLength..it.maxNationalLength }
    }

    /**
     * Tira o prefixo de tronco (`0`, `1`…) quando o número digitado passa do comprimento máximo do
     * país **e** sem ele cabe. Na Itália não há prefixo: o `0` de `06 1234 5678` é do número.
     */
    private fun stripNationalPrefix(digitos: String, dados: PhoneRegionData): String {
        val prefixo = dados.nationalPrefix ?: return digitos
        if (!digitos.startsWith(prefixo)) return digitos
        val semPrefixo = digitos.drop(prefixo.length)
        return if (digitos.length > dados.maxNationalLength && semPrefixo.length >= dados.minNationalLength) {
            semPrefixo
        } else {
            digitos
        }
    }

    private fun formatNational(ddi: Int, nacional: String): String = when (ddi) {
        BRAZIL_CALLING_CODE -> formatBrazilPartial(nacional)
        NANP_CALLING_CODE -> formatNanpPartial(nacional)
        else -> groupDigits(nacional)
    }

    /** `(65) 99999-8888` / `(65) 3322-1100`, parcial enquanto digita. */
    private fun formatBrazilPartial(d: String): String = buildString {
        if (d.isEmpty()) return@buildString
        append('(').append(d.take(2))
        if (d.length <= 2) return@buildString
        append(") ")
        val resto = d.drop(2)
        val corte = if (d.length >= 11) 5 else 4
        append(resto.take(corte))
        if (resto.length > corte) append('-').append(resto.drop(corte))
    }

    /** `415-555-0100`, parcial enquanto digita (plano de numeração norte-americano). */
    private fun formatNanpPartial(d: String): String = buildString {
        append(d.take(3))
        if (d.length > 3) append('-').append(d.substring(3, minOf(6, d.length)))
        if (d.length > 6) append('-').append(d.drop(6))
    }

    /** Grupos de 3 da esquerda; um dígito sobrando no fim vai para o grupo anterior (`912 345 678`). */
    private fun groupDigits(d: String): String {
        if (d.length <= 4) return d
        val grupos = d.chunked(3).toMutableList()
        if (grupos.size > 1 && grupos.last().length == 1) {
            val ultimo = grupos.removeAt(grupos.lastIndex)
            grupos[grupos.lastIndex] = grupos.last() + ultimo
        }
        return grupos.joinToString(" ")
    }

    private const val BRAZIL_CALLING_CODE: Int = 55
    private const val NANP_CALLING_CODE: Int = 1

    /** Região principal dos DDIs compartilhados (a que o libphonenumber usa como "main country"). */
    private val MAIN_REGION_FOR_SHARED_CODE: Map<Int, String> = mapOf(
        1 to "US", 7 to "RU", 39 to "IT", 44 to "GB", 47 to "NO", 61 to "AU", 212 to "MA",
        262 to "RE", 290 to "SH", 358 to "FI", 590 to "GP", 599 to "CW",
    )
}
