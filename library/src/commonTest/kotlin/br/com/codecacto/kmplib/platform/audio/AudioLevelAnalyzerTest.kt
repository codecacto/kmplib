package br.com.codecacto.kmplib.platform.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vetores conhecidos de DSP: cada caso tem um valor que a teoria prevê, e é ele que está afirmado.
 * Nenhum teste depende de microfone.
 */
class AudioLevelAnalyzerTest {

    private val sampleRate = 44_100

    private fun cruAnalyzer() = AudioLevelAnalyzer(
        sampleRate = sampleRate,
        weighting = AudioWeighting.Z,
        timeWeighting = AudioTimeWeighting.NONE,
    )

    private fun AudioLevelAnalyzer.medir(samples: ShortArray): AudioLevel {
        accumulate(samples)
        return buildLevel(timestampMillis = 1_000L)
    }

    @Test
    fun senoDeAmplitudePlena_da3Ponto01DbfsDeRms() {
        // RMS de uma senoide é A/√2 → 20·log10(1/√2) = -3,01 dBFS. É o vetor mais conhecido do
        // ramo, e o que prova que a normalização por fundo de escala está certa.
        val level = cruAnalyzer().medir(AudioSignals.sine(1_000.0, sampleRate, 0.5))
        assertEquals(-3.01, level.rmsDbfs, 0.05)
    }

    @Test
    fun continuaEmFundoDeEscala_da0Dbfs() {
        val level = cruAnalyzer().medir(
            AudioSignals.constant(AudioSignals.FULL_SCALE_SHORT, sampleRate / 10),
        )
        assertEquals(0.0, level.rmsDbfs, 0.01)
        assertEquals(0.0, level.peakDbfs, 0.01)
    }

    @Test
    fun silencioDigital_paraNoPisoEnuncaEmiteInfinitoOuNan() {
        // log10(0) é -Infinity, que vira NaN na animação da UI e SOME com o número da tela.
        val level = cruAnalyzer().medir(AudioSignals.silence(sampleRate / 10))

        assertEquals(AudioLevel.SILENCE_DBFS, level.rmsDbfs)
        assertEquals(AudioLevel.SILENCE_DBFS, level.peakDbfs)
        assertEquals(AudioLevel.SILENCE_DBFS, level.noiseFloorDbfs)
        assertEquals(0f, level.clippedSampleRatio)
        assertFalse(level.rmsDbfs.isNaN(), "rmsDbfs não pode ser NaN")
        assertFalse(level.rmsDbfs.isInfinite(), "rmsDbfs não pode ser infinito")
        assertFalse(level.peakDbfs.isNaN(), "peakDbfs não pode ser NaN")
        assertFalse(level.peakDbfs.isInfinite(), "peakDbfs não pode ser infinito")
        assertFalse(level.isClipping)
    }

    @Test
    fun silencioComCurvaAeIntegracaoLenta_tambemNaoProduzNan() {
        // O caminho completo (filtro + integração) também precisa terminar no piso, não em NaN.
        val analyzer = AudioLevelAnalyzer(sampleRate, AudioWeighting.A, AudioTimeWeighting.SLOW)
        repeat(5) {
            analyzer.accumulate(AudioSignals.silence(sampleRate / 10))
            val level = analyzer.buildLevel(it.toLong())
            assertFalse(level.rmsDbfs.isNaN())
            assertFalse(level.rmsDbfs.isInfinite())
            assertTrue(level.rmsDbfs <= 0.0)
        }
    }

    @Test
    fun janelaSemAmostras_naoQuebraENaoInventaNumero() {
        val level = cruAnalyzer().buildLevel(timestampMillis = 7L)
        assertEquals(AudioLevel.SILENCE_DBFS, level.rmsDbfs)
        assertEquals(AudioLevel.SILENCE_DBFS, level.peakDbfs)
        assertEquals(AudioLevel.SILENCE_DBFS, level.noiseFloorDbfs)
        assertEquals(0f, level.clippedSampleRatio)
        assertEquals(7L, level.timestampMillis)
    }

