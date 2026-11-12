package br.com.codecacto.kmplib.familia.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip as androidxClip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.codecacto.kmplib.ads.router.ManagedBannerAd
import br.com.codecacto.kmplib.developer.DeveloperApp
import br.com.codecacto.kmplib.developer.DeveloperInfoService
import br.com.codecacto.kmplib.developer.resolveStoreUrl
import br.com.codecacto.kmplib.ui.screens.developer.DeveloperScreen
import br.com.codecacto.kmplib.familia.components.FamilyBadge
import br.com.codecacto.kmplib.familia.components.FamilyButton
import br.com.codecacto.kmplib.familia.components.FamilyButtonVariant
import br.com.codecacto.kmplib.familia.components.FamilyCard
import br.com.codecacto.kmplib.familia.components.FamilyChip
import br.com.codecacto.kmplib.familia.components.FamilyChipRow
import br.com.codecacto.kmplib.familia.components.FamilyEyebrow
import br.com.codecacto.kmplib.familia.components.FamilyIconButton
import br.com.codecacto.kmplib.familia.components.FamilyList
import br.com.codecacto.kmplib.familia.components.FamilyListScope
import br.com.codecacto.kmplib.familia.components.FamilyPill
import br.com.codecacto.kmplib.familia.components.FamilyProgress
import br.com.codecacto.kmplib.familia.components.FamilyRadio
import br.com.codecacto.kmplib.familia.components.FamilyRowBadge
import br.com.codecacto.kmplib.familia.components.FamilyRowChevron
import br.com.codecacto.kmplib.familia.components.FamilyScaffold
import br.com.codecacto.kmplib.familia.components.FamilySheet
import br.com.codecacto.kmplib.familia.components.FamilySkeleton
import br.com.codecacto.kmplib.familia.components.FamilySubTopBar
import br.com.codecacto.kmplib.familia.components.FamilySwitch
import br.com.codecacto.kmplib.familia.components.FamilyToastState
import br.com.codecacto.kmplib.familia.components.FamilyAppIcon
import br.com.codecacto.kmplib.familia.components.rememberIsPremium
import br.com.codecacto.kmplib.familia.familiaString
import br.com.codecacto.kmplib.familia.generated.resources.*
import br.com.codecacto.kmplib.familia.icons.FamilyIcons
import br.com.codecacto.kmplib.familia.theme.FamilyApp
import br.com.codecacto.kmplib.familia.theme.FamilyTheme
import br.com.codecacto.kmplib.ui.screens.feedback.FeedbackScreen
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.ui.screens.about.AboutExtraLink
import br.com.codecacto.kmplib.ui.screens.about.AboutScreen
import br.com.codecacto.kmplib.ui.theme.TextScale
import br.com.codecacto.kmplib.ui.theme.TextScaleController
import br.com.codecacto.kmplib.ui.theme.ThemeMode
import br.com.codecacto.kmplib.ui.theme.ThemeModeController

/** Banner padrão das subtelas (rodapé, com o inset da barra de gestos). */
@Composable
private fun SubscreenBanner() {
    if (!rememberIsPremium()) ManagedBannerAd()
}

/** Subtítulo da linha Premium do menu e de Configurações. */
@Composable
fun familyPremiumSubtitle(isPremium: Boolean, planName: String?): String = when {
    !isPremium -> familiaString(Res.string.familia_premium_sub_off)
    planName != null -> familiaString(Res.string.familia_premium_sub_on, planName)
    else -> familiaString(Res.string.familia_premium_sub_on_noplan)
}

/**
 * Tela **Mais** (menu ☰, 02 §3) na ordem da fábrica (D3): Premium · Configurações · Outros apps ·
 * **Enviar feedback · Sobre**. [onOtherApps] `null` esconde "Outros apps" (nenhum irmão publicado).
 * Ids `mais-premium`, `mais-configuracoes`, `mais-outros-apps`, `mais-feedback`, `mais-sobre`.
 */
