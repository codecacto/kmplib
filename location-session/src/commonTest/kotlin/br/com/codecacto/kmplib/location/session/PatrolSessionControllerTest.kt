package br.com.codecacto.kmplib.location.session

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakePort(var startResult: PatrolStartResult = PatrolStartResult.Started) : PatrolPlatformPort {
    val calls = mutableListOf<String>()
    var saved: PersistedPatrol? = null

    override fun start(sessionId: String, accuracy: PatrolAccuracy): PatrolStartResult {
        calls += "start:$sessionId:$accuracy"
        return startResult
    }
    override fun stop() { calls += "stop" }
    override fun applyAccuracy(accuracy: PatrolAccuracy) { calls += "accuracy:$accuracy" }
    override fun loadPersisted(): PersistedPatrol? = saved
    override fun persist(session: PersistedPatrol?) { saved = session }
}

private fun fix(t: Long, acc: Float = 10f, mock: Boolean = false) =
    LocationFix(latitude = -15.6, longitude = -56.1, accuracy = acc, timestampMillis = t, isMock = mock)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
/** 2.308.0 — estado da sessão, precisão adaptativa, espaçamento e retomada. */
class PatrolSessionControllerTest {

    @Test
    fun iniciaPersisteEParaApagando() {
        val port = FakePort()
        val c = PatrolSessionController(port)
        assertEquals(PatrolStartResult.Started, c.startPatrol("ronda-1"))
        assertTrue(c.isPatrolActive.value)
        assertEquals("ronda-1", c.activeSessionId.value)
        assertEquals(PersistedPatrol("ronda-1", PatrolAccuracy.BALANCED), port.saved)

        c.stopPatrol()
        assertFalse(c.isPatrolActive.value)
        assertNull(c.activeSessionId.value)
        assertNull(port.saved)
        assertEquals(listOf("start:ronda-1:BALANCED", "stop"), port.calls)
    }

    @Test
    fun startIdempotenteETrocaDeSessaoSemReligarOGps() {
        val port = FakePort()
        val c = PatrolSessionController(port)
        c.startPatrol("a")
        assertEquals(PatrolStartResult.Started, c.startPatrol("a"))
        assertEquals(PatrolStartResult.Started, c.startPatrol("b"))
        assertEquals("b", c.activeSessionId.value)
        assertEquals(1, port.calls.count { it.startsWith("start") })
        assertEquals("b", port.saved?.sessionId)
    }

    @Test
    fun recusaDaPlataformaNaoAtiva() {
        val port = FakePort(PatrolStartResult.PermissionRequired)
        val c = PatrolSessionController(port)
        assertEquals(PatrolStartResult.PermissionRequired, c.startPatrol("a"))
        assertFalse(c.isPatrolActive.value)
        assertNull(port.saved)
    }

    @Test
    fun sessaoVaziaERecusada() {
        assertFailsWith<IllegalArgumentException> { PatrolSessionController(FakePort()).startPatrol(" ") }
    }

    @Test
    fun precisaoAdaptativaPelaDistanciaAoPonto() {
        assertEquals(PatrolAccuracy.HIGH, PatrolAccuracy.forDistanceToCheckpoint(0.0))
        assertEquals(PatrolAccuracy.HIGH, PatrolAccuracy.forDistanceToCheckpoint(200.0))
        assertEquals(PatrolAccuracy.BALANCED, PatrolAccuracy.forDistanceToCheckpoint(200.1))
        assertEquals(PatrolAccuracy.BALANCED, PatrolAccuracy.forDistanceToCheckpoint(null))
        assertEquals(PatrolAccuracy.BALANCED, PatrolAccuracy.forDistanceToCheckpoint(-1.0))
        assertEquals(10_000L, PatrolAccuracy.BALANCED.intervalMillis)
        assertEquals(5_000L, PatrolAccuracy.HIGH.intervalMillis)

        val port = FakePort()
        val c = PatrolSessionController(port)
        c.startPatrol("a")
        c.updateDistanceToCheckpoint(150.0)
        c.updateDistanceToCheckpoint(120.0) // mesmo modo: não reconfigura
        c.updateDistanceToCheckpoint(900.0)
        assertEquals(listOf("accuracy:HIGH", "accuracy:BALANCED"), port.calls.filter { it.startsWith("accuracy") })
        assertEquals(PatrolAccuracy.BALANCED, port.saved?.accuracy)
    }

    @Test
    fun precisaoSemSessaoSoGuardaOModo() {
        val port = FakePort()
        val c = PatrolSessionController(port)
        c.setAccuracy(PatrolAccuracy.HIGH)
        assertTrue(port.calls.isEmpty())
        c.startPatrol("a")
        assertEquals("start:a:HIGH", port.calls.single())
    }