    @Test
    fun janelaInteiraNoTetoDoConversor_marcaSaturacaoComFracaoCheia() {
        val level = cruAnalyzer().medir(
            AudioSignals.square(AudioSignals.FULL_SCALE_SHORT, sampleRate / 20),
        )
        assertTrue(level.isClipping, "±32767 é o teto do conversor: tem de acusar saturação")
        assertEquals(1f, level.clippedSampleRatio, 1e-6f)
    }

    @Test
    fun sinalSeisDbAbaixoDoTeto_naoMarcaSaturacao() {
        // Metade da amplitude = -6 dBFS de pico: longe do teto.
        val level = cruAnalyzer().medir(AudioSignals.sine(1_000.0, sampleRate, 0.2, amplitude = 0.5))
        assertFalse(level.isClipping)
        assertEquals(0f, level.clippedSampleRatio)
        assertEquals(-6.02, level.peakDbfs, 0.1)
    }

    @Test
    fun janelaLimpa_naoTemAmostraSaturada() {
        val level = cruAnalyzer().medir(AudioSignals.sine(440.0, sampleRate, 0.2, amplitude = 0.3))
        assertEquals(0f, level.clippedSampleRatio)
        assertFalse(level.isClipping)
    }

    @Test
    fun metadeDasAmostrasNoFundoDeEscala_saturaComFracaoDeMeio() {
        val level = cruAnalyzer().medir(saturadas(total = 4_096, saturadas = 2_048))
        assertTrue(level.isClipping)
        assertEquals(0.5f, level.clippedSampleRatio, 1e-6f)
    }

    @Test
    fun umaAmostraSaturadaEm2048_naoMarcaSaturacao() {
        // O teste que trava o falso positivo: uma porta batendo, um toque no aparelho, um clique do
        // conversor. Marcar a janela inteira por causa dele destrói a confiança na tela principal.
        val level = cruAnalyzer().medir(saturadas(total = 2_048, saturadas = 1))
        assertFalse(level.isClipping, "1 amostra em 2.048 (0,049%) não é saturação")
        assertEquals(1f / 2_048f, level.clippedSampleRatio, 1e-6f)
    }

    @Test
    fun fronteiraDoLimiarDeFracao_umaAmostraDecideOsDoisLados() {
        // 0,1% de 10.000 amostras = 10. Com 10 satura (o critério é >=); com 9, não.
        val noLimiar = cruAnalyzer().medir(saturadas(total = 10_000, saturadas = 10))
        assertEquals(AudioLevelAnalyzer.CLIPPING_RATIO_THRESHOLD, noLimiar.clippedSampleRatio, 1e-9f)
        assertTrue(noLimiar.isClipping, "exatamente no limiar tem de saturar")

        val abaixo = cruAnalyzer().medir(saturadas(total = 10_000, saturadas = 9))
        assertFalse(abaixo.isClipping, "uma amostra abaixo do limiar não satura")
    }

    @Test
    fun bufferFloatDoIos_tambemContaAmostraSaturada() {
        val floats = FloatArray(4_096) { index -> if (index < 2_048) 1f else 0f }
        val analyzer = cruAnalyzer()
        analyzer.accumulate(floats)
        val level = analyzer.buildLevel(0L)

        assertTrue(level.isClipping)
        assertEquals(0.5f, level.clippedSampleRatio, 1e-6f)
    }

    @Test
    fun sinalMuitoBaixo_saiComoEstaSemGrampoNoPiso() {
        // -80 dBFS é bem abaixo do ruído próprio de um microfone de celular e MUITO acima do
        // SILENCE_DBFS: a lib devolve o número do conversor, não um piso inventado.
        val analyzer = cruAnalyzer()
        analyzer.accumulate(FloatArray(4_410) { 1e-4f })
        val level = analyzer.buildLevel(0L)

        assertEquals(-80.0, level.rmsDbfs, 0.01)
        assertEquals(-80.0, level.peakDbfs, 0.01)
    }

