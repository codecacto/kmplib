package br.com.codecacto.kmplib.monetization.purchase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * As regras do estado de assinatura do adaptador da loja (2.250.0) — transição da leitura e geração de
 * identidade —, testadas sem o SDK.
 */
class SubscriptionStateHolderTest {

    private val ativa = SubscriptionInfo(isActive = true, productId = "premium_mensal")
    private val inativa = SubscriptionInfo(isActive = false)

    // ------------------------------------------------------------------ transição pura

    @Test
    fun `sucesso e sempre READ`() {
        SubscriptionReadState.entries.forEach {
            assertEquals(SubscriptionReadState.READ, nextReadState(it, succeeded = true))
        }
    }

    @Test
    fun `falha antes de qualquer leitura vira FAILED`() {
        assertEquals(SubscriptionReadState.FAILED, nextReadState(SubscriptionReadState.PENDING, succeeded = false))
    }

    @Test
    fun `falha depois de lido preserva READ e FAILED continua FAILED`() {
        assertEquals(SubscriptionReadState.READ, nextReadState(SubscriptionReadState.READ, succeeded = false))
        assertEquals(SubscriptionReadState.FAILED, nextReadState(SubscriptionReadState.FAILED, succeeded = false))
    }

    // ------------------------------------------------------------------ holder

    @Test
    fun `nasce PENDING com o marcador inativo`() = runTest {
        val holder = SubscriptionStateHolder()
        assertEquals(SubscriptionReadState.PENDING, holder.readState.first())
        assertFalse(holder.subscriptionState.first().isActive)
    }

    @Test
    fun `publish marca READ`() = runTest {
        val holder = SubscriptionStateHolder()
        holder.publish(ativa)
        assertEquals(ativa, holder.info)
        assertEquals(SubscriptionReadState.READ, holder.readStateValue)
    }

    @Test
    fun `leitura que falha sem leitura anterior marca FAILED`() {
        val holder = SubscriptionStateHolder()
        assertFalse(holder.completeRead(holder.beginRead(), null))
        assertEquals(SubscriptionReadState.FAILED, holder.readStateValue)
    }

    @Test
    fun `leitura que falha depois de lido nao rebaixa o assinante`() {
        val holder = SubscriptionStateHolder()
        holder.publish(ativa)

        assertFalse(holder.completeRead(holder.beginRead(), null))

        assertEquals(ativa, holder.info)
        assertEquals(SubscriptionReadState.READ, holder.readStateValue)
    }

    @Test
    fun `leitura sem troca de identidade e aplicada`() {
        val holder = SubscriptionStateHolder()
        val token = holder.beginRead()
        assertTrue(holder.completeRead(token, ativa))
        assertEquals(ativa, holder.info)
        assertEquals(SubscriptionReadState.READ, holder.readStateValue)
    }

    @Test
    fun `leitura iniciada antes de um logIn e descartada`() {
        val holder = SubscriptionStateHolder()
        val leituraDeBoot = holder.beginRead()          // pede o CustomerInfo do sujeito anônimo…
        holder.identityChanged(inativa)                  // …o logIn conclui antes, com o novo sujeito…
        val aplicada = holder.completeRead(leituraDeBoot, ativa) // …e a resposta velha chega depois.

        assertFalse(aplicada)
        assertEquals(inativa, holder.info, "o premium do sujeito anterior não pode cair no novo")
    }

    @Test
    fun `falha velha tambem e descartada e nao marca FAILED`() {
        val holder = SubscriptionStateHolder()
        val velha = holder.beginRead()
        holder.identityChanged(ativa)
        holder.completeRead(velha, null)

        assertEquals(ativa, holder.info)
        assertEquals(SubscriptionReadState.READ, holder.readStateValue)
    }

    @Test
    fun `leitura iniciada depois da troca vale`() {
        val holder = SubscriptionStateHolder()
        holder.identityChanged(inativa)
        val nova = holder.beginRead()
        assertTrue(holder.completeRead(nova, ativa))
        assertEquals(ativa, holder.info)
    }
}
