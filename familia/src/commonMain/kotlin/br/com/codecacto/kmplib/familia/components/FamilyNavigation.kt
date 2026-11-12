package br.com.codecacto.kmplib.familia.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ads.router.ManagedBannerAd
import br.com.codecacto.kmplib.familia.familiaString
import br.com.codecacto.kmplib.familia.generated.resources.*
import br.com.codecacto.kmplib.familia.icons.FamilyIcons
import br.com.codecacto.kmplib.familia.theme.FamilyTheme
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.PremiumStatus

/** `true` só com o Premium CONFIRMADO pela loja (`Unknown` não libera nada nem esconde anúncio de vez). */
@Composable
fun rememberIsPremium(): Boolean {
    val status by MonetizationManager.premiumStatus.collectAsState()
    return status == PremiumStatus.Premium
}

/**
 * Selo "Premium" do topo (`.seal`): altura 34, raio 999, borda 1,5 `ln`, texto `acd`; ativo = pílula
 * cheia `acs` sem borda. Abre a tela Premium. Id `topo-selo-premium`.
 */
@Composable
fun PremiumSeal(isPremium: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = FamilyTheme.colors
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .heightIn(min = 34.dp)
            .clip(shape)
            .background(if (isPremium) c.acs else Color.Transparent)
            .let { if (!isPremium) it.border(1.5.dp, c.ln, shape) else it }
            .selectable(selected = isPremium, role = Role.Button, onClick = onClick)
            .testTag("topo-selo-premium")
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(if (isPremium) FamilyIcons.StarFilled else FamilyIcons.Star, null, tint = c.acd, modifier = Modifier.size(18.dp))
        Text(familiaString(Res.string.familia_premium), style = FamilyTheme.type.xs.copy(fontWeight = FontWeight.Bold), color = c.acd)
    }
}

/**
 * Topo de aba (`.hd`): altura 68, padding 12 8 0 20 — ícone do app 34 · título Fraunces (reticências) ·
 * selo Premium · ☰ (abre "Mais"). Pinta o fundo `bg` por trás da barra de status antes do inset.
 * [showPremiumSeal] = `false` em app sem Premium. Ids `topo-titulo`, `topo-selo-premium`, `topo-menu`.
 */
@Composable
fun FamilyTopBar(
    title: String,
    isPremium: Boolean,
    onPremiumClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPremiumSeal: Boolean = true,
    appIcon: ImageVector = FamilyTheme.identity.icon,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = FamilyTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(68.dp)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FamilyAppIcon(appIcon, 34.dp)
        HGap(4.dp)
        Text(
            title,
            style = FamilyTheme.type.hdT,
            color = c.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }.testTag("topo-titulo"),
        )
        actions()
        if (showPremiumSeal) PremiumSeal(isPremium, onPremiumClick)
        CompositionLocalProvider(LocalContentColor provides c.fg) {
            FamilyIconButton(FamilyIcons.Menu, familiaString(Res.string.familia_menu), onMenuClick, testTag = "topo-menu")
        }
    }
}

/** Topo de subtela (`.hd.sub`): voltar + título; sem selo e sem menu. Ids `topo-voltar`, `topo-titulo`. */
@Composable
fun FamilySubTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = FamilyTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(68.dp)
            .padding(start = 8.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides c.fg) {
            FamilyIconButton(FamilyIcons.Back, familiaString(Res.string.familia_back), onBack, testTag = "topo-voltar")
        }
        Text(
            title,
            style = FamilyTheme.type.hdT,
            color = c.fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }.testTag("topo-titulo"),
        )
        actions()
    }
}

/** Uma aba da [FamilyBottomBar]. */
data class FamilyTab(val id: String, val label: String, val icon: ImageVector)

/**
 * Abas (`.tabs`): altura 78, fundo `sf`, borda-topo `ln`, padding 6 4 14; pílula do ícone 58×32 raio 16
 * (ativa `acs`/`acd`, rótulo `fg`; inativa `mu`). Pinta o fundo por trás da barra de gestos. Ids
 * `aba-<id>`.
 */
@Composable
fun FamilyBottomBar(tabs: List<FamilyTab>, selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = FamilyTheme.colors
    Column(modifier.fillMaxWidth().background(c.sf)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln))
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(77.dp).padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 14.dp),
        ) {
            tabs.forEach { tab ->
                val on = tab.id == selectedId
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .selectable(selected = on, role = Role.Tab) { onSelect(tab.id) }
                        .testTag("aba-${tab.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier.size(58.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) c.acs else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(tab.icon, null, tint = if (on) c.acd else c.mu, modifier = Modifier.size(24.dp)) }
                    Text(tab.label, style = FamilyTheme.type.tab, color = if (on) c.fg else c.mu, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * A casca de uma tela da família: `[topo] [conteúdo] → [ManagedBannerAd 60, só sem Premium] → [abas 78]`.
 *
 * O `PaddingValues` do [content] já inclui topo + banner + abas: **a lista termina colada no banner**
 * (regra da fábrica — o último item nunca passa por baixo do anúncio), e sem Premium… com Premium o
 * banner sai e a lista desce até as abas. O aviso ([toastState]) fica 14 acima do rodapé; o
 * [floatingAction] 18 da direita e 18 acima do rodapé.
 *
 * @param showBanner default = sem Premium confirmado ([rememberIsPremium]). Telas sem banner (oração,
 *   leitura em tela cheia, boas-vindas) passam `false`.
 * @param banner o anúncio — default `ManagedBannerAd` (STANDARD, sem inset: as abas já cuidam dele).
 */
@Composable
fun FamilyScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    tabs: (@Composable () -> Unit)? = null,
    showBanner: Boolean = !rememberIsPremium(),
    banner: @Composable () -> Unit = { ManagedBannerAd(windowInsets = if (tabs == null) WindowInsets.navigationBars else WindowInsets(0)) },
    toastState: FamilyToastState? = null,
    floatingAction: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = FamilyTheme.colors
    var bottomHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(modifier.fillMaxSize().background(c.bg)) {
        Scaffold(
            topBar = topBar,
            bottomBar = {
                // O inset da barra de gestos é aplicado AQUI, uma vez, para o bloco inteiro (e consumido: abas e
                // banner não somam de novo). O fundo segue o último item, que é o que encosta na borda.
                Column(
                    Modifier
                        .onSizeChanged { bottomHeight = with(density) { it.height.toDp() } }
                        .background(if (tabs != null) c.sf else if (showBanner) c.sf2 else c.bg)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    if (showBanner) Box(Modifier.fillMaxWidth().background(c.sf2)) { banner() }
                    tabs?.invoke()
                }
            },
            containerColor = c.bg,
            contentColor = c.fg,
            // Os insets são das barras (topo pinta a de status; abas/banner, a de gestos).
            contentWindowInsets = WindowInsets(0),
        ) { inner -> content(inner) }
        if (floatingAction != null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = bottomHeight + 18.dp)) { floatingAction() }
        }
        if (toastState != null) FamilyToastHost(toastState, bottomOffset = bottomHeight + 14.dp)
    }
}

/** Distância do rodapé que o aviso usa numa tela sem barras inferiores. */
val FamilyToastBottomWithoutBars: Dp = 18.dp
