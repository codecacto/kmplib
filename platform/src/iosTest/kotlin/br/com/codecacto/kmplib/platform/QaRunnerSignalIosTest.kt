package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.platform.automation.isQaRunnerMarked
import br.com.codecacto.kmplib.platform.automation.isRunningAsAppBundle
import kotlin.test.Test
import kotlin.test.assertFalse

class QaRunnerSignalIosTest {

    @Test
    fun executavelDeTesteNaoEApp() {
        // A suíte roda como `test.kexe`, não como `.app`: a marca do runner (que pode estar no
        // simulador) não a alcança.
        assertFalse(isRunningAsAppBundle())
        assertFalse(isQaRunnerMarked())
    }
}