@Composable
fun FamilyMoreScreen(
    onBack: () -> Unit,
    onPremium: () -> Unit,
    onSettings: () -> Unit,
    onOtherApps: (() -> Unit)?,
    onFeedback: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
    isPremium: Boolean = rememberIsPremium(),
    planName: String? = null,
    showPremium: Boolean = true,
) {
    FamilyScaffold(modifier.testTag("mais-tela"), topBar = { FamilySubTopBar(familiaString(Res.string.familia_more_title), onBack) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp)) {
            val premiumSub = familyPremiumSubtitle(isPremium, planName)
            val t = listOf(
                familiaString(Res.string.familia_premium), familiaString(Res.string.familia_settings), familiaString(Res.string.familia_other_apps),
                familiaString(Res.string.familia_feedback), familiaString(Res.string.familia_about),
            )
            FamilyList {
                if (showPremium) row(t[0], premiumSub, leading = { FamilyRowBadge(FamilyIcons.Star) }, trailing = { FamilyRowChevron() }, onClick = onPremium, testTag = "mais-premium")
                row(t[1], leading = { FamilyRowBadge(FamilyIcons.Sliders) }, trailing = { FamilyRowChevron() }, onClick = onSettings, testTag = "mais-configuracoes")
                if (onOtherApps != null) row(t[2], leading = { FamilyRowBadge(FamilyIcons.Grid) }, trailing = { FamilyRowChevron() }, onClick = onOtherApps, testTag = "mais-outros-apps")
                row(t[3], leading = { FamilyRowBadge(FamilyIcons.Send) }, trailing = { FamilyRowChevron() }, onClick = onFeedback, testTag = "mais-feedback")
                row(t[4], leading = { FamilyRowBadge(FamilyIcons.Info) }, trailing = { FamilyRowChevron() }, onClick = onAbout, testTag = "mais-sobre")
            }
        }
    }
}

/** Os 9 horários do protótipo para o lembrete (minutos desde 00:00). */
val FamilyReminderTimes: List<Int> = listOf(5 * 60 + 30, 6 * 60, 6 * 60 + 30, 7 * 60, 8 * 60, 12 * 60, 18 * 60, 20 * 60, 21 * 60)

/** "07:00" de [minutes] desde a meia-noite. Puro. */
fun formatReminderTime(minutes: Int): String {
    val m = ((minutes % 1440) + 1440) % 1440
    return "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
}

/** Um lembrete da seção "Lembretes" de Configurações (o app acrescenta os seus). */
data class FamilyReminderSetting(
    val id: String,
    val title: String,
    val frequency: String,
    val enabled: Boolean,
    val timeMinutes: Int,
    val onEnabledChange: (Boolean) -> Unit,
    val onTimeChange: (Int) -> Unit,
    val times: List<Int> = FamilyReminderTimes,
)

/**
 * Onde uma seção extra do app entra em [FamilySettingsScreen] (2.357.0). As seções do kit ficam
 * sempre na ordem do protótipo; a do app entra logo DEPOIS da âncora escolhida (âncora ausente — sem
 * lembretes, sem cópia, sem Premium — não some com a seção: ela entra no mesmo ponto da ordem).
 */
enum class FamilySettingsSlot {
    /** Depois de "Aparência". */
    AFTER_APPEARANCE,

    /** Depois de "Tamanho da letra". */
    AFTER_TEXT_SIZE,

    /** Depois de "Lembretes". */
    AFTER_REMINDERS,

    /** Depois de "Cópia de segurança" — antes de "Assinatura". O default. */
    AFTER_BACKUP,

    /** Depois de "Assinatura" (fim da tela). */
    AFTER_SUBSCRIPTION,
}

/**
 * Uma seção do APP em Configurações, no desenho do kit (2.357.0): o rótulo ([FamilyEyebrow]) e a lista
 * ([FamilyList]) com as linhas que o app monta — `row(...)` (com `FamilySwitch` no `trailing` para
 * liga/desliga) ou `custom { }`.
 *
 * ```kotlin
 * FamilySettingsSection("privacidade", "Privacidade") {
 *     row("Bloquear o app", "Pede a digital ou o PIN ao abrir",
 *         leading = { FamilyRowBadge(FamilyIcons.Lock) },
 *         trailing = { FamilySwitch(bloqueio, onBloqueio) },
 *         onClick = { onBloqueio(!bloqueio) }, role = Role.Switch, testTag = "config-bloquear")
 * }
 * ```
 *
 * @param id estável; vira o id `config-secao-<id>` da seção.
 * @param slot onde a seção entra ([FamilySettingsSlot.AFTER_BACKUP] = antes de "Assinatura").
 */
