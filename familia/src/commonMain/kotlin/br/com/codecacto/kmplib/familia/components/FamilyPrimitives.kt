package br.com.codecacto.kmplib.familia.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.familia.theme.FamilyFixedColors
import br.com.codecacto.kmplib.familia.theme.FamilyTheme
import br.com.codecacto.kmplib.platform.motion.LocalReduceMotion

// Medidas do `ds.css` (px do protótipo = dp). Ver `01-sistema-de-design.md` §4.

/** Margem lateral da tela (18) e respiro entre blocos (14). */
object FamilyDimens {
    val screenPadding: Dp = 18.dp
    val gap: Dp = 14.dp
    val topBarHeight: Dp = 68.dp
    val bannerHeight: Dp = 60.dp
    val tabsHeight: Dp = 78.dp
    val minTouch: Dp = 44.dp

    /** Padding do conteúdo de uma tela (`.ct`: 8 18 24). */
    val contentPadding: PaddingValues = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp)
}

/** Estilo do cartão (`.card`, `.card.tint`, `.card.hero`). */
enum class FamilyCardStyle { PLAIN, TINT, HERO }

/**
 * Cartão (`.card`): raio 22, borda 1 `ln`, padding 16 ([large] = 22), gap 10. `TINT` = fundo `acs` sem
 * borda; `HERO` = fundo `ac`, texto `aci`.
 */
@Composable
fun FamilyCard(
    modifier: Modifier = Modifier,
    style: FamilyCardStyle = FamilyCardStyle.PLAIN,
    large: Boolean = false,
    onClick: (() -> Unit)? = null,
    gap: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = FamilyTheme.colors
    val shape = RoundedCornerShape(22.dp)
    val (bg, fg) = when (style) {
        FamilyCardStyle.PLAIN -> c.sf to c.fg
        FamilyCardStyle.TINT -> c.acs to c.fg
        FamilyCardStyle.HERO -> c.ac to c.aci
    }
    var m = modifier.fillMaxWidth().clip(shape).background(bg)
    if (style == FamilyCardStyle.PLAIN) m = m.border(1.dp, c.ln, shape)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    CompositionLocalProvider(LocalContentColor provides fg) {
        Column(m.padding(if (large) 22.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(gap), content = content)
    }
}

/** Selo de ícone (`.badge`): 46×46, raio 15 (ou círculo), `acs`/`acd`. */
@Composable
fun FamilyBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    round: Boolean = false,
    iconSize: Dp = 24.dp,
    background: Color = FamilyTheme.colors.acs,
    contentColor: Color = FamilyTheme.colors.acd,
    cornerRadius: Dp = 15.dp,
) {
    Box(
        modifier.size(size).clip(if (round) CircleShape else RoundedCornerShape(cornerRadius)).background(background),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(iconSize)) }
}

/** Pílula de texto (`.pillt`): altura 26, `acs`/`acd`; [neutral] = `sf2`/`mu`. */
@Composable
fun FamilyPill(text: String, modifier: Modifier = Modifier, neutral: Boolean = false, icon: ImageVector? = null) {
    val c = FamilyTheme.colors
    Row(
        modifier.heightIn(min = 26.dp).clip(RoundedCornerShape(999.dp)).background(if (neutral) c.sf2 else c.acs).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val fg = if (neutral) c.mu else c.acd
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
        Text(text, style = FamilyTheme.type.xs.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 1)
    }
}

/** Chip (`.chip`): altura 44, borda 1,5 `ln`; ligado = invertido (fundo `fg`, texto `bg`). */
@Composable
fun FamilyChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String? = null,
) {
    val c = FamilyTheme.colors
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) c.fg else c.sf)
            .border(1.5.dp, if (selected) c.fg else c.ln, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        val fg = if (selected) c.bg else c.fg
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = FamilyTheme.type.sm.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 1)
    }
}

