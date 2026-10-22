package br.com.codecacto.kmplib.inspection.ui.signature

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.model.OtpChannel
import br.com.codecacto.kmplib.inspection.model.SignatureEvent
import br.com.codecacto.kmplib.inspection.model.SignatureLinkEvent
import br.com.codecacto.kmplib.inspection.model.SignatureLinkState
import br.com.codecacto.kmplib.inspection.model.SignatureLinkStatus
import br.com.codecacto.kmplib.inspection.model.SignatureState
import br.com.codecacto.kmplib.inspection.model.SignatureStatus
import br.com.codecacto.kmplib.inspection.plural
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.ui.InspectionDialog
import br.com.codecacto.kmplib.inspection.ui.InspectionFooter
import br.com.codecacto.kmplib.inspection.ui.InspectionPrimaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionSecondaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionStateChip
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBar
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.inspection.ui.items.InlineErrorBanner
import br.com.codecacto.kmplib.platform.privacy.SecureContent
import br.com.codecacto.kmplib.signature.SignaturePad
import br.com.codecacto.kmplib.signature.SignaturePadState
import br.com.codecacto.kmplib.signature.rememberSignaturePadState
import br.com.codecacto.kmplib.ui.components.DigitBoxField

/**
 * **Assinar no aparelho** (LK-M16, K07 — desenho do `MV App.dc.html`, estado `is.assinar`; subiu
 * para o kit na decisão §9: os dois produtos assinam, e o Meus Aluguéis reusa traço + código no aceite
 * de contrato e no termo de quitação). Barra "X · Assinar · Mariana Costa · Locatária"; aceite da
 * cláusula (caixa 18 na marca); o quadro de assinatura (150 dp, linha de base tracejada) com
 * "Desfazer"/"Limpar"; o código de 6 dígitos ([DigitBoxField], caixas de 56 dp) com o canal, o erro
 * ("Código incorreto, restam 2 tentativas") e "Reenviar em 0:42"; rodapé "Assinar".
 *
 * O quadro fica sob [SecureContent] (fora de print/gravação de tela). Sem rede, o traço é aceito e a
 * assinatura fica **aguardando o código** — o kit nunca mostra "assinado" sem ele. O kit não envia
 * nada: [SignatureEvent.Submit] leva o PNG do traço; quem fala com o servidor é o produto
 * (`SignaturePort`, security-review obrigatório na rodada que o ligar).
 *
 * @param padState o estado do quadro (o produto pode guardá-lo para reabrir sem perder o traço).
 */
@Composable
fun InspectionSignatureScreen(
    state: SignatureState,
    onEvent: (SignatureEvent) -> Unit,
    modifier: Modifier = Modifier,
    padState: SignaturePadState = rememberSignaturePadState(),
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val st = texts.signature
    val assinado = state.status == SignatureStatus.SIGNED
    Column(modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = st.signTitle,
            subtitle = "${state.signerName} · ${state.signerRole}",
            titleSize = 20.sp,
            navigationIcon = InspectionIcon.Close,
            navigationDescription = texts.common.close,
            onNavigate = { onEvent(SignatureEvent.Close) },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (assinado) InspectionStateChip(SignatureStatus.SIGNED, texts = texts)
            ConsentRow(state.consentChecked, state.consentText, enabled = !assinado) { onEvent(SignatureEvent.ConsentChanged(it)) }
            if (state.showValidation && !state.consentChecked) ValidationText(st.signAgreeRequired)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(st.signatureLabel, style = inspectionTextStyle(14.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    PadTextAction(st.undo, enabled = !padState.isEmpty && !assinado) { padState.undo() }
                    PadTextAction(st.clear, enabled = !padState.isEmpty && !assinado) { padState.clear() }
                }
                SecureContent {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(t.surface)
                            .border(1.dp, t.line, RoundedCornerShape(6.dp))
                            .drawBehind {
                                val y = size.height - 34.dp.toPx()
                                drawLine(
                                    color = t.line,
                                    start = Offset(24.dp.toPx(), y),
                                    end = Offset(size.width - 24.dp.toPx(), y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                                )
                            }
                            .testTag(InspectionTestTags.SIGN_PAD),
                    ) {
                        SignaturePad(state = padState, strokeColor = t.ink, height = 150.dp, contentDescription = st.signatureLabel)
                    }
                }
                if (state.showValidation && padState.isEmpty) ValidationText(st.signStrokeRequired)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = !assinado, role = Role.Button) { onEvent(SignatureEvent.ChangeChannel) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(channelLabel(state.channel, texts), style = inspectionTextStyle(14.sp, FontWeight.SemiBold))
                    InspectionIconView(InspectionIcon.ExpandMore, size = 18.dp, tint = t.ink)
                }
                val erroCodigo = when {
                    state.codeExpired -> st.codeExpired
                    state.attemptsLeft != null -> plural(state.attemptsLeft, st.codeWrongOne, st.codeWrongOther)
                    else -> null
                }
                DigitBoxField(
                    value = state.code,
                    onValueChange = { onEvent(SignatureEvent.CodeChanged(it)) },
                    length = state.codeLength,
                    label = st.codeLabel,
                    errorMessage = erroCodigo,
                    enabled = !assinado && !state.isSubmitting,
                    boxHeight = 56.dp,
                    modifier = Modifier.fillMaxWidth().testTag(InspectionTestTags.SIGN_CODE),
                )
                if (state.resendInSeconds > 0) {
                    Text(
                        st.resendIn.fmt(formatCountdown(state.resendInSeconds)),
                        style = inspectionTextStyle(14.sp, color = t.muted, tabular = true),
                    )
                } else if (!assinado) {
                    Box(
                        Modifier
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button) { onEvent(SignatureEvent.Resend) }
                            .testTag(InspectionTestTags.SIGN_RESEND),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(st.resend, style = inspectionTextStyle(14.sp, FontWeight.SemiBold, t.brand))
                    }
                }
            }
            if (state.offline) {
                Text(
                    st.signOffline,
                    style = inspectionTextStyle(14.sp, color = t.warn.fg),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (!assinado) {
            InspectionFooter {
                InspectionPrimaryButton(
                    text = st.signTitle,
                    onClick = { onEvent(SignatureEvent.Submit(if (padState.isEmpty) null else padState.toPngBytes())) },
                    loading = state.isSubmitting,
                    testTag = InspectionTestTags.SIGN_SUBMIT,
                )
            }
        }
    }
}

