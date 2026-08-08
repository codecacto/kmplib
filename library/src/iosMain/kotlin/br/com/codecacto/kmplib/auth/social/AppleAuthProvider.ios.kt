package br.com.codecacto.kmplib.auth.social

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationErrorFailed
import platform.AuthenticationServices.ASAuthorizationErrorInvalidResponse
import platform.AuthenticationServices.ASAuthorizationErrorNotHandled
import platform.AuthenticationServices.ASAuthorizationErrorUnknown
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Sign in with Apple via `ASAuthorizationController` (AuthenticationServices) — API oficial da
 * Apple, **sem Firebase**.
 *
 * O nonce enviado à Apple é sempre o **SHA-256 hex** do valor cru; o valor cru volta em
 * [AppleSignInResult.nonce] para o verificador (backend own-auth ou Firebase) refazer o hash.
 */
@OptIn(ExperimentalForeignApi::class)
actual class AppleAuthProvider actual constructor() {

    private var currentDelegate: NSObject? = null
    private var currentController: ASAuthorizationController? = null

    actual suspend fun signIn(): AppleSignInResult = perform(generateNonce())

    actual suspend fun signIn(nonce: String): AppleSignInResult =
        perform(nonce.takeIf { it.isNotBlank() } ?: generateNonce())

    private suspend fun perform(rawNonce: String): AppleSignInResult =
        suspendCancellableCoroutine { continuation ->
            val hashedNonce = sha256(rawNonce)

            val request = ASAuthorizationAppleIDProvider().createRequest().apply {
                requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
                this.nonce = hashedNonce
            }

            val controller = ASAuthorizationController(listOf(request))
            currentController = controller

            val delegate = object : NSObject(), ASAuthorizationControllerDelegateProtocol,
                ASAuthorizationControllerPresentationContextProvidingProtocol {

                override fun authorizationController(
                    controller: ASAuthorizationController,
                    didCompleteWithAuthorization: ASAuthorization
                ) {
                    val credential =
                        didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
                    if (credential == null) {
                        cleanup()
                        continuation.resume(AppleSignInResult.error("Credencial inválida"))
                        return
                    }

                    val identityToken = credential.identityToken?.let { data ->
                        NSString.create(data, NSUTF8StringEncoding)?.toString()
                    }

                    val fullName = credential.fullName?.let { name ->
                        listOfNotNull(name.givenName, name.familyName).joinToString(" ")
                    }

                    cleanup()

                    if (identityToken != null) {
                        continuation.resume(
                            AppleSignInResult(
                                idToken = identityToken,
                                // Valor CRU: o servidor refaz o SHA-256 e compara com a claim.
                                nonce = rawNonce,
                                fullName = fullName?.takeIf { it.isNotBlank() },
                                email = credential.email,
                                error = null
                            )
                        )
                    } else {
                        continuation.resume(AppleSignInResult.error("Falha ao obter token da Apple"))
                    }
                }

                override fun authorizationController(
                    controller: ASAuthorizationController,
                    didCompleteWithError: NSError
                ) {
                    val errorMessage = when (didCompleteWithError.code) {
                        ASAuthorizationErrorCanceled -> "Login cancelado"
                        ASAuthorizationErrorFailed -> "Falha no login"
                        ASAuthorizationErrorInvalidResponse -> "Resposta inválida"
                        ASAuthorizationErrorNotHandled -> "Requisição não tratada"
                        ASAuthorizationErrorUnknown -> "Erro desconhecido"
                        else -> didCompleteWithError.localizedDescription
                    }

                    cleanup()

                    continuation.resume(AppleSignInResult.error(errorMessage))
                }

                override fun presentationAnchorForAuthorizationController(
                    controller: ASAuthorizationController
                ): ASPresentationAnchor {
                    return UIApplication.sharedApplication.keyWindow!!
                }
            }

            currentDelegate = delegate

            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            controller.performRequests()

            continuation.invokeOnCancellation {
                cleanup()
            }
        }

    private fun cleanup() {
        currentDelegate = null
        currentController = null
    }

    private fun generateNonce(length: Int = 32): String {
        val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._"
        val randomBytes = ByteArray(length)

        memScoped {
            randomBytes.usePinned { pinned ->
                SecRandomCopyBytes(kSecRandomDefault, length.toULong(), pinned.addressOf(0))
            }
        }

        return randomBytes.map { byte ->
            charset[(byte.toInt() and 0xFF) % charset.length]
        }.joinToString("")
    }

    private fun sha256(input: String): String {
        val data = input.encodeToByteArray()
        val hash = UByteArray(CC_SHA256_DIGEST_LENGTH)

        data.usePinned { inputPinned ->
            hash.usePinned { hashPinned ->
                CC_SHA256(inputPinned.addressOf(0), data.size.convert(), hashPinned.addressOf(0))
            }
        }

        return hash.joinToString("") { byte ->
            byte.toInt().toString(16).padStart(2, '0')
        }
    }
}
