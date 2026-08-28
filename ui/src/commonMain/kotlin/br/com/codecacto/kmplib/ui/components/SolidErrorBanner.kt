package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Banner de erro sólido e dispensável.
 *
 * **Substituído por [AppBanner]** (2.88.0), que cobre os quatro tons do padrão do ecossistema
 * (info/sucesso/aviso/erro), tem título, ação e paridade com o `Banner` da weblib. Este composable
 * continua funcionando — agora delegando ao [AppBanner] — para não quebrar os consumidores atuais.
 *
 * **Correção de comportamento na 2.88.0 (proposital):** apesar do nome, esta função pintava
 * `errorContainer`/`onErrorContainer`, ou seja, um vermelho **claro** — exatamente o que o padrão
 * "erro = banner sólido" do ecossistema proíbe (e o motivo de o LocaSys ter mantido uma cópia local
 * *de verdade* sólida). Os defaults passaram a ser `error`/`onError`. Quem quiser o visual antigo
 * pode passar `backgroundColor`/`contentColor` explicitamente.
 *
 * @param message Mensagem de erro a exibir.
 * @param onDismiss Callback ao clicar no botão de fechar.
 * @param modifier Modifier opcional.
 * @param backgroundColor Cor de fundo (default: `error` do tema — sólido).
 * @param contentColor Cor do conteúdo (default: `onError` do tema).
 */
@Deprecated(
    message = "Use AppBanner(message, tone = StatusTone.DANGER, onDismiss = ...) — cobre os 4 tons, " +
        "título/ação e mantém a paridade com o Banner da weblib.",
    replaceWith = ReplaceWith(
        "AppBanner(message = message, tone = StatusTone.DANGER, onDismiss = onDismiss, modifier = modifier)",
    ),
)
@Composable
fun SolidErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError,
) {
    AppBanner(
        message = message,
        modifier = modifier,
        tone = StatusTone.DANGER,
        style = BannerStyle.SOLID,
        onDismiss = onDismiss,
        containerColor = backgroundColor,
        contentColor = contentColor,
    )
}
