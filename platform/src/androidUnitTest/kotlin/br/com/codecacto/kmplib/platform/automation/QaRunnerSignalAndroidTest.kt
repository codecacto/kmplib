package br.com.codecacto.kmplib.platform.automation

import android.content.Context
import android.content.ContextWrapper
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

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

    @Test
    fun semInitDoPlatformOContextVemDoCore() {
        // App que só chama `initKmpLibCore` (módulos granulares — LocAki): o Context da automação
        // vem do `AndroidAppContext`, e a marca do runner passa a poder valer (2.310.0).
        val doCore = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        val original = AutomationContextHolder.fallback
        try {
            AutomationContextHolder.fallback = { doCore }
            assertSame(doCore, AutomationContextHolder.contextOrNull())
            AutomationContextHolder.fallback = { null }
            assertNull(AutomationContextHolder.contextOrNull())
        } finally {
            AutomationContextHolder.fallback = original
        }
    }
}
