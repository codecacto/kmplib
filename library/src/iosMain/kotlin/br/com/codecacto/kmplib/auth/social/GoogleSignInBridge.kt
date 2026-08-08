@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package br.com.codecacto.kmplib.auth.social

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.native.ObjCName

/**
 * Bridge do **Google Sign-In no iOS**: o Kotlin define o contrato e espera o resultado; o Swift
 * executa o fluxo com o SDK oficial **GoogleSignIn-iOS** (via SPM). Mesmo padrão do
 * [ApplePushBridge][br.com.codecacto.kmplib.push.ApplePushBridge] (2.76.0).
 *
 * **Por que bridge, e não Kotlin/Native puro:** o GoogleSignIn-iOS é distribuído como pacote
 * Swift/ObjC e exige um `UIViewController` apresentador; consumi-lo por cinterop dentro da lib
 * obrigaria todo app consumidor — inclusive os que não usam login social — a linkar o SDK. O padrão
 * da casa é o Kotlin declarar o contrato e o app plugar o SDK nativo. **Não há reimplementação do
 * fluxo OAuth à mão** (que seria o atalho errado).
 *
 * ### Passo a passo no app iOS (Swift)
 *
 * **1) SPM:** adicionar `https://github.com/google/GoogleSignIn-iOS` ao target do app.
 *
 * **2) `Info.plist`:** incluir o *reversed client ID* do **client ID de iOS** em `CFBundleURLTypes`
 * (`URL Schemes`), conforme a documentação do Google.
 *
 * **3) Registrar o executor no start do app** (`@main` App / `AppDelegate`):
 * ```swift
 * import KmpLib
 * import GoogleSignIn
 *
 * GoogleSignInBridge.shared.setSignInStarter { serverClientId, nonce in
 *     guard let root = UIApplication.shared.connectedScenes
 *         .compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController })
 *         .first else {
 *         GoogleSignInBridge.shared.onSignInFailure(message: "Sem view controller para apresentar")
 *         return
 *     }
 *     // clientID = client ID de iOS (do GoogleService/console);
 *     // serverClientID = client ID WEB — é ele que vira o `aud` do idToken que o backend confere.
 *     GIDSignIn.sharedInstance.configuration = GIDConfiguration(
 *         clientID: Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as! String,
 *         serverClientID: serverClientId
 *     )
 *     GIDSignIn.sharedInstance.signIn(withPresenting: root, hint: nil,
 *                                     additionalScopes: nil, nonce: nonce) { result, error in
 *         if let error = error as NSError? {
 *             if error.code == GIDSignInError.canceled.rawValue {
 *                 GoogleSignInBridge.shared.onSignInCancelled()
 *             } else {
 *                 GoogleSignInBridge.shared.onSignInFailure(message: error.localizedDescription)
 *             }
 *             return
 *         }
 *         guard let user = result?.user, let idToken = user.idToken?.tokenString else {
 *             GoogleSignInBridge.shared.onSignInFailure(message: "Google não devolveu idToken")
 *             return
 *         }
 *         GoogleSignInBridge.shared.onSignInSuccess(
 *             idToken: idToken,
 *             email: user.profile?.email,
 *             displayName: user.profile?.name,
 *             photoUrl: user.profile?.imageURL(withDimension: 200)?.absoluteString
 *         )
 *     }
 * }
 * ```
 *
 * **4) Abrir a URL de retorno:**
 * ```swift
 * .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }   // SwiftUI
 * // ou application(_:open:options:) no AppDelegate
 * ```
 *
 * ### Contrato
 * - Exatamente **um** dos três callbacks ([onSignInSuccess]/[onSignInFailure]/[onSignInCancelled])
 *   deve ser chamado por fluxo iniciado. Um callback tardio, depois do fluxo já concluído, é
 *   **ignorado** (só loga) — nunca corrompe a próxima tentativa.
 * - Fluxos são **serializados** (`Mutex`): dois toques no botão não disparam duas sessões.
 * - **`accessToken` não é repassado de propósito** — ele não é prova de identidade
 *   (ver [GoogleSignInResult]).
 * - Sem [setSignInStarter], [GoogleAuthProvider] devolve erro explicando exatamente o que falta.
 *   **Nunca** silêncio.
 */
@ObjCName("GoogleSignInBridge")
object GoogleSignInBridge {

    private val mutex = Mutex()
    private var starter: ((String, String?) -> Unit)? = null
    private var pending: CompletableDeferred<GoogleSignInResult>? = null

    /** `true` quando o Swift já registrou o executor ([setSignInStarter]). */
    val isConfigured: Boolean get() = starter != null

    /**
     * Registra o bloco Swift que dispara o `GIDSignIn`. Recebe o **server client ID** (o client ID
     * do tipo Web, que vira o `aud` do `idToken`) e o **nonce** (nulo quando o chamador não usa
     * nonce — caso Firebase).
     */
    fun setSignInStarter(starter: (serverClientId: String, nonce: String?) -> Unit) {
        this.starter = starter
    }

    /** Chamado pelo Swift quando o Google devolveu o `idToken`. */
    fun onSignInSuccess(
        idToken: String,
        email: String? = null,
        displayName: String? = null,
        photoUrl: String? = null,
    ) {
        complete(
            GoogleSignInResult(
                idToken = idToken,
                accessToken = null,
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
            )
        )
    }

    /** Chamado pelo Swift quando o fluxo falhou (erro do SDK, sem view controller, etc.). */
    fun onSignInFailure(message: String) {
        complete(GoogleSignInResult.error(message))
    }

    /** Chamado pelo Swift quando o usuário desistiu (`GIDSignInError.canceled`). */
    fun onSignInCancelled() {
        complete(GoogleSignInResult.cancelled())
    }

    /** Inicia o fluxo e suspende até o Swift responder. Usado pelo [GoogleAuthProvider] do iOS. */
    internal suspend fun awaitSignIn(serverClientId: String, nonce: String?): GoogleSignInResult {
        val start = starter ?: return GoogleSignInResult.error(NOT_CONFIGURED)
        return mutex.withLock {
            val deferred = CompletableDeferred<GoogleSignInResult>()
            pending = deferred
            try {
                start(serverClientId, nonce)
            } catch (e: Throwable) {
                pending = null
                return@withLock GoogleSignInResult.error(e.message ?: "Falha ao iniciar o login com Google")
            }
            val result = deferred.await()
            pending = null
            result
        }
    }

    private fun complete(result: GoogleSignInResult) {
        val deferred = pending
        if (deferred == null || deferred.isCompleted) {
            AppLogger.w(TAG, "Callback do Google Sign-In sem fluxo pendente — ignorado.")
            return
        }
        deferred.complete(result)
    }

    private const val TAG = "GoogleSignInBridge"

    internal const val NOT_CONFIGURED: String =
        "GoogleSignInBridge sem executor. Chame GoogleSignInBridge.shared.setSignInStarter { ... } " +
            "no start do app iOS (ver o KDoc de GoogleSignInBridge)."
}
