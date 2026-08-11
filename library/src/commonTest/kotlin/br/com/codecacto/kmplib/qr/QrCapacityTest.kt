package br.com.codecacto.kmplib.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `qrCodeFits` — o helper que a tela de exportar usa para escolher entre **QR** e **arquivo**.
 *
 * A capacidade tem de ser **exata**: conservadora demais manda o usuário para o arquivo sem
 * necessidade; otimista demais faz a tela prometer um QR que o encoder não entrega.
 */
class QrCapacityTest {

    @Test
    fun `capacidade declarada bate exatamente com o que o encoder aceita`() {
        // A prova de que o helper e o encoder concordam — se divergirem, a tela mente para o usuário.
        for (version in listOf(1, 2, 5, 10, 20, 40)) {
            for (level in QrErrorCorrection.entries) {
                val capacity = qrByteCapacity(version, level)
                val payload = "x".repeat(capacity)

                val check = qrCodeFits(payload, level, maxVersion = version)
                assertTrue(check.fits, "helper disse que não cabe em $version-$level ($capacity bytes)")
                assertEquals(version, check.requiredVersion)
                assertNotNull(
                    encodeQrOrNull(payload, level, maxVersion = version),
                    "encoder recusou o que o helper aprovou em $version-$level",
                )

                val overflow = "x".repeat(capacity + 1)
                assertTrue(
                    !qrCodeFits(overflow, level, maxVersion = version).fits,
                    "helper aprovou 1 byte além da capacidade em $version-$level",
                )
                assertNull(encodeQrOrNull(overflow, level, maxVersion = version))
            }
        }
    }

    @Test
    fun `check devolve numeros uteis, nao so um booleano`() {
        val check = qrCodeFits("confere-qr:v1:" + "a".repeat(80), QrErrorCorrection.L, maxVersion = 20)

        assertTrue(check.fits)
        assertNotNull(check.requiredVersion)
        assertTrue(check.requiredBits > 0)
        assertTrue(check.capacityBits >= check.requiredBits)
        assertEquals(check.capacityBits - check.requiredBits, check.remainingBits)
        assertEquals(check.remainingBits / 8, check.remainingBytes)
        assertTrue(check.usedFraction in 0f..1f)
        assertEquals(QrMode.Byte, check.mode)
        assertEquals(QrErrorCorrection.L, check.errorCorrection)
        assertEquals(20, check.maxVersion)
    }

    @Test
    fun `quando nao cabe, o check diz quanto falta`() {
        val check = qrCodeFits("z".repeat(3000), QrErrorCorrection.H, maxVersion = 40)

        assertTrue(!check.fits)
        assertNull(check.requiredVersion)
        assertTrue(check.requiredBits > check.capacityBits)
        assertEquals(0, check.remainingBits, "sem espaço sobrando quando não cabe")
        assertEquals(0, check.remainingBytes)
        assertEquals(1f, check.usedFraction, "fração é clampada em 1")
    }

    @Test
    fun `nivel de correcao mais alto reduz a capacidade`() {
        val version = 10
        val l = qrByteCapacity(version, QrErrorCorrection.L)
        val m = qrByteCapacity(version, QrErrorCorrection.M)
        val q = qrByteCapacity(version, QrErrorCorrection.Q)
        val h = qrByteCapacity(version, QrErrorCorrection.H)
        assertTrue(l > m && m > q && q > h, "esperado L > M > Q > H, foi $l/$m/$q/$h")
    }

    @Test
    fun `capacidade cresce com a versao`() {
        for (level in QrErrorCorrection.entries) {
            var previous = 0
            for (version in 1..40) {
                val capacity = qrByteCapacity(version, level)
                assertTrue(capacity > previous, "capacidade não cresceu de ${version - 1} para $version")
                previous = capacity
            }
        }
    }

    @Test
    fun `capacidade maxima do padrao esta na ordem de grandeza publicada`() {
        // Valores publicados do padrão (modo binário): 40-L ≈ 2953 bytes, 40-H ≈ 1273.
        assertEquals(2953, qrByteCapacity(40, QrErrorCorrection.L))
        assertEquals(1273, qrByteCapacity(40, QrErrorCorrection.H))
        // Versão 1: 17 bytes em L, 7 em H.
        assertEquals(17, qrByteCapacity(1, QrErrorCorrection.L))
        assertEquals(7, qrByteCapacity(1, QrErrorCorrection.H))
    }

    @Test
    fun `qrCodeFitsPayload e o atalho booleano`() {
        assertTrue(qrCodeFitsPayload("curto", QrErrorCorrection.L))
        assertTrue(!qrCodeFitsPayload("z".repeat(5000), QrErrorCorrection.H))
        // Com teto de versão baixo, o mesmo payload deixa de caber — é o gatilho do fallback.
        assertTrue(qrCodeFitsPayload("p".repeat(300), QrErrorCorrection.L, maxVersion = 20))
        assertTrue(!qrCodeFitsPayload("p".repeat(300), QrErrorCorrection.L, maxVersion = 5))
    }

    @Test
    fun `modo economico aparece no check e muda a versao exigida`() {
        val digits = "7".repeat(200)
        val numeric = qrCodeFits(digits, QrErrorCorrection.M)
        assertEquals(QrMode.Numeric, numeric.mode)

        // O mesmo tamanho de conteúdo em byte precisa de versão maior — a economia é real.
        val bytes = qrCodeFits("z".repeat(200), QrErrorCorrection.M)
        assertEquals(QrMode.Byte, bytes.mode)
        assertTrue(
            numeric.requiredVersion!! < bytes.requiredVersion!!,
            "modo numérico deveria exigir versão menor (${numeric.requiredVersion} vs ${bytes.requiredVersion})",
        )
    }

    @Test
    fun `argumento invalido lanca`() {
        val cases = listOf<() -> Unit>(
            { qrByteCapacity(0) },
            { qrByteCapacity(41) },
            { qrCodeFits("a", maxVersion = 41) },
            { qrCodeFits("a", minVersion = 0) },
            { qrCodeFits("a", minVersion = 10, maxVersion = 5) },
        )
        for (case in cases) {
            val threw = try {
                case()
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            assertTrue(threw, "esperado IllegalArgumentException")
        }
    }
}
