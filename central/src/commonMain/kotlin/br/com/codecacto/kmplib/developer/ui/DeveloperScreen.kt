package br.com.codecacto.kmplib.ui.screens.developer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.developer.DeveloperApp
import br.com.codecacto.kmplib.developer.DeveloperContact
import br.com.codecacto.kmplib.developer.DeveloperInfoService
import br.com.codecacto.kmplib.developer.resolveStoreUrl
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.codecacto_logo
import br.com.codecacto.kmplib.mask.PhoneInputFormat
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.ui.components.AppTopBar
import br.com.codecacto.kmplib.ui.components.NavigationType
import br.com.codecacto.kmplib.ui.locale.rememberDevicePhoneInputFormat
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.developer.withoutCurrentApp
import br.com.codecacto.kmplib.ui.theme.readableContentColorFor
import coil3.compose.AsyncImagePainter

private val WhatsAppGreen = Color(0xFF25D366)
private val CONTENT_MAX_WIDTH = 560.dp

/**
 * Ids da [DeveloperScreen] para os flows Maestro (vocabulário `tela-elemento-acao`). Estáveis.
 * O voltar é o `topbar-voltar` da `AppTopBar`.
 */
object DeveloperTestTags {
    const val TELA: String = "desenvolvedor-tela"
    const val BTN_SITE: String = "desenvolvedor-btn-site"
    const val BTN_WHATSAPP: String = "desenvolvedor-btn-whatsapp"
    const val PROJETO: String = "desenvolvedor-projeto"

    val all: List<String> = listOf(TELA, BTN_SITE, BTN_WHATSAPP, PROJETO)
}

/**
 * Tela **"Desenvolvido por"** — padrão de todo app da fábrica, aberta pela tela Sobre
 * (`AboutScreen`). Mesmo desenho da Sobre: barra de topo padrão, coluna central de no máximo 560dp.
 *
 * De cima para baixo (2.336.0):
 * - a **marca** — o logo oficial da CodeCacto (o mesmo do site e do `DevelopedByCredit` da weblib),
 *   o nome e o slogan **"Apps que resolvem."**;
 * - o **parágrafo de venda** ([DeveloperTexts.brandTagline]): fazemos aplicativo e site sob medida;
 * - **duas ações** — **Conversar no WhatsApp** (mensagem já dizendo de qual app a pessoa veio) e
 *   **Visitar o site**;
 * - **Nossos projetos**: um cartão por linha (ícone, nome, descrição de até 3 linhas e o indicador de
 *   que abre a loja), sem o próprio app em que a pessoa está ([currentAppName]).
 *
 * Contato e projetos vêm do apps-api ([DeveloperInfoService], `GET /public/developer`, com reserva
 * offline — nunca quebra a tela). A ordem dos projetos é a do servidor: fixados primeiro e o resto
 * sorteado a cada visita, curado no Nexus → Vitrine.
 *
 * App infantil: site, WhatsApp e loja saem pelo `getUrlLauncher()`, que já passa pelo portão de pais.
 *
 * ```kotlin
 * composable<Route.Developer> {
 *     DeveloperScreen(onBack = { navController.popBackStack() })
 * }
 * ```
 *
 * @param onBack voltar (seta da `AppTopBar`, id `topbar-voltar`).
 * @param primaryColor cor do botão "Visitar o site". O texto do botão sai pelo contraste
 *   ([readableContentColorFor]), nunca branco fixo.
 * @param backgroundColor fundo da tela.
 * @param cardColor fundo dos cartões de projeto.
 * @param texts textos; default no idioma do aparelho ([rememberDeveloperTexts]).
 * @param defaultName sem efeito desde a 2.323.0 (era o formulário de contato, que saiu da tela).
 * @param defaultEmail sem efeito desde a 2.323.0.
 * @param defaultWhatsapp sem efeito desde a 2.323.0.
 * @param bottomBar rodapé fixo, opcional (banner de house ad). O último cartão termina colado ao
 *   topo dele, nunca por baixo.
 * @param phoneFormat sem efeito desde a 2.323.0.
 * @param currentAppName nome do app em execução, para tirá-lo de "Nossos projetos" (2.336.0).
 *   Default: o nome que o sistema mostra ([BuildInfo.appName]); `null` mostra todos.
 */
