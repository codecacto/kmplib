package br.com.codecacto.kmplib.bible

import br.com.codecacto.kmplib.core.text.foldForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Um versículo com o texto da tradução. [ref] é sempre de UM versículo. */
data class BibleVerse(val ref: VerseRef, val text: String)

/**
 * O que a tabela `meta` do arquivo diz da tradução — o crédito que a licença exige ("Sobre a
 * tradução") sai daqui, nunca de texto fixo no app.
 */
data class BibleMeta(
    val id: String,
    val name: String,
    val abbreviation: String,
    val language: String,
    val license: String,
    val credit: String,
    val url: String,
    val sourceVersion: String,
    val generatedAt: String,
    val schema: Int,
    /** Todas as chaves, inclusive as que esta versão da lib não conhece. */
    val raw: Map<String, String>,
)

/** Os capítulos que o arquivo TEM de um livro (o arquivo pode ser um recorte). */
data class BibleBookAvailability(val book: BibleBook, val chapters: List<Int>)

/**
 * Leitor do **banco embarcado** de uma tradução (SQLite, somente leitura) — o contrato do
 * `Ferramentas/conteudo-biblico/` (esquema 1):
 *
 * ```sql
 * meta(chave TEXT PRIMARY KEY, valor TEXT NOT NULL)
 * livro(id TEXT PRIMARY KEY, ordem INTEGER NOT NULL, capitulos INTEGER NOT NULL)
 * verso(livro, cap, ver, texto, busca, PRIMARY KEY(livro,cap,ver)) WITHOUT ROWID
 * ```
 *
 * Abra pelo [BibleAssets.open] (que copia o arquivo de `composeResources/files/` para o armazenamento
 * do app). O arquivo pode ser **recorte** (só alguns versos): nada aqui supõe a Bíblia inteira — o
 * que existe se pergunta ([availability], [chapters], [verseCount]).
 *
 * Android: `SQLiteDatabase.openDatabase(…, OPEN_READONLY)`. iOS: SQLite do sistema pelo SQLiter (o
 * mesmo motor do SQLDelight), sem checagem de versão — nada é gravado no arquivo.
 *
 * Todas as consultas suspendem e rodam fora da main thread. Feche com [close] quando não precisar mais.
 */
class BibleDatabase internal constructor(private val connection: BibleSqlConnection) {

    private val mutex = Mutex()

    private suspend fun <T> read(block: (BibleSqlConnection) -> T): T =
        withContext(Dispatchers.Default) { mutex.withLock { block(connection) } }

    /** A tabela `meta`. */
    suspend fun meta(): BibleMeta = read { c ->
        val m = c.query("SELECT chave, valor FROM meta", emptyList()).associate { it[0] as String to (it[1] as String) }
        BibleMeta(
            id = m["id"].orEmpty(), name = m["nome"].orEmpty(), abbreviation = m["sigla"].orEmpty(),
            language = m["idioma"].orEmpty(), license = m["licenca"].orEmpty(), credit = m["credito"].orEmpty(),
            url = m["url"].orEmpty(), sourceVersion = m["versao_fonte"].orEmpty(), generatedAt = m["gerado_em"].orEmpty(),
            schema = m["esquema"]?.toIntOrNull() ?: 0, raw = m,
        )
    }

    /** Livros e capítulos que existem de fato no arquivo, na ordem canônica. */
    suspend fun availability(): List<BibleBookAvailability> = read { c ->
        c.query("SELECT DISTINCT livro, cap FROM verso", emptyList())
            .mapNotNull { row -> BibleBook.fromUsfm(row[0] as String)?.let { it to (row[1] as Long).toInt() } }
            .groupBy({ it.first }, { it.second })
            .map { (book, chs) -> BibleBookAvailability(book, chs.sorted()) }
            .sortedBy { it.book.order }
    }

    /** Capítulos existentes de [book] (vazio se o livro não está no arquivo). */
    suspend fun chapters(book: BibleBook): List<Int> = read { c ->
        c.query("SELECT DISTINCT cap FROM verso WHERE livro = ? ORDER BY cap", listOf(book.usfm))
            .map { (it[0] as Long).toInt() }
    }

    /** Quantos versículos o capítulo tem NO ARQUIVO (o maior número de versículo presente). */
    suspend fun verseCount(book: BibleBook, chapter: Int): Int = read { c ->
        (c.query("SELECT MAX(ver) FROM verso WHERE livro = ? AND cap = ?", listOf(book.usfm, chapter.toLong()))
            .firstOrNull()?.get(0) as? Long)?.toInt() ?: 0
    }

    /** Um versículo (de [ref] vale só o primeiro versículo); `null` se não está no arquivo. */
    suspend fun verse(ref: VerseRef): BibleVerse? {
        val v = ref.verseStart ?: return null
        return passage(VerseRef(ref.book, ref.chapter, v)).firstOrNull()
    }

