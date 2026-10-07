package br.com.codecacto.kmplib.platform.automation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class QaRunnerSignalTest {

    @Test
    fun nomesDoContratoComORunner() {
        // Mudar aqui exige mudar `Ferramentas/qa-runner/src/sinal-automacao.ts`.
        assertEquals("debug.codecacto.automacao", QaRunnerSignal.ANDROID_PROPERTY)
        assertEquals("CodecactoAutomacao", QaRunnerSignal.IOS_DEFAULTS_KEY)
    }

    @Test
    fun valoresQueLigamAMarca() {
        listOf("1", "true", "TRUE", " yes ", "on\n").forEach { assertTrue(QaRunnerSignal.isOnValue(it), it) }
        listOf(null, "", "0", "false", "no", "2", "ligado").forEach { assertFalse(QaRunnerSignal.isOnValue(it), "$it") }
    }

    @Test
    fun binarioNaoElegivelNuncaLeAMarca() {
        var leituras = 0
        val probe = QaRunnerProbe(eligible = { false }, readMark = { leituras++; true })
        assertFalse(probe.isOn())
        assertFalse(probe.isOn())
        assertEquals(0, leituras, "release não pode nem consultar a marca")
    }

    @Test
    fun elegibilidadeEConsultadaUmaVezSo() {
        var consultas = 0
        val tempo = TestTimeSource()
        val probe = QaRunnerProbe(eligible = { consultas++; true }, readMark = { true }, ttl = 5.seconds, timeSource = tempo)
        repeat(3) { probe.isOn(); tempo += 10.seconds }
        assertEquals(1, consultas)
    }

    @Test
    fun binarioElegivelComMarcaLigada() {
        val probe = QaRunnerProbe(eligible = { true }, readMark = { true })
        assertTrue(probe.isOn())
    }

    @Test
    fun leituraValeDentroDaFolgaERelêDepois() {
        val tempo = TestTimeSource()
        var marca = false
        var leituras = 0
        val probe = QaRunnerProbe(
            eligible = { true },
            readMark = { leituras++; marca },
            ttl = 5.seconds,
            timeSource = tempo,
        )
        assertFalse(probe.isOn())
        marca = true
        tempo += 4.seconds
        assertFalse(probe.isOn(), "dentro da folga vale a leitura anterior")
        assertEquals(1, leituras)
        tempo += 2.seconds
        assertTrue(probe.isOn(), "passada a folga, relê a marca")
        assertEquals(2, leituras)
    }

    @Test
    fun falhaNaLeituraContaComoDesligada() {
        val probe = QaRunnerProbe(eligible = { true }, readMark = { error("getprop indisponível") })
        assertFalse(probe.isOn())
    }
}
