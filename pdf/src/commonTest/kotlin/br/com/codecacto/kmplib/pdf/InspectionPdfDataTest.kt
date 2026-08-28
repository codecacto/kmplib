package br.com.codecacto.kmplib.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InspectionPdfDataTest {

    @Test
    fun defaultFileName_usesNicknameAndPlate() {
        val file = defaultInspectionPdfFileName(
            InspectionPdfVehicle(nickname = "Gol da frota", plate = "ABC1D23"),
        )
        assertEquals("vistoria-Gol_da_frota-ABC1D23.pdf", file)
    }

    @Test
    fun defaultFileName_blankVehicleFallsBack() {
        assertEquals("vistoria.pdf", defaultInspectionPdfFileName(InspectionPdfVehicle(nickname = "  ", plate = "")))
    }

    @Test
    fun defaultFileName_keepsPdfExtension() {
        // Nome que já termina em .pdf após sanitização não duplica a extensão.
        val file = defaultInspectionPdfFileName(InspectionPdfVehicle(nickname = "x", plate = "y"))
        assertTrue(file.endsWith(".pdf"))
        assertEquals(1, Regex("\\.pdf", RegexOption.IGNORE_CASE).findAll(file).count())
    }

    @Test
    fun company_equalsHandlesLogoBytes() {
        val a = InspectionPdfCompany(name = "X", logoBytes = byteArrayOf(1, 2, 3))
        val b = InspectionPdfCompany(name = "X", logoBytes = byteArrayOf(1, 2, 3))
        val c = InspectionPdfCompany(name = "X", logoBytes = byteArrayOf(9))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test
    fun item_equalsHandlesPhotoBytes() {
        val a = InspectionPdfItem(text = "Farol", statusLabel = "OK", photos = listOf(byteArrayOf(1), byteArrayOf(2)))
        val b = InspectionPdfItem(text = "Farol", statusLabel = "OK", photos = listOf(byteArrayOf(1), byteArrayOf(2)))
        val c = InspectionPdfItem(text = "Farol", statusLabel = "OK", photos = listOf(byteArrayOf(1)))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test
    fun signature_equalsHandlesPngBytes() {
        val a = InspectionPdfSignature(label = "Responsável", pngBytes = byteArrayOf(1, 2), name = "Ana")
        val b = InspectionPdfSignature(label = "Responsável", pngBytes = byteArrayOf(1, 2), name = "Ana")
        val c = InspectionPdfSignature(label = "Responsável", pngBytes = byteArrayOf(9), name = "Ana")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test
    fun data_composesLaudoShape() {
        // Modo Laudo: terceiro + assinaturas + seções com foto-prova.
        val data = InspectionPdfData(
            company = InspectionPdfCompany(name = "Vistoria X"),
            vehicle = InspectionPdfVehicle(nickname = "Gol", plate = "ABC-1234", typeLabel = "Carro"),
            title = "Laudo de Vistoria",
            generatedAtLabel = "07/07/2026 14:30",
            sections = listOf(
                InspectionPdfSection(
                    title = "Lataria",
                    items = listOf(
                        InspectionPdfItem("Porta dianteira", "Não-OK", statusColorArgb = 0xFFD32F2F.toInt(), observation = "Amassado", photos = listOf(byteArrayOf(1))),
                    ),
                ),
            ),
            thirdParty = InspectionPdfThirdParty(responsibleName = "Ana", responsibleDocument = "123"),
            signatures = listOf(InspectionPdfSignature("Responsável", byteArrayOf(1, 2), name = "Ana")),
            watermark = true,
            watermarkText = "Free",
        )
        assertEquals(1, data.sections.size)
        assertEquals(1, data.sections.first().items.first().photos.size)
        assertEquals("Ana", data.thirdParty?.responsibleName)
        assertEquals(1, data.signatures.size)
    }
}
