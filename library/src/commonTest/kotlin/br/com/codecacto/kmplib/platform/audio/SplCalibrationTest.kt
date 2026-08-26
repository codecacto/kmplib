package br.com.codecacto.kmplib.platform.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class SplCalibrationTest {

    @Test
    fun offsetPositivo_sobeAleitura() {
        val calibracao = SplCalibration(offsetDb = 90.0)
        assertEquals(60.0, calibracao.toSpl(-30.0), 1e-9)
        assertEquals(90.0, calibracao.toSpl(0.0), 1e-9)
    }

    @Test
    fun offsetNegativo_desceAleitura() {
        val calibracao = SplCalibration(offsetDb = -12.5)
        assertEquals(-42.5, calibracao.toSpl(-30.0), 1e-9)
    }

    @Test
    fun offsetZero_deixaAleituraEmDbfsPuro() {
        assertEquals(-30.0, SplCalibration.NONE.toSpl(-30.0), 1e-9)
        assertEquals(0.0, SplCalibration.NONE.offsetDb, 1e-9)
    }

    @Test
    fun oCaminhoDeVoltaDesfazAConta() {
        // É o que a tela de calibração usa: "o medidor ao lado marca 74" → qual offset é esse?
        val calibracao = SplCalibration(offsetDb = 87.5)
        val dbfs = -33.2
        assertEquals(dbfs, calibracao.toDbfs(calibracao.toSpl(dbfs)), 1e-9)
    }

    @Test
    fun oDefaultDaLibEoPontoDePartidaTipicoDeCelular() {
        assertEquals(90.0, SplCalibration.DEFAULT_OFFSET_DB, 1e-9)
        assertEquals(SplCalibration.DEFAULT_OFFSET_DB, SplCalibration().offsetDb, 1e-9)
        // -30 dBFS numa sala de conversa vira ~60 dB SPL: a ordem de grandeza plausível.
        assertEquals(60.0, SplCalibration.DEFAULT.toSpl(-30.0), 1e-9)
    }
}
