package br.com.codecacto.kmplib.familia.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.familia.components.FamilyAppIcon
import br.com.codecacto.kmplib.familia.components.FamilyBadge
import br.com.codecacto.kmplib.familia.components.FamilyButton
import br.com.codecacto.kmplib.familia.components.FamilyButtonVariant
import br.com.codecacto.kmplib.familia.components.FamilyCard
import br.com.codecacto.kmplib.familia.components.FamilyCardStyle
import br.com.codecacto.kmplib.familia.components.FamilyConfirmDialog
import br.com.codecacto.kmplib.familia.components.FamilyFullScreen
import br.com.codecacto.kmplib.familia.components.FamilySheet
import br.com.codecacto.kmplib.familia.familiaString
import br.com.codecacto.kmplib.familia.generated.resources.*
import br.com.codecacto.kmplib.familia.icons.FamilyIcons
import br.com.codecacto.kmplib.familia.theme.FamilyFixedColors
import br.com.codecacto.kmplib.familia.theme.FamilyTheme
import br.com.codecacto.kmplib.platform.ShareImageOptions
import br.com.codecacto.kmplib.platform.ShareTarget
import br.com.codecacto.kmplib.platform.ShareTargetResult
import br.com.codecacto.kmplib.platform.getClipboard
import br.com.codecacto.kmplib.platform.getShareHandler

/** Página 2 das boas-vindas (o lembrete). `null` em [FamilyOnboarding] = app sem lembrete natural. */
data class FamilyOnboardingReminder(val question: String, val detail: String)

/**
 * **Boas-vindas** (02 §12), duas telas cheias sem banner, pontinhos embaixo:
 * 1. ícone 112 · [promise] (h1 2,1em) · [bullets] com ✓ · "Continuar";
 * 2. selo do sino 100 · pergunta · frase · "Permitir notificações" · "Agora não".
 *
 * [onFinished] recebe `true` quando a pessoa escolheu "Permitir" — o APP pede a permissão do sistema,
 * liga o lembrete e grava o "já viu" (sem conta: `AppPreferences`). Com [reminder] `null`, a 1ª tela
 * termina direto (`false`). Ids `boas-vindas-continuar`, `boas-vindas-permitir`, `boas-vindas-agora-nao`.
 *
 * @param firstPageNote nota opcional na 1ª página, depois dos [bullets] (2.357.0) — o aviso que a
 *   pessoa precisa ler ANTES de usar (Jejuar: "Jejum não substitui orientação médica…"). Cartão tingido
 *   com o ícone de informação, lido como um bloco pelo leitor de tela; id `boas-vindas-nota`.
 */
@Composable
fun FamilyOnboarding(
    promise: String,
    bullets: List<String>,
    reminder: FamilyOnboardingReminder?,
    onFinished: (allowNotifications: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    appIcon: ImageVector = FamilyTheme.app.icon,
    firstPageNote: String? = null,
) {
    val c = FamilyTheme.colors
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = if (reminder == null) 1 else 2
    FamilyFullScreen(modifier.testTag("boas-vindas")) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 26.dp, end = 26.dp, top = 56.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (page == 0) 24.dp else 22.dp, Alignment.CenterVertically)) {
                if (page == 0) {
                    FamilyAppIcon(appIcon, 112.dp)
                    Text(promise, style = FamilyTheme.type.h1.copy(fontSize = FamilyTheme.type.base * 2.1f, lineHeight = FamilyTheme.type.base * 2.1f * 1.14f), color = c.fg)
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        bullets.forEach { b ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FamilyBadge(FamilyIcons.Check, size = 36.dp, round = true, iconSize = 18.dp)
                                Text(b, style = FamilyTheme.type.body, color = c.fg)
                            }
                        }
                    }
                    if (!firstPageNote.isNullOrBlank()) {
                        FamilyCard(style = FamilyCardStyle.TINT, modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("boas-vindas-nota")) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(FamilyIcons.Info, null, tint = c.acd, modifier = Modifier.size(22.dp))
                                Text(firstPageNote, style = FamilyTheme.type.sm, color = c.fg)
                            }
                        }
                    }
                } else if (reminder != null) {
                    FamilyBadge(FamilyIcons.Bell, size = 100.dp, cornerRadius = 34.dp, iconSize = 48.dp)
                    Text(reminder.question, style = FamilyTheme.type.h1.copy(fontSize = FamilyTheme.type.base * 2f, lineHeight = FamilyTheme.type.base * 2f * 1.14f), color = c.fg)
                    Text(reminder.detail, style = FamilyTheme.type.body.copy(fontSize = FamilyTheme.type.base * 1.1f), color = c.mu)
                }
            }
            FamilyDots(count = pages, current = page)
            if (page == 0) {
                FamilyButton(familiaString(Res.string.familia_continue), { if (reminder == null) onFinished(false) else page = 1 }, testTag = "boas-vindas-continuar")
            } else {
                FamilyButton(familiaString(Res.string.familia_allow_notifications), { onFinished(true) }, testTag = "boas-vindas-permitir")
                FamilyButton(familiaString(Res.string.familia_not_now), { onFinished(false) }, variant = FamilyButtonVariant.OUTLINE, testTag = "boas-vindas-agora-nao")
            }
        }
    }
}