@Composable
fun DeveloperScreen(
    onBack: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    cardColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    texts: DeveloperTexts? = null,
    defaultName: String? = null,
    defaultEmail: String? = null,
    defaultWhatsapp: String? = null,
    bottomBar: @Composable () -> Unit = {},
    phoneFormat: PhoneInputFormat = rememberDevicePhoneInputFormat(),
    currentAppName: String? = remember { BuildInfo.appName },
) {
    @Suppress("NAME_SHADOWING")
    val texts: DeveloperTexts = texts ?: rememberDeveloperTexts(phoneFormat)
    var contact by remember { mutableStateOf(DeveloperContact()) }
    var apps by remember { mutableStateOf<List<DeveloperApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentAppName) {
        contact = DeveloperInfoService.fetchContact().getOrDefault(DeveloperContact())
        apps = DeveloperInfoService.fetchApps().getOrDefault(emptyList()).withoutCurrentApp(currentAppName)
        isLoading = false
    }

    Scaffold(
        modifier = Modifier.testTag(DeveloperTestTags.TELA),
        topBar = {
            AppTopBar(
                title = texts.title,
                navigationType = NavigationType.BACK,
                onNavigationClick = onBack,
            )
        },
        bottomBar = bottomBar,
        containerColor = backgroundColor,
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
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Marca(texts)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AcaoPrincipal(
                            texto = texts.whatsappButton,
                            icone = Icons.AutoMirrored.Filled.Chat,
                            cor = WhatsAppGreen,
                            testTag = DeveloperTestTags.BTN_WHATSAPP,
                            onClick = { getUrlLauncher().openWhatsApp(contact.whatsapp, texts.whatsappMessage) },
                        )
                        AcaoPrincipal(
                            texto = texts.siteButton,
                            icone = Icons.Default.Language,
                            cor = primaryColor,
                            testTag = DeveloperTestTags.BTN_SITE,
                            onClick = { getUrlLauncher().openUrl(contact.site.ifBlank { DeveloperContact().site }) },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = texts.appsSectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    when {
                        isLoading -> ListaCarregando(cardColor)
                        apps.isEmpty() -> Text(
                            text = texts.appsEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> ListaDeProjetos(apps, cardColor, texts.appsOpenLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun Marca(texts: DeveloperTexts) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Logo oficial (o do site), fundo transparente: serve ao tema claro e ao escuro.
        Image(
            painter = painterResource(Res.drawable.codecacto_logo),
            // O nome logo abaixo é o que o leitor de tela anuncia.
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = texts.brandName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = texts.brandSlogan,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (texts.brandTagline.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = texts.brandTagline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }
}

@Composable
private fun AcaoPrincipal(
    texto: String,
    icone: ImageVector,
    cor: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        // Texto pelo contraste (AA): branco sobre o verde do WhatsApp dava ~2:1.
        colors = ButtonDefaults.buttonColors(
            containerColor = cor,
            contentColor = readableContentColorFor(cor, backdrop = MaterialTheme.colorScheme.background),
        ),
    ) {
        Icon(imageVector = icone, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = texto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** Um projeto por linha: ícone, nome e descrição legíveis no celular, sem cortar no meio. */
@Composable
private fun ListaDeProjetos(apps: List<DeveloperApp>, cardColor: Color, openLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        apps.forEach { app -> CartaoDeProjeto(app, cardColor, openLabel) }
    }
}

@Composable
private fun CartaoDeProjeto(app: DeveloperApp, cardColor: Color, openLabel: String) {
    val forma = RoundedCornerShape(18.dp)
    val url = app.resolveStoreUrl()
    val abre = url.isNotBlank()
    Surface(
        shape = forma,
        color = cardColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .then(
                if (abre) {
                    Modifier.clickable(role = Role.Button, onClickLabel = openLabel) {
                        getUrlLauncher().openUrl(url)
                    }
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {}
            .testTag(DeveloperTestTags.PROJETO),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 80.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconeDoProjeto(app.logoUrl)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.description.isNotBlank()) {
                    Text(
                        text = app.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (abre) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    // A ação já é anunciada pelo `onClickLabel` do cartão.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Ícone do app com moldura fina (ícone de fundo branco não some no cartão claro) e reserva. */
@Composable
private fun IconeDoProjeto(logoUrl: String) {
    val forma = RoundedCornerShape(14.dp)
    var carregou by remember(logoUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(forma)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma),
        contentAlignment = Alignment.Center,
    ) {
        if (!carregou) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (logoUrl.isNotBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onState = { carregou = it is AsyncImagePainter.State.Success },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Esqueleto com a moldura e a altura aproximada dos cartões — a tela não pula quando eles chegam. */
@Composable
private fun ListaCarregando(cardColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardColor),
            )
        }
    }
}
