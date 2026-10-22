package br.com.codecacto.kmplib.inspection.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.theme.InspectionTokens
import br.com.codecacto.kmplib.ui.components.rememberSyncedTextFieldState

/** Medidas fixas do kit (§1.4). */
object InspectionDimens {
    val ControlRadius: Dp = 6.dp
    val SmallRadius: Dp = 4.dp
    val DialogRadius: Dp = 8.dp
    val TouchTarget: Dp = 44.dp
    val FooterButtonHeight: Dp = 52.dp
    val ScreenPadding: Dp = 16.dp
    val TopBarHeight: Dp = 56.dp
    val TopBarHeightWithSubtitle: Dp = 64.dp
}

/**
 * Estilo de texto do kit. [mono] = prova (data, hora, hash, GPS, contador), sempre com algarismos
 * tabulares; [tabular] liga os algarismos tabulares no texto sans também ("5 de 8").
 */
@Composable
fun inspectionTextStyle(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = InspectionTheme.tokens.ink,
    mono: Boolean = false,
    serif: Boolean = false,
    tabular: Boolean = false,
    lineHeight: TextUnit = TextUnit.Unspecified,
): TextStyle {
    val t = InspectionTheme.tokens
    val family: FontFamily = when {
        mono -> t.fonts.mono
        serif -> t.fonts.serif
        else -> t.sans
    }
    return TextStyle(
        fontFamily = family,
        fontSize = size,
        fontWeight = weight,
        color = color,
        lineHeight = lineHeight,
        fontFeatureSettings = if (mono || tabular) "tnum" else null,
    )
}

/** Borda tracejada (botão "Adicionar", tile de adicionar, foto ausente). */
fun Modifier.dashedBorder(color: Color, width: Dp = 1.5.dp, radius: Dp = InspectionDimens.ControlRadius): Modifier =
    drawBehind {
        val w = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        )
    }

/**
 * A barra do topo do kit: 56 dp (64 com subtítulo), ícone de navegação 44×44 à esquerda, título
 * 21/600 e subtítulo 13 (em `warn` 600 quando é resumo de problema). Aplica o inset da barra de status.
 */
@Composable
fun InspectionTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleIsWarning: Boolean = false,
    navigationIcon: InspectionIcon? = InspectionIcon.ArrowBack,
    navigationDescription: String? = null,
    onNavigate: (() -> Unit)? = null,
    titleSize: TextUnit = 21.sp,
    titleWeight: FontWeight = FontWeight.SemiBold,
    showDivider: Boolean = false,
    background: Color = InspectionTheme.tokens.bg,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = InspectionTheme.tokens
    Column(modifier.background(background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (subtitle != null) InspectionDimens.TopBarHeightWithSubtitle else InspectionDimens.TopBarHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (navigationIcon != null && onNavigate != null) {
                KitIconButton(
                    icon = navigationIcon,
                    description = navigationDescription,
                    onClick = onNavigate,
                    tint = t.ink,
                    testTag = InspectionTestTags.TOPBAR_BACK,
                )
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = inspectionTextStyle(titleSize, titleWeight),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }.testTag(InspectionTestTags.TOPBAR_TITLE),
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = inspectionTextStyle(
                            13.sp,
                            if (subtitleIsWarning) FontWeight.SemiBold else FontWeight.Normal,
                            if (subtitleIsWarning) t.warn.fg else t.muted,
                            tabular = true,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
        if (showDivider) HorizontalDivider(thickness = 1.dp, color = t.line)
    }
}

/** Ação de texto na barra do topo ("Revisão", "Salvar"): `brand` 15/600, alvo 44. */
@Composable
fun InspectionTopBarTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: TextUnit = 15.sp,
    weight: FontWeight = FontWeight.SemiBold,
) {
    val t = InspectionTheme.tokens
    Box(
        modifier = modifier
            .heightIn(min = InspectionDimens.TouchTarget)
            .clip(RoundedCornerShape(InspectionDimens.ControlRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = inspectionTextStyle(size, weight, if (enabled) t.brand else t.muted))
    }
}

/** Botão de ícone 44×44. [description] `null` só quando há texto equivalente ao lado. */
@Composable
fun KitIconButton(
    icon: InspectionIcon,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = InspectionTheme.tokens.muted,
    size: Dp = 24.dp,
    filled: Boolean = false,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Box(
        modifier = modifier
            .size(InspectionDimens.TouchTarget)
            .clip(RoundedCornerShape(InspectionDimens.ControlRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        InspectionIconView(icon, filled = filled, size = size, tint = tint, contentDescription = description)
    }
}

/**
 * O rodapé de ação do kit: **espaço próprio** (não flutua sobre a lista), `padding 12 16 28`
 * (o fundo inclui a barra de gestos), borda superior 1 dp, fundo `surface`.
 */
@Composable
fun InspectionFooter(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = InspectionTheme.tokens
    Column(modifier.fillMaxWidth().background(t.surface)) {
        HorizontalDivider(thickness = 1.dp, color = t.line)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/**
 * Botão primário do kit: 52 dp, raio 6, 16/600, `brand`/`onBrand`. Desabilitado = `sunken`/`muted`,
 * sem borda. [loading] mostra o indicador e desliga o toque (salvando).
 */
@Composable
fun InspectionPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: InspectionIcon? = null,
    height: Dp = InspectionDimens.FooterButtonHeight,
    testTag: String? = null,
) {
    val t = InspectionTheme.tokens
    val ativo = enabled && !loading
    val fundo = if (enabled) t.brand else t.sunken
    val conteudo = if (enabled) t.onBrand else t.muted
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(InspectionDimens.ControlRadius))
            .background(fundo)
            .clickable(enabled = ativo, role = Role.Button, onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = conteudo, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            InspectionIconView(icon, size = 20.dp, tint = conteudo)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = inspectionTextStyle(16.sp, FontWeight.SemiBold, conteudo), maxLines = 1)
    }
}

/** Botão secundário: borda 1 `line`, fundo `surface`, texto/ícone em [contentColor] (default `ink`). */
@Composable
fun InspectionSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: InspectionIcon? = null,
    enabled: Boolean = true,
    height: Dp = InspectionDimens.FooterButtonHeight,
    contentColor: Color = InspectionTheme.tokens.ink,
    textSize: TextUnit = 16.sp,
    testTag: String? = null,
) {
    val t = InspectionTheme.tokens
    val cor = if (enabled) contentColor else t.muted
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(InspectionDimens.ControlRadius))
            .background(t.surface)
            .border(BorderStroke(1.dp, t.line), RoundedCornerShape(InspectionDimens.ControlRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            InspectionIconView(icon, size = 20.dp, tint = cor)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = inspectionTextStyle(textSize, FontWeight.SemiBold, cor), maxLines = 1)
    }
}

