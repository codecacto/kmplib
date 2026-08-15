package br.com.codecacto.kmplib.ui.screens.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.format.formatDateBrFromMillis
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionInfo
import androidx.compose.ui.platform.testTag
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.NeedHelpSection
import br.com.codecacto.kmplib.ui.components.UsageMeter

/**
 * Paywall **canonico** da kmplib — stateless, parametrizavel por [PaywallTexts] e tematizado 100%
 * por tokens ([MaterialTheme]). Sem `koinViewModel()`, sem rede, sem calculo de negocio: o ViewModel
 * do app coleta planos/usage/assinatura e aplica as [PaywallAction].
 *
 * Wrapper completo de tela: [Scaffold] + top bar (voltar dispara [PaywallAction.Back]) + slot de
 * snackbar opcional. O conteudo em si vive em [PaywallContent] (embutivel, ex.: bottom sheet de
 * limite de uso).
 *
 * @param state Estado da tela.
 * @param onAction Despacha acoes para o ViewModel do app.
 * @param texts Textos (i18n); defaults em pt-BR.
 * @param snackbarHostState Host de snackbar opcional (o app coleta effects e mostra mensagens).
 * @param modifier Modificador externo.
 * @param headerIcon Icone premium opcional do topo (ex.: logo/icone do app). `null` usa um default
 *   tasteful ([Icons.Filled.WorkspacePremium]). Tematizado pelo `colorScheme.primary`.
 * @param beforePlansContent Slot acima dos cards de plano (ex.: oferta de teste gratis). So renderiza
 *   no estado nao-premium. Ver o KDoc de [PaywallContent].
 * @param afterPlansContent Slot abaixo dos cards e acima do bloco legal (ex.: "assinar por Pix no
 *   portal"). So renderiza no estado nao-premium.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    state: PaywallState,
    onAction: (PaywallAction) -> Unit,
    texts: PaywallTexts = PaywallTexts(),
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier,
    headerIcon: ImageVector? = null,
    beforePlansContent: (@Composable () -> Unit)? = null,
    afterPlansContent: (@Composable () -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = texts.screenTitle, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(PaywallAction.Back) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = texts.backContentDescription,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
    ) { paddingValues ->
        PaywallContent(
            state = state,
            onAction = onAction,
            texts = texts,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            headerIcon = headerIcon,
            beforePlansContent = beforePlansContent,
            afterPlansContent = afterPlansContent,
        )
    }
}

/**
 * Conteudo do paywall **sem chrome de tela** — embutivel em qualquer container (tela cheia, bottom
 * sheet de limite de uso, dialog). Renderiza, de cima para baixo:
 *
 * header → [UsageMeter] (se `usage != null`) → bloco "assinatura ativa" (se `isPremium`) OU cards de
 * plano + disclosure legal de auto-renovacao + botao restaurar → [NeedHelpSection] → card de erro.
 *
 * Tema 100% via [MaterialTheme] (zero cor hardcoded); preco SEMPRE [PaywallPlan.priceLabel] (string
 * da loja). Responsivo via [BoxWithConstraints] (limita a largura do conteudo em telas largas).
 *
 * ## Os dois slots (2.113.0) — o que a tela canônica NÃO sabe
 *
 * `beforePlansContent` e `afterPlansContent` renderizam **só no estado não-premium**, em volta dos
 * cards de plano, e existem para duas coisas que a lib não tem como conhecer:
 *
 * - **antes:** a oferta de **teste grátis**, que não é produto de loja — quem concede é o admin-api
 *   central (um por conta, para sempre). Ela precisa aparecer ANTES dos preços; depois deles, quem
 *   já decidiu não pagar hoje nunca mais rola até lá.
 * - **depois:** o **caminho alternativo de pagamento** (o Pix do portal web nos produtos own-auth).
 *   Vem depois dos cards porque a loja é o caminho principal no app, e antes do bloco legal para
 *   não ficar embaixo do texto de renovação automática, que ninguém lê.
 *
 * Sem eles, cada app reimplementava a tela inteira para acrescentar um botão — e a primeira coisa
 * que se perde nessa cópia é o disclosure legal que a Apple e o Google exigem.
 *
 * @param headerIcon Icone premium opcional do topo; `null` usa o default ([Icons.Filled.WorkspacePremium]).
 */
