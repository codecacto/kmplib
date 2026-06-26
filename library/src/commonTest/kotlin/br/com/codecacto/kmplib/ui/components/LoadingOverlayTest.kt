package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Ignore
import kotlin.test.Test

// KMP só unit/fluxo por ora — teste de UI automatizado desativado (decisão fundador, jun/2026).
@Ignore
@OptIn(ExperimentalTestApi::class)
class LoadingOverlayTest {

    @Test
    fun `nao renderiza nada quando show e false`() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize()) {
                Text("Conteúdo de fundo")
                LoadingOverlay(show = false, text = "Carregando...")
            }
        }
        onNodeWithText("Conteúdo de fundo").assertIsDisplayed()
        // Texto do overlay não aparece
        onNodeWithText("Carregando...").assertIsNotDisplayed()
    }

    @Test
    fun `renderiza spinner e texto quando show e true`() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize()) {
                Text("Conteúdo de fundo")
                LoadingOverlay(show = true, text = "Salvando...")
            }
        }
        onNodeWithText("Salvando...").assertIsDisplayed()
    }

    @Test
    fun `renderiza sem texto quando text e null`() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize()) {
                Text("background-content")
                LoadingOverlay(show = true, text = null)
            }
        }
        // Sem texto, o overlay ainda mostra spinner — não tem texto pra
        // verificar diretamente, mas conteúdo de fundo ainda está na árvore.
        onNodeWithText("background-content").assertExists()
    }

    @Test
    fun `nao renderiza texto quando text e em branco`() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize()) {
                LoadingOverlay(show = true, text = "   ")
            }
        }
        // Implementação checa `!text.isNullOrBlank()` — espaços não renderizam
        onNodeWithText("   ").assertIsNotDisplayed()
    }

    @Test
    fun `alterna entre visivel e invisivel ao mudar estado`() = runComposeUiTest {
        val show = mutableStateOf(false)

        setContent {
            Box(Modifier.fillMaxSize()) {
                LoadingOverlay(show = show.value, text = "Aguarde")
            }
        }

        // Estado inicial: escondido
        onNodeWithText("Aguarde").assertIsNotDisplayed()

        // Mostra
        show.value = true
        waitForIdle()
        onNodeWithText("Aguarde").assertIsDisplayed()

        // Esconde de volta
        show.value = false
        waitForIdle()
        onNodeWithText("Aguarde").assertIsNotDisplayed()
    }
}
