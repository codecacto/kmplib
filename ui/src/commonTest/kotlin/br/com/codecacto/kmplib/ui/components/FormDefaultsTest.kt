package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.isUnspecified
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass
import br.com.codecacto.kmplib.ui.theme.leituraMaxWidth
import br.com.codecacto.kmplib.ui.theme.windowSizeClassFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O teto de largura do formulário (GAP-NCX-T-01).
 *
 * `FormContainer` é `@Composable` e depende de `LocalWindowSizeClass`; o que decide o layout, e o
 * que pode estar errado, é a regra pura [FormDefaults.maxContentWidth]. É ela que se prova aqui.
 *
 * As larguras são de **aparelho real**, não números redondos — é nelas que um `<=` no lugar de um
 * `<` aparece. A comparação com [Dp.Unspecified] usa `isUnspecified`, e não `==`: `Dp.Unspecified`
 * é `NaN`, e `NaN == NaN` é falso.
 */
class FormDefaultsTest {

    @Test
    fun telefone_nao_ganha_teto() {
        // Pixel 8 (412dp), iPhone 15 (393dp), telefone pequeno (360dp). A coluna útil já é ~354dp
        // com o padding de 24dp de cada lado: um teto de 480 aqui seria peso morto na hierarquia.
        listOf(360.dp, 393.dp, 412.dp).forEach { largura ->
            assertTrue(
                FormDefaults.maxContentWidth(windowSizeClassFor(largura)).isUnspecified,
                "largura $largura deveria ficar sem teto",
            )
        }
    }

    @Test
    fun limiar_de_600_e_exclusivo() {
        // 599 ainda é telefone (sem teto); 600 já é média (480dp). Um `<=` no `windowSizeClassFor`
        // faria um telefone grande em paisagem ganhar formulário de 480 no meio de 599dp.
        assertTrue(FormDefaults.maxContentWidth(windowSizeClassFor(599.dp)).isUnspecified)
        assertEquals(480.dp, FormDefaults.maxContentWidth(windowSizeClassFor(600.dp)))
    }

    @Test
    fun tablet_em_retrato_tem_teto_de_480() {
        // iPad mini retrato (744dp), iPad 10.9" retrato (820dp), limite superior da MEDIA (839dp).
        listOf(744.dp, 820.dp, 839.dp).forEach { largura ->
            assertEquals(480.dp, FormDefaults.maxContentWidth(windowSizeClassFor(largura)), "largura $largura")
        }
    }

    @Test
    fun tablet_em_paisagem_e_desktop_tem_o_MESMO_teto() {
        // 840 (limiar), iPad 10.9" paisagem (1180dp), tablet Android paisagem (1280dp), desktop
        // (1440dp). O teto NÃO cresce com a tela: campo de e-mail de 720dp é tão ruim quanto o de
        // 1184dp — o que muda entre MEDIA e EXPANDIDA é o que existe AO LADO do formulário.
        listOf(840.dp, 1180.dp, 1280.dp, 1440.dp).forEach { largura ->
            assertEquals(480.dp, FormDefaults.maxContentWidth(windowSizeClassFor(largura)), "largura $largura")
        }
    }

    @Test
    fun teto_de_formulario_e_mais_estreito_que_o_de_leitura() {
        // Campo de formulário é mais estreito que texto corrido de propósito. Se um dia alguém
        // igualar os dois, este teste cai — e é essa a intenção.
        listOf(WindowSizeClass.MEDIA, WindowSizeClass.EXPANDIDA).forEach { classe ->
            val formulario = FormDefaults.maxContentWidth(classe)
            val leitura = leituraMaxWidth(classe)
            assertTrue(leitura.isSpecified, "leituraMaxWidth($classe) deveria ter valor")
            assertTrue(formulario < leitura, "formulário ($formulario) deveria ser < leitura ($leitura) em $classe")
        }
    }

    @Test
    fun painel_de_marca_deixa_o_formulario_inteiro_de_pe() {
        // Na menor janela EXPANDIDA (840dp), o que sobra para o formulário depois do painel de
        // marca tem de caber os 480dp de conteúdo MAIS os 24dp de padding de cada lado. Aumentar a
        // fração do painel sem checar isto espremeria o campo justamente onde ele deveria respirar.
        val menorExpandida = 840f
        val sobraParaOFormulario = menorExpandida * (1f - FormDefaults.BrandPanelFraction)
        assertTrue(
            sobraParaOFormulario >= 480f + 48f,
            "sobrariam ${sobraParaOFormulario}dp para um formulário que precisa de 528dp",
        )
    }
}
