package br.com.codecacto.kmplib.video.feed

import br.com.codecacto.kmplib.video.VideoErrorKind
import br.com.codecacto.kmplib.video.VideoStatus
import br.com.codecacto.kmplib.video.VideoStreamKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Um player de mentira: registra o que o controller mandou fazer. */
private class PlayerFalso : FeedVideoEngine() {
    var tocando = false
    var mudo: Boolean? = null
    val carregamentos = mutableListOf<String>()
    var esvaziado = 0
    var destruido = false

    override fun load(url: String, kind: VideoStreamKind) {
        carregamentos += url
        loadedUrl = url
        status = VideoStatus.Loading
    }

    override fun play() {
        tocando = true
        if (status !is VideoStatus.Error && loadedUrl != null) status = VideoStatus.Playing
    }

    override fun pause() {
        tocando = false
        if (status == VideoStatus.Playing) status = VideoStatus.Paused
    }

    override fun setMuted(muted: Boolean) {
        mudo = muted
    }

    override fun clear() {
        esvaziado++
        tocando = false
        loadedUrl = null
        status = VideoStatus.Idle
    }

    override fun release() {
        destruido = true
        tocando = false
    }

    fun falhar(kind: VideoErrorKind) {
        status = VideoStatus.Error(kind, "falhou")
    }

    fun outroAppTomouOAudio() = onAudioLost?.invoke()
}

class FeedVideoControllerTest {

    private val criados = mutableListOf<PlayerFalso>()
    private val som = FeedVideoSoundState()

    private fun controller(config: FeedVideoConfig = FeedVideoConfig()) =
        FeedVideoController(config, som) { PlayerFalso().also { criados += it } }

    private fun FeedVideoController.item(key: String, fracao: Float, top: Float) {
        setSource(key, "https://cdn/$key.m3u8", VideoStreamKind.Auto)
        report(key, fracao, top)
    }

    private fun FeedVideoController.player(key: String) = engineFor(key) as PlayerFalso?

    private val tocandoAgora get() = criados.filter { it.tocando && !it.destruido }

    // ------------------------------------------------------------------ um de cada vez

    @Test
    fun soOMaisVisivelToca() {
        val c = controller()
        c.item("a", 1f, 0f)
        c.item("b", 0.3f, 1200f)

        assertEquals("a", c.activeKey)
        assertEquals(1, tocandoAgora.size)
        assertTrue(c.player("a")!!.tocando)
        // O segundo está PREPARADO (pré-carregado), mas parado.
        assertEquals(listOf("https://cdn/b.m3u8"), c.player("b")!!.carregamentos)
        assertFalse(c.player("b")!!.tocando)
    }

    @Test
    fun rolarPassaAVezEPausaOAnterior() {
        val c = controller()
        c.item("a", 1f, 0f)
        c.item("b", 0.3f, 1200f)
        val playerDoA = c.player("a")!!
        val playerDoB = c.player("b")!!

        c.report("a", 0.4f, -600f)
        c.report("b", 0.95f, 600f)

        assertEquals("b", c.activeKey)
        assertFalse(playerDoA.tocando)
        assertTrue(playerDoB.tocando)
        assertEquals(1, tocandoAgora.size)
        // Quem já estava preparado não recarrega ao ganhar a vez.
        assertEquals(1, playerDoB.carregamentos.size)
    }

    @Test
    fun nadaAcimaDoLimiteNadaToca() {
        val c = controller()
        c.item("a", 0.5f, 0f)
        assertNull(c.activeKey)
        assertTrue(tocandoAgora.isEmpty())
        assertFalse(c.isPlaying)
    }

    @Test
    fun relatorioAntesDaFonteEhIgnorado() {
        val c = controller()
        c.report("a", 1f, 0f)
        assertNull(c.activeKey)
        assertTrue(criados.isEmpty())
    }

    // ------------------------------------------------------------------------------ pool

    @Test
    fun oPoolNuncaPassaDoTeto() {
        val c = controller(FeedVideoConfig(maxPlayers = 2))
        (0 until 8).forEach { c.item("v$it", 0.2f + it * 0.1f, it * 300f) }
        (0 until 8).forEach { c.report("v$it", 1f - it * 0.1f, it * 300f) }
        assertEquals(2, criados.size)
    }

    @Test
    fun playerEhRecicladoEntreItens() {
        val c = controller(FeedVideoConfig(maxPlayers = 1))
        c.item("a", 1f, 0f)
        val unico = c.player("a")!!
        c.report("a", 0f, -2000f)
        c.item("b", 1f, 0f)

        assertEquals(1, criados.size)
        assertSame(unico, c.player("b"))
        assertEquals(listOf("https://cdn/a.m3u8", "https://cdn/b.m3u8"), unico.carregamentos)
        assertNull(c.engineFor("a"))
    }

    @Test
    fun itemQueSaiDaComposicaoDevolveOPlayerEsvaziado() {
        val c = controller()
        c.item("a", 1f, 0f)
        val player = c.player("a")!!
        c.remove("a")

        assertNull(c.activeKey)
        assertEquals(1, player.esvaziado)
        assertNull(player.loadedUrl)
        assertFalse(player.destruido)
    }

    @Test
    fun trocarAUrlRecarregaNoMesmoPlayer() {
        val c = controller()
        c.item("a", 1f, 0f)
        val player = c.player("a")!!
        c.setSource("a", "https://cdn/a.m3u8?token=novo", VideoStreamKind.Auto)

        assertSame(player, c.player("a"))
        assertEquals("https://cdn/a.m3u8?token=novo", player.carregamentos.last())
        assertTrue(player.tocando)
    }

