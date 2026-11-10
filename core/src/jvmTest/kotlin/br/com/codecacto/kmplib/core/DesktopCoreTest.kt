package br.com.codecacto.kmplib.core

import br.com.codecacto.kmplib.core.context.DesktopAppContext
import br.com.codecacto.kmplib.core.context.DesktopAppInfo
import br.com.codecacto.kmplib.core.context.defaultAppDataDirectory
import br.com.codecacto.kmplib.core.context.isValidDesktopAppId
import br.com.codecacto.kmplib.core.locale.canonicalTemplate
import br.com.codecacto.kmplib.core.locale.styledPattern
import br.com.codecacto.kmplib.core.network.createHttpClient
import br.com.codecacto.kmplib.core.prefs.DesktopAppPreferences
import br.com.codecacto.kmplib.core.storage.DesktopFileBlobStore
import br.com.codecacto.kmplib.core.storage.deleteFileDurably
import br.com.codecacto.kmplib.core.storage.writeFileDurably
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.core.util.currentPlatform
import io.ktor.client.engine.okhttp.OkHttpEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O alvo desktop do `kmplib-core` (2.353.0). */
class DesktopCoreTest {

    private val dir = Files.createTempDirectory("kmplib-core-desktop")

    @AfterTest
    fun limpar() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun pasta_de_dados_por_sistema() {
        val win = defaultAppDataDirectory("statushub", "Windows 11", { if (it == "LOCALAPPDATA") "C:\\Users\\ana\\AppData\\Local" else null }, "C:\\Users\\ana")
        assertTrue(win.toString().endsWith("statushub"))
        assertTrue(win.toString().contains("AppData"))
        assertEquals(
            Paths.get("/home/ana", "Library", "Application Support", "statushub"),
            defaultAppDataDirectory("statushub", "Mac OS X", { null }, "/home/ana"),
        )
        assertEquals(Paths.get("/home/ana/.local/share/statushub"), defaultAppDataDirectory("statushub", "Linux", { null }, "/home/ana"))
        assertEquals(Paths.get("/xdg/statushub"), defaultAppDataDirectory("statushub", "Linux", { "/xdg" }, "/home/ana"))
    }

    @Test
    fun app_id_que_escapa_da_pasta_e_recusado() {
        assertTrue(isValidDesktopAppId("statushub-desktop.v2"))
        assertFalse(isValidDesktopAppId(".."))
        assertFalse(isValidDesktopAppId("a/b"))
        assertFalse(isValidDesktopAppId("a\\b"))
        assertFalse(isValidDesktopAppId(""))
        assertFailsWith<IllegalArgumentException> { DesktopAppInfo(appId = "../x") }
    }

    @Test
    fun build_info_e_plataforma_vem_do_init() {
        initKmpLibCore(DesktopAppInfo(appId = "kmplib-teste", appName = "StatusHub", versionName = "1.2.3", buildNumber = "45", isDebug = true, dataDirectory = dir))
        assertEquals("StatusHub", BuildInfo.appName)
        assertEquals("1.2.3", BuildInfo.appVersionName)
        assertEquals("45", BuildInfo.appBuildNumber)
        assertTrue(BuildInfo.isDebug)
        assertEquals(dir, DesktopAppContext.appDataDirectory())
        assertEquals("desktop", currentPlatform)
    }

    @Test
    fun gravacao_duravel_substitui_inteiro_e_apaga() {
        val f = dir.resolve("sub").resolve("x.bin")
        writeFileDurably(f, byteArrayOf(1, 2, 3))
        writeFileDurably(f, byteArrayOf(9))
        assertContentEquals(byteArrayOf(9), Files.readAllBytes(f))
        assertTrue(Files.list(f.parent).use { s -> s.noneMatch { it.fileName.toString().startsWith("~") } }, "sem temporário sobrando")
        assertTrue(deleteFileDurably(f))
        assertFalse(deleteFileDurably(f))
    }

    @Test
    fun preferencias_persistem_e_notificam() = runTest {
        val arquivo = dir.resolve("preferences.json")
        val prefs = DesktopAppPreferences.forFile(arquivo)
        prefs.setString("tema", "escuro")
        prefs.setBoolean("tour", true)
        prefs.setLong("ultimo", 42L)
        assertEquals("escuro", prefs.getString("tema"))
        assertTrue(prefs.getBoolean("tour"))
        assertEquals(42L, prefs.getLong("ultimo"))
        assertTrue(Files.readString(arquivo).contains("escuro"))
        val emitidos = mutableListOf<Int>()
        val job = launch { prefs.observeInt("n", 0).take(2).toList(emitidos) }
        yield()
        prefs.setInt("n", 7)
        job.join()
        assertEquals(listOf(0, 7), emitidos)
        prefs.remove("tema")
        assertFalse(prefs.has("tema"))
        prefs.clear()
        assertEquals(0L, prefs.observeLong("ultimo", 0L).first())
    }

    @Test
    fun blob_store_desktop() = runTest {
        val store = DesktopFileBlobStore(dir.resolve("blobs"))
        assertTrue(store.write("foto-1.jpg", byteArrayOf(1, 2)))
        assertFalse(store.write("../fora", byteArrayOf(1)))
        assertContentEquals(byteArrayOf(1, 2), store.read("foto-1.jpg"))
        assertEquals(listOf("foto-1.jpg"), store.ids())
        assertEquals(2L, store.sizeOf("foto-1.jpg"))
        assertTrue(store.localFilePath("foto-1.jpg")!!.endsWith("foto-1.jpg"))
        assertTrue(store.delete("foto-1.jpg"))
        assertNull(store.read("foto-1.jpg"))
    }

    @Test
    fun cliente_http_padrao_usa_okhttp() {
        val client = createHttpClient()
        try {
            assertIs<OkHttpEngine>(client.engine)
        } finally {
            client.close()
        }
    }

    @Test
    fun padrao_de_data_por_regiao() {
        assertEquals("yyyyMMdd", canonicalTemplate("ddMMyyyy"))
        assertEquals("jjmm", canonicalTemplate("jjmm"))
        val br = Locale.forLanguageTag("pt-BR")
        assertEquals("dd/MM/yyyy", styledPattern(br, "ddMMyyyy"))
        assertEquals("dd/MM", styledPattern(br, "ddMM"))
        assertEquals("MM/dd/yyyy", styledPattern(Locale.US, "ddMMyyyy"))
        assertEquals("MM/dd", styledPattern(Locale.US, "ddMM"))
        assertTrue(styledPattern(br, "jjmm").contains("HH:mm"))
    }
}
