package br.com.codecacto.kmplib.ui.tour

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Trava da 2.334.0: a camada do tour NÃO consome todo evento de toque, e o toque fora do balão PULA.
 *
 * Até a 2.333.0 a camada fazia `awaitPointerEvent().changes.forEach { it.consume() }` para a tela de
 * baixo não reagir. O clique do Compose (`waitForUpOrCancellation`) se cancela quando um MOVE do mesmo
 * toque chega consumido — e em aparelho que reporta o tremor do dedo como MOVE, Pular e Próximo não
 * respondiam: o tour travava a tela no primeiro acesso (LocAki Android, 29/out/2026). A tela de baixo
 * já não recebe o toque sem isso (só o irmão de cima com entrada recebe), então o consumo era inútil.
 * Sem teste de UI automatizado na lib (decisão do fundador, jun/2026), a varredura do fonte impede a
 * volta do padrão.
 */
class TourToqueNosBotoesTest {

    private val fonte: String = File("src/commonMain/kotlin/br/com/codecacto/kmplib/ui/tour/ProductTourHost.kt").readText()

    @Test
    fun `a camada nao consome todo evento de toque`() {
        val consumoTotal = Regex("""awaitPointerEvent\(\)\s*\.changes\s*\.forEach\s*\{\s*it\.consume\(\)\s*\}""")
        assertFalse(consumoTotal.containsMatchIn(fonte), "a camada do tour voltou a consumir todo evento — os botões do balão param de responder")
    }

    @Test
    fun `toque fora do balao pula o tour`() {
        assertTrue(
            Regex("""detectTapGestures\(\s*onTap\s*=\s*\{\s*state\.skip\(\)\s*\}\s*\)""").containsMatchIn(fonte),
            "o toque simples na camada (fora do balão) tem de chamar state.skip()",
        )
    }

    @Test
    fun `o balao segura o toque fora dos botoes consumindo so o soltar`() {
        val balao = fonte.substringAfter(".testTag(ProductTourTestTags.BALAO)").substringBefore(".semantics {")
        assertTrue("changedToUp()" in balao && "consume()" in balao, "o balão precisa consumir o soltar para não pular o tour")
        assertFalse("forEach { it.consume() }" in balao, "o balão não pode consumir todo evento — cancelaria os botões")
    }
}
