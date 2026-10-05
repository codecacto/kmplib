package br.com.codecacto.kmplib.core.network

import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** As exceções que o OkHttp lança de verdade, classificadas como o Android as recebe. */
class HttpRetryAndroidTest {

    @Test
    fun `piscadas de rede movel repetem`() {
        assertTrue(isTransientTransportFailure(SocketException("Connection reset")))
        assertTrue(isTransientTransportFailure(java.io.EOFException("unexpected end of stream")))
        assertTrue(isTransientTransportFailure(ConnectException("Failed to connect")))
        assertTrue(isTransientTransportFailure(UnknownHostException("api.codecacto.com.br")))
        // Handshake derrubado no meio (sem certificado na causa) é piscada, não recusa.
        assertTrue(isTransientTransportFailure(SSLHandshakeException("connection closed")))
    }

    @Test
    fun `falhas permanentes NAO repetem`() {
        assertFalse(isTransientTransportFailure(SSLPeerUnverifiedException("hostname mismatch")))
        assertFalse(
            isTransientTransportFailure(
                SSLHandshakeException("cert").apply { initCause(CertificateException("untrusted")) },
            ),
        )
        assertFalse(isTransientTransportFailure(UnknownServiceException("CLEARTEXT not permitted")))
    }
}
