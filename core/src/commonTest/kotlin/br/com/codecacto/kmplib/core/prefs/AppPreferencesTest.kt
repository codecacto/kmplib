package br.com.codecacto.kmplib.core.prefs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPreferencesTest {

    private lateinit var prefs: InMemoryAppPreferences

    @BeforeTest
    fun setup() {
        prefs = InMemoryAppPreferences()
    }

    @AfterTest
    fun tearDown() = runTest {
        prefs.clear()
    }

    // ====== String ======

    @Test
    fun `getString retorna default quando chave nao existe`() = runTest {
        assertEquals("fallback", prefs.getString("missing", default = "fallback"))
        assertEquals("", prefs.getString("missing"))
    }

    @Test
    fun `setString persiste e getString retorna valor`() = runTest {
        prefs.setString("theme", "dark")
        assertEquals("dark", prefs.getString("theme"))
    }

    @Test
    fun `setString sobrescreve valor anterior`() = runTest {
        prefs.setString("theme", "light")
        prefs.setString("theme", "dark")
        assertEquals("dark", prefs.getString("theme"))
    }

    // ====== Boolean / Int / Long / Float ======

    @Test
    fun `boolean default e set funcionam`() = runTest {
        assertEquals(true, prefs.getBoolean("flag", default = true))
        prefs.setBoolean("flag", false)
        assertEquals(false, prefs.getBoolean("flag", default = true))
    }

    @Test
    fun `int default e set funcionam`() = runTest {
        assertEquals(42, prefs.getInt("count", default = 42))
        prefs.setInt("count", 7)
        assertEquals(7, prefs.getInt("count"))
    }

    @Test
    fun `long default e set funcionam`() = runTest {
        prefs.setLong("ts", 1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, prefs.getLong("ts"))
    }

    @Test
    fun `float default e set funcionam`() = runTest {
        prefs.setFloat("scale", 1.5f)
        assertEquals(1.5f, prefs.getFloat("scale"))
    }

    // ====== has / remove / clear ======

    @Test
    fun `has retorna true apos set e false apos remove`() = runTest {
        assertFalse(prefs.has("k"))
        prefs.setString("k", "v")
        assertTrue(prefs.has("k"))
        prefs.remove("k")
        assertFalse(prefs.has("k"))
    }

    @Test
    fun `clear esvazia tudo`() = runTest {
        prefs.setString("a", "1")
        prefs.setInt("b", 2)
        prefs.setBoolean("c", true)
        prefs.clear()
        assertFalse(prefs.has("a"))
        assertFalse(prefs.has("b"))
        assertFalse(prefs.has("c"))
    }

    @Test
    fun `remove de chave inexistente nao quebra`() = runTest {
        prefs.remove("ghost")
        assertFalse(prefs.has("ghost"))
    }

    // ====== observe* ======

    @Test
    fun `observeString emite valor inicial e mudancas`() = runTest {
        prefs.setString("k", "first")
        val first = prefs.observeString("k", "fallback").first()
        assertEquals("first", first)
    }

    @Test
    fun `observeString re-emite ao chamar setString`() = runTest {
        prefs.setString("k", "v1")
        val flow = prefs.observeString("k", "")

        val collected = mutableListOf<String>()
        val job = launch {
            flow.take(3).toList(collected)
        }

        // O collector precisa ASSINAR o SharedFlow antes das mutações (sem replay,
        // emissões sem assinante são perdidas). runCurrent() deixa o coletor subir
        // e capturar o valor inicial; depois cada mudança é processada em ordem.
        runCurrent()
        prefs.setString("k", "v2")
        runCurrent()
        prefs.setString("k", "v3")
        runCurrent()

        job.join()
        // observeString emite atual + cada mudança; com distinctUntilChanged
        // espera-se v1 → v2 → v3
        assertEquals(listOf("v1", "v2", "v3"), collected)
    }

    @Test
    fun `observeBoolean emite default e mudancas`() = runTest {
        val flow = prefs.observeBoolean("flag", default = false)
        val collected = mutableListOf<Boolean>()
        val job = launch { flow.take(2).toList(collected) }

        runCurrent()
        prefs.setBoolean("flag", true)
        runCurrent()
        job.join()
        assertEquals(listOf(false, true), collected)
    }

    @Test
    fun `observeInt respeita default quando chave nao existe`() = runTest {
        val value = prefs.observeInt("missing", default = 99).first()
        assertEquals(99, value)
    }

    @Test
    fun `observeLong re-emite apos remove`() = runTest {
        prefs.setLong("k", 100L)
        val flow = prefs.observeLong("k", default = 0L)
        val collected = mutableListOf<Long>()
        val job = launch { flow.take(2).toList(collected) }

        runCurrent()
        prefs.remove("k")
        runCurrent()
        job.join()
        assertEquals(listOf(100L, 0L), collected)
    }

    @Test
    fun `observe re-emite apos clear`() = runTest {
        prefs.setString("k", "v")
        val flow = prefs.observeString("k", "default")
        val collected = mutableListOf<String>()
        val job = launch { flow.take(2).toList(collected) }

        runCurrent()
        prefs.clear()
        runCurrent()
        job.join()
        assertEquals(listOf("v", "default"), collected)
    }

    // ====== distinctUntilChanged ======

    @Test
    fun `observeString nao re-emite valor identico`() = runTest {
        prefs.setString("k", "same")
        val flow = prefs.observeString("k", "")
        val collected = mutableListOf<String>()
        val job = launch { flow.take(2).toList(collected) }

        runCurrent()
        prefs.setString("k", "same")  // valor idêntico — não deve re-emitir
        runCurrent()
        prefs.setString("k", "different")  // diferente — emite
        runCurrent()
        job.join()
        assertEquals(listOf("same", "different"), collected)
    }
}
