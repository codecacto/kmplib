package br.com.codecacto.kmplib.ui.screens.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_about_developed_by
import br.com.codecacto.kmplib.generated.resources.kmplib_about_open_in_browser
import br.com.codecacto.kmplib.generated.resources.kmplib_about_privacy
import br.com.codecacto.kmplib.generated.resources.kmplib_about_terms
import br.com.codecacto.kmplib.generated.resources.kmplib_about_title
import br.com.codecacto.kmplib.generated.resources.kmplib_about_version
import br.com.codecacto.kmplib.generated.resources.kmplib_about_version_build
import br.com.codecacto.kmplib.platform.brand.rememberAppIconPainter
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.ui.components.AppBrandHeader
import br.com.codecacto.kmplib.ui.components.AppTopBar
import br.com.codecacto.kmplib.ui.components.NavigationType
import br.com.codecacto.kmplib.ui.locale.kmpGetString
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Textos da [AboutScreen]. Os defaults literais são pt-BR (teste/preview); na tela use
 * [rememberAboutTexts], que segue o idioma do aparelho (pt-BR / pt-PT / en / es).
 *
 * @property version "Versão 1.4.2" a partir da versão.
 * @property versionWithBuild "Versão 1.4.2 (37)" a partir da versão e do build.
 * @property openInBrowser rótulo da AÇÃO dos links legais para o leitor de tela ("abrir no navegador").
 */
data class AboutTexts(
    val title: String = "Sobre",
    val version: (String) -> String = { "Versão $it" },
    val versionWithBuild: (String, String) -> String = { v, b -> "Versão $v ($b)" },
    val terms: String = "Termos de uso",
    val privacy: String = "Política de privacidade",
    val developedBy: String = "Desenvolvido por",
    val openInBrowser: String = "abrir no navegador",
)

/** [AboutTexts] no idioma do aparelho. */
@Composable
fun rememberAboutTexts(): AboutTexts {
    // As duas frases com parâmetro são lidas na composição (o idioma pode mudar com o app aberto) e
    // viram lambdas puras — a regra de [aboutVersionLabel] continua testável sem tela.
    val versionPattern = kmpStringResource(Res.string.kmplib_about_version, ARG_1)
    val versionBuildPattern = kmpStringResource(Res.string.kmplib_about_version_build, ARG_1, ARG_2)
    return AboutTexts(
        title = kmpStringResource(Res.string.kmplib_about_title),
        version = { v -> versionPattern.replace(ARG_1, v) },
        versionWithBuild = { v, b -> versionBuildPattern.replace(ARG_1, v).replace(ARG_2, b) },
        terms = kmpStringResource(Res.string.kmplib_about_terms),
        privacy = kmpStringResource(Res.string.kmplib_about_privacy),
        developedBy = kmpStringResource(Res.string.kmplib_about_developed_by),
        openInBrowser = kmpStringResource(Res.string.kmplib_about_open_in_browser),
    )
}

/** [AboutTexts] fora de composição (ViewModel, teste), no idioma do aparelho. */
suspend fun loadAboutTexts(): AboutTexts {
    val versionPattern = kmpGetString(Res.string.kmplib_about_version, ARG_1)
    val versionBuildPattern = kmpGetString(Res.string.kmplib_about_version_build, ARG_1, ARG_2)
    return AboutTexts(
        title = kmpGetString(Res.string.kmplib_about_title),
        version = { v -> versionPattern.replace(ARG_1, v) },
        versionWithBuild = { v, b -> versionBuildPattern.replace(ARG_1, v).replace(ARG_2, b) },
        terms = kmpGetString(Res.string.kmplib_about_terms),
        privacy = kmpGetString(Res.string.kmplib_about_privacy),
        developedBy = kmpGetString(Res.string.kmplib_about_developed_by),
        openInBrowser = kmpGetString(Res.string.kmplib_about_open_in_browser),
    )
}

// Marcadores improváveis em texto real: o recurso é formatado com eles e depois substituído.
private const val ARG_1 = "\u0001v\u0001"
private const val ARG_2 = "\u0001b\u0001"

/**
 * A linha de versão da tela Sobre. Regra pura:
 * - sem versão legível → `null` (a linha não aparece — "Versão " solto é pior que nada);
 * - com [showBuildNumber] e build legível → "Versão 1.4.2 (37)";
 * - senão → "Versão 1.4.2".
 */
fun aboutVersionLabel(
    versionName: String?,
    buildNumber: String?,
    showBuildNumber: Boolean,
    texts: AboutTexts,
): String? {
    val v = versionName?.trim().orEmpty()
    if (v.isEmpty()) return null
    val b = buildNumber?.trim().orEmpty()
    return if (showBuildNumber && b.isNotEmpty()) texts.versionWithBuild(v, b) else texts.version(v)
}

/**
 * Ids da [AboutScreen] para os flows Maestro (vocabulário `tela-elemento-acao`). Estáveis.
 * O voltar é o `topbar-voltar` da `AppTopBar`.
 */
object AboutTestTags {
    const val TELA: String = "sobre-tela"
    const val VERSAO: String = "sobre-versao"
    const val BTN_TERMOS: String = "sobre-btn-termos"
    const val BTN_PRIVACIDADE: String = "sobre-btn-privacidade"
    const val BTN_DESENVOLVIDO_POR: String = "sobre-btn-desenvolvido-por"

    val all: List<String> = listOf(TELA, VERSAO, BTN_TERMOS, BTN_PRIVACIDADE, BTN_DESENVOLVIDO_POR)
}

