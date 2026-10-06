package br.com.codecacto.kmplib.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Trava dos invólucros de janela do Material3 (2.257.0, `AppMaterialWindows.kt`).
 *
 * Duas promessas, e as duas quebram com build verde se ninguém olhar:
 *
 * 1. **Mesma API do original.** A migração dos apps é trocar `DropdownMenu(` por `AppDropdownMenu(`;
 *    se um parâmetro faltar ou mudar de nome, a troca deixa de ser mecânica. A lista de parâmetros
 *    do Material3 é lida do PRÓPRIO bytecode da versão em uso (a string de *source information*
 *    `C(DropdownMenu)N(expanded,onDismissRequest,…)` que o compilador do Compose grava), então um
 *    bump do Material3 que acrescente parâmetro reprova aqui, e não no app que precisava dele.
 * 2. **Toda janela religa a flag** `testTagsAsResourceId` — senão o invólucro não serve para nada.
 */
class AppMaterialWindowsTest {

    private val fonte: String = File("src/commonMain/kotlin/br/com/codecacto/kmplib/ui/components/AppMaterialWindows.kt")
        .also { assertTrue(it.exists(), "fonte não encontrada a partir de ${File(".").canonicalPath}") }
        .readText()

    /** Nomes dos parâmetros de `fun [nome](…)` no fonte, respeitando parênteses/chaves aninhados. */
    private fun parametrosDoInvolucro(nome: String): List<String> {
        val m = Regex("""fun\s+(?:\w+\.)?$nome\s*\(""").find(fonte) ?: fail("invólucro $nome não encontrado")
        var nivel = 0
        val atual = StringBuilder()
        val partes = mutableListOf<String>()
        for (c in fonte.substring(m.range.last + 1)) {
            when (c) {
                '(', '{', '[' -> nivel++
                ')', '}', ']' -> if (nivel == 0) break else nivel--
            }
            if (c == ',' && nivel == 0) { partes += atual.toString(); atual.clear() } else atual.append(c)
        }
        partes += atual.toString()
        return partes.map { it.trim() }.filter { it.isNotEmpty() }.map { it.substringBefore(':').trim() }
    }

    /** Variantes `N(…)` da função [composable] gravadas pelo compilador do Compose em [classe]. */
    private fun assinaturasDoMaterial3(classe: String, composable: String): List<List<String>> {
        val bytes = javaClass.classLoader!!.getResourceAsStream("androidx/compose/material3/$classe.class")
            ?.use { it.readBytes() } ?: fail("classe do Material3 não está no classpath: $classe")
        val texto = String(bytes, Charsets.ISO_8859_1)
        return Regex("""C\($composable\)N\(([^)]*)\)""").findAll(texto)
            .map { r -> r.groupValues[1].split(',').map { it.substringBefore(':') } }
            .distinct().toList()
    }

    private fun confere(involucro: String, classe: String, original: String) {
        val variantes = assinaturasDoMaterial3(classe, original)
        assertTrue(variantes.isNotEmpty(), "sem source information de $original em $classe")
        // A variante "cheia" (a de mais parâmetros) é a não-depreciada; as menores são sobrecargas antigas.
        val cheia = variantes.maxBy { it.size }
        assertEquals(cheia, parametrosDoInvolucro(involucro), "$involucro diverge da API de $original do Material3")
    }

    @Test fun `AppAlertDialog tem a API do AlertDialog`() =
        confere("AppAlertDialog", "AndroidAlertDialog_androidKt", "AlertDialog")

    @Test fun `AppBasicAlertDialog tem a API do BasicAlertDialog`() =
        confere("AppBasicAlertDialog", "AlertDialogKt", "BasicAlertDialog")

    @Test fun `AppDatePickerDialog tem a API do DatePickerDialog`() =
        confere("AppDatePickerDialog", "DatePickerDialog_androidKt", "DatePickerDialog")

    @Test fun `AppModalBottomSheet tem a API do ModalBottomSheet`() =
        confere("AppModalBottomSheet", "ModalBottomSheetKt", "ModalBottomSheet")

    @Test fun `AppDropdownMenu tem a API do DropdownMenu`() =
        confere("AppDropdownMenu", "AndroidMenu_androidKt", "DropdownMenu")

    @Test fun `AppExposedDropdownMenu tem a API do ExposedDropdownMenu`() {
        // A variante cheia do ExposedDropdownMenu ainda carrega `focusable`/`matchTextFieldWidth`
        // (depreciada); a vigente troca os dois por `matchAnchorWidth`.
        val vigente = assinaturasDoMaterial3("ExposedDropdownMenuBoxScope", "ExposedDropdownMenu")
            .filter { "matchAnchorWidth" in it && "focusable" !in it }
        assertEquals(1, vigente.size, "variante vigente do ExposedDropdownMenu não encontrada: $vigente")
        assertEquals(vigente.single(), parametrosDoInvolucro("AppExposedDropdownMenu"))
    }

    @Test fun `AppDialog e AppPopup tem a API de androidx compose ui window`() {
        assertEquals(listOf("onDismissRequest", "properties", "content"), parametrosDoInvolucro("AppDialog"))
        assertEquals(
            listOf("alignment", "offset", "onDismissRequest", "properties", "content"),
            parametrosDoInvolucro("AppPopup"),
        )
    }

    @Test fun `todo involucro abre a janela original e religa a flag`() {
        val corpos = Regex("""\nfun\s+(?:\w+\.)?(App\w+)\s*\(""").findAll(fonte).map { it.groupValues[1] to it.range.first }.toList()
        assertEquals(9, corpos.size, "invólucros encontrados: ${corpos.map { it.first }}")
        corpos.forEachIndexed { i, (nome, inicio) ->
            val fim = corpos.getOrNull(i + 1)?.second ?: fonte.length
            val corpo = fonte.substring(inicio, fim)
            val original = nome.removePrefix("App")
            assertTrue(Regex("""\n\s+$original\s*\(""").containsMatchIn(corpo), "$nome não chama $original")
            assertTrue(
                "modifier = modifier.exposeTestTagsAsResourceId()" in corpo ||
                    "Box(modifier = Modifier.exposeTestTagsAsResourceId(), propagateMinConstraints = true) { content() }" in corpo,
                "$nome não religa testTagsAsResourceId",
            )
        }
    }
}
