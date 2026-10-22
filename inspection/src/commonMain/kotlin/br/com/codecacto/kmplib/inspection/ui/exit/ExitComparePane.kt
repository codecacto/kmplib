package br.com.codecacto.kmplib.inspection.ui.exit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.model.ExitCompareEvent
import br.com.codecacto.kmplib.inspection.model.ExitCompareState
import br.com.codecacto.kmplib.inspection.model.ExitCompareTab
import br.com.codecacto.kmplib.inspection.model.InspectionMediaThumb
import br.com.codecacto.kmplib.inspection.plural
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.ui.InspectionDivergenceTiles
import br.com.codecacto.kmplib.inspection.ui.InspectionEyebrow
import br.com.codecacto.kmplib.inspection.ui.InspectionFooter
import br.com.codecacto.kmplib.inspection.ui.InspectionNoteField
import br.com.codecacto.kmplib.inspection.ui.InspectionPrimaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionResultTiles
import br.com.codecacto.kmplib.inspection.ui.InspectionSecondaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionStateChip
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBar
import br.com.codecacto.kmplib.inspection.ui.PhotoStampStrip
import br.com.codecacto.kmplib.inspection.ui.dashedBorder
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.inspection.ui.items.InlineErrorBanner
import br.com.codecacto.kmplib.ui.components.AppTabRow
import br.com.codecacto.kmplib.ui.components.rememberPhotoSourceRequest
import coil3.compose.AsyncImage

/**
 * **Item da saída, lado a lado** (§2.8, LK-M11): barra 64 ("Torneira da pia" · "Banheiro · Saída"),
 * abas **Entrada · Saída · Lado a lado** ([AppTabRow], indicador de 2 dp em toda a aba), as duas
 * colunas (ENTRADA/SAÍDA com foto 4:3, carimbo, chip do estado e a observação da entrada), os tiles de
 * **Resultado** e — só quando o resultado não é Conforme (D7) — os de **Divergência**, obrigatória;
 * observação, "Foto de saída" (câmera com a entrada sobreposta) e "Salvar".
 *
 * As abas Entrada e Saída mostram a coluna correspondente em largura total (D6). Sem foto na entrada,
 * a caixa tracejada "Sem foto na entrada" — não é erro.
 */
@Composable
fun ExitComparePane(
    state: ExitCompareState,
    onEvent: (ExitCompareEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val editavel = !state.readOnly
    Column(modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = state.title,
            subtitle = "${state.roomName} · ${texts.exit.tabExit}",
            onNavigate = { onEvent(ExitCompareEvent.Back) },
            navigationDescription = texts.common.back,
            titleSize = 20.sp,
        )
        AppTabRow(
            tabs = listOf(texts.exit.tabEntry, texts.exit.tabExit, texts.exit.tabSideBySide),
            selectedIndex = state.tab.ordinal,
            onSelect = { onEvent(ExitCompareEvent.SelectTab(ExitCompareTab.entries[it])) },
            modifier = Modifier.padding(horizontal = 8.dp),
            selectedColor = t.brand,
            unselectedColor = t.muted,
            dividerColor = t.line,
            textStyle = inspectionTextStyle(15.sp),
            testTag = InspectionTestTags.EXIT_TABS,
            optionTestKeys = listOf("entrada", "saida", "lado-a-lado"),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (state.tab) {
                ExitCompareTab.SIDE_BY_SIDE -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EntryColumn(state, texts, onEvent, Modifier.weight(1f))
                    ExitColumn(state, texts, onEvent, Modifier.weight(1f))
                }
                ExitCompareTab.ENTRY -> EntryColumn(state, texts, onEvent, Modifier.fillMaxWidth())
                ExitCompareTab.EXIT -> ExitColumn(state, texts, onEvent, Modifier.fillMaxWidth())
            }
            InspectionResultTiles(
                selected = state.result,
                onSelect = { onEvent(ExitCompareEvent.ResultChanged(it)) },
                enabled = editavel,
                title = texts.exit.resultLabel,
                texts = texts,
            )
            if (state.divergenceVisible) {
                InspectionDivergenceTiles(
                    selected = state.divergence,
                    onSelect = { onEvent(ExitCompareEvent.DivergenceChanged(it)) },
                    enabled = editavel,
                    title = texts.exit.divergenceLabel,
                    titleSuffix = texts.exit.requiredSuffix,
                    errorMessage = if (state.showDivergenceError && state.divergenceMissing) texts.exit.divergenceRequired else null,
                    texts = texts,
                )
            }
            InspectionNoteField(
                value = state.note,
                onValueChange = { onEvent(ExitCompareEvent.NoteChanged(it)) },
                label = texts.item.noteLabel,
                minHeight = 64.dp,
                enabled = editavel,
                testTag = InspectionTestTags.EXIT_NOTE,
            )
            if (editavel) {
                InspectionSecondaryButton(
                    text = texts.exit.exitPhoto,
                    onClick = { onEvent(ExitCompareEvent.TakeExitPhoto) },
                    icon = InspectionIcon.PhotoCamera,
                    contentColor = t.brand,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = InspectionTestTags.EXIT_PHOTO,
                )
            }
        }
        if (editavel) {
            InspectionFooter {
                if (state.errorMessage != null) {
                    InlineErrorBanner(state.errorMessage, texts.common.retry, { onEvent(ExitCompareEvent.Save) })
                }
                InspectionPrimaryButton(
                    text = texts.common.save,
                    onClick = { onEvent(ExitCompareEvent.Save) },
                    loading = state.isSaving,
                    testTag = InspectionTestTags.EXIT_SAVE,
                )
            }
        }
    }
}

