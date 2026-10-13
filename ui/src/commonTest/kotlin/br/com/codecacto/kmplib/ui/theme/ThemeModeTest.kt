@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package br.com.codecacto.kmplib.ui.theme

import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.prefs.InMemoryAppPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {

    @Test
    fun `fromStorage le os tres valores e cai no sistema para o resto`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage(" DARK "))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("noturno"))
    }

    @Test
    fun `valores gravados sao estaveis`() {
        // Renomear quebra a escolha de quem já gravou — é contrato com o disco do aparelho.
        assertEquals(listOf("system", "light", "dark"), ThemeMode.entries.map { it.storageValue })
    }

    @Test
    fun `resolveDark segue o sistema so em SYSTEM`() {
        assertTrue(ThemeMode.SYSTEM.resolveDark(systemIsDark = true))
        assertFalse(ThemeMode.SYSTEM.resolveDark(systemIsDark = false))
        assertFalse(ThemeMode.LIGHT.resolveDark(systemIsDark = true))
        assertTrue(ThemeMode.DARK.resolveDark(systemIsDark = false))
    }

    @Test
    fun `controlador nasce no sistema e carrega o valor gravado`() = runTest {
        val prefs = InMemoryAppPreferences().apply { setString(ThemeModeController.DEFAULT_KEY, "dark") }
        val controller = ThemeModeController(prefs, backgroundScope)
        assertEquals(ThemeMode.SYSTEM, controller.mode.value)
        assertFalse(controller.isLoaded.value)
        runCurrent()
        assertEquals(ThemeMode.DARK, controller.mode.value)
        assertTrue(controller.isLoaded.value)
    }

    @Test
    fun `sem nada gravado fica o sistema`() = runTest {
        val controller = ThemeModeController(InMemoryAppPreferences(), backgroundScope)
        runCurrent()
        assertEquals(ThemeMode.SYSTEM, controller.mode.value)
        assertTrue(controller.isLoaded.value)
    }

    @Test
    fun `setMode troca na hora e persiste`() = runTest {
        val prefs = InMemoryAppPreferences()
        val controller = ThemeModeController(prefs, backgroundScope)
        runCurrent()
        controller.setMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, controller.currentMode)
        runCurrent()
        assertEquals(ThemeMode.LIGHT, controller.mode.value)
        assertEquals("light", prefs.getString(ThemeModeController.DEFAULT_KEY, ""))

        // Um processo novo lê a escolha.
        val outro = ThemeModeController(prefs, backgroundScope)
        runCurrent()
        assertEquals(ThemeMode.LIGHT, outro.mode.value)
    }

    @Test
    fun `toques rapidos persistem o ultimo`() = runTest {
        val prefs = InMemoryAppPreferences()
        val controller = ThemeModeController(prefs, backgroundScope)
        controller.setMode(ThemeMode.DARK)
        controller.setMode(ThemeMode.LIGHT)
        controller.setMode(ThemeMode.SYSTEM)
        runCurrent()
        assertEquals(ThemeMode.SYSTEM, controller.mode.value)
        assertEquals("system", prefs.getString(ThemeModeController.DEFAULT_KEY, ""))
    }

    @Test
    fun `escolha feita antes de a leitura terminar vence a leitura`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val inner = InMemoryAppPreferences().apply { setString(ThemeModeController.DEFAULT_KEY, "dark") }
        val slow = object : AppPreferences by inner {
            override suspend fun getString(key: String, default: String): String {
                gate.await()
                return inner.getString(key, default)
            }
        }
        val controller = ThemeModeController(slow, backgroundScope)
        runCurrent()
        controller.setMode(ThemeMode.LIGHT)
        gate.complete(Unit)
        runCurrent()
        assertEquals(ThemeMode.LIGHT, controller.mode.value)
        assertEquals("light", inner.getString(ThemeModeController.DEFAULT_KEY, ""))
    }

    @Test
    fun `leitura que falha deixa o sistema e marca carregado`() = runTest {
        val inner = InMemoryAppPreferences()
        val broken = object : AppPreferences by inner {
            override suspend fun getString(key: String, default: String): String = error("disco")
        }
        val controller = ThemeModeController(broken, backgroundScope)
        runCurrent()
        assertEquals(ThemeMode.SYSTEM, controller.mode.value)
        assertTrue(controller.isLoaded.value)
    }

    @Test
    fun `textos e ids das opcoes`() {
        val t = ThemeModeTexts()
        assertEquals(listOf("Padrão do sistema", "Claro", "Escuro"), ThemeMode.entries.map(t::labelOf))
        assertEquals(listOf("tema-sistema", "tema-claro", "tema-escuro"), ThemeMode.entries.map(ThemeModeTestTags::option))
        assertEquals("tema", ThemeModeTestTags.GRUPO)
    }
}
