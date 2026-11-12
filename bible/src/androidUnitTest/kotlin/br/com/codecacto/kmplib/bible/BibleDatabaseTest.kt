package br.com.codecacto.kmplib.bible

import kotlinx.coroutines.test.runTest
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O leitor contra SQLite DE VERDADE (JDBC), com um arquivo no formato do gerador (esquema 1). */
class BibleDatabaseTest {

    private class JdbcConnection(private val c: Connection) : BibleSqlConnection {
        override fun query(sql: String, args: List<Any>): List<List<Any?>> = c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> if (a is Long) st.setLong(i + 1, a) else st.setString(i + 1, a.toString()) }
            st.executeQuery().use { rs ->
                val out = ArrayList<List<Any?>>()
                val n = rs.metaData.columnCount
                while (rs.next()) out += (1..n).map { i ->
                    when (val v = rs.getObject(i)) { is Int -> v.toLong(); else -> v }
                }
                out
            }
        }
        override fun close() = c.close()
    }

    private val file = File.createTempFile("biblia-teste", ".db")
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").apply {
        createStatement().use { s ->
            s.executeUpdate("CREATE TABLE meta(chave TEXT PRIMARY KEY, valor TEXT NOT NULL)")
            s.executeUpdate("CREATE TABLE livro(id TEXT PRIMARY KEY, ordem INTEGER NOT NULL, capitulos INTEGER NOT NULL)")
            s.executeUpdate("CREATE TABLE verso(livro TEXT NOT NULL, cap INTEGER NOT NULL, ver INTEGER NOT NULL, texto TEXT NOT NULL, busca TEXT NOT NULL, PRIMARY KEY(livro,cap,ver)) WITHOUT ROWID")
            s.executeUpdate("INSERT INTO meta VALUES ('id','blivre'),('nome','Bíblia Livre'),('sigla','BLIVRE'),('idioma','pt-BR'),('licenca','CC BY 4.0 BR'),('credito','Crédito'),('esquema','1'),('novo','x')")
            s.executeUpdate("INSERT INTO livro VALUES ('GEN',1,50),('PSA',19,150),('JHN',43,21)")
        }
        // Recorte: só alguns versos.
        listOf(
            Triple("JHN", 3 to 16, "Porque Deus amou ao mundo de tal maneira, que deu o seu Filho unigênito"),
            Triple("JHN", 3 to 17, "Porque Deus enviou o seu Filho ao mundo, não para que condenasse o mundo"),
            Triple("PSA", 23 to 1, "O SENHOR é o meu pastor; nada me faltará."),
            Triple("PSA", 23 to 2, "Em pastos verdejantes me faz deitar; guia-me mansamente a águas tranquilas."),
            Triple("GEN", 1 to 1, "No princípio criou Deus os céus e a terra."),
            Triple("GEN", 3 to 1, "Ora, a serpente era mais astuta que todos os animais do campo, 100% (teste_x)"),
        ).forEach { (b, cv, t) ->
            prepareStatement("INSERT INTO verso VALUES (?,?,?,?,?)").use { st ->
                st.setString(1, b); st.setInt(2, cv.first); st.setInt(3, cv.second); st.setString(4, t)
                st.setString(5, normalizeBibleSearchText(t)); st.executeUpdate()
            }
        }
    }

    private val db = BibleDatabase(JdbcConnection(conn))

    @AfterTest
    fun fim() {
        db.close()
        file.delete()
    }

    @Test
    fun meta() = runTest {
        val m = db.meta()
        assertEquals("BLIVRE", m.abbreviation)
        assertEquals(1, m.schema)
        assertEquals("x", m.raw["novo"])
    }

    @Test
    fun `disponibilidade e do arquivo nao da Biblia inteira`() = runTest {
        val a = db.availability()
        assertEquals(listOf(BibleBook.GENESIS, BibleBook.PSALMS, BibleBook.JOHN), a.map { it.book })
        assertEquals(listOf(1, 3), a.first().chapters)
        assertEquals(listOf(3), db.chapters(BibleBook.JOHN))
        assertEquals(emptyList(), db.chapters(BibleBook.REVELATION))
        assertEquals(17, db.verseCount(BibleBook.JOHN, 3))
        assertEquals(0, db.verseCount(BibleBook.JOHN, 4))
    }

    @Test
    fun `versiculo intervalo e capitulo`() = runTest {
        assertTrue(db.verse(VerseRef(BibleBook.JOHN, 3, 16))!!.text.startsWith("Porque Deus amou"))
        assertNull(db.verse(VerseRef(BibleBook.JOHN, 3, 1)))
        assertEquals(listOf("JHN.3.16", "JHN.3.17"), db.passage(VerseRef(BibleBook.JOHN, 3, 16, 17)).map { it.ref.key })
        assertEquals(2, db.chapter(BibleBook.PSALMS, 23).size)
    }

    @Test
    fun `busca por partes sem acento e com palavras somadas`() = runTest {
        // Pedaço de palavra: "past" acha "pastor" e "pastos".
        assertEquals(listOf("PSA.23.1", "PSA.23.2"), db.search("past").map { it.ref.key })
        assertEquals(listOf("PSA.23.1"), db.search("pastor").map { it.ref.key })
        assertEquals(listOf("PSA.23.2"), db.search("AGUAS tranq").map { it.ref.key })
        assertEquals(listOf("JHN.3.16", "JHN.3.17"), db.search("deus filho").map { it.ref.key })
        assertEquals(listOf("GEN.1.1", "JHN.3.16", "JHN.3.17"), db.search("deus").map { it.ref.key })
        assertEquals(listOf("JHN.3.16"), db.search("unigenito").map { it.ref.key })
        assertEquals(1, db.search("deus", limit = 1, offset = 1).size)
        assertEquals(3, db.searchCount("deus"))
        assertEquals(listOf("GEN.1.1"), db.search("deus", books = setOf(BibleBook.GENESIS)).map { it.ref.key })
        // Coringa do LIKE digitado não vira coringa.
        assertEquals(emptyList(), db.search("%"))
        assertEquals(emptyList(), db.search("_"))
        assertEquals(emptyList(), db.search(""))
    }

    @Test
    fun `arquivo real do gerador quando presente`() = runTest {
        val real = File("../../Ferramentas/conteudo-biblico/saida/biblia-blivre.db").takeIf { it.isFile } ?: return@runTest
        val c = DriverManager.getConnection("jdbc:sqlite:${real.absolutePath}")
        val rdb = BibleDatabase(JdbcConnection(c))
        try {
            val a = rdb.availability()
            assertEquals(66, a.size)
            a.forEach { assertEquals(it.book.chapters, it.chapters.size, "capítulos de ${it.book}") }
            assertTrue(rdb.search("pastor nada faltara").any { it.ref.key == "PSA.23.1" })
        } finally {
            rdb.close()
        }
    }
}
