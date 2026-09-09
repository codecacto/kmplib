package br.com.codecacto.kmplib.video.download

import br.com.codecacto.kmplib.core.prefs.InMemoryAppPreferences
import br.com.codecacto.kmplib.video.VideoStreamKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaDownloadStoreTest {

    private fun store() = PreferencesMediaDownloadStore(InMemoryAppPreferences())

    private val aula = MediaDownloadRecord(
        id = "aula-1",
        url = "https://cdn.exemplo/aula-1.m3u8?token=abc",
        kind = VideoStreamKind.Hls,
        title = "Cadência do saque plano",
        groupId = "curso-7",
        estimatedBytes = 120_000_000,
        expiresAtMillis = 9_999_999L,
    )

    @Test
    fun `grava, le e apaga`() = runTest {
        val store = store()
        store.put(aula)
        assertEquals(aula, store.get("aula-1"))
        assertEquals(1, store.all().size)
        store.remove("aula-1")
        assertNull(store.get("aula-1"))
    }

    @Test
    fun `regravar o mesmo id substitui, nao duplica`() = runTest {
        val store = store()
        store.put(aula)
        store.put(aula.copy(url = "https://cdn.exemplo/aula-1.m3u8?token=NOVO"))
        assertEquals(1, store.all().size)
        assertTrue(store.get("aula-1")!!.url.endsWith("NOVO"))
    }

    @Test
    fun `o que foi gravado sobrevive a uma nova instancia sobre as mesmas preferencias`() = runTest {
        // É a metade do requisito "retomar depois de o app ser fechado" que é NOSSA: o subsistema
        // nativo retoma os bytes, mas não sabe a que curso a aula pertence nem quando ela expira.
        val prefs = InMemoryAppPreferences()
        PreferencesMediaDownloadStore(prefs).put(aula)
        val outraSessao = PreferencesMediaDownloadStore(prefs)
        assertEquals("curso-7", outraSessao.get("aula-1")?.groupId)
        assertEquals(9_999_999L, outraSessao.get("aula-1")?.expiresAtMillis)
    }

    @Test
    fun `registro ilegivel nao derruba o app`() = runTest {
        // O pior caso aceitável é a tela de Downloads perder os nomes — nunca o app não abrir.
        val prefs = InMemoryAppPreferences()
        prefs.setString(PreferencesMediaDownloadStore.DEFAULT_KEY, "{isto não é json}")
        assertEquals(emptyList(), PreferencesMediaDownloadStore(prefs).all())
    }

    @Test
    fun `o pedido vai e volta inteiro pelo registro`() = runTest {
        val pedido = MediaDownloadRequest(
            id = "aula-9",
            url = "https://cdn.exemplo/aula-9.m3u8",
            kind = VideoStreamKind.Hls,
            title = "Erros comuns",
            groupId = "curso-7",
            quality = MediaDownloadQuality.Low,
            estimatedBytes = 50_000_000,
            expiresAtMillis = 123L,
        )
        assertEquals(pedido, pedido.toRecord(nowMillis = 1L).toRequest())
    }
}
