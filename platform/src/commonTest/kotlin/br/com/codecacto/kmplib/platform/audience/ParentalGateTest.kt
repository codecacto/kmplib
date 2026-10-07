package br.com.codecacto.kmplib.platform.audience

import br.com.codecacto.kmplib.platform.AppReviewManager
import br.com.codecacto.kmplib.platform.FakeReviewStore
import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.UrlLauncher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParentalGateTest {

    private val host = Any()

    @BeforeTest
    fun setUp() {
        KmpLibAudience.configure(AppAudience.GENERAL)
        ParentalGate.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        KmpLibAudience.configure(AppAudience.GENERAL)
        ParentalGate.resetForTest()
    }

    private fun kids() {
        KmpLibAudience.configure(AppAudience.KIDS)
        ParentalGate.attachHost(host)
    }

    @Test
    fun publicoGeralNaoTemPortaoEExecutaNaHora() {
        var ran = 0
        ParentalGate.guard { ran++ }
        assertEquals(1, ran)
        assertNull(ParentalGate.pending.value)
        assertFalse(ParentalGate.isRequired)
    }

    @Test
    fun appInfantilSoExecutaDepoisDoAdultoPassar() {
        kids()
        var ran = 0
        ParentalGate.guard { ran++ }
        assertEquals(0, ran)
        val pedido = assertNotNull(ParentalGate.pending.value)
        ParentalGate.resolve(pedido, passed = true)
        assertEquals(1, ran)
        assertNull(ParentalGate.pending.value)
    }

    @Test
    fun respostaErradaNaoSaiDoApp() {
        kids()
        var ran = 0
        ParentalGate.guard { ran++ }
        ParentalGate.resolve(ParentalGate.pending.value!!, passed = false)
        assertEquals(0, ran)
        assertNull(ParentalGate.pending.value)
    }

    @Test
    fun segundoToqueComPortaoAbertoENegadoSemEmpilhar() {
        kids()
        var primeiro = 0
        var segundo: Boolean? = null
        ParentalGate.guard { primeiro++ }
        val aberto = ParentalGate.pending.value
        ParentalGate.request { segundo = it }
        assertEquals(false, segundo)
        assertSame(aberto, ParentalGate.pending.value)
        ParentalGate.resolve(aberto!!, passed = true)
        assertEquals(1, primeiro)
    }

    @Test
    fun semHostOPortaoNegaEmVezDeDeixarPassar() {
        KmpLibAudience.configure(AppAudience.KIDS)
        var resultado: Boolean? = null
        var ran = 0
        ParentalGate.request { resultado = it }
        ParentalGate.guard { ran++ }
        assertEquals(false, resultado)
        assertEquals(0, ran)
        assertNull(ParentalGate.pending.value)
    }

    @Test
    fun hostQueSaiNegaOPedidoAberto() {
        kids()
        var resultado: Boolean? = null
        ParentalGate.request { resultado = it }
        ParentalGate.detachHost(host)
        assertEquals(false, resultado)
        assertNull(ParentalGate.pending.value)
    }

    @Test
    fun soOPrimeiroHostDesenha() {
        kids()
        val outro = Any()
        ParentalGate.attachHost(outro)
        assertSame(host, ParentalGate.primaryHost.value)
        ParentalGate.detachHost(host)
        assertSame(outro, ParentalGate.primaryHost.value)
    }

    @Test
    fun resolverPedidoVelhoNaoAfetaOAberto() {
        kids()
        ParentalGate.request { }
        val velho = ParentalGate.pending.value!!
        ParentalGate.resolve(velho, false)
        var ran = 0
        ParentalGate.guard { ran++ }
        ParentalGate.resolve(velho, true)
        assertEquals(0, ran)
        assertNotNull(ParentalGate.pending.value)
    }

    @Test
    fun awaitPassSuspendeAteAResposta() = runTest {
        kids()
        val resposta = async { ParentalGate.awaitPass() }
        yield()
        val pedido = assertNotNull(ParentalGate.pending.value)
        ParentalGate.resolve(pedido, true)
        assertTrue(resposta.await())
    }

    @Test
    fun awaitPassCanceladoFechaOPortao() = runTest {
        kids()
        val resposta = async { ParentalGate.awaitPass() }
        yield()
        assertNotNull(ParentalGate.pending.value)
        resposta.cancel()
        yield()
        assertNull(ParentalGate.pending.value)
    }

    @Test
    fun awaitPassNoPublicoGeralNaoSuspende() = runTest {
        assertTrue(ParentalGate.awaitPass())
    }

    @Test
    fun urlLauncherEmbrulhadoPassaPeloPortaoEmTodasAsSaidas() {
        val fake = RecordingUrlLauncher()
        val launcher = fake.withParentalGate()
        assertSame(launcher, launcher.withParentalGate())
        kids()
        val saidas: List<() -> Unit> = listOf(
            { launcher.openUrl("https://x") },
            { launcher.openEmail("a@b.c") },
            { launcher.openPhone("1") },
            { launcher.openWhatsApp("1") },
            { launcher.openStorePage() },
            { launcher.openMap("q") },
            { launcher.openSubscriptionManagement() },
            { launcher.openAppSettings() },
            { launcher.openNotificationSettings() },
        )
        saidas.forEachIndexed { i, saida ->
            saida()
            assertEquals(i, fake.calls.size, "saída $i escapou do portão")
            ParentalGate.resolve(ParentalGate.pending.value!!, passed = true)
            assertEquals(i + 1, fake.calls.size)
        }
    }

    @Test
    fun urlLauncherEmbrulhadoNoPublicoGeralVaiDireto() {
        val fake = RecordingUrlLauncher()
        fake.withParentalGate().openUrl("https://x")
        assertEquals(listOf("url:https://x"), fake.calls)
    }

    @Test
    fun shareHandlerEmbrulhadoPassaPeloPortaoEEngoleFalhaDepoisDele() {
        val fake = RecordingShareHandler()
        val handler = fake.withParentalGate()
        kids()
        handler.shareLink("https://x", "oi")
        assertTrue(fake.calls.isEmpty())
        ParentalGate.resolve(ParentalGate.pending.value!!, true)
        assertEquals(listOf("link"), fake.calls)
        // Limpeza não é saída.
        assertEquals(7, handler.clearSharedFiles(0))
        fake.fail = true
        handler.shareText("t")
        ParentalGate.resolve(ParentalGate.pending.value!!, true) // não lança
    }

    @Test
    fun appInfantilNaoPedeAvaliacaoSozinho() {
        val store = FakeReviewStore()
        val manager = AppReviewManager(triggerCount = 1, store = store)
        KmpLibAudience.configure(AppAudience.KIDS)
        assertFalse(manager.onCompletion())
        assertEquals(0, store.getCompletionCount())
        KmpLibAudience.configure(AppAudience.GENERAL)
        assertTrue(manager.onCompletion())
    }

    private class RecordingUrlLauncher : UrlLauncher {
        val calls = mutableListOf<String>()
        override fun openUrl(url: String) { calls += "url:$url" }
        override fun openEmail(to: String, subject: String, body: String) { calls += "email" }
        override fun openPhone(phoneNumber: String) { calls += "phone" }
        override fun openWhatsApp(phone: String, message: String) { calls += "whatsapp" }
        override fun openStorePage(androidPackage: String?, iosAppId: String?) { calls += "store" }
        override fun openMap(query: String) { calls += "map" }
        override fun openSubscriptionManagement() { calls += "subs" }
        override fun openAppSettings() { calls += "settings" }
        override fun openNotificationSettings() { calls += "notif" }
    }

    private class RecordingShareHandler : ShareHandler {
        val calls = mutableListOf<String>()
        var fail = false
        override fun shareText(text: String, title: String) {
            if (fail) error("sem chooser")
            calls += "text"
        }
        override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) { calls += "image" }
        override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) { calls += "file" }
        override fun shareLink(url: String, message: String, title: String) { calls += "link" }
        override fun clearSharedFiles(olderThanMillis: Long): Int = 7
    }
}
