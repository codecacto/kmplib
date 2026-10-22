package br.com.codecacto.kmplib.ui.screens.developer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
 * Mostra a marca CodeCacto (logo, slogan e a linha do site), **duas ações** — **Visitar o site** e
 * **Conversar no WhatsApp** (mensagem já dizendo de qual app a pessoa veio) — e a grade **Nossos
 * projetos**. Desde a 2.323.0 não há mais e-mail nem formulário "Entrar em contato" aqui: o contato
 * da empresa é o WhatsApp e o site.
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
 * @param primaryColor cor do botão "Visitar o site".
 * @param backgroundColor fundo da tela.
 * @param cardColor fundo dos cartões de projeto.
 * @param texts textos; default no idioma do aparelho ([rememberDeveloperTexts]).
 * @param defaultName sem efeito desde a 2.323.0 (era o formulário de contato, que saiu da tela).
 * @param defaultEmail sem efeito desde a 2.323.0.
 * @param defaultWhatsapp sem efeito desde a 2.323.0.
 * @param bottomBar rodapé fixo, opcional (banner de house ad). O último cartão termina colado ao
 *   topo dele, nunca por baixo.
 * @param phoneFormat sem efeito desde a 2.323.0.
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
) {
    @Suppress("NAME_SHADOWING")
    val texts: DeveloperTexts = texts ?: rememberDeveloperTexts(phoneFormat)
    var contact by remember { mutableStateOf(DeveloperContact()) }
    var apps by remember { mutableStateOf<List<DeveloperApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        contact = DeveloperInfoService.fetchContact().getOrDefault(DeveloperContact())
        apps = DeveloperInfoService.fetchApps().getOrDefault(emptyList())
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
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Marca(texts)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AcaoPrincipal(
                        texto = texts.siteButton,
                        icone = Icons.Default.Language,
                        cor = primaryColor,
                        testTag = DeveloperTestTags.BTN_SITE,
                        onClick = { getUrlLauncher().openUrl(contact.site.ifBlank { DeveloperContact().site }) },
                    )
                    AcaoPrincipal(
                        texto = texts.whatsappButton,
                        icone = Icons.AutoMirrored.Filled.Chat,
                        cor = WhatsAppGreen,
                        testTag = DeveloperTestTags.BTN_WHATSAPP,
                        onClick = { getUrlLauncher().openWhatsApp(contact.whatsapp, texts.whatsappMessage) },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = texts.appsSectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    when {
                        isLoading -> GradeCarregando(cardColor)
                        apps.isEmpty() -> Text(
                            text = texts.appsEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> GradeDeProjetos(apps, cardColor)
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.codecacto_logo),
            // O nome logo abaixo é o que o leitor de tela anuncia.
            contentDescription = null,
            modifier = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = texts.brandName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = texts.brandSlogan,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (texts.brandTagline.isNotBlank()) {
            Text(
                text = texts.brandTagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
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
            .height(52.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = cor, contentColor = Color.White),
    ) {
        Icon(imageVector = icone, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = texto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** Duas colunas, cartões da mesma linha com a mesma altura. */
@Composable
private fun GradeDeProjetos(apps: List<DeveloperApp>, cardColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        apps.chunked(2).forEach { linha ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                linha.forEach { app ->
                    CartaoDeProjeto(app, cardColor, Modifier.weight(1f).fillMaxHeight())
                }
                if (linha.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CartaoDeProjeto(app: DeveloperApp, cardColor: Color, modifier: Modifier) {
    val forma = RoundedCornerShape(20.dp)
    Surface(
        shape = forma,
        color = cardColor,
        modifier = modifier
            .clip(forma)
            .clickable(role = Role.Button) {
                val url = app.resolveStoreUrl()
                if (url.isNotBlank()) getUrlLauncher().openUrl(url)
            }
            .testTag(DeveloperTestTags.PROJETO),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (app.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = app.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.description.isNotBlank()) {
                Text(
                    text = app.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Esqueleto com a moldura e a altura aproximada dos cartões — a tela não pula quando eles chegam. */
@Composable
private fun GradeCarregando(cardColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardColor),
                    )
                }
            }
        }
    }
}
