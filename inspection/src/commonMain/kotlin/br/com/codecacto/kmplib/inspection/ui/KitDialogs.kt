package br.com.codecacto.kmplib.inspection.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.ui.components.AppDialog
import br.com.codecacto.kmplib.platform.automation.DialogTestTags
import br.com.codecacto.kmplib.ui.components.dismissKeyboardOnTapOutside
import br.com.codecacto.kmplib.ui.components.rememberSyncedTextFieldState

/**
 * O diálogo do kit (§2.11 "Finalizar a vistoria?"): cartão raio 8, `padding 24`, título 20/600,
 * texto 15 `muted` entrelinha 1,45, ações à direita — a de saída em texto `brand` 600 e a principal
 * preenchida (44 dp, raio 6). [destructive] pinta a principal em `bad` (remover, descartar).
 *
 * Sobre o `AppDialog` da lib: janela própria visível ao Maestro (`dialogo-*`), voltar do sistema
 * fecha (salvo [busy]).
 */
@Composable
fun InspectionDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissText: String? = null,
    destructive: Boolean = false,
    busy: Boolean = false,
    confirmEnabled: Boolean = true,
    errorMessage: String? = null,
    confirmTestTag: String = DialogTestTags.BTN_CONFIRMAR,
    dismissTestTag: String = DialogTestTags.BTN_CANCELAR,
    maxWidth: Dp = 420.dp,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val t = InspectionTheme.tokens
    AppDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
    ) {
        Column(
            modifier = modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .clip(RoundedCornerShape(InspectionDimens.DialogRadius))
                .background(t.surface)
                .dismissKeyboardOnTapOutside()
                .semantics { paneTitle = title }
                .testTag(DialogTestTags.CONTAINER)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = inspectionTextStyle(20.sp, FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() }.testTag(DialogTestTags.TITULO),
            )
            if (message != null) {
                Text(
                    message,
                    style = inspectionTextStyle(15.sp, color = t.muted, lineHeight = 21.75.sp),
                    modifier = Modifier.testTag(DialogTestTags.MENSAGEM),
                )
            }
            content?.invoke(this)
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    style = inspectionTextStyle(13.sp, color = t.bad.fg),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(DialogTestTags.ERRO),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissText != null) {
                    Box(
                        modifier = Modifier
                            .height(InspectionDimens.TouchTarget)
                            .clip(RoundedCornerShape(InspectionDimens.ControlRadius))
                            .clickable(enabled = !busy, role = Role.Button, onClick = onDismiss)
                            .testTag(dismissTestTag)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(dismissText, style = inspectionTextStyle(15.sp, FontWeight.SemiBold, t.brand))
                    }
                }
                val ativo = confirmEnabled && !busy
                val fundo = when {
                    !confirmEnabled -> t.sunken
                    destructive -> t.bad.fg
                    else -> t.brand
                }
                val texto = when {
                    !confirmEnabled -> t.muted
                    destructive -> if (t.isDark) t.bg else Color.White
                    else -> t.onBrand
                }
                Row(
                    modifier = Modifier
                        .height(InspectionDimens.TouchTarget)
                        .clip(RoundedCornerShape(InspectionDimens.ControlRadius))
                        .background(fundo)
                        .clickable(enabled = ativo, role = Role.Button, onClick = onConfirm)
                        .testTag(confirmTestTag)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.height(16.dp), color = texto, strokeWidth = 2.dp)
                        Spacer(Modifier.padding(start = 8.dp))
                    }
                    Text(confirmText, style = inspectionTextStyle(15.sp, FontWeight.SemiBold, texto))
                }
            }
        }
    }
}

/**
 * Campo de uma linha no desenho do kit (§1.4: altura 48, raio 6, borda 1 `line`, foco 1,5 `brand`,
 * erro 1,5 `bad` + frase 13 embaixo, rótulo 14/600 acima). O texto mora no campo (`TextFieldState`).
 */
@Composable
fun InspectionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    textSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    testTag: String? = null,
) {
    val t = InspectionTheme.tokens
    val state = rememberSyncedTextFieldState(value, onValueChange, saveable = false)
    var focado by remember { mutableStateOf(false) }
    val borda = when {
        errorMessage != null -> t.bad.fg
        focado -> t.brand
        else -> t.line
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = inspectionTextStyle(14.sp, FontWeight.SemiBold))
        BasicTextField(
            state = state,
            enabled = enabled,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = inspectionTextStyle(textSize, color = if (enabled) t.ink else t.muted),
            cursorBrush = SolidColor(t.brand),
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(InspectionDimens.ControlRadius))
                .background(if (enabled) t.surface else t.sunken)
                .border(
                    if (focado || errorMessage != null) 1.5.dp else 1.dp,
                    borda,
                    RoundedCornerShape(InspectionDimens.ControlRadius),
                )
                .onFocusChanged { focado = it.isFocused }
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .semantics { if (errorMessage != null) error(errorMessage) },
            decorator = { campo ->
                Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) { campo() }
            },
        )
        if (errorMessage != null) {
            Text(errorMessage, style = inspectionTextStyle(13.sp, color = t.bad.fg))
        }
    }
}
