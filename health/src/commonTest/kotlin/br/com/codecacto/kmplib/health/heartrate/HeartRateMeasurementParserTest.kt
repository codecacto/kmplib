package br.com.codecacto.kmplib.health.heartrate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vetores da `0x2A37` montados à mão a partir da especificação do Heart Rate Service 1.0. */
class HeartRateMeasurementParserTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `FC em UINT8 sem contato suportado`() {
        val m = HeartRateMeasurementParser.parse(bytes(0x00, 72))!!
        assertEquals(72, m.bpm)
        assertEquals(SensorContact.NOT_SUPPORTED, m.sensorContact)
        assertNull(m.energyExpendedKj)
        assertTrue(m.rrIntervalsMillis.isEmpty())
        assertTrue(m.isUsable)
    }

    @Test
    fun `FC em UINT8 acima de 127 nao vira negativo`() {
        assertEquals(200, HeartRateMeasurementParser.parse(bytes(0x00, 200))!!.bpm)
    }

    @Test
    fun `FC em UINT16 little-endian`() {
        // 0x012C = 300
        val m = HeartRateMeasurementParser.parse(bytes(0x01, 0x2C, 0x01))!!
        assertEquals(300, m.bpm)
    }

    @Test
    fun `contato suportado e detectado - suportado e nao detectado`() {
        assertEquals(SensorContact.DETECTED, HeartRateMeasurementParser.parse(bytes(0x06, 80))!!.sensorContact)
        val loose = HeartRateMeasurementParser.parse(bytes(0x04, 80))!!
        assertEquals(SensorContact.NOT_DETECTED, loose.sensorContact)
        assertFalse(loose.isUsable)
        // Bit 1 sem o bit 2 = contato não suportado (o bit 1 só vale com o 2).
        assertEquals(SensorContact.NOT_SUPPORTED, HeartRateMeasurementParser.parse(bytes(0x02, 80))!!.sensorContact)
    }

    @Test
    fun `bpm zero nao e utilizavel`() {
        assertFalse(HeartRateMeasurementParser.parse(bytes(0x06, 0))!!.isUsable)
    }

    @Test
    fun `energia gasta e intervalos RR`() {
        // flags 0x18: UINT8 + energia + RR · FC 75 · energia 0x0102 = 258 kJ · RR 1024 e 512 (1/1024 s)
        val m = HeartRateMeasurementParser.parse(bytes(0x18, 75, 0x02, 0x01, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(75, m.bpm)
        assertEquals(258, m.energyExpendedKj)
        assertEquals(listOf(1000.0, 500.0), m.rrIntervalsMillis)
    }

    @Test
    fun `RR com FC em UINT16`() {
        val m = HeartRateMeasurementParser.parse(bytes(0x11, 0x50, 0x00, 0x00, 0x04))!!
        assertEquals(80, m.bpm)
        assertEquals(listOf(1000.0), m.rrIntervalsMillis)
    }

    @Test
    fun `byte de RR sobrando e ignorado`() {
        val m = HeartRateMeasurementParser.parse(bytes(0x10, 60, 0x00, 0x04, 0x07))!!
        assertEquals(listOf(1000.0), m.rrIntervalsMillis)
    }

    @Test
    fun `pacote truncado devolve null - nunca numero inventado`() {
        assertNull(HeartRateMeasurementParser.parse(ByteArray(0)))
        assertNull(HeartRateMeasurementParser.parse(bytes(0x00)))
        assertNull(HeartRateMeasurementParser.parse(bytes(0x01, 0x50)))
        assertNull(HeartRateMeasurementParser.parse(bytes(0x08, 70, 0x01)))
    }

    @Test
    fun `codificador do sensor simulado e o inverso do leitor`() {
        for (bpm in listOf(0, 1, 60, 72, 127, 128, 255, 256, 300)) {
            val m = HeartRateMeasurementParser.parse(HeartRateMeasurementEncoder.encode(bpm))!!
            assertEquals(bpm, m.bpm, "bpm $bpm")
            assertEquals(if (bpm > 0) SensorContact.DETECTED else SensorContact.NOT_DETECTED, m.sensorContact)
            if (bpm > 0) {
                assertEquals(1, m.rrIntervalsMillis.size)
                assertEquals(60_000.0 / bpm, m.rrIntervalsMillis.single(), 1.0)
            } else {
                assertTrue(m.rrIntervalsMillis.isEmpty())
            }
        }
    }

    @Test
    fun `UUIDs do perfil padrao do Bluetooth SIG`() {
        assertEquals("0000180D-0000-1000-8000-00805F9B34FB", HeartRateGattProfile.SERVICE_UUID)
        assertEquals("00002A37-0000-1000-8000-00805F9B34FB", HeartRateGattProfile.MEASUREMENT_UUID)
        assertEquals("00002902-0000-1000-8000-00805F9B34FB", HeartRateGattProfile.CLIENT_CONFIG_UUID)
        assertTrue(HeartRateGattProfile.SERVICE_UUID.startsWith("0000" + HeartRateGattProfile.SERVICE_SHORT))
        assertTrue(HeartRateGattProfile.MEASUREMENT_UUID.startsWith("0000" + HeartRateGattProfile.MEASUREMENT_SHORT))
    }
}