/** Pontinhos (`.dots`): 9, o ativo com 26 de largura em `ac`. */
@Composable
fun FamilyDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    val c = FamilyTheme.colors
    val desc = familiaString(Res.string.familia_dot_page, current + 1, count)
    Row(modifier.fillMaxWidth().semantics { contentDescription = desc }, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        repeat(count) { i ->
            Box(Modifier.height(9.dp).width(if (i == current) 26.dp else 9.dp).clip(RoundedCornerShape(5.dp)).background(if (i == current) c.ac else c.ln))
        }
    }
}

/** O que a folha de compartilhar envia. */
class FamilySharePayload(
    /** Bytes PNG/JPEG da imagem (gerada com `renderDrawingToPng`, já com/sem a marca d'água). */
    val imageBytes: ByteArray,
    /** Nome do arquivo (ex.: `"versiculo-hoje.png"`). */
    val fileName: String,
    /** O que vai para a área de transferência no "Copiar" (ex.: "versículo — referência"). */
    val copyText: String,
)

/** Os destinos da [FamilyShareSheet] (2.357.0); [id] é o sufixo do id `compartilhar-<id>`. */
enum class FamilyShareDestination(val id: String) {
    WHATSAPP("whatsapp"),
    WHATSAPP_STATUS("status"),
    INSTAGRAM_STORIES("instagram"),
    COPY("copiar"),
    MORE("mais"),
}

/**
 * **Folha de compartilhar** (02 §9): prévia ([preview] + [title] + [subtitle]) e os destinos
 * WhatsApp · Status · Instagram · Copiar · Mais — **destino ausente é escondido** (`ShareHandler.isInstalled`;
 * "Status" não tem caminho oficial e some), "Mais" = folha do sistema. Instagram exige [metaAppId].
 *
 * [onResult] devolve o aviso para mostrar ("Texto copiado.", "Não foi possível compartilhar.") ou
 * `null` — e é chamado em TODO destino, "Mais" inclusive (até a 2.356.0 o "Mais" que dava certo não
 * chamava nada). [onShared] (2.357.0) é chamado só quando o envio saiu (app de destino aberto, folha do
 * sistema aberta, texto copiado), com o destino — é o gancho do intersticial depois de compartilhar.
 * A folha do sistema não diz se a pessoa concluiu o envio lá dentro: "saiu" = a folha abriu.
 * Ids `compartilhar-<destino>`.
 */
@Composable
fun FamilyShareSheet(
    title: String,
    subtitle: String,
    payload: FamilySharePayload,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    metaAppId: String? = null,
    preview: (@Composable () -> Unit)? = null,
    onResult: (message: String?) -> Unit = {},
    onShared: (destination: FamilyShareDestination) -> Unit = {},
) {
    val c = FamilyTheme.colors
    val handler = remember { getShareHandler() }
    val options = ShareImageOptions(metaAppId = metaAppId, title = title)
    val copied = familiaString(Res.string.familia_copied)
    val failed = familiaString(Res.string.familia_share_failed)
    data class Dest(val id: String, val label: String, val icon: ImageVector, val bg: Color, val fg: Color, val action: () -> Unit)
    fun report(destination: FamilyShareDestination, outcome: FamilyShareOutcome) {
        when (outcome) {
            FamilyShareOutcome.SHARED -> { onResult(null); onShared(destination) }
            FamilyShareOutcome.FAILED -> onResult(failed)
        }
    }
    fun systemSheet(destination: FamilyShareDestination) =
        report(destination, familyShareOutcome(runCatching { handler.shareImage(payload.imageBytes, payload.fileName, title) }))
    fun direct(target: ShareTarget, destination: FamilyShareDestination) {
        when (handler.shareImageTo(target, payload.imageBytes, payload.fileName, options)) {
            ShareTargetResult.Opened -> report(destination, FamilyShareOutcome.SHARED)
            else -> systemSheet(destination)
        }
    }
    val destinations = buildList {
        if (handler.isInstalled(ShareTarget.WHATSAPP, options)) {
            add(Dest("whatsapp", familiaString(Res.string.familia_share_whatsapp), FamilyIcons.Send, FamilyFixedColors.shareWhatsApp, FamilyFixedColors.shareOnColor) { direct(ShareTarget.WHATSAPP, FamilyShareDestination.WHATSAPP) })
        }
        if (handler.isInstalled(ShareTarget.WHATSAPP_STATUS, options)) {
            add(Dest("status", familiaString(Res.string.familia_share_status), FamilyIcons.Image, FamilyFixedColors.shareStatus, FamilyFixedColors.shareOnColor) { direct(ShareTarget.WHATSAPP_STATUS, FamilyShareDestination.WHATSAPP_STATUS) })
        }
        if (handler.isInstalled(ShareTarget.INSTAGRAM_STORIES, options)) {
            add(Dest("instagram", familiaString(Res.string.familia_share_instagram), FamilyIcons.Camera, FamilyFixedColors.shareInstagram, FamilyFixedColors.shareOnColor) { direct(ShareTarget.INSTAGRAM_STORIES, FamilyShareDestination.INSTAGRAM_STORIES) })
        }
        add(Dest("copiar", familiaString(Res.string.familia_share_copy), FamilyIcons.Copy, c.sf2, c.fg) {
            getClipboard().copy(payload.copyText)
            onResult(copied)
            onShared(FamilyShareDestination.COPY)
        })
        add(Dest("mais", familiaString(Res.string.familia_share_more), FamilyIcons.More, c.sf2, c.fg) {
            systemSheet(FamilyShareDestination.MORE)
        })
    }
    FamilySheet(onDismiss = onDismiss, modifier = modifier, testTag = "compartilhar-folha") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (preview != null) Box(Modifier.size(64.dp, 112.dp).clip(RoundedCornerShape(10.dp))) { preview() }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = FamilyTheme.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.fg)
                Text(subtitle, style = FamilyTheme.type.sm, color = c.mu)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            destinations.forEach { d ->
                Column(
                    Modifier.weight(1f).clickable(role = Role.Button) { onDismiss(); d.action() }.padding(vertical = 4.dp).testTag("compartilhar-${d.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(d.bg), contentAlignment = Alignment.Center) {
                        Icon(d.icon, null, tint = d.fg, modifier = Modifier.size(24.dp))
                    }
                    Text(d.label, style = FamilyTheme.type.tab.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, textAlign = TextAlign.Center), color = c.fg, maxLines = 1)
                }
            }
            repeat(5 - destinations.size) { Box(Modifier.weight(1f)) }
        }
        FamilyButton(familiaString(Res.string.familia_cancel), onDismiss, variant = FamilyButtonVariant.OUTLINE, testTag = "compartilhar-cancelar")
    }
}

