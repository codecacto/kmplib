@file:OptIn(kotlin.time.ExperimentalTime::class)

package br.com.codecacto.kmplib.familia.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import br.com.codecacto.kmplib.familia.components.FamilyBadge
import br.com.codecacto.kmplib.familia.components.FamilyButton
import br.com.codecacto.kmplib.familia.components.FamilyButtonVariant
import br.com.codecacto.kmplib.familia.components.FamilyCard
import br.com.codecacto.kmplib.familia.components.FamilyCardStyle
import br.com.codecacto.kmplib.familia.components.FamilyEyebrow
import br.com.codecacto.kmplib.familia.components.FamilyProgressDialog
import br.com.codecacto.kmplib.familia.components.FamilyRadio
import br.com.codecacto.kmplib.familia.components.FamilyScaffold
import br.com.codecacto.kmplib.familia.components.FamilySkeleton
import br.com.codecacto.kmplib.familia.components.FamilySpinner
import br.com.codecacto.kmplib.familia.components.FamilySubTopBar
import br.com.codecacto.kmplib.familia.components.FamilyToastState
import br.com.codecacto.kmplib.familia.components.rememberFamilyToastState
import br.com.codecacto.kmplib.familia.familiaString
import br.com.codecacto.kmplib.familia.generated.resources.*
import br.com.codecacto.kmplib.familia.icons.FamilyIcons
import br.com.codecacto.kmplib.familia.theme.FamilyTheme
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallAction
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallHostAction
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallHostEffect
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallPlan
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallState
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallTexts
import br.com.codecacto.kmplib.ui.screens.paywall.rememberPaywallTexts
import br.com.codecacto.kmplib.ui.screens.paywall.PaywallViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Tela **Premium** da família (02 §2) — o desenho do protótipo sobre o [PaywallViewModel] da kmplib
 * (loja, restaurar, gerenciar, ordem Mensal → Semestral → Anual e preços SEMPRE da loja).
 *
 * - Sem Premium: medalha + "{Nome} Premium", benefícios ("Sem anúncios" primeiro + [extras]; app sem
 *   anúncio, só [extras] — [adsRemovedBenefit]), planos
 *   com rádio e selo "Melhor valor" no de MAIOR duração disponível, "Assinar {plano}", nota da loja,
 *   Termos · Privacidade, "Restaurar compras". Estados: carregando (3 esqueletos), loja indisponível
 *   ("Tentar de novo"), compra pendente ("Verificar agora"), "Abrindo a loja…" durante a compra.
 *   **Sem teste grátis** (os apps da família não têm trial — monte o `PaywallViewModel` com
 *   `PaywallConfig(trialPolicy = PaywallTrialPolicy.NoTrial)`). Se a loja ainda assim trouxer oferta
 *   introdutória que não dá para pular, o botão e o termo de cobrança dizem a verdade
 *   ([familyPremiumCta], id `premium-termo-trial`). A folha de compra é a do sistema.
 * - Premium ativo: cartão herói, plano e renovação, "Liberado para você", "Gerenciar assinatura".
 *
 * Ids: `premium-plano-<id>`, `premium-btn-assinar`, `premium-btn-restaurar`, `premium-btn-tentar`,
 * `premium-btn-verificar`, `premium-btn-gerenciar`, `premium-link-termos`, `premium-link-privacidade`.
 *
 * @param extras benefícios do app depois de "Sem anúncios" (lista do doc de cada app); em app sem
 *   anúncio, são TODOS os benefícios (o 1º sai em negrito).
 * @param toastState aviso da tela (mensagens da loja e "Premium ativado").
 * @param adsRemovedBenefit `true` = "Sem anúncios" é o 1º benefício e o aviso de ativação diz "Os
 *   anúncios sumiram"; `false` = só [extras] e o aviso neutro "Premium ativado. Tudo liberado." (4
 *   idiomas). Default = [br.com.codecacto.kmplib.familia.theme.FamilyAppIdentity.showsAds] (2.359.0).
 */
