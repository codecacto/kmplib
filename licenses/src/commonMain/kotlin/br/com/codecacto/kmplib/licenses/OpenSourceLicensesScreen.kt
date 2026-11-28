package br.com.codecacto.kmplib.licenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_about_open_source_licenses
import br.com.codecacto.kmplib.generated.resources.kmplib_licenses_close
import br.com.codecacto.kmplib.generated.resources.kmplib_licenses_unavailable
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.ui.components.AppAlertDialog
import br.com.codecacto.kmplib.ui.components.AppTopBar
import br.com.codecacto.kmplib.ui.components.EmptyState
import br.com.codecacto.kmplib.ui.components.NavigationType
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Onde o plugin `br.com.codecacto.kmplib.licenses` grava o arquivo, relativo ao `composeResources` do app. */
const val OPEN_SOURCE_LICENSES_RESOURCE_PATH: String = "files/aboutlibraries.json"

/** Textos da [OpenSourceLicensesScreen]; default no idioma da tela ([rememberOpenSourceLicensesTexts]). */
data class OpenSourceLicensesTexts(
    val title: String = "Licenças de código aberto",
    val unavailable: String = "A lista de licenças não está disponível nesta versão.",
    val close: String = "Fechar",
)

/** [OpenSourceLicensesTexts] no idioma da tela (pt-BR / pt-PT / en / es). */
@Composable
fun rememberOpenSourceLicensesTexts(): OpenSourceLicensesTexts = OpenSourceLicensesTexts(
    title = kmpStringResource(Res.string.kmplib_about_open_source_licenses),
    unavailable = kmpStringResource(Res.string.kmplib_licenses_unavailable),
    close = kmpStringResource(Res.string.kmplib_licenses_close),
)

/** Ids para os flows Maestro. O voltar é o `topbar-voltar` da `AppTopBar`. */
object OpenSourceLicensesTestTags {
    const val TELA: String = "licencas-tela"
    const val LISTA: String = "licencas-lista"
    const val CARREGANDO: String = "licencas-carregando"
    const val INDISPONIVEL: String = "licencas-indisponivel"
    const val TEXTO_DA_LICENCA: String = "licencas-texto"

    val all: List<String> = listOf(TELA, LISTA, CARREGANDO, INDISPONIVEL, TEXTO_DA_LICENCA)
}

/** Estado da carga da lista (puro, para teste). */
sealed interface OpenSourceLicensesLoad {
    data object Loading : OpenSourceLicensesLoad
    /** O app não gerou o arquivo (plugin não aplicado) ou ele não é legível. */
    data object Unavailable : OpenSourceLicensesLoad
    data class Ready(val libraries: Libs) : OpenSourceLicensesLoad
}

/**
 * Lê o JSON gerado pelo AboutLibraries. `null` quando está em branco, não é o formato do plugin ou não
 * lista biblioteca nenhuma (o parser do AboutLibraries devolve lista vazia para JSON quebrado) — a tela
 * mostra "não está disponível", nunca derruba o app.
 */
fun parseOpenSourceLicenses(json: String?): Libs? {
    if (json.isNullOrBlank()) return null
    return runCatching { Libs.Builder().withJson(json).build() }
        .onFailure { AppLogger.w(TAG, "aboutlibraries.json ilegível: ${it::class.simpleName}") }
        .getOrNull()
        ?.takeIf { it.libraries.isNotEmpty() }
}

/** O texto da licença para o diálogo: as licenças da biblioteca, uma depois da outra, sem HTML de quebra. */
fun licenseTextOf(library: Library): String = library.licenses
    .mapNotNull { license -> license.licenseContent?.takeIf(String::isNotBlank) }
    .joinToString("\n\n\n") { it.replace("<br />", "\n").replace("<br/>", "\n").trim() }

/** Para onde o toque leva quando não há texto embutido: a URL da licença, senão o site da biblioteca. */
fun licenseUrlOf(library: Library): String? =
    library.licenses.firstNotNullOfOrNull { it.url?.takeIf(::isHttpUrl) }
        ?: library.website?.takeIf(::isHttpUrl)

private fun isHttpUrl(value: String): Boolean =
    value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)

