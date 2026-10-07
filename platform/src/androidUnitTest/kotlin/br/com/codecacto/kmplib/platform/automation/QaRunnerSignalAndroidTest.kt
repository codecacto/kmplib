package br.com.codecacto.kmplib.platform.automation

import kotlin.test.Test
import kotlin.test.assertFalse

class QaRunnerSignalAndroidTest {

    @Test
    fun semContextoNaoEDepuravel() {
        // Sem `initKmpLibPlatform`, não se sabe se o app é depurável: fecha para o lado seguro.
        assertFalse(isDebuggableApp(null))
    }

    @Test
    fun semContextoAMarcaNaoVale() {
        assertFalse(isQaRunnerMarked())
    }

    @Test
    fun getpropAusenteDaFalseSemLancar() {
        // Na JVM de teste não existe `getprop` (ou não existe a propriedade): desligada, sem exceção.
        assertFalse(readAndroidQaRunnerProperty())
    }
}
