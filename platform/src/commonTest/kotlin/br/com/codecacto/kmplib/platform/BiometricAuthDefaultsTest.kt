package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dublê como os que os apps escrevem: implementa **só** o que a interface exigia antes da variante
 * com trava de tela. Se estes testes deixarem de compilar, a adição quebrou consumidor.
 */
private class LegacyBiometricAuth(private val available: Boolean) : BiometricAuth {
    var chamadas: Int = 0
        private set

    override fun isAvailable(): Boolean = available
    override fun getBiometricType(): BiometricType =
        if (available) BiometricType.FINGERPRINT else BiometricType.NONE

    override fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        chamadas += 1
        if (available) onSuccess() else onError("indisponível")
    }
}

class BiometricAuthDefaultsTest {

    @Test
    fun implementacaoAntigaContinuaValendoComAVarianteNova() {
        val auth = LegacyBiometricAuth(available = true)
        var autenticou = false

        auth.authenticate(
            title = "Desbloquear",
            subtitle = "",
            allowDeviceCredential = true,
            onSuccess = { autenticou = true },
            onError = {},
            onCancel = {},
        )

        assertTrue(autenticou)
        assertEquals(1, auth.chamadas)
    }

    @Test
    fun isDeviceSecuredCaiEmIsAvailableQuandoNaoImplementado() {
        assertTrue(LegacyBiometricAuth(available = true).isDeviceSecured())
        assertEquals(false, LegacyBiometricAuth(available = false).isDeviceSecured())
    }
}
