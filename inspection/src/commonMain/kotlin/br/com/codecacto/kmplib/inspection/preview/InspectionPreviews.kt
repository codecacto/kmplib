package br.com.codecacto.kmplib.inspection.preview

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.inspection.model.Divergence
import br.com.codecacto.kmplib.inspection.model.EntrySnapshot
import br.com.codecacto.kmplib.inspection.model.ExitCompareState
import br.com.codecacto.kmplib.inspection.model.ExitResult
import br.com.codecacto.kmplib.inspection.model.Functioning
import br.com.codecacto.kmplib.inspection.model.InspectionMediaSource
import br.com.codecacto.kmplib.inspection.model.InspectionMediaThumb
import br.com.codecacto.kmplib.inspection.model.InspectionPhase
import br.com.codecacto.kmplib.inspection.model.InspectionRules
import br.com.codecacto.kmplib.inspection.model.ItemCondition
import br.com.codecacto.kmplib.inspection.model.ItemListState
import br.com.codecacto.kmplib.inspection.model.ItemRowState
import br.com.codecacto.kmplib.inspection.model.ItemSheetState
import br.com.codecacto.kmplib.inspection.model.MediaUploadStatus
import br.com.codecacto.kmplib.inspection.model.ReportStatus
import br.com.codecacto.kmplib.inspection.model.ReportViewerState
import br.com.codecacto.kmplib.inspection.model.ReviewCounts
import br.com.codecacto.kmplib.inspection.model.ReviewPendency
import br.com.codecacto.kmplib.inspection.model.ReviewProblem
import br.com.codecacto.kmplib.inspection.model.ReviewState
import br.com.codecacto.kmplib.inspection.model.RoomListState
import br.com.codecacto.kmplib.inspection.model.RoomProgress
import br.com.codecacto.kmplib.inspection.model.SignatureState
import br.com.codecacto.kmplib.inspection.ui.exit.ExitComparePane
import br.com.codecacto.kmplib.inspection.ui.item.InspectionItemSheet
import br.com.codecacto.kmplib.inspection.ui.items.InspectionItemList
import br.com.codecacto.kmplib.inspection.ui.report.InspectionReportViewer
import br.com.codecacto.kmplib.inspection.ui.review.InspectionReview
import br.com.codecacto.kmplib.inspection.ui.rooms.InspectionRoomList
import br.com.codecacto.kmplib.inspection.ui.signature.InspectionSignatureScreen
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Os dados de exemplo do protótipo (Apto 302), usados nos previews e nos testes. */
internal object InspectionSamples {
    val rooms = RoomListState(
        contextLabel = "Apto 302 · Entrada",
        rooms = listOf(
            RoomProgress("sala", "Sala", 12, 12),
            RoomProgress("cozinha", "Cozinha", 9, 9),
            RoomProgress("quarto1", "Quarto 1", 4, 10),
            RoomProgress("quarto2", "Quarto 2", 10, 10),
            RoomProgress("banheiro", "Banheiro", 0, 14),
            RoomProgress("servico", "Área de serviço", 6, 6),
            RoomProgress("varanda", "Varanda", 5, 5),
            RoomProgress("hall", "Hall de entrada", 2, 4),
        ),
    )

    val items = listOf(
        ItemRowState("piso", "Piso e revestimento", ItemCondition.GOOD, photoCount = 1),
        ItemRowState("torneira", "Torneira da pia", ItemCondition.BAD, photoCount = 2),
        ItemRowState("chuveiro", "Chuveiro", ItemCondition.REGULAR, hasVoiceNote = true),
        ItemRowState("box", "Box de vidro", ItemCondition.GOOD, missingRequiredPhotos = 2),
        ItemRowState("paredes", "Paredes", ItemCondition.GOOD),
        ItemRowState("teto", "Teto", ItemCondition.GOOD),
    )

    val media = listOf(
        InspectionMediaThumb("f1", null, MediaUploadStatus.CONFIRMED),
        InspectionMediaThumb("f2", null, MediaUploadStatus.QUEUED, origin = InspectionMediaSource.GALLERY),
        InspectionMediaThumb("f3", null, MediaUploadStatus.FAILED),
    )

    val itemSheet = ItemSheetState(
        itemId = "torneira",
        title = "Torneira da pia",
        roomName = "Banheiro",
        position = 4,
        total = 14,
        condition = ItemCondition.BAD,
        functioning = Functioning.DOES_NOT_WORK,
        asksFunctioning = true,
        note = "Pingando com o registro fechado. Base com oxidação.",
        media = media,
    )