@Composable
fun FamilyPremiumScreen(
    viewModel: PaywallViewModel,
    appName: String,
    extras: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    toastState: FamilyToastState = rememberFamilyToastState(),
    onOpenUrl: (String) -> Unit = { getUrlLauncher().openUrl(it) },
    onManageSubscription: () -> Unit = { getUrlLauncher().openSubscriptionManagement() },
    adsRemovedBenefit: Boolean = FamilyTheme.identity.showsAds,
) {
    val host by viewModel.state.collectAsStateWithLifecycle()
    val state = host.paywall
    val currentBack by rememberUpdatedState(onBack)
    val activated = familiaString(if (adsRemovedBenefit) Res.string.familia_activated else Res.string.familia_activated_neutral)
    val benefits = familyPremiumBenefits(familiaString(Res.string.familia_no_ads), extras, adsRemovedBenefit)
    var wasPremium by remember { mutableStateOf<Boolean?>(null) }

    LifecycleResumeEffect(viewModel) {
        viewModel.dispatch(PaywallHostAction.Load)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PaywallHostEffect.Close -> currentBack()
                PaywallHostEffect.OpenDeveloper -> Unit
                is PaywallHostEffect.OpenUrl -> onOpenUrl(effect.url)
                PaywallHostEffect.OpenSubscriptionManagement -> onManageSubscription()
                is PaywallHostEffect.ShowMessage -> toastState.show(effect.message)
            }
        }
    }
    // "Premium ativado. Os anúncios sumiram." — só na TRANSIÇÃO (abrir já assinante não comemora).
    LaunchedEffect(state.isPremium) {
        if (wasPremium == false && state.isPremium) toastState.show(activated)
        wasPremium = state.isPremium
    }
    // Erro com planos na tela (cartão recusado, cancelada pela loja): aviso, e some.
    LaunchedEffect(state.error, state.plans.isNotEmpty(), state.pendingPurchasePlanId) {
        val e = state.error
        if (e != null && state.plans.isNotEmpty() && state.pendingPurchasePlanId == null) {
            toastState.show(e)
            viewModel.dispatch(PaywallHostAction.Paywall(PaywallAction.DismissError))
        }
    }

    FamilyScaffold(
        modifier = modifier.testTag("premium-tela"),
        topBar = { FamilySubTopBar(familiaString(Res.string.familia_premium), onBack) },
        toastState = toastState,
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.isPremium) PremiumActive(state, appName, benefits) { viewModel.dispatch(PaywallHostAction.Paywall(PaywallAction.ManageSubscription)) }
            else PremiumOffer(state, appName, benefits, viewModel)
        }
    }
    if (state.isPurchasing && state.purchasingPlanId != null) FamilyProgressDialog(familiaString(Res.string.familia_opening_store), testTag = "premium-abrindo-loja")
}

@Composable
private fun Benefits(benefits: List<String>, active: Boolean) {
    if (benefits.isEmpty()) return
    val c = FamilyTheme.colors
    FamilyCard(modifier = Modifier.testTag("premium-beneficios")) {
        if (active) FamilyEyebrow(familiaString(Res.string.familia_unlocked))
        benefits.forEachIndexed { i, text ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (active) Icon(FamilyIcons.Check, null, tint = c.acd, modifier = Modifier.size(24.dp))
                else FamilyBadge(FamilyIcons.Check, size = 34.dp, round = true, iconSize = 18.dp)
                Text(text, style = if (i == 0) FamilyTheme.type.body.copy(fontWeight = FontWeight.Bold) else FamilyTheme.type.body, color = c.fg)
            }
        }
    }
}

