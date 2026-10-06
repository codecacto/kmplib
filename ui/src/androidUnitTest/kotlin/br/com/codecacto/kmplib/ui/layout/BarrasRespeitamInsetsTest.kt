package br.com.codecacto.kmplib.ui.layout

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Trava da 2.235.0: toda barra que a lib desenha na BORDA da janela aplica o inset do sistema.
 *
 * Com `targetSdk` 35+ o Android 15 força o edge-to-edge (e o Compose no iOS sempre desenha sob o
 * notch e o home indicator). O `TopAppBar`/`NavigationBar` do Material aplicam o inset sozinhos; uma
 * barra feita de `Row`/`Box`/`Column`/`Surface` NÃO — e compila verde. Foi assim que a barra de
 * abas do [AdaptiveScaffold] ficava sob a barra de gestos e o título da Home do Chamada Fácil sob o
 * relógio (02/out/2026). Esta varredura reprova o esquecimento no `testDebugUnitTest`.
 */
class BarrasRespeitamInsetsTest {

    private val raiz = File("..").canonicalFile

    private val inset = Regex(
        """windowInsetsPadding|statusBarsPadding|navigationBarsPadding|systemBarsPadding|safeDrawingPadding|safeContentPadding|windowInsets\s*=""",
    )

    /** Corpo entre a chave que abre em [inicio] e a que fecha, contando o aninhamento. */
    private fun corpo(fonte: String, inicio: Int): String {
        var nivel = 0
        for (i in inicio until fonte.length) {
            when (fonte[i]) {
                '{' -> nivel++
                '}' -> { nivel--; if (nivel == 0) return fonte.substring(inicio + 1, i) }
            }
        }
        return fonte.substring(inicio + 1)
    }

    /** Slots `topBar = {`/`bottomBar = {` cujo conteúdo é um layout cru, sem inset nenhum. */
    internal fun slotsSemInset(fonte: String): List<String> =
        Regex("""\b(topBar|bottomBar)\s*=\s*\{""").findAll(fonte).mapNotNull { m ->
            val slot = corpo(fonte, m.range.last)
            val primeiro = Regex("""\b([A-Z]\w*)\s*\(""").find(slot)?.groupValues?.get(1)
            if (primeiro in setOf("Row", "Box", "Column", "Surface") && !inset.containsMatchIn(slot)) {
                "${m.groupValues[1]} → $primeiro"
            } else {
                null
            }
        }.toList()

    private fun fontes(): List<File> = raiz.listFiles().orEmpty()
        .filter { File(it, "build.gradle.kts").exists() }
        .flatMap { File(it, "src/commonMain/kotlin").walkTopDown().filter { f -> f.extension == "kt" }.toList() }

    private fun fonte(caminho: String): String = File(raiz, caminho).readText()

    private fun funcao(fonte: String, nome: String): String {
        val m = Regex("""fun\s+$nome\s*\(""").find(fonte) ?: fail("função $nome não encontrada")
        val abre = fonte.indexOf(") {", m.range.last).let { if (it < 0) fail("corpo de $nome") else it + 2 }
        return corpo(fonte, abre)
    }

    @Test
    fun `nenhum slot de barra da lib e um layout cru sem inset`() {
        val todas = fontes()
        assertTrue(todas.size > 100, "varredura não achou os fontes da lib em ${raiz.path}")
        val faltando = todas.flatMap { f -> slotsSemInset(f.readText()).map { "${f.relativeTo(raiz)}: $it" } }
        if (faltando.isNotEmpty()) {
            fail(
                "Barra própria no slot do Scaffold sem inset — com edge-to-edge ela desenha sob a " +
                    "status bar / barra de gestos:\n" + faltando.joinToString("\n"),
            )
        }
    }

    @Test
    fun `a varredura reconhece uma barra esquecida e aceita a corrigida`() {
        val esquecida = "Scaffold(topBar = { Row(Modifier.background(c).padding(12.dp)) { Text(t) } }) {}"
        assertEquals(listOf("topBar → Row"), slotsSemInset(esquecida))
        val corrigida = "Scaffold(bottomBar = { Row(Modifier.background(c).windowInsetsPadding(WindowInsets.navigationBars)) {} }) {}"
        assertEquals(emptyList(), slotsSemInset(corrigida))
        assertEquals(emptyList(), slotsSemInset("Scaffold(topBar = { AppTopBar(title = t) }) {}"))
    }

    @Test
    fun `barra de abas e rail do AdaptiveScaffold aplicam o inset`() {
        val f = fonte("ui/src/commonMain/kotlin/br/com/codecacto/kmplib/ui/layout/AdaptiveScaffold.kt")
        assertTrue(inset.containsMatchIn(funcao(f, "AdaptiveBottomBar")), "AdaptiveBottomBar sem inset da barra de navegação")
        assertTrue(inset.containsMatchIn(funcao(f, "AdaptiveRail")), "AdaptiveRail sem inset das barras do sistema")
        assertTrue("consumeWindowInsets" in funcao(f, "AdaptiveScaffold"), "conteúdo ao lado do rail somaria a borda inicial de novo")
    }

    @Test
    fun `banner de conectividade no topo aplica a status bar e o app embaixo nao a soma de novo`() {
        val banner = fonte("ui/src/commonMain/kotlin/br/com/codecacto/kmplib/ui/components/OfflineBanner.kt")
        assertTrue(".windowInsetsPadding(windowInsets)" in banner, "OfflineBanner não aplica o windowInsets recebido")
        val gate = fonte("ui/src/commonMain/kotlin/br/com/codecacto/kmplib/ui/components/ConnectivityGate.kt")
        assertTrue("windowInsets = WindowInsets.statusBars" in gate, "ConnectivityStyle.Banner sem a status bar")
        assertTrue("consumeWindowInsets(WindowInsets.statusBars)" in gate, "conteúdo sob o banner somaria a status bar de novo")
    }

    @Test
    fun `banner de anuncio desligado ainda reserva a barra de navegacao`() {
        val f = fonte("ads/src/commonMain/kotlin/br/com/codecacto/kmplib/ads/router/ManagedBannerAd.kt")
        // 2.254.0: o inset virou parâmetro (`WindowInsets(0)` dentro do conteúdo — banner do estado
        // vazio); o DEFAULT continua sendo a barra de navegação, e o ramo OFF aplica o recebido.
        assertTrue(Regex("""windowInsets:\s*WindowInsets\s*=\s*WindowInsets\.navigationBars""").containsMatchIn(f))
        assertTrue(Regex("""AdProvider\.OFF\s*->.*windowInsetsPadding\(windowInsets\)""").containsMatchIn(f))
    }

    @Test
    fun `telas cheias da lib respeitam as barras do sistema`() {
        listOf(
            "auth/src/commonMain/kotlin/br/com/codecacto/kmplib/auth/ui/LoginScreen.kt",
            "auth/src/commonMain/kotlin/br/com/codecacto/kmplib/auth/ui/RegisterScreen.kt",
            "platform/src/commonMain/kotlin/br/com/codecacto/kmplib/appupdate/AppUpdateGate.kt",
            "platform/src/commonMain/kotlin/br/com/codecacto/kmplib/appupdate/AppServiceGate.kt",
            "ui/src/commonMain/kotlin/br/com/codecacto/kmplib/ui/components/OnboardingPager.kt",
        ).forEach { caminho ->
            assertTrue(inset.containsMatchIn(fonte(caminho)), "$caminho sem inset das barras do sistema")
        }
    }
}
