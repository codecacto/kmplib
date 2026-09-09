package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressRingTest {

    // -----------------------------------------------------------------------------------------
    // O arco
    // -----------------------------------------------------------------------------------------

    @Test
    fun `zero nao desenha arco nenhum`() {
        assertEquals(0f, progressRingSweep(0f))
        assertEquals(0f, progressRingSweep(-0.5f))
        assertEquals(0f, progressRingSweep(Float.NaN))
    }

    @Test
    fun `meia volta e um quarto`() {
        assertEquals(180f, progressRingSweep(0.5f))
        assertEquals(90f, progressRingSweep(0.25f))
        assertEquals(360f, progressRingSweep(1f))
        assertEquals(360f, progressRingSweep(2f), "fração acima de 1 é grampeada")
    }

    @Test
    fun `comecar aparece`() {
        // 1% dariam 3,6 graus — menos que a ponta arredondada do traço, e o anel ficaria idêntico
        // ao de quem não começou. É a única informação que essa pessoa quer ver ali.
        assertTrue(progressRingSweep(0.01f) >= 6f)
        assertTrue(progressRingSweep(0.001f) > 0f)
    }

    // -----------------------------------------------------------------------------------------
    // O número do meio
    // -----------------------------------------------------------------------------------------

    @Test
    fun `nunca mostra 100 com aula faltando`() {
        // Arredondar para cima faria 99,6% virar "100%" — a reclamação clássica de plataforma de
        // curso, e a que faz o aluno procurar o certificado que não existe.
        assertEquals(99, progressRingPercentLabel(0.996f))
        assertEquals(99, progressRingPercentLabel(0.999f))
        assertEquals(100, progressRingPercentLabel(1f))
    }

    @Test
    fun `nunca mostra 0 para quem ja comecou`() {
        assertEquals(0, progressRingPercentLabel(0f))
        assertEquals(1, progressRingPercentLabel(0.004f))
        assertEquals(1, progressRingPercentLabel(0.01f))
    }

    @Test
    fun `arredonda para baixo`() {
        assertEquals(37, progressRingPercentLabel(0.379f))
        assertEquals(50, progressRingPercentLabel(0.5f))
    }

    // -----------------------------------------------------------------------------------------
    // O marco de 100%
    // -----------------------------------------------------------------------------------------

    @Test
    fun `so 100 cravado conta como concluido`() {
        assertTrue(isProgressRingComplete(1f))
        assertTrue(isProgressRingComplete(1.5f))
        assertTrue(!isProgressRingComplete(0.999f))
        assertTrue(!isProgressRingComplete(0f))
    }

    @Test
    fun `o anel usa a MESMA normalizacao da barra`() {
        // `ProgressRing` e `AppProgressBar` leem a mesma grandeza `0f..1f`. Se cada um tratasse
        // NaN e valores fora do intervalo à sua maneira, a mesma fração desenharia diferente nas
        // duas formas da mesma tela.
        assertEquals(normalizeProgress(1.4f), progressRingSweep(1.4f) / 360f)
        assertEquals(0f, normalizeProgress(Float.NaN))
    }

    @Test
    fun `as medidas do desenho`() {
        assertEquals(44f, ProgressRingDefaults.Size.value)
        assertEquals(4f, ProgressRingDefaults.StrokeWidth.value)
    }
}