/**
 * Tela **Sobre** — padrão da fábrica para TODO app (fundador, 13/out/2026): **ícone do app, nome,
 * versão, Termos de uso, Política de privacidade e "Desenvolvido por"** — e só.
 *
 * Os três últimos **moram aqui dentro, nunca soltos no menu principal**. O único item que fica fora,
 * visível no menu, é o **Enviar feedback** (logo acima de "Sobre"). Nada de explicar arquitetura
 * ("100% offline", "seus dados ficam no aparelho") nesta tela — isso é texto de loja/site.
 *
 * O app só configura: ícone, nome e versão vêm do SISTEMA por default ([rememberAppIconPainter],
 * [BuildInfo.appName], [BuildInfo.appVersionName]) — saem certos em todo flavor sem repasse. Os links
 * legais abrem no **navegador** (`UrlLauncher`; em app infantil, atrás do portão de pais), nunca em
 * WebView. "Desenvolvido por" chama [onDevelopedByClick] — o app navega até a `DeveloperScreen`
 * (`kmplib-central`), que esta tela não importa para não obrigar o artefato.
 *
 * ```kotlin
 * composable<Route.About> {
 *     AboutScreen(
 *         onBack = { navController.popBackStack() },
 *         termsUrl = AppConfig.termsUrl,
 *         privacyUrl = AppConfig.privacyUrl,
 *         onDevelopedByClick = { navController.navigate(Route.Developer) },
 *     )
 * }
 * ```
 *
 * @param onBack voltar (seta da `AppTopBar`, id `topbar-voltar`).
 * @param termsUrl URL dos Termos de uso (a mesma do site). Em branco = a linha não aparece.
 * @param privacyUrl URL da Política de privacidade. Em branco = a linha não aparece.
 * @param onDevelopedByClick abre a tela "Desenvolvido por CodeCacto" (`DeveloperScreen`).
 * @param appName nome do app. Default: o rótulo que o sistema mostra.
 * @param appIcon ícone. Default: o do launcher/bundle; `null` = só o nome.
 * @param versionName versão exibida. Default: `versionName`/`CFBundleShortVersionString`.
 * @param buildNumber número do build. Default: `versionCode`/`CFBundleVersion`.
 * @param showBuildNumber mostra o build entre parênteses ("Versão 1.4.2 (37)") — útil em homologação.
 * @param onOpenUrl como abrir um link legal. Default: `getUrlLauncher().openUrl`, resolvido no toque.
 * @param bottomBar rodapé fixo (ex.: banner de house ad) — o conteúdo desconta a altura dele.
 * @param texts textos; default no idioma do aparelho.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    termsUrl: String,
    privacyUrl: String,
    onDevelopedByClick: () -> Unit,
    modifier: Modifier = Modifier,
    appName: String? = BuildInfo.appName,
    appIcon: Painter? = rememberAppIconPainter(),
    versionName: String? = BuildInfo.appVersionName,
    buildNumber: String? = BuildInfo.appBuildNumber,
    showBuildNumber: Boolean = false,
    onOpenUrl: (String) -> Unit = ::openAboutUrl,
    bottomBar: @Composable () -> Unit = {},
    texts: AboutTexts = rememberAboutTexts(),
) {
    val versionLabel = aboutVersionLabel(versionName, buildNumber, showBuildNumber, texts)

    Scaffold(
        modifier = modifier.testTag(AboutTestTags.TELA),
        topBar = {
            AppTopBar(
                title = texts.title,
                navigationType = NavigationType.BACK,
                onNavigationClick = onBack,
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ABOUT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(PaddingValues(horizontal = 16.dp, vertical = 32.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppBrandHeader(appName = appName, icon = appIcon)
                if (versionLabel != null) {
                    Text(
                        text = versionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag(AboutTestTags.VERSAO),
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) {
                    Column {
                        val rows = buildList {
                            if (termsUrl.isNotBlank()) {
                                add(AboutRow(texts.terms, AboutTestTags.BTN_TERMOS, external = true) { onOpenUrl(termsUrl) })
                            }
                            if (privacyUrl.isNotBlank()) {
                                add(AboutRow(texts.privacy, AboutTestTags.BTN_PRIVACIDADE, external = true) { onOpenUrl(privacyUrl) })
                            }
                            add(AboutRow(texts.developedBy, AboutTestTags.BTN_DESENVOLVIDO_POR, external = false, onDevelopedByClick))
                        }
                        rows.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            AboutListItem(row, texts)
                        }
                    }
                }
            }
        }
    }
}

private class AboutRow(
    val label: String,
    val testTag: String,
    val external: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun AboutListItem(row: AboutRow, texts: AboutTexts) {
    val icon: ImageVector =
        if (row.external) Icons.AutoMirrored.Outlined.OpenInNew else Icons.AutoMirrored.Filled.KeyboardArrowRight
    ListItem(
        headlineContent = { Text(row.label) },
        // Decorativo: o rótulo da ação ("abrir no navegador") já vai no clique para o leitor de tela.
        trailingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .clickable(
                role = Role.Button,
                onClickLabel = if (row.external) texts.openInBrowser else null,
                onClick = row.onClick,
            )
            .testTag(row.testTag),
    )
}

/** Abre [url] no navegador; o `UrlLauncher` é resolvido no toque (init ausente não derruba a tela). */
private fun openAboutUrl(url: String) {
    runCatching { getUrlLauncher().openUrl(url) }
        .onFailure { AppLogger.e(ABOUT_TAG, "não foi possível abrir o link legal", it) }
}

private val ABOUT_MAX_WIDTH = 560.dp
private const val ABOUT_TAG = "AboutScreen"