    @Test
    fun pisoDeRuido_guardaOMenorRmsDaSessaoEcaiNoReset() {
        val analyzer = cruAnalyzer()

        analyzer.accumulate(FloatArray(4_410) { 1e-2f }) // -40 dBFS
        assertEquals(-40.0, analyzer.buildLevel(0L).noiseFloorDbfs, 0.01)

        analyzer.accumulate(FloatArray(4_410) { 1e-3f }) // -60 dBFS: piso novo
        assertEquals(-60.0, analyzer.buildLevel(1L).noiseFloorDbfs, 0.01)

        analyzer.accumulate(FloatArray(4_410) { 1e-1f }) // -20 dBFS: barulho não sobe o piso
        val depoisDoBarulho = analyzer.buildLevel(2L)
        assertEquals(-20.0, depoisDoBarulho.rmsDbfs, 0.01)
        assertEquals(-60.0, depoisDoBarulho.noiseFloorDbfs, 0.01)

        // Silêncio digital é ausência de sinal, não piso medido: não pode travar o valor em -120.
        analyzer.accumulate(AudioSignals.silence(4_410))
        assertEquals(-60.0, analyzer.buildLevel(3L).noiseFloorDbfs, 0.01)

        analyzer.reset()
        analyzer.accumulate(FloatArray(4_410) { 1e-2f })
        assertEquals(-40.0, analyzer.buildLevel(4L).noiseFloorDbfs, 0.01)
    }

    @Test
    fun picoNuncaFicaAbaixoDoRms() {
        // Invariante de qualquer sinal, medido sem ponderação: o pico é o maior valor da janela.
        val analyzer = cruAnalyzer()
        listOf(0.9, 0.5, 0.1, 0.01).forEach { amplitude ->
            val level = analyzer.medir(
                AudioSignals.sine(440.0, sampleRate, 0.2, amplitude = amplitude),
            )
            assertTrue(
                level.peakDbfs >= level.rmsDbfs,
                "pico ${level.peakDbfs} deveria ser >= rms ${level.rmsDbfs}",
            )
        }
    }

    @Test
    fun buildLevelCarregaTaxaPonderacaoEinstante() {
        val analyzer = AudioLevelAnalyzer(48_000, AudioWeighting.A, AudioTimeWeighting.FAST)
        analyzer.accumulate(AudioSignals.sine(1_000.0, 48_000, 0.2))
        val level = analyzer.buildLevel(timestampMillis = 42L)

        assertEquals(48_000, level.sampleRate)
        assertEquals(AudioWeighting.A, level.weighting)
        assertEquals(42L, level.timestampMillis)
    }

    @Test
    fun contagemParcialIgnoraOrestoDoBuffer() {
        // O AudioRecord devolve MENOS amostras que o array comporta; medir o resto (zeros da
        // leitura anterior) puxaria o nível para baixo sem nenhum aviso.
        val buffer = ShortArray(1_000)
        val cheio = AudioSignals.constant(AudioSignals.FULL_SCALE_SHORT, 500)
        cheio.copyInto(buffer, destinationOffset = 0)

        val analyzer = cruAnalyzer()
        analyzer.accumulate(buffer, count = 500)
        assertEquals(0.0, analyzer.buildLevel(0L).rmsDbfs, 0.01)

        val outro = cruAnalyzer()
        outro.accumulate(buffer, count = 1_000)
        assertEquals(-3.01, outro.buildLevel(0L).rmsDbfs, 0.05)
    }