    /**
     * O trecho de [ref]: um versículo, o intervalo ou o capítulo inteiro, na ordem. Versos ausentes
     * (recorte) simplesmente não vêm.
     */
    suspend fun passage(ref: VerseRef): List<BibleVerse> = read { c ->
        val start = ref.verseStart
        val rows = if (start == null) {
            c.query(SELECT_VERSES + " WHERE livro = ? AND cap = ? ORDER BY ver", listOf(ref.book.usfm, ref.chapter.toLong()))
        } else {
            c.query(
                SELECT_VERSES + " WHERE livro = ? AND cap = ? AND ver BETWEEN ? AND ? ORDER BY ver",
                listOf(ref.book.usfm, ref.chapter.toLong(), start.toLong(), (ref.verseEnd ?: start).toLong()),
            )
        }
        rows.mapNotNull(::toVerse)
    }

    /** O capítulo inteiro. */
    suspend fun chapter(book: BibleBook, chapter: Int): List<BibleVerse> = passage(VerseRef(book, chapter))

    /**
     * Busca **por partes, sem acento**: cada palavra de [query] precisa aparecer (como pedaço) no
     * versículo — "pastor" acha "pastores", "jose" acha "José", "amor deus" exige as duas. Ordem
     * canônica. Consulta com `instr()` sobre a coluna `busca` e parâmetros ligados — nunca `LIKE`
     * montado à mão (o `%`/`_` digitado viraria coringa).
     *
     * @param books restringe a estes livros (`null` = todos).
     * @return até [limit] versículos a partir de [offset]; query sem palavra = lista vazia.
     */
    suspend fun search(query: String, limit: Int = 50, offset: Int = 0, books: Set<BibleBook>? = null): List<BibleVerse> {
        val terms = bibleSearchTerms(query)
        if (terms.isEmpty() || limit <= 0) return emptyList()
        val (where, args) = searchWhere(terms, books)
        val lim = limit.coerceAtMost(MAX_SEARCH_LIMIT)
        val off = offset.coerceAtLeast(0)
        return read { c ->
            c.query(
                "SELECT v.livro, v.cap, v.ver, v.texto FROM verso v JOIN livro l ON l.id = v.livro WHERE $where " +
                    "ORDER BY l.ordem, v.cap, v.ver LIMIT $lim OFFSET $off",
                args,
            ).mapNotNull(::toVerse)
        }
    }

    /** Quantos versículos casam com [query] (para "123 resultados"). */
    suspend fun searchCount(query: String, books: Set<BibleBook>? = null): Int {
        val terms = bibleSearchTerms(query)
        if (terms.isEmpty()) return 0
        val (where, args) = searchWhere(terms, books)
        return read { c -> ((c.query("SELECT COUNT(*) FROM verso v WHERE $where", args).firstOrNull()?.get(0)) as? Long)?.toInt() ?: 0 }
    }

    /** Fecha o arquivo. Depois disso nenhuma consulta funciona. */
    fun close() = connection.close()

    private fun searchWhere(terms: List<String>, books: Set<BibleBook>?): Pair<String, List<Any>> {
        val clauses = terms.map { "instr(v.busca, ?) > 0" }.toMutableList()
        val args: MutableList<Any> = terms.toMutableList()
        if (!books.isNullOrEmpty()) {
            clauses += "v.livro IN (" + books.joinToString(",") { "?" } + ")"
            args.addAll(books.map { it.usfm })
        }
        return clauses.joinToString(" AND ") to args
    }

    private fun toVerse(row: List<Any?>): BibleVerse? {
        val book = BibleBook.fromUsfm(row[0] as String) ?: return null
        return BibleVerse(VerseRef(book, (row[1] as Long).toInt(), (row[2] as Long).toInt()), row[3] as String)
    }

    companion object {
        private const val SELECT_VERSES = "SELECT livro, cap, ver, texto FROM verso"

        /** Teto de uma página de busca. */
        const val MAX_SEARCH_LIMIT: Int = 500

        /** Abre o arquivo em [path] somente leitura. */
        suspend fun open(path: String): BibleDatabase =
            withContext(Dispatchers.Default) { BibleDatabase(openBibleSqlConnection(path)) }
    }
}

/**
 * A normalização da coluna `busca` do gerador (`normalizar_busca` do `gerar.py`): minúsculas, sem
 * acento, só letras `a-z`/dígitos (e `ñ`→`n`), espaço simples. Puro — a busca só acha se o termo for
 * normalizado do MESMO jeito.
 */
fun normalizeBibleSearchText(text: String): String =
    foldForSearch(text.replace('ñ', 'n').replace('Ñ', 'n'))
        .map { if (it in 'a'..'z' || it in '0'..'9') it else ' ' }
        .joinToString("")
        .split(' ').filter { it.isNotEmpty() }.joinToString(" ")

/** As palavras de [query] para a busca (normalizadas, sem repetição). Puro. */
fun bibleSearchTerms(query: String): List<String> =
    normalizeBibleSearchText(query).split(' ').filter { it.isNotEmpty() }.distinct()

/** Uma conexão SQLite somente leitura (por plataforma). */
internal interface BibleSqlConnection {
    /** Linhas com as colunas em `String`, `Long`, `Double` ou `null`. [args] em `String`/`Long`. */
    fun query(sql: String, args: List<Any>): List<List<Any?>>

    fun close()
}

internal expect fun openBibleSqlConnection(path: String): BibleSqlConnection
