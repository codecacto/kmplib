package br.com.codecacto.kmplib.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ramo de ARQUIVO (`VideoSource.File`) do `VideoPlayerInline` (2.329.0): montado de saída não toca
 * sozinho, pausa no segundo plano e para ao sair — nas duas plataformas. Sem teste de UI na kmplib,
 * a promessa é conferida no fonte das duas `actual`.
 */
class VideoPlayerInlineArquivoTest {

    private fun fonte(plataforma: String): String =
        File("src/${plataforma}Main/kotlin/br/com/codecacto/kmplib/ui/components/video/VideoPlayerInline.$plataforma.kt")
            .also { assertTrue(it.exists(), "fonte não encontrada a partir de ${File(".").canonicalPath}") }
            .readText()

    private fun ramoArquivo(fonte: String): String {
        val inicio = fonte.indexOf("is VideoSource.File ->")
        val fim = fonte.indexOf("is VideoSource.External ->", inicio)
        assertTrue(inicio >= 0 && fim > inicio, "ramo VideoSource.File não encontrado")
        return fonte.substring(inicio, fim)
    }

    @Test
    fun `so o toque na capa autoriza tocar sozinho`() {
        for (p in listOf("android", "ios")) {
            val f = fonte(p)
            assertTrue("montadoPeloToque = true" in f, "$p: o toque na capa precisa marcar a origem")
            val ramo = ramoArquivo(f)
            assertTrue("if (autoplay)" in ramo, "$p: início do arquivo condicionado ao toque")
            assertFalse(Regex("""setOnPreparedListener \{ it\.start\(\) \}""").containsMatchIn(ramo), "$p: start incondicional")
        }
    }

    @Test
    fun `pausa no segundo plano e para ao sair`() {
        for (p in listOf("android", "ios")) {
            val ramo = ramoArquivo(fonte(p))
            assertTrue("Lifecycle.Event.ON_STOP" in ramo, "$p: pausa no ON_STOP")
            assertFalse("onRelease = { }" in ramo, "$p: onRelease vazio deixa o áudio tocando")
        }
        assertTrue("player.pause()" in ramoArquivo(fonte("ios")))
        assertTrue(".hide()" in ramoArquivo(fonte("android")), "MediaController escondido ao sair")
    }
}
