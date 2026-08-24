package br.com.codecacto.kmplib.auth.social

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import java.security.MessageDigest
import java.security.SecureRandom

/** O login em andamento. Um por vez, por construção — ver [SocialBrowserLogin.authenticate]. */
internal object SocialBrowserLoginState {
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

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            SocialBrowserLoginState.pendente = null
            throw SocialBrowserException("Nenhum navegador disponível para concluir o login.")
        }

        return try {
            aguardando.await()
        } finally {
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
     * Chame ao descartar a tela de login: sem isto, quem fecha a aba do navegador com o gesto de
     * voltar deixa a corrotina suspensa até a tela morrer.
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
