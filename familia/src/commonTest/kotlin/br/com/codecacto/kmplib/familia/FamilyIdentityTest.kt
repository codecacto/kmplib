package br.com.codecacto.kmplib.familia

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.developer.DeveloperApp
import br.com.codecacto.kmplib.familia.screens.selectFamilyOtherApps
import br.com.codecacto.kmplib.familia.theme.FamilyApp
import br.com.codecacto.kmplib.familia.theme.FamilyAppIdentity
import br.com.codecacto.kmplib.familia.theme.FamilyColorScheme
import br.com.codecacto.kmplib.familia.theme.FamilyPalette
import br.com.codecacto.kmplib.familia.theme.FamilyPalettes
import br.com.codecacto.kmplib.familia.theme.familyApp
import br.com.codecacto.kmplib.familia.theme.familyOtherAppsCatalogFor
import br.com.codecacto.kmplib.familia.theme.familyPaletteFor
import br.com.codecacto.kmplib.familia.theme.toAppColorPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Identidade de app de FORA da família cristã (2.358.0). */
class FamilyIdentityTest {

    private fun palette(seed: Long) = FamilyPalette(
        bg = Color(0xFF000000 + seed), sf = Color(0xFF000010 + seed), sf2 = Color(0xFF000020 + seed),
        fg = Color(0xFF000030 + seed), mu = Color(0xFF000040 + seed), ln = Color(0xFF000050 + seed),
        ac = Color(0xFF000060 + seed), aci = Color(0xFF000070 + seed), acs = Color(0xFF000080 + seed),
        acd = Color(0xFF000090 + seed), ac2 = Color(0xFF0000A0 + seed),
    )

    private val icon = ImageVector.Builder(name = "teste", defaultWidth = 100.dp, defaultHeight = 100.dp, viewportWidth = 100f, viewportHeight = 100f).build()
    private val scheme = FamilyColorScheme(light = palette(1), dark = palette(2))
    private val alfaiate = FamilyAppIdentity(slug = "alfaiate", displayName = "Meu Ateliê", icon = icon, scheme = scheme)

    @Test
    fun `identidade de fora guarda o que foi passado e o code cai no slug`() {
        assertEquals("alfaiate", alfaiate.slug)
        assertEquals("alfaiate", alfaiate.code)
        assertEquals("Meu Ateliê", alfaiate.displayName)
        assertSame(icon, alfaiate.icon)
        assertNull(alfaiate.familyApp)
        assertEquals(alfaiate, FamilyAppIdentity(slug = "alfaiate", displayName = "Meu Ateliê", icon = icon, scheme = scheme))
        assertEquals("ma", FamilyAppIdentity("alfaiate", "Meu Ateliê", icon, scheme, code = "ma").code)
        assertFailsWith<IllegalArgumentException> { FamilyAppIdentity(" ", "x", icon, scheme) }
    }

    @Test
    fun `o tema aplica a paleta da identidade passada`() {
        assertEquals(scheme.light, familyPaletteFor(alfaiate, darkTheme = false))
        assertEquals(scheme.dark, familyPaletteFor(alfaiate, darkTheme = true))
        val p = alfaiate.scheme.toAppColorPalette()
        assertEquals(scheme.light.ac, p.lightBrand?.primary)
        assertEquals(scheme.dark.ac, p.darkBrand?.primary)
        assertEquals(scheme.dark.bg, p.darkSurfaces?.background)
    }

    @Test
    fun `FamilyApp continua sendo a identidade dos 12 com a paleta do prototipo`() {
        FamilyApp.entries.forEach { app ->
            val id: FamilyAppIdentity = app
            assertEquals(FamilyPalettes.of(app), id.scheme)
            assertSame(app, id.familyApp)
            assertEquals(FamilyPalettes.of(app).dark, familyPaletteFor(app, darkTheme = true))
        }
    }

    @Test
    fun `app de fora nao recebe a grade da familia crista`() {
        assertTrue(familyOtherAppsCatalogFor(alfaiate).isEmpty())
        assertEquals(FamilyApp.entries, familyOtherAppsCatalogFor(FamilyApp.Jejuar))

        val nexus = listOf(
            DeveloperApp(name = "Bíblia Aberta", playStoreUrl = "https://play/ba", appStoreUrl = "https://apple/ba"),
            DeveloperApp(name = "Jejuar", playStoreUrl = "https://play/jj", appStoreUrl = "https://apple/jj"),
            DeveloperApp(name = "Meu Ateliê", playStoreUrl = "https://play/at", appStoreUrl = "https://apple/at"),
        )
        assertTrue(selectFamilyOtherApps(nexus, alfaiate, familyOtherAppsCatalogFor(alfaiate)).isEmpty())

        val daFamilia = selectFamilyOtherApps(nexus, FamilyApp.Jejuar, familyOtherAppsCatalogFor(FamilyApp.Jejuar))
        assertEquals(listOf<FamilyAppIdentity>(FamilyApp.BibliaAberta), daFamilia.map { it.first })
    }

    @Test
    fun `app de fora pode passar o proprio catalogo`() {
        val portaria = FamilyAppIdentity("portaria", "Encomenda na Portaria", icon, scheme)
        val nexus = listOf(
            DeveloperApp(name = "encomenda na portaria", playStoreUrl = "https://play/p", appStoreUrl = "https://apple/p"),
            DeveloperApp(name = "Meu Ateliê", playStoreUrl = "https://play/at", appStoreUrl = "https://apple/at"),
            DeveloperApp(name = "Bíblia Aberta", playStoreUrl = "https://play/ba", appStoreUrl = "https://apple/ba"),
            DeveloperApp(name = "Sem Loja"),
        )
        val sem = FamilyAppIdentity("sem-loja", "Sem Loja", icon, scheme)
        val lista = selectFamilyOtherApps(nexus, alfaiate, listOf(alfaiate, portaria, sem))
        assertEquals(listOf("portaria"), lista.map { it.first.slug })
    }
}
