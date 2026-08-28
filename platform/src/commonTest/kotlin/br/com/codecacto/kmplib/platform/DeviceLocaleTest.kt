package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Testes da normalização da região — a parte que tem regra.
 *
 * O acesso à plataforma (`platformRegionCode`) não é testado aqui de propósito: ele devolve o que o
 * sistema disser, e afirmar um valor esperado testaria a configuração da máquina de build, não o
 * código. O que precisa de garantia é o que fazemos com a resposta, inclusive com as respostas
 * estranhas que as plataformas realmente dão.
 */
class DeviceLocaleTest {

    @Test
    fun normaliza_codigoValido() {
        assertEquals("BR", normalizeRegion("BR"))
        assertEquals("US", normalizeRegion("us"))
        assertEquals("PT", normalizeRegion(" pt "))
    }

    @Test
    fun ausenteOuVazio_viraNulo() {
        assertNull(normalizeRegion(null))
        assertNull(normalizeRegion(""))
        assertNull(normalizeRegion("   "))
    }

    @Test
    fun codigoQueNaoEhPais_viraNulo() {
        // O Android devolve "419" nesta posição para o "espanhol da América Latina" — é um código
        // de ÁREA (UN M.49), não de país. Aceitá-lo faria o app procurar a tabela do país "419".
        assertNull(normalizeRegion("419"))
        assertNull(normalizeRegion("BRA"))
        assertNull(normalizeRegion("B"))
        assertNull(normalizeRegion("B1"))
    }

    @Test
    fun regiaoDoDispositivo_ouEhNulaOuEstaNormalizada() {
        // Não afirma QUAL é a região (isso seria testar a máquina de build), e sim que o contrato
        // vale seja qual for: ou `null`, ou duas letras maiúsculas.
        val regiao = deviceRegion()
        if (regiao != null) {
            assertEquals(2, regiao.length)
            assertEquals(regiao.uppercase(), regiao)
        }
    }
}
