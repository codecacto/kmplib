package br.com.codecacto.kmplib.core.text

/**
 * Dobra um texto para **busca por partes** (2.272.0): minúsculas, sem acento e com os espaços
 * colapsados — `"  José  da SILVA "` → `"jose da silva"`. É o par, no aparelho, do
 * `searchTextUnaccented` da backlib (`immutable_unaccent(lower(col))`): "jose" acha "José" porque o
 * acento se dobra **nos dois lados**.
 *
 * ### Como (padrão Unicode, não tabela de acentos)
 * Decomposição canônica **NFD** pela API do sistema (`java.text.Normalizer` no Android,
 * `decomposedStringWithCanonicalMapping` da Foundation no iOS) e remoção das marcas combinantes
 * (`CharCategory.NON_SPACING_MARK`). Cobre qualquer letra latina acentuada — inclusive as que uma
 * tabela à mão esquece (ő, ů, ș, ǎ). As poucas letras que NÃO se decompõem em base + marca (ß, æ, œ,
 * ø, ł, đ, ð, þ, ı) seguem a mesma substituição do `unaccent` do Postgres, para a busca local e a do
 * servidor concordarem.
 *
 * Não mexe em pontuação nem em dígito ("(65) 9999" continua com os parênteses) — quem quer casar só
 * os algarismos de um telefone filtra os dígitos antes.
 */
fun foldForSearch(text: String): String {
    if (text.isEmpty()) return text
    val decomposto = decomposeCanonical(text.lowercase())
    val sb = StringBuilder(decomposto.length)
    var espacoPendente = false
    for (c in decomposto) {
        when {
            c.category == CharCategory.NON_SPACING_MARK -> Unit
            c.isWhitespace() -> espacoPendente = sb.isNotEmpty()
            else -> {
                if (espacoPendente) {
                    sb.append(' ')
                    espacoPendente = false
                }
                val troca = SEM_DECOMPOSICAO[c]
                if (troca != null) sb.append(troca) else sb.append(c)
            }
        }
    }
    return sb.toString()
}

/**
 * Os termos de uma busca, já dobrados ([foldForSearch]) e sem vazios: `"Mar  silva"` →
 * `["mar", "silva"]`. Lista vazia = consulta em branco (mostre tudo).
 */
fun searchTerms(query: String): List<String> =
    foldForSearch(query).split(' ').filter { it.isNotEmpty() }

/**
 * `true` se **todos** os termos de [query] aparecem, como pedaço, em algum dos [fields] — a regra da
 * fábrica para todo campo de busca: "mar" acha Maria e Marcos, "jose" acha José, e palavras separadas
 * por espaço se SOMAM ("mar silva" só acha quem tem as duas). Consulta em branco casa com tudo.
 *
 * Para filtrar uma lista grande, dobre os campos uma vez (guarde o [foldForSearch] de cada item) e
 * compare com [searchTerms] — esta função dobra a cada chamada.
 */
fun matchesSearch(query: String, vararg fields: String?): Boolean {
    val termos = searchTerms(query)
    if (termos.isEmpty()) return true
    val alvo = fields.filterNotNull().joinToString(" ") { foldForSearch(it) }
    return termos.all { alvo.contains(it) }
}

/** Decomposição canônica Unicode (NFD) pela API do sistema. */
internal expect fun decomposeCanonical(text: String): String

/** Letras sem decomposição canônica — as mesmas regras do `unaccent` do Postgres (já em minúsculas). */
private val SEM_DECOMPOSICAO: Map<Char, String> = mapOf(
    'ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'ø' to "o", 'ł' to "l",
    'đ' to "d", 'ð' to "d", 'þ' to "th", 'ı' to "i",
)