@Composable
fun PaywallContent(
    state: PaywallState,
    onAction: (PaywallAction) -> Unit,
    texts: PaywallTexts = PaywallTexts(),
    modifier: Modifier = Modifier,
    headerIcon: ImageVector? = null,
    beforePlansContent: (@Composable () -> Unit)? = null,
    afterPlansContent: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        val contentModifier = if (wide) {
            Modifier.fillMaxWidth().widthIn(max = 560.dp)
        } else {
            Modifier.fillMaxWidth()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 32.dp else 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PaywallHeader(texts = texts, headerIcon = headerIcon)

                state.usage?.let { usage ->
                    UsageMeter(usage = usage, label = texts.usageLabel)
                }

                if (state.isPremium) {
                    ActiveSubscriptionCard(
                        subscription = state.subscription,
                        texts = texts,
                        isPurchasing = state.isPurchasing,
                        onManage = { onAction(PaywallAction.ManageSubscription) },
                    )
                } else {
                    beforePlansContent?.invoke()
                    PlansSection(state = state, onAction = onAction, texts = texts)
                    afterPlansContent?.invoke()
                    LegalDisclosureSection(texts = texts, onAction = onAction)
                    RestoreButton(
                        isRestoring = state.isPurchasing && state.purchasingPlanId == null,
                        enabled = !state.isPurchasing,
                        texts = texts,
                        onRestore = { onAction(PaywallAction.Restore) },
                    )
                }

                NeedHelpSection(
                    title = texts.needHelpTitle,
                    description = texts.needHelpDescription,
                    buttonText = texts.needHelpButton,
                    onOpenDeveloper = { onAction(PaywallAction.OpenDeveloper) },
                )

                state.error?.let { err ->
                    ErrorCard(
                        message = err,
                        dismissLabel = texts.errorDismiss,
                        onDismiss = { onAction(PaywallAction.DismissError) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaywallHeader(texts: PaywallTexts, headerIcon: ImageVector?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Disco de fundo discreto (acento, NUNCA preenchido forte): primaria a 12% sobre o circulo.
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = headerIcon ?: Icons.Filled.WorkspacePremium,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = texts.headerTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = texts.headerSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlansSection(
    state: PaywallState,
    onAction: (PaywallAction) -> Unit,
    texts: PaywallTexts,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = texts.choosePlanLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            state.isLoadingPlans -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.plans.isEmpty() -> {
                Text(
                    text = texts.emptyPlans,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Paywall vazio tem id próprio: a suíte precisa distinguir "não há o que comprar"
                    // (incidente) de "a tela não abriu" (outra falha, outro dono).
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PaywallTestTags.SEM_PLANOS)
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                state.plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        selected = plan.id == state.selectedPlanId,
                        isPurchasing = state.isPurchasing,
                        isThisPurchasing = state.isPurchasing && state.purchasingPlanId == plan.id,
                        texts = texts,
                        onSelect = { onAction(PaywallAction.SelectPlan(plan.id)) },
                    )
                }
            }
        }
    }
}

/**
 * Card de plano. **Fundo SEMPRE `surface`** (recomendado e nao-recomendado) para legibilidade — o
 * destaque do recomendado/selecionado vem de ACENTOS: borda primaria 2dp, leve elevacao e o badge
 * proeminente. A cor primaria nunca preenche grandes areas (so badge, borda, checks, preco e CTA).
 */
@Composable
private fun PlanCard(
    plan: PaywallPlan,
    selected: Boolean,
    isPurchasing: Boolean,
    isThisPurchasing: Boolean,
    texts: PaywallTexts,
    onSelect: () -> Unit,
) {
    val highlighted = plan.isRecommended || selected
    val border = if (highlighted) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Ancoragem para automação de UI (Maestro/Appium/Compose test). Ver `PaywallTestTags`.
            .testTag(PaywallTestTags.plano(plan))
            .clickable(enabled = !isPurchasing) { onSelect() },
        shape = RoundedCornerShape(20.dp),
        border = border,
        // Fundo branco/surface SEMPRE — sem area preenchida de primaria.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (highlighted) 6.dp else 0.dp,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            if (plan.isRecommended) {
                RecommendedBadge(label = plan.badgeLabel ?: texts.recommendedBadge)
                Spacer(Modifier.height(14.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    plan.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = plan.priceLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (highlighted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    plan.pricePerMonthLabel?.let { perMonth ->
                        Text(
                            text = perMonth,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            plan.durationLabel?.let { duration ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = duration,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (plan.highlights.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                plan.highlights.forEach { highlight ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Check num disco discreto de primaria a 12% (acento, nao area cheia).
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = highlight,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // CTA: recomendado/selecionado em primaria cheia (chamada principal); demais em outlined
            // (acento), preservando "primaria so como acento" no resto do card.
            if (highlighted) {
                AppButton(
                    text = texts.ctaSubscribe,
                    onClick = onSelect,
                    enabled = !isPurchasing,
                    isLoading = isThisPurchasing,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PaywallTestTags.botaoAssinar(plan)),
                )
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    enabled = !isPurchasing,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag(PaywallTestTags.botaoAssinar(plan)),
                ) {
                    if (isThisPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = texts.ctaSubscribe,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Selo "Recomendado" proeminente: pill de cor primaria, leve sombra, icone de estrela e tipografia
 * em bold. Tematizado por `colorScheme.primary`/`onPrimary` (zero cor hardcoded).
 */
@Composable
private fun RecommendedBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ActiveSubscriptionCard(
    subscription: SubscriptionInfo?,
    texts: PaywallTexts,
    isPurchasing: Boolean,
    onManage: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(PaywallTestTags.ASSINATURA_ATIVA),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = texts.activeTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = texts.activeDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )

            subscription?.expirationDate?.let { expiration ->
                // Data formatada dd/MM/yyyy (padrao BR) dentro da lib.
                val formatted = formatDateBrFromMillis(expiration.toEpochMilliseconds())
                val label = if (subscription.willRenew) texts.renewsAtLabel else texts.expiresAtLabel
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "$label $formatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onManage,
                enabled = !isPurchasing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag(PaywallTestTags.BOTAO_GERENCIAR),
            ) {
                Text(texts.manageSubscription)
            }
        }
    }
}

@Composable
private fun LegalDisclosureSection(
    texts: PaywallTexts,
    onAction: (PaywallAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = texts.legalInfoTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = texts.autoRenewalNotice,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = texts.subscriptionDisclosure,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = { onAction(PaywallAction.Privacy) }) {
                    Text(
                        text = texts.privacyPolicy,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = { onAction(PaywallAction.Terms) }) {
                    Text(
                        text = texts.termsOfUse,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestoreButton(
    isRestoring: Boolean,
    enabled: Boolean,
    texts: PaywallTexts,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(
            onClick = onRestore,
            enabled = enabled,
            modifier = Modifier.testTag(PaywallTestTags.BOTAO_RESTAURAR),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isRestoring) texts.restoring else texts.restore,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(PaywallTestTags.ERRO),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
