package br.com.codecacto.kmplib.bible

import br.com.codecacto.kmplib.core.text.foldForSearch

/**
 * Lê uma referência **digitada** por gente: "Jo 3:16", "João 3,16", "joao 3.16", "John 3:16",
 * "Juan 3:16", "1 Co 13:4-7", "1co 13", "Sl 23", "Lamentações 3:22–23", "salmo 23", "I Coríntios 13".
 *
 * Regras:
 * - Caixa, acento, ponto e espaço no nome do livro não importam ("1 Co.", "1co", "1 cor").
 * - Separador capítulo/versículo `:` `,` `.` ou espaço; intervalo com `-`, `–` ou `—`.
 * - O livro casa, nesta ordem: abreviação/nome exatos **com** acento (o que separa "Jó" de "Jo" =
 *   João em português) → os mesmos sem acento (sem acento, "jo" é João, o uso comum) → apelido
 *   conhecido ("salmo", "cantares", "song of solomon") → **prefixo único** do nome ("gen", "apoc").
 * - Os idiomas são tentados na ordem de [languages] (o do app primeiro), e depois os demais: quem tem
 *   o app em inglês e digita "João 3:16" é atendido igual.
 *
 * Não confere se o capítulo/versículo existe na tradução — isso é do [BibleDatabase].
 */
class BibleReferenceParser(languages: List<BibleLanguage> = BibleLanguage.entries) {

    private val order: List<BibleLanguage> = (languages + BibleLanguage.entries).distinct()
    private val indexes: List<LanguageIndex> = order.map { LanguageIndex.of(it) }

    /** A referência de [input], ou `null` se não reconhecida. */
    fun parse(input: String): VerseRef? {
        val m = PATTERN.matchEntire(input.trim().replace(' ', ' ')) ?: return null
        val rawBook = (m.groupValues[1] + m.groupValues[2]).trim()
        val chapter = m.groupValues[3].toIntOrNull() ?: return null
        val vs = m.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull()
        val ve = m.groupValues[5].takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (chapter < 1 || (vs != null && vs < 1)) return null
        if (ve != null && (vs == null || ve < vs)) return null
        val book = findBook(rawBook) ?: return null
        return VerseRef(book, chapter, vs, ve?.takeIf { it != vs })
    }

    /** O livro do nome/abreviação [text] (sem capítulo), ou `null`. */
    fun findBook(text: String): BibleBook? {
        val accented = keepAccentsKey(text)
        val folded = foldedKey(text)
        if (folded.isEmpty()) return null
        indexes.forEach { idx -> idx.exactAccented[accented]?.let { return it } }
        indexes.forEach { idx -> idx.exactFolded[folded]?.let { return it } }
        ALIASES[folded]?.let { return it }
        if (folded.count { it.isLetter() } >= 2) {
            indexes.forEach { idx ->
                val hits = idx.names.filter { (name, _) -> name.startsWith(folded) }.map { it.second }.distinct()
                if (hits.size == 1) return hits.single()
            }
        }
        return null
    }

    private class LanguageIndex(
        val exactAccented: Map<String, BibleBook>,
        val exactFolded: Map<String, BibleBook>,
        val names: List<Pair<String, BibleBook>>,
    ) {
        companion object {
            private val cache = mutableMapOf<BibleLanguage, LanguageIndex>()

            fun of(language: BibleLanguage): LanguageIndex = cache.getOrPut(language) {
                val table = BibleBookNames.of(language)
                val accented = HashMap<String, BibleBook>()
                val folded = HashMap<String, BibleBook>()
                val all = table.entries.flatMap { (book, n) -> listOf(n.abbreviation to book, n.name to book) }
                all.forEach { (text, book) -> accented.getOrPut(keepAccentsKey(text)) { book } }
                // Sem acento, a forma que JÁ não tinha acento vence ("Jo" = João ganha de "Jó" = Jó).
                all.filter { keepAccentsKey(it.first) == foldedKey(it.first) }
                    .forEach { (text, book) -> folded.getOrPut(foldedKey(text)) { book } }
                all.forEach { (text, book) -> folded.getOrPut(foldedKey(text)) { book } }
                LanguageIndex(accented, folded, table.entries.map { (book, n) -> foldedKey(n.name) to book })
            }
        }
    }

    companion object {
        // [ordinal opcional][nome do livro] [capítulo] [sep versículo [- fim]]
        private val PATTERN = Regex(
            """^((?:[1-3]|i{1,3})\s*\.?\s*)?(\p{L}[\p{L}. ]*?)\s*\.?\s*(\d{1,3})(?:\s*[:,. ]\s*(\d{1,3})(?:\s*[-–—]\s*(\d{1,3}))?)?\s*$""",
            RegexOption.IGNORE_CASE,
        )

        /** Minúsculas, sem espaço nem ponto, ACENTO MANTIDO; "I/II/III" iniciais viram 1/2/3. */
        internal fun keepAccentsKey(text: String): String = romanPrefix(text.lowercase().filter { it.isLetterOrDigit() })

        /** O mesmo, sem acento. */
        internal fun foldedKey(text: String): String = romanPrefix(foldForSearch(text).filter { it.isLetterOrDigit() })

        private fun romanPrefix(s: String): String = when {
            s.startsWith("iii") && s.length > 4 -> "3" + s.drop(3)
            s.startsWith("ii") && s.length > 3 -> "2" + s.drop(2)
            // "i" isolado só vira 1 seguido de nome de livro numerado (não come "isaías").
            s.startsWith("i") && NUMBERED_STEMS.any { s.drop(1).startsWith(it) } -> "1" + s.drop(1)
            else -> s
        }

        private val NUMBERED_STEMS = listOf(
            "sam", "sm", "re", "rs", "kg", "ki", "cr", "ch", "cor", "co", "tes", "ts", "th", "tim", "tm", "ti",
            "ped", "pe", "pet", "jo", "joh", "jn", "jua",
        )

        /** Apelidos usuais que não são nem o nome nem a abreviação da tabela (chaves já sem acento). */
        private val ALIASES: Map<String, BibleBook> = mapOf(
            "salmo" to BibleBook.PSALMS, "psalm" to BibleBook.PSALMS, "psa" to BibleBook.PSALMS, "sal" to BibleBook.PSALMS,
            "cantares" to BibleBook.SONG_OF_SONGS, "canticos" to BibleBook.SONG_OF_SONGS,
            "canticodoscanticos" to BibleBook.SONG_OF_SONGS, "canticosdoscanticos" to BibleBook.SONG_OF_SONGS,
            "cantaresdesalomao" to BibleBook.SONG_OF_SONGS, "songofsolomon" to BibleBook.SONG_OF_SONGS,
            "canticles" to BibleBook.SONG_OF_SONGS, "cantardeloscantares" to BibleBook.SONG_OF_SONGS,
            "revelations" to BibleBook.REVELATION, "apocalypse" to BibleBook.REVELATION,
            "actos" to BibleBook.ACTS, "atosdosapostolos" to BibleBook.ACTS,
            "filemon" to BibleBook.PHILEMON, "abdias" to BibleBook.OBADIAH,
        )
    }
}
