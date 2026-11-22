package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.pdf.report.ReportSummary
import br.com.codecacto.kmplib.pdf.report.ReportSummaryPdf
import br.com.codecacto.kmplib.pdf.report.ReportSummaryTexts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2.377.0 — o timbrado do PDF: regra de layout comum a Android e iOS. */
class PdfLetterheadLayoutTest {

    /** Medida de texto fixa: 5 pt por caractere — determinística, sem fonte. */
    private val measure: (String) -> Float = { it.length * 5f }

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0)

    // --- formato e limites -------------------------------------------------

    @Test
    fun reconhecePngEJpegPelaAssinatura() {
        assertEquals(LetterheadImageFormat.PNG, PdfLetterheadLayout.detectFormat(png))
        assertEquals(LetterheadImageFormat.JPEG, PdfLetterheadLayout.detectFormat(jpeg))
        assertNull(PdfLetterheadLayout.detectFormat("GIF89a".encodeToByteArray()))
        assertNull(PdfLetterheadLayout.detectFormat(byteArrayOf(1, 2)))
        assertNull(PdfLetterheadLayout.detectFormat(null))
    }

    @Test
    fun recusaImagemAcimaDoTeto() {
        val grande = ByteArray(PdfLetterheadLayout.MAX_IMAGE_BYTES + 1).also { png.copyInto(it) }
        assertFalse(PdfLetterheadLayout.isAcceptedImage(grande))
        val noLimite = ByteArray(PdfLetterheadLayout.MAX_IMAGE_BYTES).also { png.copyInto(it) }
        assertTrue(PdfLetterheadLayout.isAcceptedImage(noLimite))
        assertFalse(PdfLetterheadLayout.isAcceptedImage("texto".encodeToByteArray()))
    }

    @Test
    fun subAmostraEmPotenciaDeDoisSemPassarDoTeto() {
        assertEquals(1, PdfLetterheadLayout.sampleSize(1200, 400))
        assertEquals(1, PdfLetterheadLayout.sampleSize(3199, 100))
        assertEquals(2, PdfLetterheadLayout.sampleSize(3200, 100))
        assertEquals(4, PdfLetterheadLayout.sampleSize(8000, 6000))
        assertEquals(1, PdfLetterheadLayout.sampleSize(0, 10))
    }

    @Test
    fun limitaOMaiorLadoENaoAmplia() {
        assertEquals(1600 to 400, PdfLetterheadLayout.boundedSize(3200, 800))
        assertEquals(400 to 1600, PdfLetterheadLayout.boundedSize(800, 3200))
        assertEquals(300 to 100, PdfLetterheadLayout.boundedSize(300, 100))
    }

    @Test
    fun encaixaSemDistorcerENuncaAlemDeUmPxPorPt() {
        assertEquals(200f to 50f, PdfLetterheadLayout.fitImage(800, 200, 200f, 72f))
        assertEquals(144f to 72f, PdfLetterheadLayout.fitImage(400, 200, 515f, 72f))
        // Logo pequeno não estica: 64 px = 64 pt.
        assertEquals(64f to 32f, PdfLetterheadLayout.fitImage(64, 32, 515f, 72f))
        assertEquals(0f to 0f, PdfLetterheadLayout.fitImage(0, 10, 100f, 100f))
    }

    @Test
    fun pixelsEmbutidosSaoTresPorPtSemPassarDaDecodificada() {
        assertEquals(600 to 150, PdfLetterheadLayout.renderPixels(200f, 50f, 1600, 400))
        assertEquals(300 to 100, PdfLetterheadLayout.renderPixels(200f, 50f, 300, 100))
    }

    // --- texto -------------------------------------------------------------

    @Test
    fun quebraPorPalavraERespeitaQuebraExplicita() {
        val lines = PdfLetterheadLayout.wrapText("Clinica Royal Doctor\nCRM 1234", 60f, 3, measure)
        assertEquals(listOf("Clinica", "Royal Doctor", "CRM 1234"), lines)
        assertTrue(lines.all { measure(it) <= 60f })
    }

    @Test
    fun cortaNoLimiteDeLinhasComReticencias() {
        val lines = PdfLetterheadLayout.wrapText("um dois tres quatro cinco seis sete oito", 50f, 2, measure)
        assertEquals(2, lines.size)
        assertTrue(lines.last().endsWith("…"), lines.toString())
        assertTrue(lines.all { measure(it) <= 50f })
    }

    @Test
    fun partePalavraMaiorQueALinha() {
        val lines = PdfLetterheadLayout.wrapText("ABCDEFGHIJKLMNOPQRST", 50f, 5, measure)
        assertEquals(listOf("ABCDEFGHIJ", "KLMNOPQRST"), lines)
    }

    @Test
    fun textoBrancoNaoGeraLinhaEEspacosColapsam() {
        assertTrue(PdfLetterheadLayout.wrapText("   \n  ", 100f, 3, measure).isEmpty())
        assertTrue(PdfLetterheadLayout.wrapText(null, 100f, 3, measure).isEmpty())
        assertEquals(listOf("a b"), PdfLetterheadLayout.wrapText("  a    b ", 100f, 3, measure))
    }

    // --- faixas ------------------------------------------------------------

    @Test
    fun faixaVaziaNaoExiste() {
        assertNull(
            PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.HEADER, PdfLetterheadAlign.LEFT, 40f, 515f, null, null, "  ", measure),
        )
    }

    @Test
    fun esquerdaPoeImagemAEsquerdaETextoAoLado() {
        val band = assertNotNull(
            PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.HEADER, PdfLetterheadAlign.LEFT, 40f, 515f, 600, 300, "Clinica Royal", measure),
        )
        val img = assertNotNull(band.image)
        assertEquals(40f, img.x)
        assertEquals(72f, img.height) // 600x300 em 200x72 → 144x72
        assertEquals(144f, img.width)
        assertEquals(40f + 144f + PdfLetterheadLayout.INNER_GAP, band.textAnchorX)
        assertEquals(listOf("Clinica Royal"), band.lines)
        assertTrue(band.height <= PdfLetterheadLayout.HEADER_MAX_HEIGHT)
    }

    @Test
    fun direitaPoeImagemADireitaETextoAoLadoAlinhadoADireita() {
        val band = assertNotNull(
            PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.HEADER, PdfLetterheadAlign.RIGHT, 40f, 515f, 600, 300, "Clinica Royal", measure),
        )
        val img = assertNotNull(band.image)
        assertEquals(144f, img.width)
        assertEquals(72f, img.height)
        assertEquals(40f + 515f - 144f, img.x) // encostada na margem direita
        assertEquals(PdfLetterheadAlign.RIGHT, band.align)
        assertEquals(img.x - PdfLetterheadLayout.INNER_GAP, band.textAnchorX) // texto termina antes da imagem
        assertEquals(listOf("Clinica Royal"), band.lines)
        assertTrue(band.height <= PdfLetterheadLayout.HEADER_MAX_HEIGHT)
    }

    @Test
    fun direitaQuebraOTextoNoEspacoAEsquerdaDaImagem() {
        val band = assertNotNull(
            PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.HEADER, PdfLetterheadAlign.RIGHT, 40f, 515f, 600, 300, "palavra ".repeat(40), measure),
        )
        val disponivel = band.textAnchorX - 40f
        assertTrue(band.lines.isNotEmpty())
        assertTrue(band.lines.all { measure(it) <= disponivel }, band.lines.toString())
    }

    @Test
    fun direitaSemImagemAncoraNaMargemDireitaESoTextoNoRodape() {
        val band = assertNotNull(
            PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.FOOTER, PdfLetterheadAlign.RIGHT, 40f, 515f, null, null, "Rua X, 100", measure),
        )
        assertNull(band.image)
        assertEquals(40f + 515f, band.textAnchorX)
        assertTrue(band.height <= PdfLetterheadLayout.FOOTER_MAX_HEIGHT)
        // Só imagem: encosta à direita e pode ocupar a largura toda.
        val soImagem = assertNotNull(
            PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.HEADER, PdfLetterheadAlign.RIGHT, 40f, 515f, 2000, 100, null, measure),
        )
        val img = assertNotNull(soImagem.image)
        assertEquals(40f + 515f, img.x + img.width)
    }

    @Test
    fun direitaEEspelhoDaEsquerda() {
        val l = assertNotNull(PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.HEADER, PdfLetterheadAlign.LEFT, 40f, 515f, 600, 300, "Clinica Royal Doctor CRM 1234", measure))
        val r = assertNotNull(PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.HEADER, PdfLetterheadAlign.RIGHT, 40f, 515f, 600, 300, "Clinica Royal Doctor CRM 1234", measure))
        assertEquals(l.height, r.height)
        assertEquals(l.lines, r.lines)
        assertEquals(l.baselines, r.baselines)
        assertEquals(l.image!!.width, r.image!!.width)
        assertEquals(l.image!!.y, r.image!!.y)
        assertEquals(l.image!!.x - 40f, 555f - (r.image!!.x + r.image!!.width))
    }

    @Test
    fun centroCentralizaImagemETextoEmbaixoSemPassarDaAltura() {
        val texto = "linha um\nlinha dois\nlinha tres\nlinha quatro"
        val band = assertNotNull(
            PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.HEADER, PdfLetterheadAlign.CENTER, 40f, 515f, 1000, 1000, texto, measure),
        )
        val img = assertNotNull(band.image)
        assertEquals(40f + (515f - img.width) / 2f, img.x)
        assertEquals(PdfLetterheadLayout.HEADER_MAX_LINES, band.lines.size)
        assertEquals(40f + 515f / 2f, band.textAnchorX)
        assertTrue(band.height <= PdfLetterheadLayout.HEADER_MAX_HEIGHT, "altura ${band.height}")
        assertTrue(band.baselines.first() > img.y + img.height, "texto abaixo da imagem")
        assertTrue(band.baselines.last() <= band.height, "texto dentro da faixa")
    }

    @Test
    fun rodapeLimitaLinhasEAltura() {
        val band = assertNotNull(
            PdfLetterheadLayout.layoutBand(
                PdfLetterheadLayout.FOOTER, PdfLetterheadAlign.CENTER, 40f, 515f, null, null,
                "a\nb\nc\nd", measure,
            ),
        )
        assertEquals(PdfLetterheadLayout.FOOTER_MAX_LINES, band.lines.size)
        assertTrue(band.height <= PdfLetterheadLayout.FOOTER_MAX_HEIGHT)
        assertNull(band.image)
    }

    @Test
    fun imagemAltaNuncaPassaDaAlturaMaxima() {
        PdfLetterheadAlign.entries.forEach { align ->
            val h = assertNotNull(PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.HEADER, align, 40f, 515f, 100, 5000, null, measure))
            assertTrue(h.height <= PdfLetterheadLayout.HEADER_MAX_HEIGHT)
            val f = assertNotNull(PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.FOOTER, align, 40f, 515f, 100, 5000, "x", measure))
            assertTrue(f.height <= PdfLetterheadLayout.FOOTER_MAX_HEIGHT)
        }
    }

    // --- página ------------------------------------------------------------

    @Test
    fun conteudoFicaEntreAsFaixasComFolga() {
        val header = PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.HEADER, PdfLetterheadAlign.LEFT, 40f, 515f, 600, 300, null, measure)
        val footer = PdfLetterheadLayout.layoutBand(PdfLetterheadLayout.FOOTER, PdfLetterheadAlign.CENTER, 40f, 515f, null, null, "Rua X", measure)
        val frame = PdfLetterheadLayout.pageFrame(842f, 40f, header, footer)
        assertEquals(40f + header!!.height + PdfLetterheadLayout.BAND_GAP, frame.contentTop)
        assertEquals(842f - 40f - footer!!.height - PdfLetterheadLayout.BAND_GAP, frame.contentBottom)
        assertTrue(frame.headerSeparatorY!! in (40f + header.height)..frame.contentTop)
        assertTrue(frame.footerSeparatorY!! in frame.contentBottom..frame.footerTop)
        // O texto de rodapé do documento fica na margem, abaixo do rodapé do timbrado.
        assertTrue(frame.documentFooterBaseline > frame.footerTop + footer.height)
        assertTrue(frame.documentFooterBaseline < 842f)
    }

    @Test
    fun semFaixaOLimiteEAMargem() {
        val frame = PdfLetterheadLayout.pageFrame(842f, 40f, null, null)
        assertEquals(40f, frame.contentTop)
        assertEquals(802f, frame.contentBottom)
        assertNull(frame.headerSeparatorY)
        assertNull(frame.footerSeparatorY)
    }

    // --- modelo ------------------------------------------------------------

    @Test
    fun modeloSabeOQueTemEComparaBytes() {
        assertTrue(PdfLetterhead().isEmpty)
        assertTrue(PdfLetterhead(headerText = "  ", footerImage = ByteArray(0)).isEmpty)
        assertTrue(PdfLetterhead(headerImage = png).hasHeader)
        assertTrue(PdfLetterhead(footerText = "Rua X").hasFooter)
        assertFalse(PdfLetterhead(footerText = "x", replacesDefaultHeader = true).hidesDefaultHeader)
        assertTrue(PdfLetterhead(headerText = "x", replacesDefaultHeader = true).hidesDefaultHeader)
        assertEquals(PdfLetterhead(headerImage = png.copyOf()), PdfLetterhead(headerImage = png.copyOf()))
        assertEquals(PdfLetterhead(headerImage = png.copyOf()).hashCode(), PdfLetterhead(headerImage = png.copyOf()).hashCode())
        assertNotEquals(PdfLetterhead(headerImage = png), PdfLetterhead(headerImage = jpeg))
        val dump = PdfLetterhead(headerImage = png).toString()
        assertTrue(dump.contains("10 bytes") && !dump.contains("-119"), dump)
    }

    @Test
    fun resumoLevaOTimbradoAoDocumentoESemEleNadaMuda() {
        val summary = ReportSummary(title = "Atendimentos", periodLabel = "Out/2026")
        val company = DocumentPdfCompany("Clinica")
        val semTimbrado = ReportSummaryPdf.build(summary, company, ReportSummaryTexts(), "01/11/2026 10:00")
        assertNull(semTimbrado.letterhead)
        val timbrado = PdfLetterhead(headerText = "Clinica Royal", footerText = "Rua X")
        val com = ReportSummaryPdf.build(summary, company, ReportSummaryTexts(), "01/11/2026 10:00", timbrado)
        assertEquals(timbrado, com.letterhead)
        assertEquals(semTimbrado, com.copy(letterhead = null))
    }
}