/** Resultado de um destino da [FamilyShareSheet]. */
enum class FamilyShareOutcome { SHARED, FAILED }

/** A folha do sistema abriu (`SHARED`) ou lançou (`FAILED`). Puro. */
fun familyShareOutcome(result: Result<Unit>): FamilyShareOutcome =
    if (result.isSuccess) FamilyShareOutcome.SHARED else FamilyShareOutcome.FAILED

/**
 * Folha **"Cópia pronta"** (02 §10): nome do arquivo + "Salvar arquivo" (seletor de destino do sistema)
 * · "Enviar para mim" (folha do sistema) · "Fechar". Ids `copia-salvar`, `copia-enviar`, `copia-fechar`.
 */
@Composable
fun FamilyBackupReadySheet(fileName: String, onSave: () -> Unit, onSend: () -> Unit, onClose: () -> Unit) {
    val c = FamilyTheme.colors
    FamilySheet(onDismiss = onClose, testTag = "copia-pronta") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FamilyBadge(FamilyIcons.File)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(familiaString(Res.string.familia_backup_ready), style = FamilyTheme.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.fg)
                Text(fileName, style = FamilyTheme.type.sm, color = c.mu)
            }
        }
        FamilyButton(familiaString(Res.string.familia_backup_save), onSave, icon = FamilyIcons.Download, testTag = "copia-salvar")
        FamilyButton(familiaString(Res.string.familia_backup_send), onSend, variant = FamilyButtonVariant.SOFT, icon = FamilyIcons.Share, testTag = "copia-enviar")
        FamilyButton(familiaString(Res.string.familia_close), onClose, variant = FamilyButtonVariant.OUTLINE, testTag = "copia-fechar")
    }
}

/**
 * Diálogo **"Restaurar esta cópia?"** (02 §10): "O que está no app agora será trocado pelo que está na
 * cópia de {data}." · Cancelar · Restaurar. A escolha do arquivo é o seletor do SISTEMA, antes deste
 * diálogo (o protótipo só simula a lista). Ids `restaurar-btn-cancelar`, `restaurar-btn-confirmar`.
 */
@Composable
fun FamilyRestoreConfirmDialog(backupDateLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    FamilyConfirmDialog(
        title = familiaString(Res.string.familia_restore_title),
        message = familiaString(Res.string.familia_restore_text, backupDateLabel),
        confirmLabel = familiaString(Res.string.familia_restore_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        testTag = "restaurar",
    )
}

/** Os textos das mensagens da cópia de segurança, para o app mostrar no aviso. */
data class FamilyBackupMessages(
    val restored: String,
    val saved: String,
    val wrongApp: String,
    val corrupt: String,
    val newerVersion: String,
)

/** Os [FamilyBackupMessages] no idioma do app. */
@Composable
fun rememberFamilyBackupMessages(): FamilyBackupMessages = FamilyBackupMessages(
    restored = familiaString(Res.string.familia_restored),
    saved = familiaString(Res.string.familia_saved),
    wrongApp = familiaString(Res.string.familia_backup_wrong_app),
    corrupt = familiaString(Res.string.familia_backup_corrupt),
    newerVersion = familiaString(Res.string.familia_backup_newer),
)

/** "Disponível no Premium." — o aviso do cadeado. */
@Composable
fun familyLockedMessage(): String = familiaString(Res.string.familia_locked)
