package br.com.codecacto.kmplib.core.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SignedInAccountTest {

    @Test
    fun aceita_ids_opacos() {
        listOf(
            "0b6c1f4e-3a2d-4c8e-9f10-2b7d5e6a9c01", // UUID
            "42", "123456789", // autoincremento
            "1234567890123456789", // snowflake
            "Xy9AbCdEfGhIjKlMnOpQrStUvWx2", // uid do Firebase
            "google-oauth2|1234", "usr_abc.def:1",
        ).forEach { assertEquals(it, opaqueAccountIdOrNull(it), it) }
        assertEquals("acc-1", opaqueAccountIdOrNull("  acc-1  "))
    }

    @Test
    fun recusa_o_que_identifica_a_pessoa() {
        listOf(
            null, "", "   ",
            "ana@exemplo.com", "ana@x",
            "Ana Souza", "joão",
            "+5565998030240", "(65) 99803-0240",
            "65998030240", "5565998030240", // telefone só algarismos
            "123.456.789-09", "12345678909", // CPF
            "12.345.678/0001-95", "12345678000195", // CNPJ
            "a".repeat(129),
        ).forEach { assertNull(opaqueAccountIdOrNull(it), it.toString()) }
    }

    @Test
    fun publish_filtra_notifica_mudancas_e_remove_ouvinte() {
        SignedInAccount.publish(null)
        val recebidos = mutableListOf<String?>()
        val remover = SignedInAccount.addListener { recebidos += it }

        SignedInAccount.publish("acc-1")
        SignedInAccount.publish("acc-1") // mesmo valor: não notifica
        SignedInAccount.publish("ana@exemplo.com") // e-mail vira anônimo
        assertNull(SignedInAccount.id.value)
        SignedInAccount.publish("acc-2")
        remover()
        SignedInAccount.publish(null)

        assertEquals(listOf("acc-1", null, "acc-2"), recebidos)
        assertNull(SignedInAccount.id.value)
    }
}