@Composable
private fun PremiumOffer(state: PaywallState, appName: String, benefits: List<String>, vm: PaywallViewModel) {
    val c = FamilyTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FamilyBadge(FamilyIcons.StarFilled, size = 76.dp, round = true, iconSize = 36.dp, background = c.ac, contentColor = c.aci)
        Text(familiaString(Res.string.familia_premium_title, appName), style = FamilyTheme.type.h1.copy(textAlign = TextAlign.Center), color = c.fg)
    }
    Benefits(benefits, active = false)

    val plans = state.plans.filterNot { it.isFree }.sortedBy { it.durationMonths ?: Int.MAX_VALUE }
    val best = familyBestPlanId(plans)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = plans.firstOrNull { it.id == selected } ?: plans.firstOrNull { it.id == best } ?: plans.lastOrNull()
    val pending = state.pendingPurchasePlanId

    when {
        state.isLoadingPlans && plans.isEmpty() -> {
            repeat(3) { FamilySkeleton(84.dp) }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                FamilySpinner(size = 22.dp, strokeWidth = 3.dp)
                Text(familiaString(Res.string.familia_store_loading), style = FamilyTheme.type.sm, color = c.mu)
            }
        }
        pending != null -> FamilyCard(style = FamilyCardStyle.TINT, gap = 12.dp, modifier = Modifier.testTag("premium-pendente")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FamilySpinner(size = 26.dp, strokeWidth = 3.dp)
                Text(familiaString(Res.string.familia_pending_title), style = FamilyTheme.type.h2, color = c.fg)
            }
            Text(familiaString(Res.string.familia_pending_text), style = FamilyTheme.type.body, color = c.fg)
            FamilyButton(familiaString(Res.string.familia_check_now), { vm.dispatch(PaywallHostAction.Refresh) }, variant = FamilyButtonVariant.OUTLINE, testTag = "premium-btn-verificar")
        }
        plans.isEmpty() -> FamilyCard(large = true, modifier = Modifier.testTag("premium-loja-indisponivel")) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FamilyBadge(FamilyIcons.Alert, size = 64.dp, round = true, iconSize = 32.dp)
                Text(familiaString(Res.string.familia_store_down_title), style = FamilyTheme.type.h2.copy(textAlign = TextAlign.Center), color = c.fg)
                Text(familiaString(Res.string.familia_store_down_text), style = FamilyTheme.type.body.copy(textAlign = TextAlign.Center), color = c.mu)
                FamilyButton(familiaString(Res.string.familia_try_again), { vm.dispatch(PaywallHostAction.Refresh) }, testTag = "premium-btn-tentar")
            }
        }
        else -> {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                plans.forEach { plan ->
                    FamilyPlanCard(
                        name = plan.name,
                        subtitle = plan.pricePerMonthLabel?.takeIf { (plan.durationMonths ?: 1) > 1 } ?: plan.description,
                        price = plan.priceLabel,
                        period = plan.durationLabel,
                        selected = plan.id == chosen?.id,
                        bestLabel = if (plan.id == best && plans.size > 1) familiaString(Res.string.familia_best_value) else null,
                        onClick = { selected = plan.id },
                        testTag = "premium-plano-${plan.id}",
                    )
                }
            }
            chosen?.let { p ->
                val cta = familyPremiumCta(p, familiaString(Res.string.familia_subscribe, p.name), rememberPaywallTexts())
                FamilyButton(
                    cta.label,
                    { vm.dispatch(PaywallHostAction.Paywall(PaywallAction.SelectPlan(p.id))) },
                    enabled = !state.isPurchasing,
                    testTag = "premium-btn-assinar",
                )
                // Termo de cobrança JUNTO do botão quando a loja vai dar período grátis (Apple 3.1.2).
                if (cta.terms != null) {
                    Text(
                        cta.terms,
                        style = FamilyTheme.type.sm.copy(textAlign = TextAlign.Center),
                        color = c.fg,
                        modifier = Modifier.fillMaxWidth().testTag("premium-termo-trial"),
                    )
                }
            }
        }
    }
    Text(familiaString(Res.string.familia_store_note), style = FamilyTheme.type.xs.copy(textAlign = TextAlign.Center), color = c.mu, modifier = Modifier.fillMaxWidth())
    LegalLinks(vm)
    FamilyButton(
        familiaString(Res.string.familia_restore),
        { vm.dispatch(PaywallHostAction.Paywall(PaywallAction.Restore)) },
        variant = FamilyButtonVariant.OUTLINE,
        enabled = !state.isPurchasing,
        loading = state.isPurchasing && state.purchasingPlanId == null,
        testTag = "premium-btn-restaurar",
    )
}

/** Termos e Privacidade junto da oferta — exigência das lojas para assinatura (Apple 3.1.2). */
@Composable
private fun LegalLinks(vm: PaywallViewModel) {
    val c = FamilyTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
        listOf(
            Triple(familiaString(Res.string.familia_terms), PaywallAction.Terms, "premium-link-termos"),
            Triple(familiaString(Res.string.familia_privacy), PaywallAction.Privacy, "premium-link-privacidade"),
        ).forEach { (label, action, tag) ->
            Text(
                label,
                style = FamilyTheme.type.xs.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                color = c.mu,
                modifier = Modifier.clickable(role = Role.Button) { vm.dispatch(PaywallHostAction.Paywall(action)) }.padding(vertical = 12.dp).testTag(tag),
            )
        }
    }
}

@Composable
private fun PremiumActive(state: PaywallState, appName: String, benefits: List<String>, onManage: () -> Unit) {
    val c = FamilyTheme.colors
    FamilyCard(style = FamilyCardStyle.HERO, large = true, gap = 14.dp, modifier = Modifier.testTag("premium-ativo")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(FamilyIcons.StarFilled, null, tint = c.aci, modifier = Modifier.size(34.dp))
            FamilyEyebrow(familiaString(Res.string.familia_active), color = c.aci.copy(alpha = 0.85f))
        }
        Text(familiaString(Res.string.familia_thanks, appName), style = FamilyTheme.type.h1, color = c.aci)
        val sub = state.subscription
        val planName = state.plans.firstOrNull { it.isCurrent }?.name
            ?: sub?.productId?.let { pid -> state.plans.firstOrNull { p -> p.id.contains(pid.substringBefore(':'), ignoreCase = true) }?.name }
        val renew = sub?.expirationDate?.toLocalDateTime(TimeZone.currentSystemDefault())?.date?.let { RegionalFormat.formatDate(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (planName != null) LabelValue(familiaString(Res.string.familia_plan), planName, Modifier.weight(1f))
            if (renew != null && sub?.willRenew == true) LabelValue(familiaString(Res.string.familia_renews), renew, Modifier.weight(1f))
        }
    }
    Benefits(benefits, active = true)
    FamilyButton(familiaString(Res.string.familia_manage), onManage, variant = FamilyButtonVariant.SOFT, testTag = "premium-btn-gerenciar")
    Text(familiaString(Res.string.familia_manage_note), style = FamilyTheme.type.xs.copy(textAlign = TextAlign.Center), color = c.mu, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    val c = FamilyTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = FamilyTheme.type.xs, color = c.aci.copy(alpha = 0.8f))
        Text(value, style = FamilyTheme.type.body.copy(fontWeight = FontWeight.Bold), color = c.aci)
    }
}