class FamilySettingsSection(
    val id: String,
    val title: String,
    val slot: FamilySettingsSlot = FamilySettingsSlot.AFTER_BACKUP,
    val rows: FamilyListScope.() -> Unit,
)

/** As seções de [sections] que entram depois de [slot], na ordem em que o app as passou. Puro. */
fun familySettingsSectionsAt(sections: List<FamilySettingsSection>, slot: FamilySettingsSlot): List<FamilySettingsSection> =
    sections.filter { it.slot == slot }

@Composable
private fun ExtraSettingsSections(sections: List<FamilySettingsSection>, slot: FamilySettingsSlot) {
    familySettingsSectionsAt(sections, slot).forEach { section ->
        FamilyEyebrow(section.title)
        FamilyList(modifier = Modifier.testTag("config-secao-${section.id}"), content = section.rows)
    }
}

/** O que a seção "Cópia de segurança" faz (`null` em Configurações = app sem dado do usuário: some). */
data class FamilyBackupActions(val onMakeBackup: () -> Unit, val onRestoreBackup: () -> Unit)

/**
 * **Configurações** (02 §4): Aparência (Igual ao do aparelho / Claro / Escuro — o único lugar do tema),
 * Tamanho da letra (A− barra A+ com prévia em Fraunces), Lembretes ([reminders] + linhas do app em
 * [extraReminderRows]), Cópia de segurança ([backup] — some se `null`), Assinatura (linha Premium).
 *
 * @param textPreview a frase da prévia do tamanho de letra — da TRADUÇÃO embarcada, nunca do protótipo.
 * @param extraSections seções do APP no desenho do kit (2.357.0; ex.: "Privacidade → Bloquear o app"),
 *   cada uma no seu [FamilySettingsSlot]. Ver [FamilySettingsSection].
 * Ids `config-tema-<system|light|dark>`, `config-letra-menos`/`-mais`, `config-lembrete-<id>`,
 * `config-lembrete-<id>-horario`, `config-copia-fazer`, `config-copia-restaurar`, `config-premium`.
 */
