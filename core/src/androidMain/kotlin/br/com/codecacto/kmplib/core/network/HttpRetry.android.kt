package br.com.codecacto.kmplib.core.network

import java.net.MalformedURLException
import java.net.UnknownServiceException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * OkHttp: falhas que não passam com nova tentativa — certificado recusado/não confiável
 * (`SSLPeerUnverifiedException`, `CertificateException` na cadeia do `SSLHandshakeException`),
 * cleartext proibido pela `network_security_config` (`UnknownServiceException`) e URL inválida.
 * Um `SSLHandshakeException` SEM certificado na causa (conexão derrubada no meio do handshake) é
 * transitório e repete.
 */
internal actual fun isPermanentTransportFailure(cause: Throwable): Boolean {
    var atual: Throwable? = cause
    var profundidade = 0
    while (atual != null && profundidade < MAX_CAUSE_DEPTH) {
        when (atual) {
            is SSLPeerUnverifiedException,
            is CertificateException,
            is UnknownServiceException,
            is MalformedURLException,
            -> return true
        }
        if (atual.cause === atual) break
        atual = atual.cause
        profundidade++
    }
    return false
}

private const val MAX_CAUSE_DEPTH = 8