/** "0:42". */
fun formatCountdown(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private fun channelLabel(channel: OtpChannel, texts: InspectionTexts): String = when (channel) {
    OtpChannel.SMS -> texts.signature.codeSentSms
    OtpChannel.EMAIL -> texts.signature.codeSentEmail
    OtpChannel.WHATSAPP -> texts.signature.codeSentWhatsapp
}

/** O aceite: caixa 18 dp raio 3 (cheia na marca com ✓ `onBrand`) + o texto 15, entrelinha 1,45. */
@Composable
private fun ConsentRow(checked: Boolean, text: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val t = InspectionTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .testTag(InspectionTestTags.SIGN_CONSENT),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (checked) t.brand else t.surface)
                .then(if (checked) Modifier else Modifier.border(1.5.dp, t.muted, RoundedCornerShape(3.dp))),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) InspectionIconView(InspectionIcon.Check, size = 16.dp, tint = t.onBrand)
        }
        Text(text, style = inspectionTextStyle(15.sp, lineHeight = 21.75.sp), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PadTextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val t = InspectionTheme.tokens
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = inspectionTextStyle(14.sp, FontWeight.SemiBold, if (enabled) t.brand else t.muted))
    }
}

@Composable
private fun ValidationText(text: String) {
    val t = InspectionTheme.tokens
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        InspectionIconView(InspectionIcon.Error, filled = true, size = 16.dp, tint = t.bad.fg)
        Text(text, style = inspectionTextStyle(13.sp, color = t.bad.fg), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/**
 * **Enviar para assinar** (K08, `SignatureLinkSheet`): criando o link → pronto (Compartilhar /
 * Copiar) → enviado; erro com "Tentar de novo". O produto cria o link (`backlib-public-link`) e
 * compartilha pelo `ShareHandler.shareLink`.
 */
@Composable
fun SignatureLinkDialog(
    state: SignatureLinkState,
    onEvent: (SignatureLinkEvent) -> Unit,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val st = texts.signature
    val status = state.status
    InspectionDialog(
        title = st.signatureLinkTitle,
        message = "${state.signerName} · ${state.signerRole}",
        confirmText = when (status) {
            is SignatureLinkStatus.Failed -> texts.common.retry
            is SignatureLinkStatus.Ready -> st.shareLink
            else -> texts.common.close
        },
        dismissText = if (status is SignatureLinkStatus.Shared) null else texts.common.cancel,
        busy = status is SignatureLinkStatus.Creating,
        confirmTestTag = InspectionTestTags.SIGN_LINK_SHARE,
        onConfirm = {
            when (status) {
                is SignatureLinkStatus.Failed -> onEvent(SignatureLinkEvent.Retry)
                is SignatureLinkStatus.Ready -> onEvent(SignatureLinkEvent.Share(status.url))
                else -> onEvent(SignatureLinkEvent.Dismiss)
            }
        },
        onDismiss = { onEvent(SignatureLinkEvent.Dismiss) },
    ) {
        when (status) {
            SignatureLinkStatus.Creating -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = t.brand, strokeWidth = 2.dp)
                Text(st.creatingLink, style = inspectionTextStyle(15.sp, color = t.muted))
            }
            is SignatureLinkStatus.Failed -> InlineErrorBanner(status.message ?: st.linkError, texts.common.retry, { onEvent(SignatureLinkEvent.Retry) })
            is SignatureLinkStatus.Ready -> InspectionSecondaryButton(
                text = st.copyLink,
                onClick = { onEvent(SignatureLinkEvent.Copy(status.url)) },
                icon = InspectionIcon.Link,
                contentColor = t.brand,
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp,
            )
            SignatureLinkStatus.Shared -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InspectionIconView(InspectionIcon.CheckCircle, filled = true, size = 18.dp, tint = t.good.fg)
                Text(st.linkShared, style = inspectionTextStyle(15.sp, FontWeight.SemiBold, t.good.fg))
            }
        }
    }
}
