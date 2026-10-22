package br.com.codecacto.kmplib.inspection.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.chipLabel
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.label
import br.com.codecacto.kmplib.inspection.model.Divergence
import br.com.codecacto.kmplib.inspection.model.ExitResult
import br.com.codecacto.kmplib.inspection.model.Functioning
import br.com.codecacto.kmplib.inspection.model.InspectionValue
import br.com.codecacto.kmplib.inspection.model.ItemCondition
import br.com.codecacto.kmplib.inspection.model.MediaUploadStatus
import br.com.codecacto.kmplib.inspection.model.SignatureStatus
import br.com.codecacto.kmplib.inspection.model.VerificationOutcome
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.theme.InspectionTokens
import br.com.codecacto.kmplib.inspection.theme.InspectionTone
import br.com.codecacto.kmplib.inspection.tileLabel
import br.com.codecacto.kmplib.platform.getClipboard
import kotlinx.coroutines.delay

// ───────────────────────────── Chip de estado (§2.1) ─────────────────────────────

/**
 * **Chip de estado** do kit: 26 dp, raio 4, fundo `soft` do tom, texto `fg` 13/600, ícone 16
 * `FILL 1` à esquerda, sem borda. Não é clicável (clicável é tile). Lido como o próprio rótulo —
 * nunca "ícone + cor".
 *
 * Com [count] vira o chip **contador** (30 dp, 14/600, número em mono: "**82** Conformes").
 */
