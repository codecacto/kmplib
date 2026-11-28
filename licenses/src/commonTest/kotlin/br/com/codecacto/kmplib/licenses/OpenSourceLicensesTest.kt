package br.com.codecacto.kmplib.licenses

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenSourceLicensesTest {

    // Recorte do formato que o plugin AboutLibraries 13.x grava em `aboutlibraries.json`.
    private val json = """
        {
          "libraries": [
            {
              "uniqueId": "io.ktor:ktor-client-core",
              "artifactVersion": "3.1.1",
              "name": "ktor-client-core",
              "website": "https://ktor.io",
              "licenses": ["Apache-2.0"]
            },
            {
              "uniqueId": "com.example:sem-texto",
              "artifactVersion": "1.0.0",
              "name": "Biblioteca sem texto",
              "website": "https://example.com",
              "licenses": ["MIT"]
            },
            {
              "uniqueId": "com.example:sem-nada",
              "name": "Avulsa"
            }
          ],
          "licenses": {
            "Apache-2.0": {
              "name": "Apache License 2.0",
              "url": "https://spdx.org/licenses/Apache-2.0.html",
              "spdxId": "Apache-2.0",
              "content": "Apache License<br />Version 2.0"
            },
            "MIT": {
              "name": "MIT License",
              "url": "https://spdx.org/licenses/MIT.html"
            }
          }
        }
    """.trimIndent()

    @Test
    fun le_o_json_do_plugin_em_ordem_alfabetica() {
        val libs = assertNotNull(parseOpenSourceLicenses(json))
        assertEquals(listOf("Avulsa", "Biblioteca sem texto", "ktor-client-core"), libs.libraries.map { it.name })
    }

    @Test
    fun ausente_em_branco_quebrado_ou_vazio_vira_indisponivel() {
        assertNull(parseOpenSourceLicenses(null))
        assertNull(parseOpenSourceLicenses("  "))
        assertNull(parseOpenSourceLicenses("<html>404</html>"))
        assertNull(parseOpenSourceLicenses("""{"libraries": [], "licenses": {}}"""))
    }

    @Test
    fun texto_da_licenca_sem_html_de_quebra() {
        val ktor = parseOpenSourceLicenses(json)!!.libraries.single { it.name == "ktor-client-core" }
        assertEquals("Apache License\nVersion 2.0", licenseTextOf(ktor))
    }

    @Test
    fun sem_texto_embutido_abre_a_url_da_licenca_senao_o_site_senao_nada() {
        val libs = parseOpenSourceLicenses(json)!!.libraries
        val semTexto = libs.single { it.name == "Biblioteca sem texto" }
        assertEquals("", licenseTextOf(semTexto))
        assertEquals("https://spdx.org/licenses/MIT.html", licenseUrlOf(semTexto))
        assertNull(licenseUrlOf(libs.single { it.name == "Avulsa" }))
    }

    @Test
    fun ids_estaveis_e_unicos() {
        assertEquals(OpenSourceLicensesTestTags.all.size, OpenSourceLicensesTestTags.all.toSet().size)
        assertEquals("files/aboutlibraries.json", OPEN_SOURCE_LICENSES_RESOURCE_PATH)
    }
}
