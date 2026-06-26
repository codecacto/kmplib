package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Testes de renderização do [Avatar].
 *
 * Rodam via `runComposeUiTest` (commonTest). Em CI, executam dentro do
 * `:library:iosSimulatorArm64Test` no runner macOS.
 *
 * Para lógica pura (colorForName), ver [AvatarLogicTest].
 */
// KMP só unit/fluxo por ora — teste de UI automatizado desativado (decisão fundador, jun/2026).
@Ignore
@OptIn(ExperimentalTestApi::class)
class AvatarUiTest {

    @Test
    fun `mostra iniciais quando sem imagem`() = runComposeUiTest {
        setContent {
            Avatar(name = "Joao Silva", size = 48.dp)
        }
        onNodeWithText("JS").assertIsDisplayed()
    }

    @Test
    fun `mostra iniciais de nome unico`() = runComposeUiTest {
        setContent {
            Avatar(name = "Madonna", size = 48.dp)
        }
        // initialsOf("Madonna") → "M"
        onNodeWithText("M").assertIsDisplayed()
    }

    @Test
    fun `mostra primeira e ultima inicial em nome composto`() = runComposeUiTest {
        setContent {
            Avatar(name = "Maria Aparecida da Silva", size = 48.dp)
        }
        // initialsOf da lib retorna primeira + última (pulando preposições)
        // Esperamos "MS"
        onNodeWithText("MS").assertIsDisplayed()
    }

    @Test
    fun `renderiza conteudo do slot image quando fornecido`() = runComposeUiTest {
        setContent {
            Avatar(name = "Qualquer", size = 48.dp) {
                Box(Modifier.fillMaxSize()) {
                    Text("FOTO")
                }
            }
        }
        onNodeWithText("FOTO").assertIsDisplayed()
    }

    @Test
    fun `quando slot fornecido nao mostra iniciais`() = runComposeUiTest {
        setContent {
            Avatar(name = "Joao Silva", size = 48.dp) {
                Box(Modifier.fillMaxSize()) {
                    Text("IMG")
                }
            }
        }
        // Slot tem precedência — iniciais "JS" não devem aparecer
        onNodeWithText("IMG").assertIsDisplayed()
    }
}
