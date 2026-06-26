package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Column
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

/**
 * Testa o overload `OfflineBanner(isOnline: Boolean, ...)`. A versão com
 * `ConnectivityObserver` é wrapper trivial: confiamos no overload puro.
 */
// KMP só unit/fluxo por ora — teste de UI automatizado desativado (decisão fundador, jun/2026).
@Ignore
@OptIn(ExperimentalTestApi::class)
class OfflineBannerTest {

    @Test
    fun `nao mostra banner quando isOnline true`() = runComposeUiTest {
        setContent {
            Column(Modifier.fillMaxSize()) {
                OfflineBanner(isOnline = true, text = "Sem conexão")
                Text("Conteúdo")
            }
        }
        onNodeWithText("Conteúdo").assertIsDisplayed()
        onNodeWithText("Sem conexão").assertIsNotDisplayed()
    }

    @Test
    fun `mostra banner quando isOnline false`() = runComposeUiTest {
        setContent {
            Column(Modifier.fillMaxSize()) {
                OfflineBanner(isOnline = false, text = "Você está offline")
                Text("Conteúdo")
            }
        }
        onNodeWithText("Você está offline").assertIsDisplayed()
        onNodeWithText("Conteúdo").assertIsDisplayed()
    }

    @Test
    fun `texto padrao quando nao customizado`() = runComposeUiTest {
        setContent {
            OfflineBanner(isOnline = false)
        }
        onNodeWithText("Sem conexão").assertIsDisplayed()
    }

    @Test
    fun `texto customizado e exibido`() = runComposeUiTest {
        setContent {
            OfflineBanner(isOnline = false, text = "Modo offline ativo")
        }
        onNodeWithText("Modo offline ativo").assertIsDisplayed()
    }

    @Test
    fun `alterna visibilidade ao mudar isOnline`() = runComposeUiTest {
        val online = mutableStateOf(true)

        setContent {
            OfflineBanner(isOnline = online.value, text = "Offline")
        }

        // Online: invisível
        onNodeWithText("Offline").assertIsNotDisplayed()

        // Fica offline: visível
        online.value = false
        waitForIdle()
        onNodeWithText("Offline").assertIsDisplayed()

        // Volta online: invisível
        online.value = true
        waitForIdle()
        onNodeWithText("Offline").assertIsNotDisplayed()
    }
}
