package br.com.codecacto.kmplib.auth.social

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Login social pelo navegador do sistema, no iOS.
 *
 * Usa **`ASWebAuthenticationSession`**, que é a API que a Apple indica para OAuth em aplicativo
 * nativo: ela abre o Safari fora do processo do app (o app não enxerga o que a pessoa digita),
 * aproveita a sessão de quem já está logado no aparelho e devolve a URL de volta diretamente a quem
 * a abriu — sem precisar de Activity de callback como no Android.
 *
 * `prefersEphemeralWebBrowserSession` fica **desligado** de propósito: com ele, o Safari esquece a
 * sessão do Google a cada login e a pessoa digita a senha toda vez. O ganho de privacidade não
 * compensa num aplicativo em que ela está justamente entrando na própria conta.
 *
 * ⚠️ **Não validado em host macOS.** Alvos Apple só compilam no Mac (ver `CLAUDE.md` da kmplib), e
 * este arquivo foi escrito no servidor Linux. O código segue as APIs oficiais e espelha o
 * `AppleAuthProvider.ios.kt`, mas a compilação e o teste em aparelho são do Mac.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SocialBrowserLogin actual constructor() {

    /** Mantém a sessão viva: solta antes do callback, o iOS a descarta e nada acontece. */
    private var sessao: ASWebAuthenticationSession? = null
    private var ancora: PresentationAnchor? = null

    actual suspend fun authenticate(startUrl: String, redirectScheme: String): String =
        suspendCancellableCoroutine { continuation ->
            val url = NSURL.URLWithString(startUrl)
            if (url == null) {
                continuation.resumeWithException(SocialBrowserException("URL de login inválida."))
                return@suspendCancellableCoroutine
            }

            val session = ASWebAuthenticationSession(
                uRL = url,
                callbackURLScheme = redirectScheme,
            ) { callbackUrl, erro ->
                when {
                    // O usuário fechou a folha do Safari. É cancelamento, não falha.
                    callbackUrl == null -> continuation.resumeWithException(
                        SocialBrowserException("Login cancelado.", reason = "cancelado"),
                    )
                    else -> {
                        val parametros = queryOf(callbackUrl)
                        val erroDoBackend = parametros["erro"]
                        val codigo = parametros["codigo"]
                        when {
                            erroDoBackend != null -> continuation.resumeWithException(
                                SocialBrowserException(mensagemDe(erroDoBackend), reason = erroDoBackend),
                            )
                            !codigo.isNullOrBlank() -> continuation.resume(codigo)
                            else -> continuation.resumeWithException(
                                SocialBrowserException("Retorno do login sem código.", reason = "falha"),
                            )
                        }
                    }
                }
                sessao = null
                ancora = null
            }

            val anchor = PresentationAnchor()
            ancora = anchor
            session.presentationContextProvider = anchor
            session.prefersEphemeralWebBrowserSession = false
            sessao = session

            continuation.invokeOnCancellation {
                session.cancel()
                sessao = null
                ancora = null
            }

            if (!session.start()) {
                sessao = null
                ancora = null
                continuation.resumeWithException(
                    SocialBrowserException("Não foi possível abrir o navegador para o login."),
                )
            }
        }

    /** De qual janela a folha do Safari sai. Sem isto o `ASWebAuthenticationSession` não abre. */
    private class PresentationAnchor :
        NSObject(),
        ASWebAuthenticationPresentationContextProvidingProtocol {
        override fun presentationAnchorForWebAuthenticationSession(
            session: ASWebAuthenticationSession,
        ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow ?: UIWindow()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun queryOf(url: NSURL): Map<String, String> {
    val componentes = NSURLComponents.componentsWithURL(url, resolvingAgainstBaseURL = false)
    val itens = componentes?.queryItems ?: return emptyMap()
    return buildMap {
        itens.forEach { item ->
            val nome = item.let { it as? platform.Foundation.NSURLQueryItem }?.name
            val valor = item.let { it as? platform.Foundation.NSURLQueryItem }?.value
            if (nome != null && valor != null) put(nome, valor)
        }
    }
}

internal fun mensagemDe(erro: String): String = when (erro) {
    "cancelado" -> "Login cancelado."
    "sessao_expirada" -> "A sessão de login expirou. Tente novamente."
    else -> "Não foi possível concluir o login. Tente novamente."
}

@OptIn(ExperimentalForeignApi::class)
actual object PkceCrypto {

    actual fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        memScoped {
            bytes.usePinned { pinned ->
                SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
            }
        }
        return bytes
    }

    actual fun sha256(input: ByteArray): ByteArray {
        val hash = UByteArray(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { entrada ->
            hash.usePinned { saida ->
                CC_SHA256(entrada.addressOf(0), input.size.convert(), saida.addressOf(0))
            }
        }
        return hash.toByteArray()
    }
}