@Composable
fun FamilySettingsScreen(
    onBack: () -> Unit,
    themeController: ThemeModeController,
    textScaleController: TextScaleController,
    textPreview: String,
    onPremium: (() -> Unit)?,
    modifier: Modifier = Modifier,
    reminders: List<FamilyReminderSetting> = emptyList(),
    extraReminderRows: FamilyListScope.() -> Unit = {},
    backup: FamilyBackupActions? = null,
    isPremium: Boolean = rememberIsPremium(),
    planName: String? = null,
    toastState: FamilyToastState? = null,
    extraSections: List<FamilySettingsSection> = emptyList(),
) {
    val c = FamilyTheme.colors
    val mode by themeController.mode.collectAsState()
    val scale by textScaleController.scale.collectAsState()
    var timeSheetFor by remember { mutableStateOf<String?>(null) }

    FamilyScaffold(modifier.testTag("config-tela"), topBar = { FamilySubTopBar(familiaString(Res.string.familia_settings), onBack) }, toastState = toastState) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FamilyEyebrow(familiaString(Res.string.familia_appearance))
            val themes = listOf(
                Triple(ThemeMode.SYSTEM, familiaString(Res.string.familia_theme_system), FamilyIcons.Vibrate),
                Triple(ThemeMode.LIGHT, familiaString(Res.string.familia_theme_light), FamilyIcons.Sun),
                Triple(ThemeMode.DARK, familiaString(Res.string.familia_theme_dark), FamilyIcons.Moon),
            )
            FamilyList {
                themes.forEach { (m, label, icon) ->
                    row(
                        label,
                        leading = { Icon(icon, null, tint = c.mu, modifier = Modifier.size(24.dp)) },
                        trailing = { FamilyRadio(mode == m) },
                        onClick = { themeController.setMode(m) },
                        role = Role.RadioButton,
                        testTag = "config-tema-${m.storageValue}",
                    )
                }
            }
            ExtraSettingsSections(extraSections, FamilySettingsSlot.AFTER_APPEARANCE)

            FamilyEyebrow(familiaString(Res.string.familia_text_size))
            FamilyCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextSizeButton("A−", familiaString(Res.string.familia_text_smaller), scale.canDecrease, "config-letra-menos") { textScaleController.decrease() }
                    FamilyProgress(scale.step / TextScale.STEPS.toFloat(), Modifier.weight(1f), contentDescription = familiaString(Res.string.familia_text_size))
                    TextSizeButton("A+", familiaString(Res.string.familia_text_larger), scale.canIncrease, "config-letra-mais") { textScaleController.increase() }
                }
                Text(textPreview, style = FamilyTheme.type.h2.copy(fontWeight = FontWeight.Normal), color = c.fg)
            }
            ExtraSettingsSections(extraSections, FamilySettingsSlot.AFTER_TEXT_SIZE)

            if (reminders.isNotEmpty()) {
                FamilyEyebrow(familiaString(Res.string.familia_reminders))
                val timeLabel = familiaString(Res.string.familia_reminder_time)
                val toggleLabel = familiaString(Res.string.familia_toggle_reminder)
                FamilyList {
                    reminders.forEach { r ->
                        row(
                            r.title, r.frequency,
                            leading = { FamilyRowBadge(FamilyIcons.Bell) },
                            trailing = { FamilySwitch(r.enabled, r.onEnabledChange, contentDescription = toggleLabel) },
                            onClick = { r.onEnabledChange(!r.enabled) },
                            role = Role.Switch,
                            testTag = "config-lembrete-${r.id}",
                        )
                        row(
                            timeLabel,
                            leading = { FamilyRowBadge(FamilyIcons.Clock) },
                            trailing = { Text(formatReminderTime(r.timeMinutes), style = FamilyTheme.type.h2, color = c.acd) },
                            onClick = { timeSheetFor = r.id },
                            enabled = r.enabled,
                            testTag = "config-lembrete-${r.id}-horario",
                        )
                    }
                    extraReminderRows()
                }
            }
            ExtraSettingsSections(extraSections, FamilySettingsSlot.AFTER_REMINDERS)

            if (backup != null) {
                FamilyEyebrow(familiaString(Res.string.familia_backup))
                val makeT = familiaString(Res.string.familia_backup_make)
                val makeS = familiaString(Res.string.familia_backup_make_sub)
                val restT = familiaString(Res.string.familia_backup_restore)
                val restS = familiaString(Res.string.familia_backup_restore_sub)
                FamilyList {
                    row(makeT, makeS, leading = { FamilyRowBadge(FamilyIcons.Download) }, trailing = { FamilyRowChevron() }, onClick = backup.onMakeBackup, testTag = "config-copia-fazer")
                    row(restT, restS, leading = { FamilyRowBadge(FamilyIcons.Upload) }, trailing = { FamilyRowChevron() }, onClick = backup.onRestoreBackup, testTag = "config-copia-restaurar")
                }
            }
            ExtraSettingsSections(extraSections, FamilySettingsSlot.AFTER_BACKUP)

            if (onPremium != null) {
                FamilyEyebrow(familiaString(Res.string.familia_subscription))
                val sub = familyPremiumSubtitle(isPremium, planName)
                val premiumT = familiaString(Res.string.familia_premium)
                FamilyList {
                    row(premiumT, sub, leading = { FamilyRowBadge(FamilyIcons.Star) }, trailing = { FamilyRowChevron() }, onClick = onPremium, testTag = "config-premium")
                }
            }
            ExtraSettingsSections(extraSections, FamilySettingsSlot.AFTER_SUBSCRIPTION)
        }
    }

    reminders.firstOrNull { it.id == timeSheetFor }?.let { r ->
        FamilyTimeSheet(selected = r.timeMinutes, times = r.times, onSelect = r.onTimeChange, onDone = { timeSheetFor = null })
    }
}

