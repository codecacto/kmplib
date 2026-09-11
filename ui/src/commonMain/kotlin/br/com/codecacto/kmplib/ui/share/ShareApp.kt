package br.com.codecacto.kmplib.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_share_app_label
import br.com.codecacto.kmplib.generated.resources.kmplib_share_app_message
import br.com.codecacto.kmplib.platform.AppShareLink
import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler
import org.jetbrains.compose.resources.stringResource

/**
 * `utm_content` default da entrada "Compartilhar app" do menu. Outras telas passam o próprio
 * (`resultado`, `receita`…) — é o que permite ao relatório dizer DE ONDE no app veio a visita.
 */
const val SHARE_APP_CONTENT_MENU: String = "menu"

/**
 * Textos do "Compartilhar app".
 *
 * Os defaults literais são pt-BR e existem para quem monta o objeto fora de composição (teste,
 * preview). Na tela, use [rememberShareAppTexts], que lê os Compose Resources da lib no **idioma do
 * aparelho** (pt-BR / pt-PT / en / es) — sem seletor, sem trabalho do app.
 *
 * @property label rótulo da entrada no menu ("Compartilhar app").
 * @property message texto que antecede o link na mensagem enviada. Em branco = só o link.
 * @property title título do compartilhamento — no Android, a prévia no topo da folha e o assunto do
 *   e-mail. O natural é o nome do app.
 */
data class ShareAppTexts(
    val label: String = "Compartilhar app",
    val message: String = "",
    val title: String = "",
)

/** [ShareAppTexts] no idioma do aparelho, com [appName] na mensagem e no título. */
@Composable
fun rememberShareAppTexts(appName: String): ShareAppTexts = ShareAppTexts(
    label = stringResource(Res.string.kmplib_share_app_label),
    message = stringResource(Res.string.kmplib_share_app_message, appName),
    title = appName,
)

/**
 * Abre o share sheet nativo com a mensagem de [texts] e o link do app **com UTM** (
 * `utm_source=app`, `utm_medium=share`, `utm_campaign=<slug>`, `utm_content=`[content]).
 *
 * É a regra pura por trás de [ShareAppMenuItem] e [rememberShareApp] — o que se testa sem tela.
 *
 * @throws Exception se o compartilhamento não puder ser iniciado (contrato do [ShareHandler]).
 */
fun ShareHandler.shareApp(link: AppShareLink, texts: ShareAppTexts, content: String? = null) {
    shareLink(url = link.url(content), message = texts.message, title = texts.title)
}

/**
 * Ação "compartilhar o app" pronta para qualquer gatilho da tela — botão próprio, item de menu de
 * sobrecarga, ação da top bar. Para a entrada padrão do menu, prefira [ShareAppMenuItem].
 *
 * O `ShareHandler` é resolvido **no toque**, não na composição: no Android ele depende do
 * `initKmpLibPlatform`, e resolvê-lo cedo derrubaria a tela inteira se o app esqueceu o init — em
 * vez de só o compartilhamento falhar, com o erro passando por [onError].
 *
 * @param onError recebe a falha (sem janela para apresentar, init ausente…). Já é logada pela lib;
 *   o app decide se mostra algo.
 */
@Composable
fun rememberShareApp(
    link: AppShareLink,
    texts: ShareAppTexts,
    content: String? = null,
    onError: (Throwable) -> Unit = {},
): () -> Unit {
    val currentTexts by rememberUpdatedState(texts)
    val currentOnError by rememberUpdatedState(onError)
    return remember(link, content) {
        {
            runCatching { getShareHandler().shareApp(link, currentTexts, content) }
                .onFailure { error ->
                    AppLogger.e(SHARE_APP_TAG, "não foi possível abrir o compartilhamento do app", error)
                    currentOnError(error)
                }
        }
    }
}

/**
 * Entrada padrão **"Compartilhar app"** para a tela de menu/configurações — ícone de
 * compartilhamento, rótulo no idioma do aparelho, e o toque abre o share sheet NATIVO (Android
 * `ACTION_SEND` + chooser; iOS `UIActivityViewController`) com a mensagem e o link do site do
 * produto, **com UTM**. Todo produto da fábrica deveria tê-la: é o único canal de aquisição que
 * cresce com o uso, e sem UTM a visita que ele gera chega ao site sem origem.
 *
 * ```kotlin
 * val link = remember { AppShareLink(landingUrl = AppConfig.siteBaseUrl, campaign = AppConfig.projectSlug) }
 * ShareAppMenuItem(appName = AppConfig.appName, link = link)
 * ```
 *
 * @param appName nome do app — vai na mensagem ("Estou usando o app X…") e no título.
 * @param link o link do site do produto + slug (ver [AppShareLink]).
 * @param content `utm_content`; default [SHARE_APP_CONTENT_MENU].
 * @param texts sobrescreve rótulo/mensagem/título; default no idioma do aparelho.
 */
@Composable
fun ShareAppMenuItem(
    appName: String,
    link: AppShareLink,
    modifier: Modifier = Modifier,
    content: String? = SHARE_APP_CONTENT_MENU,
    texts: ShareAppTexts = rememberShareAppTexts(appName),
    onError: (Throwable) -> Unit = {},
) {
    val share = rememberShareApp(link = link, texts = texts, content = content, onError = onError)
    ListItem(
        headlineContent = { Text(texts.label) },
        // Decorativo: o rótulo ao lado já diz o que a entrada faz.
        leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
        modifier = modifier.clickable(role = Role.Button, onClick = share),
    )
}

private const val SHARE_APP_TAG = "ShareApp"