@Composable
private fun EntryColumn(state: ExitCompareState, texts: InspectionTexts, onEvent: (ExitCompareEvent) -> Unit, modifier: Modifier) {
    val t = InspectionTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InspectionEyebrow(texts.exit.tabEntry)
        ComparePhoto(
            photo = state.entry.photo,
            loading = state.entry.isPhotoLoading,
            emptyLabel = texts.exit.noEntryPhoto,
            onClick = { state.entry.photo?.let { onEvent(ExitCompareEvent.OpenEntryPhoto(it.id)) } },
        )
        state.entry.condition?.let { InspectionStateChip(it, texts = texts) }
        state.entry.note?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = inspectionTextStyle(13.sp, color = t.muted, lineHeight = 17.5.sp))
        }
    }
}

@Composable
private fun ExitColumn(state: ExitCompareState, texts: InspectionTexts, onEvent: (ExitCompareEvent) -> Unit, modifier: Modifier) {
    val t = InspectionTheme.tokens
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InspectionEyebrow(texts.exit.tabExit)
        ComparePhoto(
            photo = state.exitPhotos.firstOrNull(),
            loading = false,
            emptyLabel = texts.exit.noExitPhoto,
            onClick = { onEvent(ExitCompareEvent.OpenExitPhotos) },
        )
        if (state.exitPhotos.isNotEmpty()) {
            Text(
                plural(state.exitPhotos.size, texts.items.photoCountOne, texts.items.photoCountOther),
                style = inspectionTextStyle(13.sp, color = t.muted),
                modifier = Modifier.clickable(role = Role.Button) { onEvent(ExitCompareEvent.OpenExitPhotos) },
            )
        }
    }
}

/** Foto 4:3 raio 4 com o carimbo; ausente = caixa tracejada com `hide_image` e a frase. */
@Composable
private fun ComparePhoto(photo: InspectionMediaThumb?, loading: Boolean, emptyLabel: String, onClick: () -> Unit) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(4.dp)
    if (photo == null && !loading) {
        Column(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .dashedBorder(t.line, radius = 4.dp)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InspectionIconView(InspectionIcon.HideImage, size = 22.dp, tint = t.muted)
            Text(emptyLabel, style = inspectionTextStyle(12.sp, color = t.muted), textAlign = TextAlign.Center)
        }
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(forma)
            .background(t.sunken)
            .clickable(role = Role.Image, onClick = onClick)
            .semantics { photo?.stampLabel?.let { contentDescription = it } },
    ) {
        val fonte = photo?.source
        if (fonte != null) {
            AsyncImage(rememberPhotoSourceRequest(fonte), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = t.brand, strokeWidth = 2.dp)
        }
        photo?.stampLabel?.let { PhotoStampStrip(dateTime = it, modifier = Modifier.align(Alignment.BottomCenter)) }
    }
}
