package br.com.codecacto.kmplib.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.media.AudioRecorderConfig
import br.com.codecacto.kmplib.media.RecordedAudio
import br.com.codecacto.kmplib.media.voicenote.VoiceNoteError
import br.com.codecacto.kmplib.media.voicenote.VoiceNoteMicButton
import br.com.codecacto.kmplib.media.voicenote.VoiceNoteRecordingBar
import br.com.codecacto.kmplib.media.voicenote.rememberVoiceNoteRecorderState

/**
 * O campo de escrever da conversa: texto (até 5 linhas) + **microfone** (segurar ou tocar-tocar) que
 * vira **enviar** quando há texto. Gravando, a barra de gravação ocupa o lugar do campo e o botão
 * fica onde está (é nele que está o dedo).
 *
 * O texto **mora no campo** (`TextFieldState`) — o ViewModel só recebe no envio. Mandar cada letra
 * pelo ViewModel e de volta perde letras no iOS em digitação rápida (ver `AppSearchField`).
 *
 * Vá no `bottomBar` do `Scaffold` (ele aplica o inset do teclado com `imePadding` no pai).
 *
 * @param onSendText o texto aparado, não vazio. O campo limpa sozinho.
 * @param onSendAudio a nota gravada (temporária — o [ChatController.sendAudio] guarda e apaga).
 * @param onVoiceError o que dizer quando a gravação não saiu ([ChatTexts.voiceNote] tem as frases).
 */
@Composable
fun ChatComposer(
    onSendText: (String) -> Unit,
    onSendAudio: (RecordedAudio) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    texts: ChatTexts = rememberChatTexts(),
    recorderConfig: AudioRecorderConfig = AudioRecorderConfig(),
    onVoiceError: (VoiceNoteError) -> Unit = {},
) {
    val campo = rememberTextFieldState()
    val gravador = rememberVoiceNoteRecorderState(onRecorded = onSendAudio, onError = onVoiceError, config = recorderConfig)
    val temTexto = campo.text.isNotBlank()

    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (gravador.isRecording) {
                VoiceNoteRecordingBar(gravador, modifier = Modifier.weight(1f), texts = texts.voiceNote)
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        state = campo,
                        enabled = enabled,
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag(ChatTestTags.INPUT).semantics { contentDescription = texts.inputPlaceholder },
                        decorator = { interno ->
                            if (campo.text.isEmpty()) {
                                Text(texts.inputPlaceholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            interno()
                        },
                    )
                }
            }
            if (temTexto && !gravador.isRecording) {
                FilledIconButton(
                    onClick = {
                        val t = normalizeChatText(campo.text.toString())
                        if (t != null) {
                            onSendText(t)
                            campo.clearText()
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.size(48.dp).testTag(ChatTestTags.SEND),
                    shape = CircleShape,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = texts.send)
                }
            } else {
                VoiceNoteMicButton(gravador, enabled = enabled, texts = texts.voiceNote)
            }
        }
    }
}
