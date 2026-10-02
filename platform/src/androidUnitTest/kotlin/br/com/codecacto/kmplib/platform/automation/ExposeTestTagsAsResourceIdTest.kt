package br.com.codecacto.kmplib.platform.automation

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.semantics.getOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Trava as duas metades da correção de 2.234.0:
 *
 * 1. o modificador liga de fato a flag `testTagsAsResourceId` (é ela que faz o `uiautomator` — e
 *    portanto o Maestro — ler a `testTag` como `resource-id`);
 * 2. **toda** janela própria aberta pela lib (`Dialog`, `AlertDialog`, `ModalBottomSheet`,
 *    `DropdownMenu`, `Popup`, `DatePickerDialog`, `TimePickerDialog`) religa a flag. Janela nova
 *    esquecida volta a ter `resource-id=""` em tudo, com build verde — foi assim que o diálogo do
 *    "digite EXCLUIR" ficou inalcançável no teste do Mac (02/out/2026). Esta varredura reprova o
 *    esquecimento no `testDebugUnitTest`, em vez de no próximo teste de loja.
 */
class ExposeTestTagsAsResourceIdTest {

    @OptIn(ExperimentalComposeUiApi::class)
    private fun Modifier.ligaAFlag(): Boolean =
        foldIn(false) { achou, elemento ->
            achou || (elemento as? SemanticsModifier)
                ?.semanticsConfiguration
                ?.getOrNull(SemanticsPropertiesAndroid.TestTagsAsResourceId) == true
        }

    @Test
    fun `o modificador liga testTagsAsResourceId`() {
        assertTrue(Modifier.exposeTestTagsAsResourceId().ligaAFlag())
    }

    @Test
    fun `o modificador preserva o que ja vinha na cadeia`() {
        val antes = Modifier.then(Modifier)
        val depois = antes.exposeTestTagsAsResourceId()
        assertEquals(1, depois.foldIn(0) { n, e -> if (e is SemanticsModifier) n + 1 else n })
        assertTrue(!Modifier.ligaAFlag(), "Modifier vazio não pode ligar a flag sozinho")
    }

    /** Chamada que abre OUTRA janela no Android. `\b` exclui `AppDialog(`, `InputDialog(` etc. */
    private val abreJanela = Regex(
        """\b(Dialog|AlertDialog|BasicAlertDialog|DatePickerDialog|TimePickerDialog|ModalBottomSheet|DropdownMenu|ExposedDropdownMenu|Popup)\s*\(""",
    )
    private val religa = Regex("""exposeTestTagsAsResourceId\(\)|WithTestTagsAsResourceId\s*\{""")

    private fun semComentario(fonte: String): String = fonte.lines()
        .filterNot { val t = it.trimStart(); t.startsWith("*") || t.startsWith("//") || t.startsWith("/*") || t.startsWith("import ") }
        .joinToString("\n") { it.substringBefore("//") }

    @Test
    fun `toda janela propria da lib religa a flag`() {
        // O diretório de trabalho do teste é o do módulo (`platform/`); a raiz da lib é o pai.
        val raiz = File("..").canonicalFile
        val fontes = raiz.listFiles().orEmpty()
            .filter { File(it, "build.gradle.kts").exists() }
            .flatMap { modulo ->
                listOf("commonMain", "androidMain").flatMap { conjunto ->
                    File(modulo, "src/$conjunto/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
                }
            }
        assertTrue(fontes.size > 100, "varredura não achou os fontes da lib em ${raiz.path}")

        val faltando = fontes.mapNotNull { arquivo ->
            val codigo = semComentario(arquivo.readText())
            val janelas = abreJanela.findAll(codigo).count()
            val religadas = religa.findAll(codigo).count()
            if (janelas > religadas) "${arquivo.relativeTo(raiz)}: $janelas janela(s), $religadas religada(s)" else null
        }
        if (faltando.isNotEmpty()) {
            fail(
                "Janela própria sem `Modifier.exposeTestTagsAsResourceId()` — no Android o Maestro " +
                    "não enxerga nenhum id dentro dela:\n" + faltando.joinToString("\n"),
            )
        }
    }

    @Test
    fun `a varredura reconhece uma janela esquecida`() {
        val esquecida = """
            fun X() { Dialog(onDismissRequest = {}) { Surface { Text("a") } } }
        """.trimIndent()
        assertEquals(1, abreJanela.findAll(semComentario(esquecida)).count())
        assertEquals(0, religa.findAll(semComentario(esquecida)).count())
        // Componentes da própria lib cujo nome termina em Dialog NÃO são janelas por si.
        assertEquals(0, abreJanela.findAll("AppDialog(show = true) {}\nInputDialog(\nConfirmationDialog(").count())
    }
}