/**
 * Tela **"Licenças de código aberto"** (2.384.0) — as bibliotecas de terceiros que o app leva, com a
 * licença de cada uma (atribuição que MIT/Apache/BSD/OFL exigem). Aberta pelo item do **Sobre**
 * (`AboutScreen(onOpenSourceLicensesClick = …)`).
 *
 * A lista é a do PRÓPRIO app: o plugin `br.com.codecacto.kmplib.licenses` (no `build.gradle.kts` do
 * `composeApp`) gera `composeResources/files/aboutlibraries.json` a cada mudança de dependência, e o app
 * entrega os bytes:
 *
 * ```kotlin
 * composable<Route.Licenses> {
 *     OpenSourceLicensesScreen(
 *         onBack = { navController.popBackStack() },
 *         librariesJson = { Res.readBytes(OPEN_SOURCE_LICENSES_RESOURCE_PATH).decodeToString() },
 *     )
 * }
 * ```
 *
 * Toque numa biblioteca abre o texto da licença (diálogo); sem texto embutido, abre a URL da licença
 * no navegador. Arquivo ausente ou ilegível = "A lista de licenças não está disponível nesta versão."
 *
 * @param onBack voltar (seta da `AppTopBar`, id `topbar-voltar`).
 * @param librariesJson lê o `aboutlibraries.json` do app (fora da thread principal). Pode lançar: vira
 *   o estado "indisponível".
 * @param bottomBar rodapé fixo (banner de house ad) — a lista termina colada no topo dele.
 * @param texts textos; default no idioma do aparelho.
 */
@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
    librariesJson: suspend () -> String,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    texts: OpenSourceLicensesTexts = rememberOpenSourceLicensesTexts(),
) {
    var load by remember { mutableStateOf<OpenSourceLicensesLoad>(OpenSourceLicensesLoad.Loading) }
    var open by remember { mutableStateOf<Library?>(null) }

    LaunchedEffect(Unit) {
        load = withContext(Dispatchers.Default) {
            val json = runCatching { librariesJson() }
                .onFailure { AppLogger.w(TAG, "sem aboutlibraries.json: ${it::class.simpleName}") }
                .getOrNull()
            parseOpenSourceLicenses(json)?.let(OpenSourceLicensesLoad::Ready) ?: OpenSourceLicensesLoad.Unavailable
        }
    }

    Scaffold(
        modifier = modifier.testTag(OpenSourceLicensesTestTags.TELA),
        topBar = {
            AppTopBar(title = texts.title, navigationType = NavigationType.BACK, onNavigationClick = onBack)
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        val content = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
        when (val state = load) {
            OpenSourceLicensesLoad.Loading -> Box(content, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag(OpenSourceLicensesTestTags.CARREGANDO))
            }
            OpenSourceLicensesLoad.Unavailable -> EmptyState(
                icon = Icons.Outlined.Description,
                title = texts.unavailable,
                modifier = content.testTag(OpenSourceLicensesTestTags.INDISPONIVEL),
            )
            is OpenSourceLicensesLoad.Ready -> Box(content, contentAlignment = Alignment.TopCenter) {
                    LibrariesContainer(
                        libraries = state.libraries,
                        modifier = Modifier
                            .widthIn(max = LIST_MAX_WIDTH)
                            .fillMaxSize()
                            .testTag(OpenSourceLicensesTestTags.LISTA),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        onLibraryClick = { library ->
                            if (licenseTextOf(library).isNotEmpty()) {
                                open = library
                            } else {
                                licenseUrlOf(library)?.let(::openUrl)
                            }
                        },
                        licenseDialogBody = null,
                    )
                }
        }
    }

    open?.let { library ->
        LicenseTextDialog(library = library, close = texts.close, onDismiss = { open = null })
    }
}

@Composable
private fun LicenseTextDialog(library: Library, close: String, onDismiss: () -> Unit) {
    val text = remember(library) { licenseTextOf(library) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(close) } },
        title = { Text(library.name) },
        text = {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag(OpenSourceLicensesTestTags.TEXTO_DA_LICENCA),
                )
            }
        },
    )
}

private fun openUrl(url: String) {
    runCatching { getUrlLauncher().openUrl(url) }
        .onFailure { AppLogger.e(TAG, "não foi possível abrir a licença", it) }
}

private val LIST_MAX_WIDTH = 720.dp
private const val TAG = "OpenSourceLicenses"
