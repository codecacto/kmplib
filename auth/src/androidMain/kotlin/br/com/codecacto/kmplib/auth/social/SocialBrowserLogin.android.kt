package br.com.codecacto.kmplib.auth.social

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import java.security.MessageDigest
import java.security.SecureRandom

/** O login em andamento. Um por vez, por construção — ver [SocialBrowserLogin.authenticate]. */
internal object SocialBrowserLoginState {
    // Escrito pela corrotina do login e lido na thread principal (redirect, vigia de retorno).
    @Volatile
    var pendente: CompletableDeferred<String>? = null
}

/**
 * Login social pelo navegador do sistema, no Android.
 *
 * ## O que o aplicativo precisa declarar
 * Uma Activity que receba o *deep link* de volta e entregue a URI a
 * [SocialBrowserRedirect.handleRedirect]:
 *
 * ```xml
 * <activity android:name=".AuthCallbackActivity"
 *           android:exported="true"
 *           android:launchMode="singleTask"
 *           android:noHistory="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="brcodecacto.inssnegou" android:host="auth" />
 *     </intent-filter>
 * </activity>
 * ```
 *
 * ```kotlin
 * class AuthCallbackActivity : Activity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         intent?.data?.let { SocialBrowserRedirect.handleRedirect(it.toString()) }
 *         finish()
 *     }
 * }
 * ```
 *
 * `launchMode="singleTask"` e `noHistory="true"` não são enfeite: sem eles a volta do navegador
 * empilha uma segunda instância da tela, e o gesto de voltar joga a pessoa de novo para dentro do
 * login que ela acabou de concluir.
 *
 * ## Por que o navegador, e não uma WebView
 * A WebView é do aplicativo: ela enxerga o que a pessoa digita, não compartilha a sessão do
 * navegador (obrigando a digitar a senha do Google toda vez) e é recusada pelos provedores. A RFC
 * 8252 pede o navegador do sistema — no Android, uma Custom Tab, que o usa sem tirar a pessoa do
 * aplicativo. Sem navegador com suporte a Custom Tabs, o mesmo `Intent` abre o navegador comum e o
 * fluxo funciona igual.
 *
 * ## Fechar a aba sem concluir = cancelado
 * O navegador não avisa quando a pessoa desiste. Quem percebe é o próprio aplicativo: uma tela dele
 * voltar à frente com o pedido ainda sem resposta encerra o login como **cancelado**
 * ([SocialBrowserException] com `reason = "cancelado"`), depois de uma folga curta para o *deep link*
 * que chega junto com a volta ([BrowserReturnWatch] — a regra do AppAuth-Android). Não depende de o
 * aplicativo chamar nada. O *deep link* que chegar DEPOIS do cancelamento é ignorado: não há mais
 * pedido para ele completar, e o código que ele traz não vale sem o `verifier` que foi descartado.
 * A exceção é a tela dividida, onde o navegador pode estar vivo ao lado: ali vale só o
 * [SocialBrowserRedirect.cancel].
 */
actual class SocialBrowserLogin actual constructor() {

    actual suspend fun authenticate(startUrl: String, redirectScheme: String): String {
        val activity = GoogleAuthHolder.getActivity()
        val context = activity ?: GoogleAuthHolder.getContext()
            ?: throw SocialBrowserException(
                "Contexto do Android indisponível: chame KmpLib.init/setActivity antes do login.",
            )

        val aguardando = CompletableDeferred<String>()
        // Um login por vez: um segundo toque no botão enquanto o navegador está aberto substituiria
        // o pedido e deixaria a primeira corrotina suspensa para sempre.
        SocialBrowserLoginState.pendente?.completeExceptionally(
            SocialBrowserException("Login substituído por outro.", reason = "cancelado"),
        )
        SocialBrowserLoginState.pendente = aguardando

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(startUrl)).apply {
            if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Extras de Custom Tabs: sem a dependência androidx.browser, o navegador que as suporta
            // as lê do próprio Intent; quem não suporta ignora e abre normalmente.
            putExtra("android.support.customtabs.extra.SHARE_STATE", 2 /* SHARE_STATE_OFF */)
        }

        // Registrado ANTES de abrir o navegador: é a pausa da tela, logo em seguida, que arma o vigia.
        val vigia = (context.applicationContext as? Application)?.let { app ->
            BrowserReturnWatchRegistration(
                application = app,
                isPending = { SocialBrowserLoginState.pendente === aguardando && !aguardando.isCompleted },
                onAbandoned = {
                    if (SocialBrowserLoginState.pendente === aguardando) SocialBrowserLoginState.pendente = null
                    aguardando.completeExceptionally(
                        SocialBrowserException("Login cancelado.", reason = "cancelado"),
                    )
                },
            ).also { it.register() }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Qualquer falha ao abrir (não só a falta de navegador) desfaz o pedido: sem isto o vigia
            // ficaria registrado e o próximo deep link completaria um login que nunca abriu.
            vigia?.unregister()
            if (SocialBrowserLoginState.pendente === aguardando) SocialBrowserLoginState.pendente = null
            if (e is ActivityNotFoundException) {
                throw SocialBrowserException("Nenhum navegador disponível para concluir o login.")
            }
            throw e
        }

        return try {
            aguardando.await()
        } finally {
            vigia?.unregister()
            if (SocialBrowserLoginState.pendente === aguardando) SocialBrowserLoginState.pendente = null
        }
    }
}

/**
 * Ponte entre a Activity de callback do aplicativo e o login suspenso.
 *
 * Existe só no Android: no iOS o `ASWebAuthenticationSession` devolve a URL de volta a quem o abriu,
 * sem precisar de Activity nenhuma.
 */
object SocialBrowserRedirect {

    /**
     * Entrega ao fluxo suspenso a URI do *deep link* de volta.
     *
     * Ignora URI quando não há login em andamento — o mesmo esquema pode ser aberto por outro
     * caminho, e completar um fluxo que ninguém pediu seria pior do que não fazer nada.
     */
    fun handleRedirect(uri: String) {
        val alvo = SocialBrowserLoginState.pendente ?: return
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return
        val erro = parsed.getQueryParameter("erro")
        val codigo = parsed.getQueryParameter("codigo")
        when {
            erro != null -> alvo.completeExceptionally(SocialBrowserException(mensagemDe(erro), reason = erro))
            !codigo.isNullOrBlank() -> alvo.complete(codigo)
            else -> alvo.completeExceptionally(
                SocialBrowserException("Retorno do login sem código.", reason = "falha"),
            )
        }
        SocialBrowserLoginState.pendente = null
    }

    /**
     * Cancela um login em andamento.
     *
     * Desde a 2.241.1 **não é preciso chamar** para o caso comum: fechar a aba e voltar ao aplicativo
     * já encerra o login como cancelado, sozinho (ver [SocialBrowserLogin]). Continua útil para
     * cancelar por decisão do aplicativo — sair da tela de login com o navegador ainda aberto, ou em
     * tela dividida, onde o cancelamento automático não age.
     */
    fun cancel() {
        SocialBrowserLoginState.pendente?.completeExceptionally(
            SocialBrowserException("Login cancelado.", reason = "cancelado"),
        )
        SocialBrowserLoginState.pendente = null
    }
}

internal fun mensagemDe(erro: String): String = when (erro) {
    "cancelado" -> "Login cancelado."
    "sessao_expirada" -> "A sessão de login expirou. Tente novamente."
    else -> "Não foi possível concluir o login. Tente novamente."
}

actual object PkceCrypto {
    private val rng = SecureRandom()

    actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    actual fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)
}