/** O botão tracejado de adicionar ("+ Adicionar cômodo"): 52 dp, borda 1,5 tracejada, `brand` 15/600. */
@Composable
fun InspectionDashedAddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = InspectionDimens.ControlRadius,
    textSize: TextUnit = 15.sp,
    weight: FontWeight = FontWeight.SemiBold,
    testTag: String? = null,
) {
    val t = InspectionTheme.tokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .dashedBorder(t.line, radius = radius)
            .clip(RoundedCornerShape(radius))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InspectionIconView(InspectionIcon.Add, size = 20.dp, tint = t.brand)
        Spacer(Modifier.width(6.dp))
        Text(text, style = inspectionTextStyle(textSize, weight, t.brand))
    }
}

/** Rótulo de seção (14/600 `ink`), com sufixo opcional em 400 `muted` ("· obrigatória"). */
@Composable
fun InspectionSectionLabel(text: String, modifier: Modifier = Modifier, suffix: String? = null) {
    val t = InspectionTheme.tokens
    Row(modifier.semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = inspectionTextStyle(14.sp, FontWeight.SemiBold))
        if (suffix != null) {
            Text(" · $suffix", style = inspectionTextStyle(14.sp, FontWeight.Normal, t.muted))
        }
    }
}

/** Eyebrow de coluna: 12/700 MAIÚSCULAS, espaçamento .08em, `muted`. */
@Composable
fun InspectionEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = inspectionTextStyle(12.sp, FontWeight.Bold, InspectionTheme.tokens.muted)
            .copy(letterSpacing = 0.96.sp),
        modifier = modifier,
    )
}

/**
 * Campo de observação do kit: área mín. [minHeight], raio 6, `padding 12`, 15 sp, entrelinha 1,4,
 * borda 1 `line` (1,5 `brand` com foco). O texto MORA no campo (`TextFieldState`) e o estado da tela
 * só recebe — é o que impede o iOS de engolir letras em digitação rápida.
 */
@Composable
fun InspectionNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 72.dp,
    enabled: Boolean = true,
    label: String? = null,
    testTag: String? = null,
) {
    val t = InspectionTheme.tokens
    val state = rememberSyncedTextFieldState(value, onValueChange, saveable = false)
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) InspectionSectionLabel(label)
        BasicTextField(
            state = state,
            enabled = enabled,
            textStyle = inspectionTextStyle(15.sp, color = if (enabled) t.ink else t.muted, lineHeight = 21.sp),
            cursorBrush = SolidColor(t.brand),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .clip(RoundedCornerShape(InspectionDimens.ControlRadius))
                .background(if (enabled) t.surface else t.sunken)
                .border(
                    if (focused) 1.5.dp else 1.dp,
                    if (focused) t.brand else t.line,
                    RoundedCornerShape(InspectionDimens.ControlRadius),
                )
                .onFocusChanged { focused = it.isFocused }
                .padding(12.dp)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        )
    }
}

/** Bloco de esqueleto (`sunken`, raio 3). */
@Composable
fun InspectionSkeletonBlock(modifier: Modifier = Modifier, tokens: InspectionTokens = InspectionTheme.tokens) {
    Box(modifier.clip(RoundedCornerShape(3.dp)).background(tokens.sunken))
}