@Composable
private fun TextSizeButton(label: String, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val c = FamilyTheme.colors
    val shape = androidx.compose.foundation.shape.CircleShape
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(48.dp)
            .androidxClip(shape)
            .background(c.sf)
            .border(1.dp, c.ln, shape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        // "A−" 15 e "A+" 22 no protótipo — fixos (o botão não cresce com a letra que ele muda).
        Text(
            label,
            style = FamilyTheme.type.body.copy(fontWeight = FontWeight.Bold, fontSize = if (label == "A+") 22.sp else 15.sp),
            color = if (enabled) c.fg else c.mu,
        )
    }
}

/** Folha "Horário do lembrete": chips em 3 colunas + "Pronto". Ids `horario-<HHMM>`, `horario-pronto`. */
@Composable
fun FamilyTimeSheet(selected: Int, times: List<Int>, onSelect: (Int) -> Unit, onDone: () -> Unit) {
    FamilySheet(onDismiss = onDone, testTag = "horario-folha") {
        Text(familiaString(Res.string.familia_reminder_time_title), style = FamilyTheme.type.h2, color = FamilyTheme.colors.fg)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            times.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t ->
                        val label = formatReminderTime(t)
                        FamilyChip(label, selected = t == selected, onClick = { onSelect(t) }, modifier = Modifier.weight(1f), testTag = "horario-${label.replace(":", "")}")
                    }
                    repeat(3 - row.size) { androidx.compose.foundation.layout.Box(Modifier.weight(1f)) }
                }
            }
        }
        FamilyButton(familiaString(Res.string.familia_done), onDone, testTag = "horario-pronto")
    }
}

/**
 * **Outros apps** (02 §6): os irmãos da família vindos do Nexus (`/public/developer`), sem o próprio
 * app; toque → página na loja do aparelho. Ids `outros-<slug>`.
 */
@Composable
fun FamilyOtherAppsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, current: FamilyApp = FamilyTheme.app) {
    val c = FamilyTheme.colors
    var apps by remember { mutableStateOf<List<Pair<FamilyApp, DeveloperApp>>?>(null) }
    LaunchedEffect(current) { apps = loadFamilyOtherApps(current) }
    FamilyScaffold(modifier.testTag("outros-tela"), topBar = { FamilySubTopBar(familiaString(Res.string.familia_other_apps), onBack) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp)) {
            val list = apps
            if (list == null) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { repeat(4) { FamilySkeleton(76.dp) } }
            } else {
                val see = familiaString(Res.string.familia_see)
                FamilyList {
                    list.forEach { (fam, dev) ->
                        row(
                            dev.name.ifBlank { fam.displayName },
                            dev.description.takeIf { it.isNotBlank() },
                            leading = { FamilyAppIcon(fam, 52.dp) },
                            trailing = { FamilyPill(see) },
                            onClick = { dev.primaryStoreUrl()?.let { getUrlLauncher().openUrl(it) } },
                            testTag = "outros-${fam.slug}",
                        )
                    }
                }
            }
        }
    }
}

/**
 * Os outros apps da família publicados (com link de loja), na ordem do Nexus. Lista vazia = esconda
 * "Outros apps" do menu (`onOtherApps = null`).
 */
suspend fun loadFamilyOtherApps(current: FamilyApp): List<Pair<FamilyApp, DeveloperApp>> =
    DeveloperInfoService.fetchApps().getOrDefault(emptyList())
        .mapNotNull { dev -> FamilyApp.matchName(dev.name)?.let { it to dev } }
        .filter { (fam, dev) -> fam != current && dev.primaryStoreUrl() != null }
        .distinctBy { it.first }

private fun DeveloperApp.primaryStoreUrl(): String? = resolveStoreUrl().takeIf { it.isNotBlank() }