/** Linha de chips (`.chips`): rolagem horizontal; [wrap] = quebra linha (`.chips.wrap`). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyChipRow(modifier: Modifier = Modifier, wrap: Boolean = false, content: @Composable () -> Unit) {
    if (wrap) {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    } else {
        Row(modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/**
 * Segmentado (`.seg`): fundo `sf2`, raio 18, padding 4; botão altura 46 raio 14; ativo `sf` com sombra.
 * Ids `<testTag>-<índice>`.
 */
@Composable
fun FamilySegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val c = FamilyTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.sf2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .let { if (on) it.shadow(2.dp, RoundedCornerShape(14.dp)) else it }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) c.sf else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSelect(i) }
                    .let { m -> if (testTag != null) m.testTag("$testTag-$i") else m }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = FamilyTheme.type.sm.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    color = if (on) c.fg else c.mu,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Interruptor (`.tog`): 58×34, bolinha 26 branca; ligado `ac`. Semântica de chave. */
@Composable
fun FamilySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val c = FamilyTheme.colors
    val reduce = LocalReduceMotion.current
    val pos by animateFloatAsState(if (checked) 1f else 0f, if (reduce) tween(0) else tween(200), label = "tog")
    Box(
        modifier
            .size(58.dp, 34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (checked) c.ac else c.ln)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .let { m -> if (contentDescription != null) m.semantics { this.contentDescription = contentDescription } else m }
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Box(
            Modifier
                .offset(x = 4.dp + 24.dp * pos, y = 4.dp)
                .size(26.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(FamilyFixedColors.switchThumb),
        )
    }
}

/** Rádio (`.radio`): 26, borda 2 `mu`; ligado borda 8 `ac`. Só o desenho — o toque é da linha. */
@Composable
fun FamilyRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val c = FamilyTheme.colors
    Canvas(modifier.size(26.dp)) {
        val w = if (selected) 8.dp.toPx() else 2.dp.toPx()
        drawCircle(color = if (selected) c.ac else c.mu, radius = (size.minDimension - w) / 2f, style = Stroke(width = w))
    }
}

/** Caixa (`.cbx`): 30, raio 9, borda 2; ligada `ac` com ✓ `aci`. */
@Composable
fun FamilyCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val c = FamilyTheme.colors
    val shape = RoundedCornerShape(9.dp)
    var m = modifier.size(30.dp).clip(shape)
        .background(if (checked) c.ac else Color.Transparent)
        .border(2.dp, if (checked) c.ac else c.mu, shape)
    if (onCheckedChange != null) m = m.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
    if (contentDescription != null) m = m.semantics { this.contentDescription = contentDescription }
    Box(m, contentAlignment = Alignment.Center) {
        if (checked) Icon(br.com.codecacto.kmplib.familia.icons.FamilyIcons.Check, null, tint = c.aci, modifier = Modifier.size(18.dp))
    }
}

/** Progresso (`.prog`): altura 10, raio 5, trilho `sf2`, barra `ac`. [fraction] em 0..1. */
@Composable
fun FamilyProgress(fraction: Float, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val c = FamilyTheme.colors
    val f = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier.fillMaxWidth().height(10.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f)
            if (contentDescription != null) this.contentDescription = contentDescription
        },
    ) {
        val r = CornerRadius(5.dp.toPx())
        drawRoundRect(c.sf2, cornerRadius = r)
        if (f > 0f) drawRoundRect(c.ac, size = size.copy(width = size.width * f), cornerRadius = r)
    }
}

/** Estado do dia de calendário (`.day`). */
enum class FamilyDayState { NORMAL, DONE, TODAY, MISSED }

