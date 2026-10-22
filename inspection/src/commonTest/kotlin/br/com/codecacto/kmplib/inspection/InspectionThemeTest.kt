package br.com.codecacto.kmplib.inspection

import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.inspection.preview.InspectionExampleThemes
import br.com.codecacto.kmplib.inspection.theme.InspectionNeutrals
import br.com.codecacto.kmplib.inspection.theme.InspectionTokens
import br.com.codecacto.kmplib.inspection.theme.InspectionTone
import br.com.codecacto.kmplib.inspection.theme.InspectionTones
import br.com.codecacto.kmplib.inspection.theme.isDarkBackground
import br.com.codecacto.kmplib.ui.theme.ColorContrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InspectionThemeTest {

    private val fixos = listOf(InspectionTone.GOOD, InspectionTone.WARN, InspectionTone.BAD, InspectionTone.NEUTRAL)

    @Test
    fun semaforo_fg_sobre_soft_atinge_AA_nos_dois_temas() {
        for (escuro in listOf(false, true)) {
            for (tom in fixos) {
                val par = InspectionTones.fixed(tom, escuro)!!
                val razao = ColorContrast.contrastRatio(par.fg, par.soft)
                assertTrue(razao >= 4.5, "$tom ${if (escuro) "escuro" else "claro"}: $razao < 4.5")
            }
        }
    }

    @Test
    fun semaforo_fg_sobre_a_superficie_do_kit_atinge_AA() {
        // O ícone/texto do tom também aparece sobre a superfície (tile não selecionado, meta do item).
        for (tom in fixos) {
            val claro = InspectionTones.fixed(tom, false)!!
            val escuro = InspectionTones.fixed(tom, true)!!
            assertTrue(ColorContrast.contrastRatio(claro.fg, InspectionNeutrals.KitLight.surface) >= 4.5, "$tom claro")
            assertTrue(ColorContrast.contrastRatio(escuro.fg, InspectionNeutrals.KitDark.surface) >= 4.5, "$tom escuro")
        }
    }

    @Test
    fun marca_sobre_brandSoft_dos_dois_produtos_atinge_AA() {
        // "Obrigatório", chip "Tarefa", item selecionado: texto na marca sobre a marca a 9 %/18 %.
        InspectionExampleThemes.Brands.forEach { (claro, escuro) ->
            val tokClaro = InspectionTokens.resolve(false, claro, Color.White, InspectionNeutrals.KitLight)
            val tokEscuro = InspectionTokens.resolve(true, escuro, Color(0xFF0F1217), InspectionNeutrals.KitDark)
            val fundoClaro = ColorContrast.compositeOver(claro, tokClaro.brandSoft.alpha, InspectionNeutrals.KitLight.surface)
            val fundoEscuro = ColorContrast.compositeOver(escuro, tokEscuro.brandSoft.alpha, InspectionNeutrals.KitDark.surface)
            assertTrue(ColorContrast.contrastRatio(claro, fundoClaro) >= 4.5, "claro $claro")
            assertTrue(ColorContrast.contrastRatio(escuro, fundoEscuro) >= 4.5, "escuro $escuro")
        }
    }

    @Test
    fun onBrand_por_contraste_atinge_AA_nas_marcas_de_exemplo() {
        InspectionExampleThemes.Brands.forEach { (claro, escuro) ->
            listOf(claro, escuro).forEach { marca ->
                val on = ColorContrast.pickOnColor(marca)
                assertTrue(ColorContrast.contrastRatio(on, marca) >= 4.5, "on sobre $marca")
            }
        }
    }

    @Test
    fun brandSoft_usa_os_alfas_do_prototipo() {
        val claro = InspectionTokens.resolve(false, Color(0xFF1F5FBF), Color.White, InspectionNeutrals.KitLight)
        val escuro = InspectionTokens.resolve(true, Color(0xFF7FA8F2), Color.Black, InspectionNeutrals.KitDark)
        assertEquals(0x17 / 255f, claro.brandSoft.alpha, 0.01f)
        assertEquals(0x2E / 255f, escuro.brandSoft.alpha, 0.01f)
        assertEquals(claro.brand, claro.tone(InspectionTone.BRAND).fg)
        assertEquals(claro.bad, claro.tone(InspectionTone.BAD))
    }

    @Test
    fun deteccao_de_tema_escuro_pelo_fundo() {
        assertTrue(isDarkBackground(InspectionNeutrals.KitDark.bg))
        assertFalse(isDarkBackground(InspectionNeutrals.KitLight.bg))
        assertTrue(isDarkBackground(Color(0xFF100F17)))
    }
}