/**
 * **Sobre** (02 §7): a `AboutScreen` da kmplib nas cores do app (ícone, nome, versão, Termos,
 * Privacidade, Desenvolvido por), com a 4ª linha "Sobre a tradução" só quando [onAboutTranslation]
 * existe (crédito exigido pela licença CC BY — D9). Banner no rodapé sem Premium.
 *
 * @param extraLinks linhas a mais do app, DEPOIS de "Sobre a tradução" (2.357.0) — ex.: "Fontes dos
 *   mapas" no Atlas: `listOf(AboutExtraLink("Fontes dos mapas", "sobre-btn-fontes", onClick = …))`.
 *   Crédito de dado/licença, nada de "Desenvolvido por"/Termos/Privacidade (já estão aqui).
 */
@Composable
fun FamilyAboutScreen(
    onBack: () -> Unit,
    termsUrl: String,
    privacyUrl: String,
    onDevelopedBy: () -> Unit,
    modifier: Modifier = Modifier,
    appName: String = FamilyTheme.app.displayName,
    onAboutTranslation: (() -> Unit)? = null,
    extraLinks: List<AboutExtraLink> = emptyList(),
) {
    val label = familiaString(Res.string.familia_about_translation)
    AboutScreen(
        onBack = onBack,
        termsUrl = termsUrl,
        privacyUrl = privacyUrl,
        onDevelopedByClick = onDevelopedBy,
        modifier = modifier,
        appName = appName,
        appIcon = androidx.compose.ui.graphics.vector.rememberVectorPainter(FamilyTheme.app.icon),
        bottomBar = { SubscreenBanner() },
        extraLinks = familyAboutLinks(onAboutTranslation?.let { AboutExtraLink(label, "sobre-btn-traducao", onClick = it) }, extraLinks),
    )
}

/** As linhas extras do Sobre: "Sobre a tradução" (quando há) e depois as do app, sem id repetido. Puro. */
fun familyAboutLinks(translation: AboutExtraLink?, extra: List<AboutExtraLink>): List<AboutExtraLink> =
    (listOfNotNull(translation) + extra).distinctBy { it.testTag }

/**
 * "Sobre a tradução": nome, licença, crédito e link, lidos da tabela `meta` do banco embarcado (o
 * app passa os valores — este módulo não depende do `kmplib-bible`). Ids `traducao-*`.
 */
@Composable
fun FamilyTranslationScreen(
    onBack: () -> Unit,
    name: String,
    license: String,
    credit: String,
    url: String?,
    modifier: Modifier = Modifier,
) {
    val c = FamilyTheme.colors
    FamilyScaffold(modifier.testTag("traducao-tela"), topBar = { FamilySubTopBar(familiaString(Res.string.familia_about_translation), onBack) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FamilyCard(large = true, gap = 14.dp) {
                Text(name, style = FamilyTheme.type.h1, color = c.fg)
                FamilyPill(license)
                Text(credit, style = FamilyTheme.type.body, color = c.fg)
                if (!url.isNullOrBlank()) {
                    FamilyButton(url, { getUrlLauncher().openUrl(url) }, variant = FamilyButtonVariant.OUTLINE, small = true, icon = FamilyIcons.Globe, testTag = "traducao-link")
                }
            }
        }
    }
}

/** **Enviar feedback** (D4): a `FeedbackScreen` da kmplib, nas cores do app, com o banner no rodapé. */
@Composable
fun FamilyFeedbackScreen(onBack: () -> Unit, onSent: (() -> Unit)? = null) {
    val c = FamilyTheme.colors
    FeedbackScreen(onBack = onBack, primaryColor = c.ac, backgroundColor = c.bg, cardColor = c.sf, bottomBar = { SubscreenBanner() }, onFeedbackSent = onSent)
}

/** **Desenvolvido por** + contato (D5): a `DeveloperScreen` da kmplib, nas cores do app, com o banner. */
@Composable
fun FamilyDeveloperScreen(onBack: () -> Unit) {
    val c = FamilyTheme.colors
    DeveloperScreen(onBack = onBack, primaryColor = c.ac, backgroundColor = c.bg, cardColor = c.sf, bottomBar = { SubscreenBanner() })
}

/** Conteúdo de tela com rolagem e o padding do `.ct` (8 18 24, gap 14). */
@Composable
fun FamilyScreenColumn(inner: androidx.compose.foundation.layout.PaddingValues, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}