/** Dia de calendário (`.day`): quadrado raio 12; `DONE` = `ac`; `TODAY` = anel 2 `acd`; `MISSED` = anel 1,5 `ln`. */
@Composable
fun FamilyDayCell(
    label: String,
    state: FamilyDayState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val c = FamilyTheme.colors
    val shape = RoundedCornerShape(12.dp)
    var m = modifier.aspectRatio(1f).clip(shape)
    m = when (state) {
        FamilyDayState.NORMAL -> m.background(c.sf2)
        FamilyDayState.DONE -> m.background(c.ac)
        FamilyDayState.TODAY -> m.background(c.sf2).border(2.dp, c.acd, shape)
        FamilyDayState.MISSED -> m.border(1.5.dp, c.ln, shape)
    }
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    if (contentDescription != null) m = m.semantics { this.contentDescription = contentDescription }
    Box(m, contentAlignment = Alignment.Center) {
        Text(
            label,
            style = FamilyTheme.type.xs.copy(fontWeight = FontWeight.Bold),
            color = when (state) {
                FamilyDayState.DONE -> c.aci
                FamilyDayState.TODAY -> c.fg
                else -> c.mu
            },
        )
    }
}

/** Variante do botão (`.btn`, `.s2`, `.s3`, `.inv`). */
enum class FamilyButtonVariant { PRIMARY, SOFT, OUTLINE, INVERTED }

/**
 * Botão (`.btn`): altura mín. 56, raio 18, padding 0 22, 700. [small] = 46, raio 14. Desligado = opacidade
 * 0,45 (`.dis`). [loading] troca o ícone por um indicador e segura o clique.
 */
@Composable
fun FamilyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FamilyButtonVariant = FamilyButtonVariant.PRIMARY,
    small: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    fullWidth: Boolean = true,
    testTag: String? = null,
) {
    val c = FamilyTheme.colors
    val shape = RoundedCornerShape(if (small) 14.dp else 18.dp)
    val (bg, fg) = when (variant) {
        FamilyButtonVariant.PRIMARY -> c.ac to c.aci
        FamilyButtonVariant.SOFT -> c.acs to c.acd
        FamilyButtonVariant.OUTLINE -> Color.Transparent to c.fg
        FamilyButtonVariant.INVERTED -> c.aci to c.ac
    }
    var m = modifier.let { if (fullWidth) it.fillMaxWidth() else it }
        .defaultMinSize(minHeight = if (small) 46.dp else 56.dp)
        .clip(shape)
        .background(bg)
    if (variant == FamilyButtonVariant.OUTLINE) m = m.border(BorderStroke(1.5.dp, c.ln), shape)
    m = m.clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
    if (testTag != null) m = m.testTag(testTag)
    Row(
        m.alpha(if (enabled) 1f else 0.45f).padding(horizontal = if (small) 16.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (loading) FamilySpinner(size = 22.dp, strokeWidth = 3.dp, color = fg, track = fg.copy(alpha = 0.35f))
        else if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        val style = FamilyTheme.type.button
        Text(text, style = if (small) style.copy(fontSize = style.fontSize * 0.88f) else style, color = fg, textAlign = TextAlign.Center)
    }
}

/** Uma ação da grade (`.act`). */
data class FamilyAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Grade de ações (`.acts`): [columns] colunas (4), gap 8; botão raio 18, altura mín. 76, ícone + rótulo
 * 0,74em; `selected` = `acs`/`acd`. Ids `<testTag>-<id>`.
 */
@Composable
fun FamilyActionGrid(actions: List<FamilyAction>, modifier: Modifier = Modifier, columns: Int = 4, testTag: String = "acoes") {
    val c = FamilyTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    val shape = RoundedCornerShape(18.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 76.dp)
                            .clip(shape)
                            .background(if (a.selected) c.acs else c.sf)
                            .let { if (!a.selected) it.border(1.dp, c.ln, shape) else it }
                            .clickable(enabled = a.enabled, role = Role.Button, onClick = a.onClick)
                            .testTag("$testTag-${a.id}")
                            .alpha(if (a.enabled) 1f else 0.45f)
                            .padding(horizontal = 2.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        val fg = if (a.selected) c.acd else c.fg
                        Icon(a.icon, null, tint = fg, modifier = Modifier.size(24.dp))
                        Text(
                            a.label,
                            style = FamilyTheme.type.xs.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                            color = fg,
                            maxLines = 2,
                        )
                    }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Botão flutuante (`.fab`): 64×64, raio 22, `ac`/`aci`, sombra. A posição é do [FamilyScaffold]. */
@Composable
fun FamilyFab(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, testTag: String = "fab") {
    val c = FamilyTheme.colors
    Box(
        modifier
            .size(64.dp)
            .shadow(10.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(c.ac)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.aci, modifier = Modifier.size(28.dp)) }
}

/** Indicador (`.spin`): 38, traço 4 `ln`, arco `ac`, girando 0,9 s. Parado com "reduzir movimento". */
@Composable
fun FamilySpinner(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    strokeWidth: Dp = 4.dp,
    color: Color = FamilyTheme.colors.ac,
    track: Color = FamilyTheme.colors.ln,
) {
    val reduce = LocalReduceMotion.current
    val angle = if (reduce) 0f else {
        val t = rememberInfiniteTransition(label = "spin")
        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinA")
        a
    }
    Canvas(modifier.size(size)) {
        val w = strokeWidth.toPx()
        val inset = w / 2
        drawCircle(track, radius = this.size.minDimension / 2 - inset, style = Stroke(w))
        drawArc(
            color, startAngle = angle - 90f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(inset, inset), size = this.size.copy(this.size.width - w, this.size.height - w),
            style = Stroke(w, cap = StrokeCap.Butt),
        )
    }
}

/** Esqueleto (`.skel`): raio 18, brilho `sf2`→`ln` em 1,4 s (parado com "reduzir movimento"). */
@Composable
fun FamilySkeleton(height: Dp, modifier: Modifier = Modifier, cornerRadius: Dp = 18.dp) {
    val c = FamilyTheme.colors
    val reduce = LocalReduceMotion.current
    val shift = if (reduce) 0.5f else {
        val t = rememberInfiniteTransition(label = "skel")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "skelV")
        v
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val x = -w + 2 * w * (1f - shift)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(c.sf2, c.ln, c.sf2), startX = x, endX = x + 2 * w),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
        )
    }
}

