package br.com.codecacto.kmplib.familia.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import br.com.codecacto.kmplib.familia.generated.resources.*
import br.com.codecacto.kmplib.familia.familiaString
import br.com.codecacto.kmplib.familia.icons.FamilyIcons
import br.com.codecacto.kmplib.familia.theme.FamilyApp
import br.com.codecacto.kmplib.familia.theme.FamilyFixedColors
import br.com.codecacto.kmplib.familia.theme.FamilyTheme
import br.com.codecacto.kmplib.ui.components.AppDialog
import br.com.codecacto.kmplib.ui.components.AppModalBottomSheet
import kotlinx.coroutines.delay

/**
 * Estado vazio (`.empty`): selo 92 raio 30 com ícone grande, h1, frase `mu`, botão para o primeiro passo.
 */
@Composable
fun FamilyEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    testTag: String = "vazio",
) {
    val c = FamilyTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp).testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        FamilyBadge(icon, size = 92.dp, cornerRadius = 30.dp, iconSize = 48.dp)
        Text(title, style = FamilyTheme.type.h1.copy(textAlign = TextAlign.Center), color = c.fg)
        Text(message, style = FamilyTheme.type.body.copy(textAlign = TextAlign.Center), color = c.mu, modifier = Modifier.widthIn(max = 420.dp))
        if (actionLabel != null && onAction != null) {
            FamilyButton(actionLabel, onAction, variant = FamilyButtonVariant.SOFT, fullWidth = false, testTag = "$testTag-acao")
        }
    }
}

/** Erro (conteúdo que não abriu): selo de alerta + "Algo não carregou" + "Tentar de novo". */
@Composable
fun FamilyErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier, testTag: String = "erro") {
    FamilyEmptyState(
        icon = FamilyIcons.Alert,
        title = familiaString(Res.string.familia_error_title),
        message = familiaString(Res.string.familia_error_text),
        actionLabel = familiaString(Res.string.familia_try_again),
        onAction = onRetry,
        modifier = modifier,
        testTag = testTag,
    )
}

/** Carregando padrão: esqueletos com a altura aproximada do conteúdo (o topo e as abas ficam). */
@Composable
fun FamilyLoadingSkeletons(heights: List<Dp>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        heights.forEach { FamilySkeleton(it) }
    }
}

/** Estado do aviso rápido (`.toast`). Um por tela raiz (o [FamilyScaffold] cria e mostra). */
@Stable
class FamilyToastState {
    internal var current by mutableStateOf<FamilyToastMessage?>(null)
        private set

    /** Mostra [message] por 2,6 s (substitui o anterior). */
    fun show(message: String) {
        current = FamilyToastMessage(message, (current?.id ?: 0) + 1)
    }

    internal fun clear(id: Int) {
        if (current?.id == id) current = null
    }

    companion object {
        /** Quanto o aviso fica na tela (o do protótipo). */
        const val DURATION_MILLIS: Long = 2_600
    }
}

internal data class FamilyToastMessage(val text: String, val id: Int)

/** Cria o estado do aviso. */
@Composable
fun rememberFamilyToastState(): FamilyToastState = remember { FamilyToastState() }

/**
 * O aviso (`.toast`): 18 das bordas, fundo `fg`, texto `bg`, 700, com ✓; região viva (o leitor de tela
 * anuncia). [bottomOffset] = distância da base (152 acima da base com banner + abas — o scaffold calcula).
 */
@Composable
fun BoxScope.FamilyToastHost(state: FamilyToastState, bottomOffset: Dp) {
    val c = FamilyTheme.colors
    val msg = state.current
    LaunchedEffect(msg?.id) {
        if (msg != null) {
            delay(FamilyToastState.DURATION_MILLIS)
            state.clear(msg.id)
        }
    }
    AnimatedVisibility(
        visible = msg != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 18.dp, end = 18.dp, bottom = bottomOffset),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(c.fg)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("aviso"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(FamilyIcons.Check, null, tint = c.bg, modifier = Modifier.size(22.dp))
            Text(msg?.text.orEmpty(), style = FamilyTheme.type.sm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.bg)
        }
    }
}

/**
 * Folha de baixo (`.sheet`) — só para ESCOLHER (compartilhar, horário, cópia, seletor), nunca para
 * formulário (regra D15: formulário é diálogo ou tela). Raio 30 30 0 0, padding 12 18 28, gap 14, alça
 * 42×5 `ln`, véu do protótipo. Sobre `AppModalBottomSheet` (ids visíveis ao Maestro).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, testTag: String = "folha", content: @Composable ColumnScope.() -> Unit) {
    val c = FamilyTheme.colors
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = c.sf,
        contentColor = c.fg,
        scrimColor = FamilyFixedColors.scrim,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(42.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(c.ln))
        },
        modifier = modifier.testTag(testTag),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** Diálogo (`.dialog`): raio 28, padding 24 20 18, gap 14, fundo `sf`. Sobre `AppDialog`. */
@Composable
fun FamilyDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    testTag: String = "dialogo",
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = FamilyTheme.colors
    AppDialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible)) {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf).padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp).testTag(testTag),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Diálogo de confirmação no desenho do protótipo: h2 + texto `mu` + dois botões lado a lado. */
@Composable
fun FamilyConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = familiaString(Res.string.familia_cancel),
    destructive: Boolean = false,
    testTag: String = "dialogo",
) {
    val c = FamilyTheme.colors
    FamilyDialog(onDismiss, testTag = testTag) {
        Text(title, style = FamilyTheme.type.h2, color = c.fg)
        Text(message, style = FamilyTheme.type.body, color = c.mu)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FamilyButton(dismissLabel, onDismiss, Modifier.weight(1f), variant = FamilyButtonVariant.OUTLINE, testTag = "$testTag-btn-cancelar")
            FamilyButton(confirmLabel, onConfirm, Modifier.weight(1f), testTag = "$testTag-btn-confirmar")
        }
    }
}

/** "Abrindo a loja…" e afins: diálogo com o indicador e uma frase, sem fechar no toque fora. */
@Composable
fun FamilyProgressDialog(message: String, testTag: String = "dialogo-progresso") {
    val c = FamilyTheme.colors
    FamilyDialog(onDismiss = {}, dismissible = false, testTag = testTag) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FamilySpinner()
            Text(message, style = FamilyTheme.type.h3, color = c.fg, textAlign = TextAlign.Center)
        }
    }
}

/** Ícone de app em squircle (`.ai`, raio 23%). */
@Composable
fun FamilyAppIcon(app: FamilyApp, size: Dp, modifier: Modifier = Modifier) = FamilyAppIcon(app.icon, size, modifier)

/** Ícone de app em squircle a partir da arte. */
@Composable
fun FamilyAppIcon(art: ImageVector, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 23)
    androidx.compose.foundation.Image(
        art,
        contentDescription = null,
        modifier = modifier.size(size).clip(shape).border(1.dp, Color.Black.copy(alpha = 0.05f), shape),
    )
}

/** Tela cheia sem barras (boas-vindas, leitura): fundo `bg`, conteúdo central. */
@Composable
fun FamilyFullScreen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(FamilyTheme.colors.bg), content = content)
}
