package br.com.codecacto.kmplib.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundEffectDefaultsTest {

    @Test
    fun maxStreams_nunca_e_um() {
        // 1 (o default do SoundPool) cortaria o bipe anterior a cada toque - o sintoma que este
        // modulo existe para nao ter.
        assertTrue(SoundEffectDefaults.MAX_STREAMS > 1)
    }

    @Test
    fun isOversized_marca_so_acima_do_recomendado() {
        assertFalse(SoundEffectDefaults.isOversized(0))
        assertFalse(SoundEffectDefaults.isOversized(SoundEffectDefaults.RECOMMENDED_MAX_BYTES))
        assertTrue(SoundEffectDefaults.isOversized(SoundEffectDefaults.RECOMMENDED_MAX_BYTES + 1))
    }

    @Test
    fun chave_em_branco_e_invalida() {
        assertFalse(SoundEffectDefaults.isValidKey(""))
        assertFalse(SoundEffectDefaults.isValidKey("   "))
        assertTrue(SoundEffectDefaults.isValidKey("volta"))
    }

    @Test
    fun fileNameFor_sanitiza_e_usa_a_extensao_do_formato() {
        val nome = SoundEffectDefaults.fileNameFor("Meta Atingida!", SoundEffectFormat.WAV)

        assertTrue(nome.endsWith(".wav"), nome)
        assertTrue(nome.startsWith("meta_atingida_"), nome)
        val permitidos = nome.all { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '.' }
        assertTrue(permitidos, "nome de arquivo com caractere de risco: $nome")
    }

    @Test
    fun fileNameFor_nao_colide_para_chaves_que_sanitizam_igual() {
        // Sem o hash da chave original, "a/b" e "a-b" virariam o mesmo arquivo e o segundo load
        // sobrescreveria o audio do primeiro efeito, calado.
        val um = SoundEffectDefaults.fileNameFor("a/b", SoundEffectFormat.WAV)
        val outro = SoundEffectDefaults.fileNameFor("a b", SoundEffectFormat.WAV)

        assertTrue(um != outro, "chaves diferentes geraram o mesmo arquivo: $um")
    }

    @Test
    fun fileNameFor_e_estavel_para_a_mesma_chave() {
        assertEquals(
            SoundEffectDefaults.fileNameFor("volta", SoundEffectFormat.WAV),
            SoundEffectDefaults.fileNameFor("volta", SoundEffectFormat.WAV),
        )
    }

    @Test
    fun fileNameFor_lida_com_chave_so_de_simbolos() {
        val nome = SoundEffectDefaults.fileNameFor("///", SoundEffectFormat.CAF)

        assertTrue(nome.startsWith("sfx_"), nome)
        assertTrue(nome.endsWith(".caf"), nome)
    }

    @Test
    fun fileNameFor_limita_o_tamanho_do_nome() {
        val nome = SoundEffectDefaults.fileNameFor("v".repeat(500), SoundEffectFormat.WAV)

        assertTrue(nome.length <= 64, "nome longo demais para o sistema de arquivos: ${nome.length}")
    }

    @Test
    fun stableHash_e_deterministico_e_hexadecimal_de_oito() {
        val hash = SoundEffectDefaults.stableHash("volta")

        assertEquals(hash, SoundEffectDefaults.stableHash("volta"))
        assertEquals(8, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' }, hash)
        assertTrue(hash != SoundEffectDefaults.stableHash("meta"))
    }
}
