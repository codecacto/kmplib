package br.com.codecacto.kmplib.core.network

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorAppTransportSecurityRequiresSecureConnection
import platform.Foundation.NSURLErrorBadURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorClientCertificateRejected
import platform.Foundation.NSURLErrorClientCertificateRequired
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorServerCertificateHasBadDate
import platform.Foundation.NSURLErrorServerCertificateHasUnknownRoot
import platform.Foundation.NSURLErrorServerCertificateNotYetValid
import platform.Foundation.NSURLErrorServerCertificateUntrusted
import platform.Foundation.NSURLErrorUnsupportedURL

/**
 * Darwin (NSURLSession): o engine embrulha o `NSError` em [DarwinHttpRequestException]. Não passam
 * com nova tentativa: URL inválida/não suportada, ATS exigindo HTTPS, certificado do servidor ou do
 * cliente recusado, e o cancelamento da própria sessão. Perda de conexão
 * (`NSURLErrorNetworkConnectionLost`), sem rede, DNS e host inalcançável repetem.
 */
internal actual fun isPermanentTransportFailure(cause: Throwable): Boolean {
    val erro = (cause as? DarwinHttpRequestException)?.origin ?: return false
    if (erro.domain != NSURLErrorDomain) return false
    return erro.code in PERMANENT_URL_ERROR_CODES
}

private val PERMANENT_URL_ERROR_CODES: Set<Long> = setOf(
    NSURLErrorCancelled,
    NSURLErrorBadURL,
    NSURLErrorUnsupportedURL,
    NSURLErrorAppTransportSecurityRequiresSecureConnection,
    NSURLErrorServerCertificateHasBadDate,
    NSURLErrorServerCertificateUntrusted,
    NSURLErrorServerCertificateHasUnknownRoot,
    NSURLErrorServerCertificateNotYetValid,
    NSURLErrorClientCertificateRejected,
    NSURLErrorClientCertificateRequired,
)
