package br.com.codecacto.kmplib.video.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaDownloadPolicyTest {

    private val mb = 1024L * 1024L

    // -----------------------------------------------------------------------------------------
    // Espaço em disco
    // -----------------------------------------------------------------------------------------

    @Test
    fun `cabe quando sobra alem da reserva`() {
        assertEquals(
            MediaDownloadSpaceCheck.Fits,
            checkMediaDownloadSpace(requiredBytes = 500 * mb, availableBytes = 1000 * mb, reservedBytes = 300 * mb),
        )
    }

    @Test
    fun `a reserva do aparelho e intocavel`() {
        // 500 MB pedidos, 700 MB livres: caberia na conta ingênua. Não cabe: encher o celular até
        // a borda faz o sistema recusar instalar app e apagar o cache de todo mundo.
        val resultado = checkMediaDownloadSpace(500 * mb, availableBytes = 700 * mb, reservedBytes = 300 * mb)
        assertTrue(resultado is MediaDownloadSpaceCheck.NotEnough)
        assertEquals(100 * mb, resultado.missingBytes)
    }

    @Test
    fun `aparelho ja abaixo da reserva nao devolve numero negativo`() {
        val resultado = checkMediaDownloadSpace(100 * mb, availableBytes = 50 * mb, reservedBytes = 300 * mb)
        assertTrue(resultado is MediaDownloadSpaceCheck.NotEnough)
        assertEquals(100 * mb, resultado.missingBytes, "o que falta é o pedido inteiro, nunca mais do que ele")
    }

    @Test
    fun `tamanho desconhecido nao impede o download`() {
        // Recusar por não saber o tamanho impediria de baixar de qualquer servidor sem
        // `Content-Length`. Segue-se, e se não couber a falha vem no meio.
        assertEquals(MediaDownloadSpaceCheck.Unknown, checkMediaDownloadSpace(0, 10 * mb))
    }

    // -----------------------------------------------------------------------------------------
    // Expiração do direito
    // -----------------------------------------------------------------------------------------

    @Test
    fun `sem data - nao expira`() {
        assertEquals(false, isMediaDownloadExpired(null, nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun `expira no instante exato`() {
        assertEquals(false, isMediaDownloadExpired(1_000L, nowMillis = 999L))
        assertEquals(true, isMediaDownloadExpired(1_000L, nowMillis = 1_000L))
        assertEquals(true, isMediaDownloadExpired(1_000L, nowMillis = 1_001L))
    }

    @Test
    fun `so os vencidos saem`() {
        val registros = listOf(
            registro("a", expira = 500L),
            registro("b", expira = 5_000L),
            registro("c", expira = null),
        )
        assertEquals(listOf("a"), expiredMediaDownloadIds(registros, nowMillis = 1_000L))
    }

    // -----------------------------------------------------------------------------------------
    // Retomada depois de o app ser fechado
    // -----------------------------------------------------------------------------------------

    @Test
    fun `volta baixando o que estava na fila - e so isso`() {
        val registros = listOf(
            registro("na-fila"),
            registro("concluida", completa = true),
            registro("pausada", pausada = true),
            registro("vencida", expira = 100L),
        )
        val retomar = mediaDownloadsToResume(registros, nowMillis = 1_000L).map { it.id }
        assertEquals(listOf("na-fila"), retomar)
    }

    @Test
    fun `pausa do usuario sobrevive ao reinicio`() {
        // É o campo `pausedByUser` do registro que garante isto: o subsistema nativo do iOS não tem
        // onde guardar "quem pausou fui eu", e sem ele tudo voltaria baixando na próxima abertura.
        val pausada = registro("x", pausada = true)
        assertEquals(emptyList(), mediaDownloadsToResume(listOf(pausada), nowMillis = 0L))
    }

    // -----------------------------------------------------------------------------------------
    // Renovação da URL assinada
    // -----------------------------------------------------------------------------------------

    @Test
    fun `so URL vencida renova`() {
        assertEquals(true, shouldRenewDownloadUrl(MediaDownloadErrorKind.Expired, renewalsSoFar = 0))
        assertEquals(false, shouldRenewDownloadUrl(MediaDownloadErrorKind.Network, renewalsSoFar = 0))
        assertEquals(false, shouldRenewDownloadUrl(MediaDownloadErrorKind.NoSpace, renewalsSoFar = 0))
        assertEquals(false, shouldRenewDownloadUrl(MediaDownloadErrorKind.NotFound, renewalsSoFar = 0))
    }

    @Test
    fun `o teto de renovacoes existe para nao virar laco`() {
        assertEquals(true, shouldRenewDownloadUrl(MediaDownloadErrorKind.Expired, MAX_DOWNLOAD_URL_RENEWALS - 1))
        assertEquals(false, shouldRenewDownloadUrl(MediaDownloadErrorKind.Expired, MAX_DOWNLOAD_URL_RENEWALS))
        assertEquals(2, MAX_DOWNLOAD_URL_RENEWALS)
    }

    private fun registro(
        id: String,
        completa: Boolean = false,
        pausada: Boolean = false,
        expira: Long? = null,
    ) = MediaDownloadRecord(
        id = id,
        url = "https://cdn.exemplo/$id.m3u8",
        completed = completa,
        pausedByUser = pausada,
        expiresAtMillis = expira,
    )
}
