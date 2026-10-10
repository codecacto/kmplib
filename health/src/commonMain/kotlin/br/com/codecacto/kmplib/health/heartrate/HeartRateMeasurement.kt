package br.com.codecacto.kmplib.health.heartrate

/**
 * Perfil padrão de frequência cardíaca do Bluetooth SIG (Heart Rate Service 1.0): serviço `0x180D`,
 * característica de medição `0x2A37` (notificação), descritor de configuração `0x2902`. Os UUIDs
 * completos na base do Bluetooth (`0000xxxx-0000-1000-8000-00805F9B34FB`).
 */
object HeartRateGattProfile {
    const val SERVICE_UUID: String = "0000180D-0000-1000-8000-00805F9B34FB"
    const val MEASUREMENT_UUID: String = "00002A37-0000-1000-8000-00805F9B34FB"
    const val CLIENT_CONFIG_UUID: String = "00002902-0000-1000-8000-00805F9B34FB"
    const val SERVICE_SHORT: String = "180D"
    const val MEASUREMENT_SHORT: String = "2A37"
}

/** Bits 1-2 das flags: se o sensor informa contato com a pele, e se há contato. */
enum class SensorContact { NOT_SUPPORTED, NOT_DETECTED, DETECTED }

/**
 * Uma notificação da característica `0x2A37`, interpretada.
 *
 * @property bpm frequência cardíaca (UINT8 ou UINT16, conforme o bit 0 das flags).
 * @property energyExpendedKj energia acumulada em kJ (bit 3), quando o sensor manda.
 * @property rrIntervalsMillis intervalos RR (bit 4), convertidos de 1/1024 s para milissegundos.
 */
data class HeartRateMeasurement(
    val bpm: Int,
    val sensorContact: SensorContact,
    val energyExpendedKj: Int? = null,
    val rrIntervalsMillis: List<Double> = emptyList(),
) {
    /** Leitura que pode ir para a tela: bpm > 0 e o sensor não diz que está sem contato (a cinta
     * frouxa manda 0 ou um valor sem sentido com o bit de contato zerado). */
    val isUsable: Boolean get() = bpm > 0 && sensorContact != SensorContact.NOT_DETECTED

    /** Sem bpm, energia nem RR (dado de saúde); só o que não mede ninguém: contato e contagens. */
    override fun toString(): String =
        "HeartRateMeasurement(sensorContact=$sensorContact, hasEnergy=${energyExpendedKj != null}, " +
            "rrIntervals=${rrIntervalsMillis.size})"
}

/**
 * Leitor do formato binário da `0x2A37` (little-endian):
 *
 * | Byte(s) | Conteúdo |
 * |---|---|
 * | 0 | flags — bit 0: FC em UINT16 (senão UINT8) · bits 1-2: contato (bit 2 = suportado, bit 1 = detectado) · bit 3: energia presente · bit 4: RR presente |
 * | 1 ou 1-2 | FC |
 * | +2 | energia gasta (UINT16, kJ), se bit 3 |
 * | resto | intervalos RR (UINT16 cada, 1/1024 s), se bit 4 |
 *
 * Pacote truncado (menor do que as flags anunciam) devolve `null` — nunca um número inventado.
 * Byte de RR sobrando (comprimento ímpar) é ignorado.
 */
object HeartRateMeasurementParser {
    private const val FLAG_UINT16 = 0x01
    private const val FLAG_CONTACT_DETECTED = 0x02
    private const val FLAG_CONTACT_SUPPORTED = 0x04
    private const val FLAG_ENERGY = 0x08
    private const val FLAG_RR = 0x10

    fun parse(bytes: ByteArray): HeartRateMeasurement? {
        if (bytes.isEmpty()) return null
        val flags = bytes.u8(0)
        var offset = 1

        val bpm = if (flags and FLAG_UINT16 != 0) {
            if (bytes.size < offset + 2) return null
            bytes.u16(offset).also { offset += 2 }
        } else {
            if (bytes.size < offset + 1) return null
            bytes.u8(offset).also { offset += 1 }
        }

        val contact = when {
            flags and FLAG_CONTACT_SUPPORTED == 0 -> SensorContact.NOT_SUPPORTED
            flags and FLAG_CONTACT_DETECTED != 0 -> SensorContact.DETECTED
            else -> SensorContact.NOT_DETECTED
        }

        val energy = if (flags and FLAG_ENERGY != 0) {
            if (bytes.size < offset + 2) return null
            bytes.u16(offset).also { offset += 2 }
        } else {
            null
        }

        val rr = if (flags and FLAG_RR != 0) {
            buildList {
                while (offset + 1 < bytes.size) {
                    add(bytes.u16(offset) * 1000.0 / 1024.0)
                    offset += 2
                }
            }
        } else {
            emptyList()
        }

        return HeartRateMeasurement(bpm, contact, energy, rr)
    }

    private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF

    private fun ByteArray.u16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)
}
