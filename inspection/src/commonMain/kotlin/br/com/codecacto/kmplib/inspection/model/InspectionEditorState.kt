package br.com.codecacto.kmplib.inspection.model

import androidx.compose.runtime.Immutable

// ───────────── Modelos de vistoria (§2.14, LK-M14) ─────────────

/** Um cartão da lista de modelos. [isReadyMade] = modelo pronto (`SEED`): só "Copiar e editar". */
@Immutable
data class TemplateCard(
    val id: String,
    val name: String,
    val roomCount: Int,
    val itemCount: Int,
    val usedInCount: Int = 0,
    /** "14/10" — formatado pelo produto. */
    val editedAtLabel: String? = null,
    val isReadyMade: Boolean = false,
)

enum class TemplateListTab { MINE, READY_MADE }

@Immutable
data class TemplateListState(
    val mine: List<TemplateCard> = emptyList(),
    val readyMade: List<TemplateCard> = emptyList(),
    val tab: TemplateListTab = TemplateListTab.MINE,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface TemplateListEvent {
    data object Back : TemplateListEvent
    data class SelectTab(val tab: TemplateListTab) : TemplateListEvent
    data class Open(val templateId: String) : TemplateListEvent
    data class Menu(val templateId: String) : TemplateListEvent
    data class CopyAndEdit(val templateId: String) : TemplateListEvent
    data object NewBlank : TemplateListEvent
    data object Retry : TemplateListEvent
}

/** Tipo de pergunta própria (D14) — mapeia para o FormSchema v1 (`text`, `choice`, `number`, `yesNo`, `date`). */
enum class TemplateQuestionType(val formType: String) {
    TEXT("text"),
    CHOICE("choice"),
    NUMBER("number"),
    YES_NO("yesNo"),
    DATE("date"),
}

/**
 * Uma pergunta do item. As padrão (Material · Cor · Marca · Quantidade) chegam com [standardKey];
 * a própria, sem. "Funciona?" NÃO é pergunta (D15): é [TemplateItem.asksFunctioning].
 */
@Immutable
data class TemplateQuestion(
    val id: String,
    val label: String,
    val type: TemplateQuestionType = TemplateQuestionType.TEXT,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val standardKey: String? = null,
    val enabled: Boolean = true,
)

@Immutable
data class TemplateItem(
    val id: String,
    val name: String,
    val required: Boolean = false,
    val minPhotos: Int = 0,
    val defaultCondition: ItemCondition = ItemCondition.GOOD,
    val asksFunctioning: Boolean = false,
    /** Só as LIGADAS (as que o item pergunta). */
    val questions: List<TemplateQuestion> = emptyList(),
)

@Immutable
data class TemplateRoom(val id: String, val name: String)

/** O rascunho do diálogo "Editar item" (vazio = item novo). */
@Immutable
data class EditItemDraft(
    val itemId: String?,
    val name: String = "",
    val required: Boolean = false,
    val minPhotos: Int = 0,
    val defaultCondition: ItemCondition = ItemCondition.GOOD,
    val asksFunctioning: Boolean = false,
    /** As perguntas-padrão ligadas, por [TemplateQuestion.standardKey]. */
    val standardQuestions: Set<String> = emptySet(),
    val customQuestions: List<TemplateQuestion> = emptyList(),
    /** Segundo passo aberto: criando uma pergunta própria (D14). */
    val composingQuestion: CustomQuestionDraft? = null,
    val nameError: String? = null,
)

@Immutable
data class CustomQuestionDraft(
    val label: String = "",
    val type: TemplateQuestionType = TemplateQuestionType.TEXT,
    val required: Boolean = false,
    /** Uma opção por linha (só [TemplateQuestionType.CHOICE]). */
    val optionsText: String = "",
    val labelError: String? = null,
    val optionsError: String? = null,
) {
    /** As opções, sem linha vazia e sem repetição (ignorando maiúsculas/minúsculas). */
    val options: List<String> get() = optionsText.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
}

@Immutable
data class TemplateEditorState(
    val name: String,
    val rooms: List<TemplateRoom>,
    val selectedRoomId: String?,
    val items: List<TemplateItem>,
    val isReadyMade: Boolean = false,
    val hasChanges: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    /** O modelo mudou noutro aparelho: recarregar antes de salvar. */
    val versionConflict: Boolean = false,
    val editing: EditItemDraft? = null,
    val confirmLeave: Boolean = false,
    val errorMessage: String? = null,
) {
    val selectedRoom: TemplateRoom? get() = rooms.firstOrNull { it.id == selectedRoomId } ?: rooms.firstOrNull()
    val canSave: Boolean get() = hasChanges && !isSaving && !versionConflict && !isReadyMade
}

sealed interface TemplateEditorEvent {
    data object Back : TemplateEditorEvent
    data object Save : TemplateEditorEvent
    data object Preview : TemplateEditorEvent
    data object Reload : TemplateEditorEvent
    data class SelectRoom(val roomId: String) : TemplateEditorEvent
    data object AddRoom : TemplateEditorEvent
    data class RoomMenu(val roomId: String) : TemplateEditorEvent
    data class MoveItem(val from: Int, val to: Int) : TemplateEditorEvent
    data object MoveItemFinished : TemplateEditorEvent
    data class EditItem(val itemId: String) : TemplateEditorEvent
    data object AddItem : TemplateEditorEvent
    data object ConfirmLeave : TemplateEditorEvent
    data object DismissLeave : TemplateEditorEvent

    // Diálogo "Editar item"
    data class DraftChanged(val draft: EditItemDraft) : TemplateEditorEvent
    data object SaveItem : TemplateEditorEvent
    data object CancelItem : TemplateEditorEvent
    data object StartCustomQuestion : TemplateEditorEvent
    data object AddCustomQuestion : TemplateEditorEvent
    data object CancelCustomQuestion : TemplateEditorEvent
}

// ───────────── Voz → item (K03, LK-M15) ─────────────

enum class VoiceCapturePhase { IDLE, LISTENING, STRUCTURING, PROPOSALS, OFFLINE, FAILED, MIC_DENIED }

/** Uma proposta da IA, para a pessoa CONFIRMAR. Sem item correspondente = "não encontrei", nunca aplicada. */
@Immutable
data class VoiceProposalRow(
    val id: String,
    val itemName: String?,
    val roomName: String?,
    val condition: ItemCondition?,
    val note: String,
    val matched: Boolean,
    val accepted: Boolean = matched,
)

@Immutable
data class VoiceCaptureState(
    val phase: VoiceCapturePhase = VoiceCapturePhase.IDLE,
    /** Transcrição parcial ao vivo (ouvindo) ou final. */
    val transcript: String = "",
    val proposals: List<VoiceProposalRow> = emptyList(),
) {
    val acceptedCount: Int get() = proposals.count { it.matched && it.accepted }
}

sealed interface VoiceCaptureEvent {
    data object Close : VoiceCaptureEvent
    data object StartListening : VoiceCaptureEvent
    data object StopListening : VoiceCaptureEvent
    data class ToggleProposal(val id: String) : VoiceCaptureEvent
    data object Apply : VoiceCaptureEvent
    data object RequestMicPermission : VoiceCaptureEvent
}

// ───────────── Assinar no aparelho (K07/K08, LK-M16) ─────────────

/** Canal do código de confirmação. */
enum class OtpChannel { SMS, EMAIL, WHATSAPP }

@Immutable
data class SignatureState(
    val signerName: String,
    /** "Locatária". */
    val signerRole: String,
    /** "Li o laudo de entrada do Apto 302 e concordo com o estado descrito de cada item." */
    val consentText: String,
    val consentChecked: Boolean = false,
    val code: String = "",
    val codeLength: Int = 6,
    val channel: OtpChannel = OtpChannel.SMS,
    val codeSent: Boolean = false,
    /** Código recusado: quantas tentativas restam (`null` = sem erro de código). */
    val attemptsLeft: Int? = null,
    val codeExpired: Boolean = false,
    /** Segundos até poder reenviar (0 = já pode). */
    val resendInSeconds: Int = 0,
    val isSubmitting: Boolean = false,
    /** Sem rede: o traço fica guardado aguardando o código (nunca "assinado" sem ele). */
    val offline: Boolean = false,
    /** Mostrar as recusas de formulário (depois de tentar assinar). */
    val showValidation: Boolean = false,
    val status: SignatureStatus = SignatureStatus.PENDING,
    /**
     * Recusa sem contagem de tentativas (código já usado, laudo mudou, formato inválido) ou outra falha —
     * a frase do servidor, mostrada no campo do código (2.321.0). `null` = sem erro.
     */
    val errorMessage: String? = null,
) {
    /** Nome e papel do signatário são dado pessoal: fora do log. */
    override fun toString(): String =
        "SignatureState(consent=$consentChecked, codeLen=${code.length}, status=$status, offline=$offline)"
}

sealed interface SignatureEvent {
    data object Close : SignatureEvent
    data class ConsentChanged(val checked: Boolean) : SignatureEvent
    data class CodeChanged(val code: String) : SignatureEvent
    data object Resend : SignatureEvent
    data object ChangeChannel : SignatureEvent
    /**
     * Tocou em "Assinar". [strokePng] é o traço exportado do quadro (`null` se vazio) — o kit não
     * envia nada: o produto confere, manda o código e o traço ao servidor (`SignaturePort`).
     */
    data class Submit(val strokePng: ByteArray?) : SignatureEvent {
        override fun equals(other: Any?): Boolean = other is Submit && other.strokePng.contentEquals(strokePng)
        override fun hashCode(): Int = strokePng?.contentHashCode() ?: 0
        override fun toString(): String = "Submit(stroke=${strokePng?.size ?: 0} bytes)"
    }
}

/** Envio do link para alguém assinar no aparelho dele (K08). */
sealed interface SignatureLinkStatus {
    data object Creating : SignatureLinkStatus
    data class Ready(val url: String) : SignatureLinkStatus {
        override fun toString(): String = "Ready(url=…)"
    }
    data class Failed(val message: String? = null) : SignatureLinkStatus
    data object Shared : SignatureLinkStatus
}

@Immutable
data class SignatureLinkState(
    val signerName: String,
    val signerRole: String,
    val status: SignatureLinkStatus,
) {
    override fun toString(): String = "SignatureLinkState(status=$status)"
}

sealed interface SignatureLinkEvent {
    data object Dismiss : SignatureLinkEvent
    data object Retry : SignatureLinkEvent
    data class Share(val url: String) : SignatureLinkEvent {
        override fun toString(): String = "Share(url=…)"
    }
    data class Copy(val url: String) : SignatureLinkEvent {
        override fun toString(): String = "Copy(url=…)"
    }
}
