package br.com.codecacto.kmplib.ui.locale

import br.com.codecacto.kmplib.core.locale.FactoryLocales
import br.com.codecacto.kmplib.core.locale.KmpLibLocales
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.allStringResources
import br.com.codecacto.kmplib.generated.resources.kmplib_locale_tag
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.test.runTest
import org.w3c.dom.Element
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GAP-PT-M31 (2.288.0): a base pt-BR gerada do `values/strings.xml` e o resolvedor central.
 *
 * A tabela é gerada no build; este teste a confere contra o XML lido de forma INDEPENDENTE (parser
 * DOM do JDK, como o plugin do Compose) e contra o `Res` (toda chave que a lib pode pedir tem base).
 */
class KmpLibBaseStringsTest {

    private val padrao = Locale.getDefault()

    @BeforeTest
    fun aparelhoEmIngles() = Locale.setDefault(Locale.US)

    @AfterTest
    fun restaurar() {
        Locale.setDefault(padrao)
        KmpLibLocales.reset()
    }

    private fun xmlBase(): Map<String, String> {
        val arquivo = File("src/commonMain/composeResources/values/strings.xml")
        assertTrue(arquivo.exists(), "não achei ${arquivo.absolutePath}")
        val nos = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(arquivo).getElementsByTagName("string")
        return (0 until nos.length).associate { i ->
            val el = nos.item(i) as Element
            el.getAttribute("name") to el.textContent
        }
    }

    @Test
    fun `a tabela gerada e o XML da base texto por texto`() {
        val xml = xmlBase()
        assertEquals(xml.size, KMPLIB_BASE_STRING_COUNT)
        xml.forEach { (chave, texto) ->
            // O XML da base não usa escape (\n, \uXXXX); se passar a usar, o gerador trata como o Compose.
            assertEquals(texto, kmplibBaseString(chave), chave)
        }
        assertNull(kmplibBaseString("chave_que_nao_existe"))
    }

    @Test
    fun `toda chave do Res tem base pt-BR`() {
        val chaves = Res.allStringResources.values.map { it.key }
        assertEquals(KMPLIB_BASE_STRING_COUNT, chaves.size)
        chaves.forEach { assertNotNull(kmplibBaseString(it), "sem base pt-BR: $it") }
        assertEquals("pt-BR", kmplibBaseString(Res.string.kmplib_locale_tag.key))
    }

    @Test
    fun `app so pt-BR em aparelho em ingles le a base sem passar pela pasta values-en`() = runTest {
        KmpLibLocales.configure(listOf(FactoryLocales.PT_BR))
        assertTrue(KmpLibLocales.shouldUseBaseTexts())
        assertEquals("pt-BR", kmpGetString(Res.string.kmplib_locale_tag))
        assertEquals("pt-BR", uiLanguageTag())
    }

    @Test
    fun `os load Texts dos ViewModels saem em pt-BR no app so pt-BR`() = runTest {
        KmpLibLocales.configure(listOf(FactoryLocales.PT_BR))
        val textos = loadDomainApiTexts()
        assertEquals(kmplibBaseString("kmplib_error_network"), textos.offline)
        assertEquals(
            kmplibBaseString("kmplib_error_server")!!.replace("%1\$d", "503"),
            textos.serverError(503),
        )
    }

    @Test
    fun `app GLOBAL no mesmo aparelho nao usa a base`() {
        assertTrue(!KmpLibLocales.shouldUseBaseTexts())
    }
}
