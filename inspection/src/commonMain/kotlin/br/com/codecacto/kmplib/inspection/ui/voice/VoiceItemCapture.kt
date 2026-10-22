package br.com.codecacto.kmplib.inspection.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.model.VoiceCaptureEvent
import br.com.codecacto.kmplib.inspection.model.VoiceCapturePhase
import br.com.codecacto.kmplib.inspection.model.VoiceCaptureState
import br.com.codecacto.kmplib.inspection.model.VoiceProposalRow
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.ui.InspectionFooter
import br.com.codecacto.kmplib.inspection.ui.InspectionPrimaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionStateChip
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBar
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle

/**
 * **Voz → item** (K03, LK-M15 — sem desenho novo no protótipo; usa só tokens e átomos do kit): segurar
 * o microfone e falar ("torneira da pia pingando, estado ruim"), soltar; a transcrição aparece ao
 * vivo; o produto estrutura (IA no servidor) e devolve PROPOSTAS, que a pessoa **confirma** — nada se
 * aplica sozinho, e proposta sem item correspondente aparece como "Não encontrei este item", nunca
 * marcada. Sem rede: a fala fica guardada; IA falhou: vira observação do cômodo; sem microfone: pede.
 *
 * O reconhecimento de fala é do produto (`createSpeechRecognizer` do `kmplib-media`): o kit só
 * recebe a fase e a transcrição.
 */
@Composable
fun VoiceItemCapture(
    state: VoiceCaptureState,
    onEvent: (VoiceCaptureEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val vt = texts.voice
    Column(modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = vt.voiceTitle,
            navigationIcon = InspectionIcon.Close,
            navigationDescription = texts.common.close,
            onNavigate = { onEvent(VoiceCaptureEvent.Close) },
            titleSize = 20.sp,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.phase) {
                VoiceCapturePhase.PROPOSALS -> ProposalsList(state, texts, onEvent)
                VoiceCapturePhase.STRUCTURING -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = t.brand)
                    Text(vt.structuring, style = inspectionTextStyle(16.sp, FontWeight.SemiBold), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Transcript(state.transcript, texts)
                }
                else -> TalkArea(state, texts, onEvent)
            }
        }
        if (state.phase == VoiceCapturePhase.PROPOSALS) {
            InspectionFooter {
                InspectionPrimaryButton(
                    text = vt.apply.fmt(state.acceptedCount),
                    onClick = { onEvent(VoiceCaptureEvent.Apply) },
                    enabled = state.acceptedCount > 0,
                    testTag = InspectionTestTags.VOICE_APPLY,
                )
            }
        }
    }
}

@Composable
private fun TalkArea(state: VoiceCaptureState, texts: InspectionTexts, onEvent: (VoiceCaptureEvent) -> Unit) {
    val t = InspectionTheme.tokens
    val vt = texts.voice
    val ouvindo = state.phase == VoiceCapturePhase.LISTENING
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state.phase) {
            VoiceCapturePhase.OFFLINE -> Notice(vt.voiceOffline, t.neutral.fg)
            VoiceCapturePhase.FAILED -> Notice(vt.voiceFailed, t.warn.fg)
            else -> Unit
        }
        if (state.phase == VoiceCapturePhase.MIC_DENIED) {
            Text(vt.micPermission, style = inspectionTextStyle(15.sp), textAlign = TextAlign.Center)
            InspectionPrimaryButton(vt.allowMic, { onEvent(VoiceCaptureEvent.RequestMicPermission) }, icon = InspectionIcon.Mic)
            return@Column
        }
        // Segurar para falar: desce = começa, sobe/cancela = termina. Para leitor de tela, o toque
        // duplo alterna (começa / termina), porque "segurar" não existe ali.
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(if (ouvindo) t.brand else t.brandSoft)
                .border(2.dp, t.brand, CircleShape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        onEvent(VoiceCaptureEvent.StartListening)
                        waitForUpOrCancellation()
                        onEvent(VoiceCaptureEvent.StopListening)
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = if (ouvindo) vt.listening else vt.holdToTalk
                    onClick {
                        onEvent(if (ouvindo) VoiceCaptureEvent.StopListening else VoiceCaptureEvent.StartListening)
                        true
                    }
                }
                .testTag(InspectionTestTags.VOICE_TALK),
            contentAlignment = Alignment.Center,
        ) {
            InspectionIconView(InspectionIcon.Mic, filled = ouvindo, size = 40.dp, tint = if (ouvindo) t.onBrand else t.brand)
        }
        Text(
            if (ouvindo) vt.listening else vt.holdToTalk,
            style = inspectionTextStyle(16.sp, FontWeight.SemiBold),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Transcript(state.transcript, texts)
    }
}

@Composable
private fun Transcript(text: String, texts: InspectionTexts) {
    if (text.isBlank()) return
    val t = InspectionTheme.tokens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(t.surface)
            .border(1.dp, t.line, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(texts.voice.transcriptLabel, style = inspectionTextStyle(13.sp, color = t.muted))
        Text(text, style = inspectionTextStyle(15.sp, lineHeight = 21.sp))
    }
}

@Composable
private fun Notice(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = inspectionTextStyle(14.sp, FontWeight.SemiBold, color), textAlign = TextAlign.Center)
}

@Composable
private fun ProposalsList(state: VoiceCaptureState, texts: InspectionTexts, onEvent: (VoiceCaptureEvent) -> Unit) {
    val t = InspectionTheme.tokens
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "titulo") {
            Text(
                texts.voice.proposalsTitle,
                style = inspectionTextStyle(15.sp, FontWeight.SemiBold),
                modifier = Modifier.padding(16.dp),
            )
        }
        items(state.proposals, key = { it.id }) { p -> ProposalRow(p, texts) { onEvent(VoiceCaptureEvent.ToggleProposal(p.id)) } }
        item(key = "transcricao") { Box(Modifier.padding(16.dp)) { Transcript(state.transcript, texts) } }
    }
}

@Composable
private fun ProposalRow(p: VoiceProposalRow, texts: InspectionTexts, onToggle: () -> Unit) {
    val t = InspectionTheme.tokens
    Column(Modifier.fillMaxWidth().background(t.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = p.accepted, enabled = p.matched, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (p.accepted && p.matched) t.brand else t.surface)
                    .border(1.5.dp, if (p.matched) t.brand else t.line, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (p.accepted && p.matched) InspectionIconView(InspectionIcon.Check, size = 16.dp, tint = t.onBrand)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (p.matched && p.itemName != null) {
                    Text(p.itemName, style = inspectionTextStyle(16.sp, FontWeight.Medium))
                    p.roomName?.let { Text(it, style = inspectionTextStyle(13.sp, color = t.muted)) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        InspectionIconView(InspectionIcon.SearchOff, size = 16.dp, tint = t.muted)
                        Text(texts.voice.notFound, style = inspectionTextStyle(15.sp, FontWeight.SemiBold, t.muted))
                    }
                }
                if (p.note.isNotBlank()) Text(p.note, style = inspectionTextStyle(14.sp, color = t.muted))
            }
            p.condition?.let { InspectionStateChip(it, texts = texts) }
        }
        HorizontalDivider(thickness = 1.dp, color = t.line)
    }
}