    @Test
    fun videoQueFalhouTentaDeNovoAoVoltarAVez() {
        val c = controller(FeedVideoConfig(maxPlayers = 1))
        c.item("a", 1f, 0f)
        c.player("a")!!.falhar(VideoErrorKind.Network)
        c.report("a", 0f, 2000f) // saiu da tela
        c.report("a", 1f, 0f) // voltou

        assertEquals(2, criados.single().carregamentos.size)
    }

    @Test
    fun erroNaoRecarregaAcadaRelatorioDeRolagem() {
        val c = controller()
        c.item("a", 1f, 0f)
        c.player("a")!!.falhar(VideoErrorKind.Network)
        repeat(5) { c.report("a", 0.9f + it * 0.01f, it.toFloat()) }
        assertEquals(1, c.player("a")!!.carregamentos.size)
    }

    @Test
    fun retryRecarregaEToca() {
        val c = controller()
        c.item("a", 1f, 0f)
        val player = c.player("a")!!
        player.falhar(VideoErrorKind.Network)
        c.retry("a")
        assertEquals(2, player.carregamentos.size)
        assertEquals(VideoStatus.Playing, player.status)
    }

    // ------------------------------------------------------------------------------- som

    @Test
    fun oFeedNasceMudo() {
        val c = controller()
        c.item("a", 1f, 0f)
        c.item("b", 0.4f, 900f)
        assertTrue(som.isMuted)
        assertTrue(criados.all { it.mudo == true })
    }

    @Test
    fun ligarOSomValeParaOFeedInteiroEParaOProximo() {
        val c = controller()
        c.item("a", 1f, 0f)
        c.toggleMuted()

        assertFalse(som.isMuted)
        assertTrue(criados.all { it.mudo == false })

        // O próximo que entrar na vez já vem com som.
        c.item("b", 0.2f, 1200f)
        c.report("a", 0.1f, -1500f)
        c.report("b", 1f, 0f)
        assertEquals(false, c.player("b")!!.mudo)
    }

    @Test
    fun somMudadoForaDoFeedEhAplicadoPeloApplySound() {
        val c = controller()
        c.item("a", 1f, 0f)
        som.setMuted(false) // o detalhe da publicação, dividindo o mesmo estado
        c.applySound()
        assertEquals(false, c.player("a")!!.mudo)
    }

    @Test
    fun outroAppTomandoOAudioVoltaOFeedAMudoESegueTocando() {
        val c = controller()
        c.item("a", 1f, 0f)
        c.setMuted(false)
        val player = c.player("a")!!

        player.tocando = false // o sistema pausou
        player.outroAppTomouOAudio()

        assertTrue(som.isMuted)
        assertEquals(true, player.mudo)
        assertTrue(player.tocando)
    }

    @Test
    fun perdaDeAudioDoPlayerQueNaoTemAVezSoEmudece() {
        val c = controller()
        c.item("a", 1f, 0f)
        c.item("b", 0.3f, 1200f)
        c.setMuted(false)
        c.player("b")!!.outroAppTomouOAudio()

        assertTrue(som.isMuted)
        assertFalse(c.player("b")!!.tocando)
    }

    // ------------------------------------------------------------------ ciclo de vida

    @Test
    fun onStopDestroiTodosOsPlayers() {
        val c = controller()
        c.item("a", 1f, 0f)
        c.item("b", 0.4f, 900f)
        c.onStop()

        assertTrue(criados.all { it.destruido })
        assertNull(c.activeKey)
        assertNull(c.engineFor("a"))
        assertFalse(c.isPlaying)
    }

    @Test
    fun rolagemNoSegundoPlanoNaoRecriaPlayer() {
        val c = controller()
        c.item("a", 1f, 0f)
        c.onStop()
        c.report("a", 0.9f, 10f)
        assertEquals(1, criados.size)
    }

    @Test
    fun onStartRecriaEVoltaATocarOQueEstaNaTela() {
        val c = controller()
        c.item("a", 1f, 0f)
        val antigo = c.player("a")!!
        c.onStop()
        c.onStart()

        val novo = c.player("a")
        assertNotNull(novo)
        assertNotSame(antigo, novo)
        assertTrue(novo.tocando)
        assertEquals("a", c.activeKey)
    }

    @Test
    fun releaseDestroiTudoEIgnoraORestante() {
        val c = controller()
        c.item("a", 1f, 0f)
        c.release()
        c.item("b", 1f, 0f)
        c.onStart()

        assertTrue(criados.single().destruido)
        assertNull(c.activeKey)
    }

    @Test
    fun desligarAReproducaoPausaSemDestruir() {
        val c = controller()
        c.item("a", 1f, 0f)
        val player = c.player("a")!!

        c.setPlaybackEnabled(false)
        assertFalse(player.tocando)
        assertFalse(player.destruido)
        assertFalse(c.isPlaybackEnabled)
        assertNull(c.activeKey)

        c.setPlaybackEnabled(true)
        assertTrue(player.tocando)
        assertSame(player, c.player("a"))
        assertEquals(1, player.carregamentos.size)
    }

    @Test
    fun isPlayingSegueOStatusDoPlayerDaVez() {
        val c = controller()
        c.item("a", 1f, 0f)
        assertTrue(c.isPlaying)
        c.player("a")!!.falhar(VideoErrorKind.Network)
        assertFalse(c.isPlaying)
    }
}