    @Test
    fun espacamentoDosFixes() {
        val bal = PatrolAccuracy.BALANCED
        assertTrue(shouldEmitFix(null, fix(1_000), bal))
        assertFalse(shouldEmitFix(fix(1_000), fix(1_000), bal))
        assertFalse(shouldEmitFix(fix(2_000), fix(1_000), bal))
        assertFalse(shouldEmitFix(fix(1_000, acc = 10f), fix(3_000, acc = 12f), bal))
        assertTrue(shouldEmitFix(fix(1_000, acc = 10f), fix(3_000, acc = 5f), bal)) // mais preciso passa
        assertTrue(shouldEmitFix(fix(1_000), fix(6_000), bal))
        assertTrue(shouldEmitFix(fix(1_000), fix(3_500), PatrolAccuracy.HIGH))
    }

    @Test
    fun fixesSoComSessaoAtivaEEspacados() = runTest(UnconfinedTestDispatcher()) {
        val c = PatrolSessionController(FakePort())
        val recebidos = mutableListOf<LocationFix>()
        val job = launch { c.fixes.collect { recebidos += it } }
        c.onFix(fix(1_000)) // sem sessão: ignorado
        c.startPatrol("a")
        c.onFix(fix(1_000, mock = true))
        c.onFix(fix(2_000))
        c.onFix(fix(7_000))
        job.cancel()
        assertEquals(listOf(1_000L, 7_000L), recebidos.map { it.timestampMillis })
        assertTrue(recebidos.first().isMock)
        assertEquals(7_000L, c.lastFix.value?.timestampMillis)
    }

    @Test
    fun retomaDepoisDaMorteDoProcesso() {
        val port = FakePort().apply { saved = PersistedPatrol("r9", PatrolAccuracy.HIGH) }
        val c = PatrolSessionController(port)
        assertEquals(PatrolStartResult.Started, c.resumePatrolIfNeeded())
        assertEquals("r9", c.activeSessionId.value)
        assertEquals(PatrolAccuracy.HIGH, c.accuracy.value)
        assertEquals(PatrolStartResult.Started, c.resumePatrolIfNeeded())
        assertNull(PatrolSessionController(FakePort()).resumePatrolIfNeeded())
    }

    @Test
    fun retomadaSemPermissaoDescartaASessao() {
        val port = FakePort(PatrolStartResult.PermissionRequired).apply { saved = PersistedPatrol("r9", PatrolAccuracy.BALANCED) }
        val c = PatrolSessionController(port)
        assertEquals(PatrolStartResult.PermissionRequired, c.resumePatrolIfNeeded())
        assertNull(port.saved)
        assertEquals(PatrolInterruption.PERMISSION_REVOKED, c.interruption.value)
    }

    @Test
    fun interrupcaoPeloSistemaGuardaParaRetomar() {
        val port = FakePort()
        val c = PatrolSessionController(port)
        c.onRestoredBySystem(PersistedPatrol("r1", PatrolAccuracy.BALANCED))
        assertTrue(c.isPatrolActive.value)
        port.saved = PersistedPatrol("r1", PatrolAccuracy.BALANCED)
        c.onInterrupted(PatrolInterruption.STOPPED_BY_SYSTEM, keepForResume = true)
        assertFalse(c.isPatrolActive.value)
        assertEquals("r1", port.saved?.sessionId)
        assertEquals(PatrolInterruption.STOPPED_BY_SYSTEM, c.interruption.value)

        c.resumePatrolIfNeeded()
        assertNull(c.interruption.value)
        c.onInterrupted(PatrolInterruption.PERMISSION_REVOKED, keepForResume = false)
        assertNull(port.saved)
        assertNull(c.activeSessionId.value)
    }

    @Test
    fun distanciaHaversine() {
        // 1 grau de latitude ≈ 111,2 km.
        val d = distanceMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(d in 111_000.0..111_400.0, "d=$d")
        assertEquals(0.0, distanceMeters(-15.6, -56.1, -15.6, -56.1))
        val f = fix(1)
        assertTrue(f.distanceTo(-15.6009, -56.1) in 95.0..105.0)
    }

    @Test
    fun toStringSemCoordenadas() {
        val s = LocationFix(-15.601234, -56.098765, 8f, 1L, false, 1.2f).toString()
        assertFalse(s.contains("15.6"))
        assertFalse(s.contains("56.09"))
    }

    @Test
    fun primeiroFixDoFlow() = runTest(UnconfinedTestDispatcher()) {
        val c = PatrolSessionController(FakePort())
        c.startPatrol("a")
        val primeiro = async { c.fixes.first() }
        c.onFix(fix(5))
        assertEquals(5L, primeiro.await().timestampMillis)
    }
}