    val exit = ExitCompareState(
        itemId = "torneira",
        title = "Torneira da pia",
        roomName = "Banheiro",
        entry = EntrySnapshot(
            photo = InspectionMediaThumb("e1", null, MediaUploadStatus.CONFIRMED, stampLabel = "03/03/25 10:41"),
            condition = ItemCondition.GOOD,
            note = "Funcionando, sem marcas.",
        ),
        exitPhotos = listOf(
            InspectionMediaThumb("s1", null, MediaUploadStatus.CONFIRMED, stampLabel = "14/09/25 09:12"),
            InspectionMediaThumb("s2", null, MediaUploadStatus.CONFIRMED),
        ),
        result = ExitResult.NON_CONFORMING,
        divergence = Divergence.DAMAGE,
        note = "Base trincada, vazamento constante.",
    )

    val review = ReviewState(
        phase = InspectionPhase.ENTRY,
        title = "Entrada · Apto 302",
        counts = ReviewCounts(rooms = 8, items = 94, photos = 187, videos = 4),
        pendencies = listOf(
            ReviewPendency("p1", "Box de vidro · faltam 2 fotos", itemId = "box"),
            ReviewPendency("p2", "Quarto 1 · 6 itens a revisar", roomId = "quarto1"),
        ),
        conditionCounts = InspectionRules.conditionCounts(
            List(6) { ItemCondition.NEW } + List(81) { ItemCondition.GOOD } + List(4) { ItemCondition.REGULAR } + List(3) { ItemCondition.BAD },
        ),
        problems = listOf(
            ReviewProblem("torneira", "Torneira da pia", "Banheiro", condition = ItemCondition.BAD),
            ReviewProblem("chuveiro", "Chuveiro", "Banheiro", condition = ItemCondition.REGULAR),
        ),
    )

    val signature = SignatureState(
        signerName = "Mariana Costa",
        signerRole = "Locatária",
        consentText = "Li o laudo de entrada do Apto 302 e concordo com o estado descrito de cada item.",
        consentChecked = true,
        code = "481903",
        codeSent = true,
        attemptsLeft = 2,
        resendInSeconds = 42,
    )
}

@Preview
@Composable
private fun RoomListMinhaVistoriaPreview() = InspectionExampleTheme(InspectionExampleThemes.MinhaVistoria, dark = false) {
    InspectionRoomList(InspectionSamples.rooms, onEvent = {})
}

@Preview
@Composable
private fun RoomListMeusAlugueisDarkPreview() = InspectionExampleTheme(InspectionExampleThemes.MeusAlugueis, dark = true) {
    InspectionRoomList(InspectionSamples.rooms, onEvent = {})
}

@Preview
@Composable
private fun ItemListPreview() = InspectionExampleTheme(InspectionExampleThemes.MinhaVistoria, dark = false) {
    InspectionItemList(
        ItemListState(roomName = "Banheiro", items = InspectionSamples.items, roomMedia = InspectionSamples.media),
        onEvent = {},
    )
}

@Preview
@Composable
private fun ItemSheetPreview() = InspectionExampleTheme(InspectionExampleThemes.MeusAlugueis, dark = false) {
    InspectionItemSheet(InspectionSamples.itemSheet, onEvent = {})
}

@Preview
@Composable
private fun ExitComparePreview() = InspectionExampleTheme(InspectionExampleThemes.MinhaVistoria, dark = true) {
    ExitComparePane(InspectionSamples.exit, onEvent = {})
}

@Preview
@Composable
private fun ReviewPreview() = InspectionExampleTheme(InspectionExampleThemes.MinhaVistoria, dark = false) {
    InspectionReview(InspectionSamples.review, onEvent = {})
}

@Preview
@Composable
private fun ReportGeneratingPreview() = InspectionExampleTheme(InspectionExampleThemes.MeusAlugueis, dark = false) {
    InspectionReportViewer(
        ReportViewerState(title = "Laudo · Apto 302 · v1", status = ReportStatus.Generating(177, 191)),
        pdf = null,
        onEvent = {},
    )
}

@Preview
@Composable
private fun SignaturePreview() = InspectionExampleTheme(InspectionExampleThemes.MinhaVistoria, dark = false) {
    InspectionSignatureScreen(InspectionSamples.signature, onEvent = {})
}
