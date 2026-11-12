package br.com.codecacto.kmplib.bible

/**
 * Uma referência bíblica: livro + capítulo, e opcionalmente versículo ou intervalo de versículos
 * no mesmo capítulo.
 *
 * A **chave canônica** ([key]) é independente de idioma e é o que se grava (favoritos, marcações,
 * histórico): `"JHN.3.16"`, `"LAM.3.22-23"`, ou `"PSA.23"` para o capítulo inteiro. Mudar o idioma do
 * aparelho não invalida nada gravado.
 *
 * @property verseStart primeiro versículo; `null` = capítulo inteiro.
 * @property verseEnd último versículo do intervalo; `null` = um versículo só. Nunca menor que [verseStart].
 */
data class VerseRef(
    val book: BibleBook,
    val chapter: Int,
    val verseStart: Int? = null,
    val verseEnd: Int? = null,
) : Comparable<VerseRef> {

    init {
        require(chapter >= 1) { "capítulo inválido: $chapter" }
        require(verseStart == null || verseStart >= 1) { "versículo inválido: $verseStart" }
        require(verseEnd == null || (verseStart != null && verseEnd >= verseStart)) { "intervalo inválido: $verseStart-$verseEnd" }
    }

    /** `true` para o capítulo inteiro. */
    val isWholeChapter: Boolean get() = verseStart == null

    /** `true` para intervalo de mais de um versículo. */
    val isRange: Boolean get() = verseEnd != null && verseEnd != verseStart

    /** Último versículo coberto (`null` no capítulo inteiro). */
    val lastVerse: Int? get() = verseEnd ?: verseStart

    /** Chave canônica: `"JHN.3.16"`, `"LAM.3.22-23"`, `"PSA.23"`. */
    val key: String
        get() = buildString {
            append(book.usfm).append('.').append(chapter)
            if (verseStart != null) {
                append('.').append(verseStart)
                if (verseEnd != null && verseEnd != verseStart) append('-').append(verseEnd)
            }
        }

    /** O mesmo capítulo, sem versículo. */
    fun wholeChapter(): VerseRef = VerseRef(book, chapter)

    /** Os versículos do intervalo, um a um (vazio no capítulo inteiro). */
    fun verses(): List<VerseRef> {
        val start = verseStart ?: return emptyList()
        return (start..(verseEnd ?: start)).map { VerseRef(book, chapter, it) }
    }

    /** `true` se [other] (um versículo) está dentro desta referência. */
    operator fun contains(other: VerseRef): Boolean {
        if (other.book != book || other.chapter != chapter) return false
        val v = other.verseStart ?: return isWholeChapter
        val start = verseStart ?: return true
        return v in start..(verseEnd ?: start)
    }

    /**
     * "João 3:16", "Lamentações 3:22-23", "Salmos 23" no [language]. Com [abbreviated], "Jo 3:16".
     * O separador capítulo/versículo é ":" nos quatro idiomas (o das traduções que a fábrica embarca).
     */
    fun format(language: BibleLanguage, abbreviated: Boolean = false): String = buildString {
        append(if (abbreviated) book.abbreviation(language) else book.name(language))
        append(' ').append(chapter)
        if (verseStart != null) {
            append(':').append(verseStart)
            if (verseEnd != null && verseEnd != verseStart) append('-').append(verseEnd)
        }
    }

    override fun compareTo(other: VerseRef): Int =
        compareValuesBy(this, other, { it.book.order }, { it.chapter }, { it.verseStart ?: 0 }, { it.verseEnd ?: it.verseStart ?: 0 })

    override fun toString(): String = key

    companion object {
        private val KEY = Regex("""^([1-3]?[A-Za-z]{2,3})\.(\d{1,3})(?:\.(\d{1,3})(?:-(\d{1,3}))?)?$""")

        /** Lê a chave canônica (`"JHN.3.16"`); `null` se malformada ou de livro desconhecido. */
        fun fromKey(key: String): VerseRef? {
            val m = KEY.matchEntire(key.trim()) ?: return null
            val book = BibleBook.fromUsfm(m.groupValues[1]) ?: return null
            val ch = m.groupValues[2].toInt()
            val vs = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            val ve = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()
            if (ch < 1 || (vs != null && vs < 1) || (ve != null && (vs == null || ve < vs))) return null
            return VerseRef(book, ch, vs, ve?.takeIf { it != vs })
        }
    }
}