/**
 * Cartão de plano (`.plan`): raio 22, borda 2 `ln`; selecionado = borda `ac` + fundo `acs`; selo
 * "Melhor valor" no canto (fundo `fg`); preço Fraunces 1,2em. Rádio visual; o cartão inteiro é a escolha.
 */
@Composable
fun FamilyPlanCard(
    name: String,
    subtitle: String?,
    price: String,
    period: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bestLabel: String? = null,
    testTag: String? = null,
) {
    val c = FamilyTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (selected) c.acs else c.sf)
                .border(2.dp, if (selected) c.ac else c.ln, shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .let { if (testTag != null) it.testTag(testTag) else it }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FamilyRadio(selected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = FamilyTheme.type.body.copy(fontWeight = FontWeight.Bold), color = c.fg)
                if (subtitle != null) Text(subtitle, style = FamilyTheme.type.sm, color = c.mu)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = FamilyTheme.type.planPrice, color = c.fg, maxLines = 1)
                if (period != null) Text(period, style = FamilyTheme.type.xs, color = c.mu)
            }
        }
        if (bestLabel != null) {
            Text(
                bestLabel,
                style = FamilyTheme.type.tab,
                color = c.bg,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = (-12).dp)
                    .clip(RoundedCornerShape(999.dp)).background(c.fg).padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * O botão de compra da tela Premium da família e o termo de cobrança abaixo dele (2.357.0).
 *
 * @property label o texto do botão.
 * @property terms "Grátis por 7 dias, depois R$ 9,90/mês. Renova automaticamente; cancele quando
 *   quiser." — só quando o plano tem trial confirmado pela loja; `null` no caso normal.
 */
data class FamilyPremiumCta(val label: String, val terms: String?)

/**
 * A regra do botão (2.357.0): a família **não tem teste grátis** e o botão é "Assinar {plano}". Se
 * a loja mesmo assim confirmou oferta introdutória para [plan] ([PaywallPlan.trial] — produto mal
 * configurado que a loja não deixa pular, iOS), o botão passa a dizer o que a loja vai fazer
 * ([PaywallTexts.ctaLabel]: "Começar 7 dias grátis") e o termo de cobrança ([PaywallTexts.trialTerms])
 * fica junto dele — começar período grátis sem o termo ao lado é recusa da App Store (3.1.2). O
 * `PaywallViewModel` com `PaywallTrialPolicy.NoTrial` compra sem a fase grátis onde a loja deixa
 * (Android) e alerta a fábrica (`TrialForaDaRegra`). Puro.
 */
fun familyPremiumCta(plan: PaywallPlan, subscribeLabel: String, texts: PaywallTexts): FamilyPremiumCta {
    val terms = texts.trialTerms(plan) ?: return FamilyPremiumCta(subscribeLabel, null)
    return FamilyPremiumCta(texts.ctaLabel(plan), terms)
}

/**
 * Os benefícios da tela Premium, na ordem (2.359.0): "Sem anúncios" ([noAdsLabel]) primeiro quando o
 * app tem anúncio ([adsRemovedBenefit]), depois os [extras] do app. App sem anúncio = só os [extras].
 * Itens em branco e repetidos saem. Puro.
 */
fun familyPremiumBenefits(noAdsLabel: String, extras: List<String>, adsRemovedBenefit: Boolean): List<String> =
    ((if (adsRemovedBenefit) listOf(noAdsLabel) else emptyList()) + extras).filter { it.isNotBlank() }.distinct()

/** O plano do selo "Melhor valor": o de MAIOR duração disponível (regra da fábrica). Puro. */
fun familyBestPlanId(plans: List<PaywallPlan>): String? =
    plans.filterNot { it.isFree }.maxByOrNull { it.durationMonths ?: 0 }?.id
