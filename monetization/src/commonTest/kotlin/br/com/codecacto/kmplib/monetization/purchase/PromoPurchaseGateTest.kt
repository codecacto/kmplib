package br.com.codecacto.kmplib.monetization.purchase

import br.com.codecacto.kmplib.platform.audience.AppAudience
import br.com.codecacto.kmplib.platform.audience.KmpLibAudience
import br.com.codecacto.kmplib.platform.audience.ParentalGate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Compra promovida da App Store x portão de pais (2.259.1). */
class PromoPurchaseGateTest {

    private val host = Any()
    private var started = 0
    private var denied = 0
    private val gated = gatePromoPurchase<String, String>(
        startPurchase = { onError, onSuccess ->
            started++
            assertEquals("erro", onError)
            assertEquals("ok", onSuccess)
        },
        onDenied = { denied++ },
    )

    @BeforeTest
    fun setUp() = clean()

    @AfterTest
    fun tearDown() = clean()

    private fun clean() {
        ParentalGate.pending.value?.let { ParentalGate.resolve(it, passed = false) }
        ParentalGate.detachHost(host)
        KmpLibAudience.configure(AppAudience.GENERAL)
    }

    private fun kids() {
        KmpLibAudience.configure(AppAudience.KIDS)
        ParentalGate.attachHost(host)
    }

    @Test
    fun publicoGeralCompraNaHoraSemPortao() {
        gated("erro", "ok")
        assertEquals(1, started)
        assertNull(ParentalGate.pending.value)
    }

    @Test
    fun infantilSoCompraDepoisDoAdultoPassar() {
        kids()
        gated("erro", "ok")
        assertEquals(0, started)
        val pedido = assertNotNull(ParentalGate.pending.value)
        ParentalGate.resolve(pedido, passed = true)
        assertEquals(1, started)
        assertEquals(0, denied)
    }

    @Test
    fun infantilNegadoDescartaACompra() {
        kids()
        gated("erro", "ok")
        ParentalGate.resolve(assertNotNull(ParentalGate.pending.value), passed = false)
        assertEquals(0, started)
        assertEquals(1, denied)
    }

    @Test
    fun infantilSemHostNegaEDescarta() {
        KmpLibAudience.configure(AppAudience.KIDS)
        gated("erro", "ok")
        assertEquals(0, started)
        assertEquals(1, denied)
        assertNull(ParentalGate.pending.value)
    }

    @Test
    fun segundaPromoComPortaoAbertoEDescartada() {
        kids()
        gated("erro", "ok")
        gated("erro", "ok")
        assertEquals(1, denied)
        ParentalGate.resolve(assertNotNull(ParentalGate.pending.value), passed = true)
        assertEquals(1, started)
    }
}