    @Test
    fun bufferFloatDoIos_medeIgualAoBufferShortDoAndroid() {
        val shorts = AudioSignals.sine(1_000.0, sampleRate, 0.2, amplitude = 0.5)
        val floats = FloatArray(shorts.size) { index ->
            (shorts[index].toDouble() / AudioLevelAnalyzer.FULL_SCALE).toFloat()
        }

        val porShort = cruAnalyzer().medir(shorts)
        val porFloat = cruAnalyzer().let { analyzer ->
            analyzer.accumulate(floats)
            analyzer.buildLevel(0L)
        }
        assertEquals(porShort.rmsDbfs, porFloat.rmsDbfs, 0.01)
    }

    @Test
    fun buildLevelZeraOsAcumuladores() {
        val analyzer = cruAnalyzer()
        analyzer.accumulate(AudioSignals.constant(AudioSignals.FULL_SCALE_SHORT, 4_410))
        assertTrue(analyzer.hasPendingSamples)
        analyzer.buildLevel(0L)
        assertFalse(analyzer.hasPendingSamples)
        assertEquals(0.0, analyzer.pendingSeconds)
    }

    @Test
    fun trocarDePonderacaoNaoVazaAcaudaDoFiltro() {
        // Trocar o ajuste na tela de Configurações não pode produzir um estouro fantasma.
        val analyzer = AudioLevelAnalyzer(sampleRate, AudioWeighting.A, AudioTimeWeighting.NONE)
        analyzer.accumulate(AudioSignals.sine(60.0, sampleRate, 0.3))
        analyzer.buildLevel(0L)

        analyzer.weighting = AudioWeighting.Z
        analyzer.accumulate(AudioSignals.sine(1_000.0, sampleRate, 0.3))
        val semPonderacao = analyzer.buildLevel(1L)

        assertEquals(AudioWeighting.Z, semPonderacao.weighting)
        assertEquals(-3.01, semPonderacao.rmsDbfs, 0.05)
    }

    @Test
    fun resetApagaFiltroIntegracaoEacumuladores() {
        val analyzer = AudioLevelAnalyzer(sampleRate, AudioWeighting.A, AudioTimeWeighting.SLOW)
        analyzer.accumulate(AudioSignals.sine(1_000.0, sampleRate, 0.5))
        analyzer.reset()
        assertFalse(analyzer.hasPendingSamples)

        // Depois do reset, a primeira janela é adotada como está (sem cauda da sessão anterior).
        analyzer.accumulate(AudioSignals.silence(4_410))
        assertEquals(AudioLevel.SILENCE_DBFS, analyzer.buildLevel(0L).rmsDbfs)
    }

    @Test
    fun conversoresDeDbSaoAprovaDeZeroEnegativo() {
        assertEquals(AudioLevel.SILENCE_DBFS, AudioLevelAnalyzer.amplitudeToDbfs(0.0))
        assertEquals(AudioLevel.SILENCE_DBFS, AudioLevelAnalyzer.amplitudeToDbfs(-1.0))
        assertEquals(AudioLevel.SILENCE_DBFS, AudioLevelAnalyzer.powerToDbfs(0.0))
        assertEquals(AudioLevel.SILENCE_DBFS, AudioLevelAnalyzer.powerToDbfs(Double.NaN))
        assertEquals(0.0, AudioLevelAnalyzer.amplitudeToDbfs(1.0), 1e-9)
        assertEquals(-20.0, AudioLevelAnalyzer.amplitudeToDbfs(0.1), 1e-9)
        assertEquals(-10.0, AudioLevelAnalyzer.powerToDbfs(0.1), 1e-9)

        // Sem grampo: SILENCE_DBFS é sentinela de silêncio digital, não mínimo de exibição.
        assertEquals(-180.0, AudioLevelAnalyzer.amplitudeToDbfs(1e-9), 1e-9)
        assertEquals(-200.0, AudioLevelAnalyzer.powerToDbfs(1e-20), 1e-9)
    }

    /** Janela com [saturadas] amostras no fundo de escala e o resto em zero. */
    private fun saturadas(total: Int, saturadas: Int): ShortArray = ShortArray(total) { index ->
        if (index < saturadas) AudioSignals.FULL_SCALE_SHORT.toShort() else 0
    }
}
