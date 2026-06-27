package br.com.codecacto.kmplib.ui.screens.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.developer.DeveloperApp
import br.com.codecacto.kmplib.developer.DeveloperContact
import br.com.codecacto.kmplib.developer.DeveloperInfoService
import br.com.codecacto.kmplib.platform.getUrlLauncher
import coil3.compose.AsyncImage

private val WhatsAppGreen = Color(0xFF25D366)

/**
 * Tela "Desenvolvido por" reutilizável.
 *
 * Mostra a identidade do desenvolvedor (CodeCacto), botões de contato
 * (WhatsApp, e-mail e, opcionalmente, site) e uma grade vertical com os apps
 * publicados. Contato e apps são carregados do backend central **apps-api** via
 * [DeveloperInfoService] (`GET /public/developer`, com fallback offline — nunca quebra a tela).
 *
 * Requer `DeveloperInfoService.initialize(DeveloperConfig(httpClient = ...))` no bootstrap do app
 * (mesmo ponto do `FeedbackService`). Sem inicializar, a tela renderiza com o fallback padrão.
 *
 * ```kotlin
 * DeveloperScreen(
 *     onBack = { navController.popBackStack() },
 *     primaryColor = Color(0xFF6D28D9)
 * )
 * ```
 *
 * @param onBack Callback para voltar à tela anterior
 * @param primaryColor Cor primária (header e botão principal)
 * @param backgroundColor Cor de fundo do conteúdo
 * @param cardColor Cor de fundo dos cards de app
 * @param texts Textos customizáveis (suporta i18n)
 * @param defaultName Nome pré-preenchido no formulário de contato (ex.: usuário logado)
 * @param defaultEmail E-mail pré-preenchido no formulário de contato (ex.: usuário logado)
 * @param defaultWhatsapp WhatsApp pré-preenchido em dígitos (ex.: telefone do perfil)
 */
@Composable
fun DeveloperScreen(
    onBack: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    cardColor: Color = MaterialTheme.colorScheme.surface,
    texts: DeveloperTexts = DeveloperTexts(),
    defaultName: String? = null,
    defaultEmail: String? = null,
    defaultWhatsapp: String? = null,
) {
    var contact by remember { mutableStateOf(DeveloperContact()) }
    var apps by remember { mutableStateOf<List<DeveloperApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showContact by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contact = DeveloperInfoService.fetchContact().getOrDefault(DeveloperContact())
        apps = DeveloperInfoService.fetchApps().getOrDefault(emptyList())
        isLoading = false
    }

    // "Entrar em contato" abre o formulário (ContactScreen) sem exigir navegação do app.
    if (showContact) {
        ContactScreen(
            onBack = { showContact = false },
            primaryColor = primaryColor,
            backgroundColor = backgroundColor,
            texts = texts.contact,
            defaultName = defaultName,
            defaultEmail = defaultEmail,
            defaultWhatsapp = defaultWhatsapp,
        )
        return
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(backgroundColor)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(primaryColor)
                    .padding(horizontal = 16.dp)
                    .padding(top = 48.dp, bottom = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = texts.backContentDescription,
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = texts.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = texts.subtitle,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Branding
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(primaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CC",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = texts.brandName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = texts.brandSlogan,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Contato
                SectionTitle(texts.contactSectionTitle)

                // Ação primária: abre o formulário "Entrar em contato" (mesmo lead do site).
                Button(
                    onClick = { showContact = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = texts.contactButton,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Button(
                    onClick = {
                        getUrlLauncher().openWhatsApp(contact.whatsapp, texts.whatsappMessage)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = texts.whatsappButton,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = {
                        getUrlLauncher().openEmail(contact.email, texts.emailSubject)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = texts.emailButton, fontSize = 16.sp)
                }

                if (contact.site.isNotBlank()) {
                    OutlinedButton(
                        onClick = { getUrlLauncher().openUrl(contact.site) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = texts.siteButton, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Apps
                SectionTitle(texts.appsSectionTitle)

                when {
                    isLoading -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(color = primaryColor)
                    }
                    apps.isEmpty() -> {
                        Text(
                            text = texts.appsEmpty,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        apps.forEach { app ->
                            AppCard(app = app, cardColor = cardColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AppCard(app: DeveloperApp, cardColor: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (app.storeUrl.isNotBlank()) {
                    getUrlLauncher().openUrl(app.storeUrl)
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (app.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = app.logoUrl,
                        contentDescription = app.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.description.isNotBlank()) {
                    Text(
                        text = app.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