/** Sobretítulo (`.ey`): CAIXA ALTA no idioma, cor `mu`. */
@Composable
fun FamilyEyebrow(text: String, modifier: Modifier = Modifier, color: Color = FamilyTheme.colors.mu) {
    Text(text.toUpperCase(Locale.current), style = FamilyTheme.type.ey, color = color, modifier = modifier)
}

/** Aviso em faixa (`.note`): raio 18, fundo `sf2`, 0,86em, ícone opcional. */
@Composable
fun FamilyNote(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = FamilyTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.sf2).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = c.mu, modifier = Modifier.size(20.dp))
        Text(text, style = FamilyTheme.type.sm, color = c.fg)
    }
}

/** Linha "entre" (`.between`): itens nas pontas, gap 12. */
@Composable
fun FamilyBetween(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, content = content)
}

/** Botão de ícone (`.ib`): 48×48 círculo; [filled] = `sf` com borda `ln`. */
@Composable
fun FamilyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
    testTag: String? = null,
) {
    val c = FamilyTheme.colors
    var m = modifier.size(48.dp).clip(CircleShape)
    if (filled) m = m.background(c.sf).border(1.dp, c.ln, CircleShape)
    m = m.clickable(role = Role.Button, onClick = onClick).semantics { this.contentDescription = contentDescription }
    if (testTag != null) m = m.testTag(testTag)
    Box(m, contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
}

/** Respiro horizontal — evita `Spacer(Modifier.width(…))` repetido. */
@Composable
internal fun HGap(w: Dp) = Box(Modifier.width(w))
