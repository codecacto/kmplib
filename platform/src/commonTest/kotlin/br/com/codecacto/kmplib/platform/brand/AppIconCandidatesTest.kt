package br.com.codecacto.kmplib.platform.brand

import kotlin.test.Test
import kotlin.test.assertEquals

class AppIconCandidatesTest {

    @Test
    fun `nome do conjunto primeiro e arquivos do maior para o menor`() {
        assertEquals(
            listOf("AppIcon", "AppIcon76x76", "AppIcon60x60"),
            appIconCandidates(iconFiles = listOf("AppIcon60x60", "AppIcon76x76"), iconName = "AppIcon"),
        )
    }

    @Test
    fun `sem nome do conjunto sobram os arquivos`() {
        assertEquals(listOf("AppIcon60x60"), appIconCandidates(listOf("AppIcon60x60"), iconName = null))
    }

    @Test
    fun `bundle sem icone declarado nao gera candidato`() {
        assertEquals(emptyList(), appIconCandidates(emptyList(), iconName = " "))
    }

    @Test
    fun `nome repetido entra uma vez`() {
        assertEquals(listOf("AppIcon"), appIconCandidates(listOf("AppIcon"), iconName = "AppIcon"))
    }
}
