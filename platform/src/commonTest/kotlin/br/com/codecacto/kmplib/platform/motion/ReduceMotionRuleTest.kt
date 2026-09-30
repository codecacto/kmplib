package br.com.codecacto.kmplib.platform.motion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReduceMotionRuleTest {

    @Test
    fun escalasPadraoNaoReduzem() {
        assertFalse(reduceMotionFromAnimationScales(1f, 1f))
        assertFalse(reduceMotionFromAnimationScales(null, null))
        assertFalse(reduceMotionFromAnimationScales(0.5f, 10f))
    }

    @Test
    fun removerAnimacoesZeraAsEscalasEReduz() {
        assertTrue(reduceMotionFromAnimationScales(0f, 0f))
    }

    @Test
    fun qualquerUmaDasEscalasEmZeroReduz() {
        assertTrue(reduceMotionFromAnimationScales(0f, 1f))
        assertTrue(reduceMotionFromAnimationScales(1f, 0f))
        assertTrue(reduceMotionFromAnimationScales(null, 0f))
    }
}
