package br.com.codecacto.kmplib.video.download

import kotlin.test.Test
import kotlin.test.assertEquals

/** A máquina de estados da fila — a ordem de precedência é o contrato. */
class MediaDownloadStatusTest {

    private val falha = MediaDownloadStatus.Failed(MediaDownloadErrorKind.Network, "sem rede")

    private fun estado(
        completed: Boolean = false,
        running: Boolean = false,
        pausedByUser: Boolean = false,
        failure: MediaDownloadStatus.Failed? = null,
        expired: Boolean = false,
    ) = mediaDownloadStatusOf(completed, running, pausedByUser, failure, expired)

    @Test
    fun `expirado vence ate o concluido`() {
        // O direito caiu. Os bytes ainda estão no disco, e é exatamente por isso que o estado não
        // pode ser "concluído": a tela mostraria "disponível offline" para quem perdeu o acesso.
        assertEquals(MediaDownloadStatus.Expired, estado(completed = true, expired = true))
        assertEquals(MediaDownloadStatus.Expired, estado(running = true, expired = true))
        assertEquals(MediaDownloadStatus.Expired, estado(pausedByUser = true, expired = true))
        assertEquals(MediaDownloadStatus.Expired, estado(failure = falha, expired = true))
    }

    @Test
    fun `concluido vence falha`() {
        // Uma falha registrada depois do término (requisito reavaliado, limpeza) não pode desfazer
        // um download que já está inteiro no disco.
        assertEquals(MediaDownloadStatus.Completed, estado(completed = true, failure = falha))
    }

    @Test
    fun `pausa vence fila e falha, e sobrevive ao reinicio`() {
        assertEquals(MediaDownloadStatus.Paused, estado(pausedByUser = true))
        assertEquals(MediaDownloadStatus.Paused, estado(pausedByUser = true, failure = falha))
        assertEquals(MediaDownloadStatus.Paused, estado(pausedByUser = true, running = true))
    }

    @Test
    fun `falha vence fila`() {
        assertEquals(falha, estado(failure = falha))
    }

    @Test
    fun `sem nada, esta na fila`() {
        assertEquals(MediaDownloadStatus.Queued, estado())
        assertEquals(MediaDownloadStatus.Downloading, estado(running = true))
    }

    @Test
    fun `isActive cobre fila e transferencia, e nada mais`() {
        fun ativo(s: MediaDownloadStatus) = MediaDownload(id = "a", url = "u", status = s).isActive
        assertEquals(true, ativo(MediaDownloadStatus.Queued))
        assertEquals(true, ativo(MediaDownloadStatus.Downloading))
        assertEquals(false, ativo(MediaDownloadStatus.Paused))
        assertEquals(false, ativo(MediaDownloadStatus.Completed))
        assertEquals(false, ativo(MediaDownloadStatus.Expired))
        assertEquals(false, ativo(falha))
    }

    @Test
    fun `403 de URL assinada nao e erro de rede tambem no download`() {
        assertEquals(MediaDownloadErrorKind.Expired, mediaDownloadErrorKindForHttpStatus(401))
        assertEquals(MediaDownloadErrorKind.Expired, mediaDownloadErrorKindForHttpStatus(403))
        assertEquals(MediaDownloadErrorKind.Expired, mediaDownloadErrorKindForHttpStatus(410))
        assertEquals(MediaDownloadErrorKind.NotFound, mediaDownloadErrorKindForHttpStatus(404))
        assertEquals(MediaDownloadErrorKind.Network, mediaDownloadErrorKindForHttpStatus(503))
        assertEquals(MediaDownloadErrorKind.Unknown, mediaDownloadErrorKindForHttpStatus(418))
    }

    @Test
    fun `cada tipo de falha tem uma frase propria`() {
        val textos = MediaDownloadTexts()
        val frases = MediaDownloadErrorKind.entries.map { textos.messageFor(it) }
        assertEquals(frases.size, frases.toSet().size, "duas falhas com a mesma frase escondem a saída certa")
    }
}
