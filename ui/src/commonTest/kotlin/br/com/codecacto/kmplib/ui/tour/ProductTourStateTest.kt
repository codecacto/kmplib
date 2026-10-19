package br.com.codecacto.kmplib.ui.tour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductTourStateTest {

    private val boasVindas = TourStep(null, "Bem-vindo", "Vamos mostrar onde fica cada coisa.")
    private val locacoes = TourStep("nav-item-locacoes", "Locações", "As locações em andamento.")
    private val clientes = TourStep("nav-item-clientes", "Clientes", "O cadastro dos clientes.")
    private val relatorios = TourStep("nav-item-relatorios", "Relatórios", "Os números do mês.")
    private val conta = TourStep("conta", "Sua conta", "Configurações e saída.")

    private fun registryWith(vararg ids: String): TourTargetRegistry = TourTargetRegistry().apply {
        ids.forEachIndexed { i, id -> report(owner = id, id = id, rootBounds = Rect(10f * i, 100f, 10f * i + 40f, 140f)) }
    }

    private fun started(registry: TourTargetRegistry, steps: List<TourStep>): Pair<ProductTourState, MutableList<TourEndReason>> {
        val ends = mutableListOf<TourEndReason>()
        val state = ProductTourState.create(registry)
        state.addEndListener { ends += it }
        state.start(steps)
        state.commitStart()
        return state to ends
    }

    @Test
    fun `resolveTourSteps mantem sem alvo e os visiveis na ordem`() {
        val visiveis = setOf("nav-item-locacoes", "conta")
        val r = resolveTourSteps(listOf(boasVindas, locacoes, clientes, conta)) { it in visiveis }
        assertEquals(listOf(boasVindas, locacoes, conta), r)
    }

    @Test
    fun `start so abre depois do commit e pula alvo ausente da contagem`() {
        val state = ProductTourState.create(registryWith("nav-item-locacoes", "conta"))
        state.start(listOf(boasVindas, locacoes, clientes, conta))
        assertTrue(state.isStarting)
        assertFalse(state.isActive)
        state.commitStart()
        assertFalse(state.isStarting)
        assertTrue(state.isActive)
        assertEquals(boasVindas, state.currentStep)
        assertEquals(1, state.currentNumber)
        assertEquals(3, state.stepCount)
    }

    @Test
    fun `proximo e voltar navegam e o ultimo conclui`() {
        val (state, ends) = started(registryWith("nav-item-locacoes", "conta"), listOf(boasVindas, locacoes, conta))
        assertFalse(state.canGoBack)
        assertFalse(state.isLastStep)
        state.back() // no primeiro: nada
        assertEquals(boasVindas, state.currentStep)
        state.next()
        assertEquals(locacoes, state.currentStep)
        assertEquals(2, state.currentNumber)
        assertTrue(state.canGoBack)
        state.back()
        assertEquals(boasVindas, state.currentStep)
        state.next(); state.next()
        assertEquals(conta, state.currentStep)
        assertTrue(state.isLastStep)
        state.next()
        assertFalse(state.isActive)
        assertNull(state.currentStep)
        assertEquals(0, state.stepCount)
        assertEquals(listOf(TourEndReason.COMPLETED), ends)
    }

    @Test
    fun `pular fecha com SKIPPED`() {
        val (state, ends) = started(registryWith("nav-item-locacoes"), listOf(locacoes, boasVindas))
        state.skip()
        assertFalse(state.isActive)
        assertEquals(listOf(TourEndReason.SKIPPED), ends)
        state.skip() // fechado: não avisa de novo
        assertEquals(1, ends.size)
    }

    @Test
    fun `nenhum alvo na tela nao abre e avisa NOTHING_TO_SHOW`() {
        val (state, ends) = started(registryWith(), listOf(locacoes, clientes))
        assertFalse(state.isActive)
        assertEquals(listOf(TourEndReason.NOTHING_TO_SHOW), ends)
    }

    @Test
    fun `lista vazia nao inicia nada`() {
        val state = ProductTourState.create(registryWith("conta"))
        state.start(emptyList())
        assertFalse(state.isStarting)
        state.commitStart()
        assertFalse(state.isActive)
    }

    @Test
    fun `alvo que some no meio do tour e pulado ao avancar e ao voltar`() {
        val registry = registryWith("nav-item-locacoes", "nav-item-clientes", "nav-item-relatorios")
        val (state, _) = started(registry, listOf(locacoes, clientes, relatorios))
        registry.remove("nav-item-clientes")
        assertFalse(state.isLastStep)
        state.next()
        assertEquals(relatorios, state.currentStep)
        assertEquals(3, state.currentNumber) // a numeração é a do início do tour
        assertTrue(state.isLastStep)
        state.back()
        assertEquals(locacoes, state.currentStep)
    }

    @Test
    fun `start com tour aberto recomeca sem avisar fim`() {
        val (state, ends) = started(registryWith("nav-item-locacoes"), listOf(boasVindas, locacoes))
        state.next()
        state.start(listOf(boasVindas, locacoes))
        assertFalse(state.isActive)
        state.commitStart()
        assertEquals(boasVindas, state.currentStep)
        assertTrue(ends.isEmpty())
    }

    @Test
    fun `pular durante o start pendente cancela sem avisar`() {
        val ends = mutableListOf<TourEndReason>()
        val state = ProductTourState.create(registryWith("conta"))
        state.addEndListener { ends += it }
        state.start(listOf(conta))
        state.skip()
        assertFalse(state.isStarting)
        state.commitStart()
        assertFalse(state.isActive)
        assertTrue(ends.isEmpty())
    }

    @Test
    fun `listener removido nao e chamado`() {
        val ends = mutableListOf<TourEndReason>()
        val listener: (TourEndReason) -> Unit = { ends += it }
        val state = ProductTourState.create(registryWith("conta"))
        state.addEndListener(listener)
        state.removeEndListener(listener)
        state.start(listOf(conta)); state.commitStart(); state.skip()
        assertTrue(ends.isEmpty())
    }

    @Test
    fun `registro converte para o host e ignora area vazia`() {
        val registry = TourTargetRegistry()
        registry.hostOrigin = Offset(0f, 24f)
        registry.report("a", "btn", Rect(10f, 100f, 50f, 140f))
        assertEquals(Rect(10f, 76f, 50f, 116f), registry.boundsOf("btn"))
        registry.report("a", "btn", Rect(10f, 100f, 10f, 100f)) // rolou para fora (recortado)
        assertNull(registry.boundsOf("btn"))
        assertFalse(registry.isVisible("btn"))
    }

    @Test
    fun `mesmo id em dois nos vale o visivel`() {
        val registry = TourTargetRegistry()
        registry.report("rail", "nav-item-inicio", Rect.Zero)
        registry.report("barra", "nav-item-inicio", Rect(0f, 700f, 90f, 780f))
        assertEquals(Rect(0f, 700f, 90f, 780f), registry.boundsOf("nav-item-inicio"))
        registry.remove("barra")
        assertNull(registry.boundsOf("nav-item-inicio"))
    }

    @Test
    fun `textos formatam o progresso`() {
        assertEquals("3 de 7", ProductTourTexts().progress(3, 7))
        assertEquals("2 of 5", ProductTourTexts(progressPattern = "%1\$d of %2\$d").progress(2, 5))
    }
}