@Composable
fun InspectionStateChip(
    tone: InspectionTone,
    icon: InspectionIcon,
    label: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val cores = InspectionTheme.tokens.tone(tone)
    val contador = count != null
    Row(
        modifier = modifier
            .height(if (contador) 30.dp else 26.dp)
            .clip(RoundedCornerShape(InspectionDimens.SmallRadius))
            .background(cores.soft)
            .padding(start = if (contador) 10.dp else 7.dp, end = if (contador) 10.dp else 9.dp)
            .clearAndSetSemantics { contentDescription = if (count != null) "$count $label" else label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        InspectionIconView(icon, filled = true, size = if (contador) 17.dp else 16.dp, tint = cores.fg)
        if (count != null) {
            Text(count.toString(), style = inspectionTextStyle(14.sp, FontWeight.SemiBold, cores.fg, mono = true))
        }
        Text(
            text = label,
            style = inspectionTextStyle(if (contador) 14.sp else 13.sp, FontWeight.SemiBold, cores.fg),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Chip de qualquer valor de eixo, com o rótulo já resolvido. */
@Composable
fun InspectionStateChip(value: InspectionValue, label: String, modifier: Modifier = Modifier, count: Int? = null) =
    InspectionStateChip(value.tone, value.icon, label, modifier, count)

@Composable
fun InspectionStateChip(value: ItemCondition, modifier: Modifier = Modifier, texts: InspectionTexts = rememberInspectionTexts()) =
    InspectionStateChip(value, texts.values.label(value), modifier)

@Composable
fun InspectionStateChip(value: ExitResult, modifier: Modifier = Modifier, texts: InspectionTexts = rememberInspectionTexts()) =
    InspectionStateChip(value, texts.values.label(value), modifier)

@Composable
fun InspectionStateChip(value: Functioning, modifier: Modifier = Modifier, texts: InspectionTexts = rememberInspectionTexts()) =
    InspectionStateChip(value, texts.values.chipLabel(value), modifier)

@Composable
fun InspectionStateChip(value: Divergence, modifier: Modifier = Modifier, texts: InspectionTexts = rememberInspectionTexts()) =
    InspectionStateChip(value, texts.values.label(value), modifier)

@Composable
fun InspectionStateChip(value: MediaUploadStatus, modifier: Modifier = Modifier, texts: InspectionTexts = rememberInspectionTexts()) =
    InspectionStateChip(value, texts.values.label(value), modifier)

@Composable
fun InspectionStateChip(value: SignatureStatus, modifier: Modifier = Modifier, texts: InspectionTexts = rememberInspectionTexts()) =
    InspectionStateChip(value, texts.values.label(value), modifier)

@Composable
fun InspectionStateChip(value: VerificationOutcome, modifier: Modifier = Modifier, texts: InspectionTexts = rememberInspectionTexts()) =
    InspectionStateChip(value, texts.values.label(value), modifier)

/** O chip "Tarefa" do item de tarefa guiada (tom da marca). */
@Composable
fun InspectionTaskChip(modifier: Modifier = Modifier, texts: InspectionTexts = rememberInspectionTexts()) =
    InspectionStateChip(InspectionTone.BRAND, InspectionIcon.TaskAlt, texts.values.guidedTask, modifier)

// ───────────────────────────── Tiles de escolha (§2.2) ─────────────────────────────

/** Uma opção de [InspectionChoiceTiles]. */
data class InspectionTile<T>(
    val value: T,
    val tone: InspectionTone,
    val icon: InspectionIcon,
    val label: String,
    /** Chave estável do id de teste (`<grupo>-<key>`). */
    val key: String,
)

/** Arranjo do tile: ícone SOBRE o rótulo (estado, 5 colunas) ou AO LADO (demais). */
enum class InspectionTileLayout { VERTICAL, HORIZONTAL }

/**
 * **Grade de opções com tom** (§2.2) — escolha EXCLUSIVA (não há "nenhum": o item nasce Bom).
 * Tiles com gap 6, raio 6, borda 1,5. Selecionado: borda e texto no `fg` do tom, fundo `soft`,
 * texto 700, ícone `FILL 1`. Não selecionado: borda `line`, fundo `surface`, texto `ink` 500, ícone
 * já na cor do tom (o semáforo se lê sem selecionar). Desabilitado: `sunken`/`muted` (o escolhido
 * continua no tom, para o laudo assinado ainda dizer o que foi escolhido).
 *
 * Erro (divergência obrigatória faltando): borda `bad` em volta do grupo + frase 13 `bad` embaixo.
 * Acessível como grupo de rádio. Com fonte grande, o rótulo **encolhe** em vez de quebrar a palavra.
 */
@Composable
fun <T> InspectionChoiceTiles(
    options: List<InspectionTile<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = options.size,
    layout: InspectionTileLayout = InspectionTileLayout.HORIZONTAL,
    height: Dp = 48.dp,
    filledIcons: Boolean = true,
    enabled: Boolean = true,
    title: String? = null,
    titleSuffix: String? = null,
    errorMessage: String? = null,
    testTag: String = "vistoria-opcoes",
) {
    val t = InspectionTheme.tokens
    val colunas = columns.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) InspectionSectionLabel(title, suffix = titleSuffix)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (errorMessage != null) {
                        Modifier
                            .border(1.5.dp, t.bad.fg, RoundedCornerShape(InspectionDimens.ControlRadius + 2.dp))
                            .padding(4.dp)
                    } else {
                        Modifier
                    },
                )
                .selectableGroup()
                .testTag(testTag)
                .semantics { if (errorMessage != null) error(errorMessage) },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.chunked(colunas).forEach { linha ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    linha.forEach { opcao ->
                        ChoiceTile(
                            option = opcao,
                            selected = opcao.value == selected,
                            enabled = enabled,
                            layout = layout,
                            height = height,
                            filledIcon = filledIcons,
                            testTag = "$testTag-${opcao.key}",
                            onClick = { onSelect(opcao.value) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(colunas - linha.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (errorMessage != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                InspectionIconView(InspectionIcon.Error, filled = true, size = 16.dp, tint = t.bad.fg)
                Text(
                    errorMessage,
                    style = inspectionTextStyle(13.sp, color = t.bad.fg),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceTile(
    option: InspectionTile<T>,
    selected: Boolean,
    enabled: Boolean,
    layout: InspectionTileLayout,
    height: Dp,
    filledIcon: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val t = InspectionTheme.tokens
    val tom = t.tone(option.tone)
    val desligado = !enabled && !selected
    val fundo = when {
        selected -> tom.soft
        desligado -> t.sunken
        else -> t.surface
    }
    val borda = when {
        selected -> tom.fg
        desligado -> t.sunken
        else -> t.line
    }
    val corTexto = when {
        selected -> tom.fg
        desligado -> t.muted
        else -> t.ink
    }
    val corIcone = if (desligado) t.muted else tom.fg
    val forma = RoundedCornerShape(InspectionDimens.ControlRadius)
    val estilo = inspectionTextStyle(
        if (layout == InspectionTileLayout.VERTICAL) 13.sp else 14.sp,
        if (selected) FontWeight.Bold else FontWeight.Medium,
        corTexto,
    )
    val conteudo: @Composable () -> Unit = {
        InspectionIconView(
            option.icon,
            filled = filledIcon,
            size = if (layout == InspectionTileLayout.VERTICAL) 22.dp else 18.dp,
            tint = corIcone,
        )
        BasicText(
            text = option.label,
            style = estilo.copy(textAlign = TextAlign.Center),
            maxLines = if (layout == InspectionTileLayout.VERTICAL) 1 else 2,
            softWrap = layout != InspectionTileLayout.VERTICAL,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = estilo.fontSize, stepSize = 0.5.sp),
        )
    }
    Box(
        modifier = modifier
            .height(height)
            .clip(forma)
            .background(fundo)
            .border(1.5.dp, borda, forma)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (layout == InspectionTileLayout.VERTICAL) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                conteudo()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                conteudo()
            }
        }
    }
}

/** Tiles de **Estado** (5 colunas, 64 dp, ícone sobre o rótulo). */
@Composable
fun InspectionConditionTiles(
    selected: ItemCondition?,
    onSelect: (ItemCondition) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String? = null,
    testTag: String = InspectionTestTags.ITEM_CONDITION,
    texts: InspectionTexts = rememberInspectionTexts(),
) = InspectionChoiceTiles(
    options = ItemCondition.entries.map { InspectionTile(it, it.tone, it.icon, texts.values.label(it), it.key) },
    selected = selected,
    onSelect = onSelect,
    modifier = modifier,
    columns = 5,
    layout = InspectionTileLayout.VERTICAL,
    height = 64.dp,
    enabled = enabled,
    title = title,
    testTag = testTag,
)

/** Tiles de **Funciona?** (3 colunas, 48 dp: Sim · Não · Não testado). */
@Composable
fun InspectionFunctioningTiles(
    selected: Functioning?,
    onSelect: (Functioning) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String? = null,
    testTag: String = InspectionTestTags.ITEM_FUNCTIONING,
    texts: InspectionTexts = rememberInspectionTexts(),
) = InspectionChoiceTiles(
    options = Functioning.entries.map { InspectionTile(it, it.tone, it.icon, texts.values.tileLabel(it), it.key) },
    selected = selected,
    onSelect = onSelect,
    modifier = modifier,
    columns = 3,
    height = 48.dp,
    enabled = enabled,
    title = title,
    testTag = testTag,
)

/** Tiles de **Resultado** da saída (3 colunas, 52 dp). */
@Composable
fun InspectionResultTiles(
    selected: ExitResult?,
    onSelect: (ExitResult) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String? = null,
    testTag: String = InspectionTestTags.EXIT_RESULT,
    texts: InspectionTexts = rememberInspectionTexts(),
) = InspectionChoiceTiles(
    options = ExitResult.entries.map { InspectionTile(it, it.tone, it.icon, texts.values.label(it), it.key) },
    selected = selected,
    onSelect = onSelect,
    modifier = modifier,
    columns = 3,
    height = 52.dp,
    enabled = enabled,
    title = title,
    testTag = testTag,
)

/** Tiles de **Divergência** (2 colunas, 48 dp, ícone `FILL 0`). */
@Composable
fun InspectionDivergenceTiles(
    selected: Divergence?,
    onSelect: (Divergence) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: String? = null,
    titleSuffix: String? = null,
    errorMessage: String? = null,
    testTag: String = InspectionTestTags.EXIT_DIVERGENCE,
    texts: InspectionTexts = rememberInspectionTexts(),
) = InspectionChoiceTiles(
    options = Divergence.entries.map { InspectionTile(it, it.tone, it.icon, texts.values.label(it), it.key) },
    selected = selected,
    onSelect = onSelect,
    modifier = modifier,
    columns = 2,
    height = 48.dp,
    filledIcons = false,
    enabled = enabled,
    title = title,
    titleSuffix = titleSuffix,
    errorMessage = errorMessage,
    testTag = testTag,
)

// ───────────────────────────── Prova (§2.4) ─────────────────────────────

/**
 * Texto de **prova** — data, hora, hash, GPS, contador — em mono com algarismos tabulares. Escalas
 * do kit: 20/600 (data-hora grande), 15, 14/500 (hash no cartão), 13 (hash na verificação), 12
 * `muted` (data · GPS), 11 (carimbo).
 */
@Composable
fun ProofText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 13.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = InspectionTheme.tokens.ink,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        style = inspectionTextStyle(size, weight, color, mono = true, lineHeight = lineHeight),
        modifier = modifier,
        maxLines = maxLines,
    )
}

/**
 * **Campo de hash**: rótulo 13 `muted` acima ("Hash do documento"), caixa raio 6 com borda `line`,
 * o hash INTEIRO em mono 13 (quebra em qualquer ponto) e o botão copiar 44×44 na cor da marca. Ao
 * copiar, o botão vira "Copiado" por 2 s (anunciado ao leitor de tela). Copia pela área de
 * transferência da plataforma; [onCopy] avisa o app (registro de evento).
 */
@Composable
fun HashField(
    hash: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    onCopy: (() -> Unit)? = null,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    var copiado by remember { mutableStateOf(false) }
    LaunchedEffect(copiado) {
        if (copiado) {
            delay(COPIED_FEEDBACK_MILLIS)
            copiado = false
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label ?: texts.report.hashLabel, style = inspectionTextStyle(13.sp, color = t.muted))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(InspectionDimens.ControlRadius))
                .background(t.surface)
                .border(1.dp, t.line, RoundedCornerShape(InspectionDimens.ControlRadius))
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProofText(
                text = hash,
                size = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            if (copiado) {
                Text(
                    texts.common.copied,
                    style = inspectionTextStyle(13.sp, FontWeight.SemiBold, t.good.fg),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                KitIconButton(
                    icon = InspectionIcon.ContentCopy,
                    description = texts.report.copyHash,
                    tint = t.brand,
                    size = 20.dp,
                    testTag = InspectionTestTags.HASH_COPY,
                    onClick = {
                        getClipboard().copy(hash, label = texts.report.hashLabel)
                        copiado = true
                        onCopy?.invoke()
                    },
                )
            }
        }
    }
}

private const val COPIED_FEEDBACK_MILLIS = 2_000L

/**
 * **Faixa de carimbo** na base da foto EXIBIDA (§2.4): fundo preto 66 %, texto branco mono. Compacta
 * (app, miniatura): só a data-hora curta (`14/09/25 09:12`), mono 11, `padding 3 6`. Grande (web,
 * tela cheia): data-hora completa · endereço · coordenadas, mono 12, em colunas que quebram.
 *
 * É sobreposição da CÓPIA exibida: o arquivo original e o hash nunca mudam. Sem GPS, passe
 * `InspectionCameraTexts.noLocation` em [coordinates]; hora do servidor pendente = [timeConfirmed]
 * `false` (D2: o carimbo diz "hora não confirmada").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoStampStrip(
    dateTime: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    address: String? = null,
    coordinates: String? = null,
    timeConfirmed: Boolean = true,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val estilo = inspectionTextStyle(if (large) 12.sp else 11.sp, color = Color.White, mono = true)
    val hora = if (timeConfirmed) dateTime else "$dateTime · ${texts.camera.timeNotConfirmed}"
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .background(if (large) InspectionTokens.PhotoStampLargeBackground else InspectionTokens.PhotoStampBackground)
            .padding(horizontal = if (large) 12.dp else 6.dp, vertical = if (large) 8.dp else 3.dp),
        horizontalArrangement = Arrangement.spacedBy(if (large) 16.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(hora, style = estilo, maxLines = 1)
        if (large && address != null) Text(address, style = estilo)
        if (large && coordinates != null) Text(coordinates, style = estilo, maxLines = 1)
    }
}

/** Espaçador horizontal fino usado nas linhas do kit. */
@Composable
internal fun HGap(width: Dp) = Spacer(Modifier.width(width))

/** Quadradinho de [size] na cor [color] (legenda da barra proporcional). */
@Composable
internal fun Dot(color: Color, size: Dp = 8.dp) =
    Box(Modifier.size(size).clip(RoundedCornerShape(2.dp)).background(color))

/** Linha clicável com alvo mínimo de 44 dp. */
internal fun Modifier.kitClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
