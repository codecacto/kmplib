package br.com.codecacto.kmplib.pdf.canvas

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Medidor determinístico: cada caractere tem 0,5 × corpo de largura; ascendente 0,8, descendente
 * 0,2 e versal 0,7 do corpo. Permite provar o layout sem a fonte da plataforma.
 */
private class FakeMeasurer : PdfTextMeasurer {
    override fun widthPt(text: String, style: PdfTextStyle): Double = text.length * style.sizePt * 0.5
    override fun fontMetrics(style: PdfTextStyle) =
        PdfFontMetricsPt(ascentPt = style.sizePt * 0.8, descentPt = style.sizePt * 0.2, capHeightPt = style.sizePt * 0.7)
}

private fun record(block: PdfDocumentScope.() -> Unit) = recordPdf(FakeMeasurer(), block)

private fun near(expected: Double, actual: Double, tolerance: Double = 1e-6) =
    assertTrue(abs(expected - actual) <= tolerance, "esperado $expected, veio $actual")

private val PT = 72.0 / 25.4

class PdfCanvasRecordingTest {

    private val style10 = PdfTextStyle(sizePt = 10.0)

    @Test
    fun paginaFixaTemAMedidaPedidaEmMilimetros() {
        val pdf = record { page(85.0, 55.0) {} }
        assertEquals(1, pdf.pages.size)
        near(85.0, pdf.pages[0].widthMm)
        near(55.0, pdf.pages[0].heightMm)
        near(85.0 * PT, pdf.recordedPages[0].widthPt)
    }

    @Test
    fun documentoAceitaPaginasDeTamanhosDiferentes() {
        val pdf = record {
            page(PdfPageSize.A4) {}
            page(PdfPageSize.fixed(70.0, 297.0)) {}
        }
        near(210.0, pdf.pages[0].widthMm)
        near(70.0, pdf.pages[1].widthMm)
    }

    @Test
    fun documentoSemPaginaFalha() {
        assertFailsWith<IllegalArgumentException> { record { } }
    }

    @Test
    fun bobinaTemAAlturaDoConteudoMaisAFolgaDoCortador() {
        val pdf = record {
            page(PdfPageSize.THERMAL_58) {
                rect(5.0, 10.0, 48.0, 20.0, stroke = null, fill = PdfColor.Black)
            }
        }
        // fundo do retângulo = 30 mm; folga padrão da bobina = 3 mm.
        near(33.0, pdf.pages[0].heightMm)
        near(58.0, pdf.pages[0].widthMm)
    }

    @Test
    fun bobinaContaMeiaEspessuraDoTracoNoFundo() {
        val stroke = PdfStroke(widthPt = 2.0)
        val pdf = record {
            page(PdfPageSize.roll(58.0, bottomPaddingMm = 0.0)) { line(0.0, 10.0, 58.0, 10.0, stroke) }
        }
        near(10.0 + (1.0 / PT), pdf.pages[0].heightMm)
    }

    @Test
    fun bobinaRespeitaAlturaMinima() {
        val size = PdfPageSize(58.0, PdfPageHeight.FitContent(bottomPaddingMm = 0.0, minHeightMm = 40.0))
        val pdf = record { page(size) { markExtent(10.0) } }
        near(40.0, pdf.pages[0].heightMm)
    }

    @Test
    fun bobinaQuePassaDoMaximoFalhaEmVezDeCortarORodape() {
        val size = PdfPageSize(58.0, PdfPageHeight.FitContent(bottomPaddingMm = 3.0, maxHeightMm = 100.0))
        val erro = assertFailsWith<PdfLayoutException> { record { page(size) { markExtent(98.0) } } }
        assertTrue(erro.message!!.contains("101,0"), erro.message)
    }

    @Test
    fun markExtentSoAlongaNaoEncolhe() {
        val pdf = record {
            page(PdfPageSize.roll(58.0, 0.0)) {
                markExtent(50.0)
                markExtent(20.0)
            }
        }
        near(50.0, pdf.pages[0].heightMm)
    }

    @Test
    fun textoTemTopoNoYEBaseNaAscendente() {
        var bottom = 0.0
        val pdf = record { page(100.0, 100.0) { bottom = text("AB", 10.0, 20.0, style10) } }
        val op = pdf.recordedPages[0].ops.single() as PdfOp.Text
        near(10.0 * PT, op.xPt)
        near(20.0 * PT + 8.0, op.baselinePt)
        near(20.0 + 10.0 / PT, bottom)
    }

    @Test
    fun alinhamentoSemLarguraAncoraNoX() {
        // "AB" em 10 pt = 10 pt de largura.
        near(100.0, alignedLeftPt(100.0, 10.0, null, PdfTextAlign.Start))
        near(95.0, alignedLeftPt(100.0, 10.0, null, PdfTextAlign.Center))
        near(90.0, alignedLeftPt(100.0, 10.0, null, PdfTextAlign.End))
    }

    @Test
    fun alinhamentoComLarguraAlinhaDentroDaCaixa() {
        near(100.0, alignedLeftPt(100.0, 10.0, 50.0, PdfTextAlign.Start))
        near(120.0, alignedLeftPt(100.0, 10.0, 50.0, PdfTextAlign.Center))
        near(140.0, alignedLeftPt(100.0, 10.0, 50.0, PdfTextAlign.End))
    }

    @Test
    fun textoComLarguraCortaComReticencia() {
        val pdf = record {
            // 10 caracteres × 5 pt = 50 pt; caixa de 32 pt cabe 6 caracteres (5 + "…").
            page(200.0, 100.0) { text("ABCDEFGHIJ", 0.0, 0.0, style10, widthMm = 32.0 / PT) }
        }
        assertEquals("ABCDE…", (pdf.recordedPages[0].ops.single() as PdfOp.Text).text)
    }

    @Test
    fun textoVisibleNaoCorta() {
        val pdf = record {
            page(200.0, 100.0) {
                text("ABCDEFGHIJ", 0.0, 0.0, style10, widthMm = 30.0 / PT, overflow = PdfTextOverflow.Visible)
            }
        }
        assertEquals("ABCDEFGHIJ", (pdf.recordedPages[0].ops.single() as PdfOp.Text).text)
    }

    @Test
    fun textoTrocaQuebraDeLinhaPorEspacoNaLinhaUnica() {
        val pdf = record { page(200.0, 100.0) { text("A\nB", 0.0, 0.0, style10) } }
        assertEquals("A B", (pdf.recordedPages[0].ops.single() as PdfOp.Text).text)
    }

    @Test
    fun textoVazioNaoGeraOperacao() {
        val pdf = record { page(200.0, 100.0) { text("", 0.0, 0.0, style10) } }
        assertTrue(pdf.recordedPages[0].ops.isEmpty())
    }

    @Test
    fun digitoNaCaixaCentralizaPelaVersal() {
        val pdf = record { page(100.0, 100.0) { textInBox("7", 10.0, 10.0, 8.0, 10.0, style10) } }
        val op = pdf.recordedPages[0].ops.single() as PdfOp.Text
        val boxTop = 10.0 * PT
        val boxHeight = 10.0 * PT
        // baseline = topo + (altura + versal) / 2 → a versal fica centrada na caixa.
        near(boxTop + (boxHeight + 7.0) / 2.0, op.baselinePt)
        // "7" = 5 pt de largura, centrado nos 8 mm.
        near(10.0 * PT + (8.0 * PT - 5.0) / 2.0, op.xPt)
    }

    @Test
    fun caixaAlinhaNoTopoENoFundo() {
        val pdf = record {
            page(100.0, 100.0) {
                textInBox("7", 0.0, 0.0, 8.0, 10.0, style10, verticalAlign = PdfVerticalAlign.Top)
                textInBox("7", 0.0, 0.0, 8.0, 10.0, style10, verticalAlign = PdfVerticalAlign.Bottom)
            }
        }
        val (top, bottom) = pdf.recordedPages[0].ops.map { it as PdfOp.Text }
        near(8.0, top.baselinePt)
        near(10.0 * PT - 2.0, bottom.baselinePt)
    }

    @Test
    fun paragrafoQuebraPorPalavra() {
        // largura de 30 pt = 6 caracteres
        val lines = wrapText("ab cd efgh ij", 30.0, style10, FakeMeasurer())
        assertEquals(listOf("ab cd", "efgh", "ij"), lines)
    }

    @Test
    fun paragrafoRespeitaQuebraDeLinhaEParagrafoVazio() {
        val lines = wrapText("ab\n\ncd", 100.0, style10, FakeMeasurer())
        assertEquals(listOf("ab", "", "cd"), lines)
    }

    @Test
    fun palavraMaiorQueALinhaEPartidaPorCaractere() {
        val lines = wrapText("abcdefghijklmn", 30.0, style10, FakeMeasurer())
        assertEquals(listOf("abcdef", "ghijkl", "mn"), lines)
    }

    @Test
    fun paragrafoAcimaDeMaxLinesTerminaEmReticencia() {
        val lines = wrapText("ab cd ef gh", 30.0, style10, FakeMeasurer(), maxLines = 1)
        assertEquals(1, lines.size)
        assertTrue(lines[0].endsWith("…"), lines[0])
        assertTrue(lines[0].length * 5.0 <= 30.0)
    }

    @Test
    fun textBlockEmpilhaLinhasComEntrelinhaEDevolveOFundo() {
        var bottom = 0.0
        val pdf = record {
            page(100.0, 200.0) { bottom = textBlock("ab cd", 0.0, 0.0, 12.0 / PT, style10, lineSpacing = 1.5) }
        }
        val ops = pdf.recordedPages[0].ops.map { it as PdfOp.Text }
        assertEquals(listOf("ab", "cd"), ops.map { it.text })
        near(8.0, ops[0].baselinePt)
        near(8.0 + 15.0, ops[1].baselinePt)
        near((8.0 + 15.0 + 2.0) / PT, bottom)
    }

    @Test
    fun textBlockValidaParametros() {
        assertFailsWith<IllegalArgumentException> { record { page(10.0, 10.0) { textBlock("a", 0.0, 0.0, 0.0, style10) } } }
        assertFailsWith<IllegalArgumentException> { record { page(10.0, 10.0) { textBlock("a", 0.0, 0.0, 5.0, style10, maxLines = 0) } } }
    }

    @Test
    fun offsetDeslocaEVoltaAOrigem() {
        val pdf = record {
            page(210.0, 297.0) {
                offset(100.0, 71.75) {
                    rect(0.0, 0.0, 10.0, 10.0)
                    offset(5.0, 5.0) { line(0.0, 0.0, 1.0, 0.0) }
                }
                rect(0.0, 0.0, 10.0, 10.0)
            }
        }
        val ops = pdf.recordedPages[0].ops
        val cell = ops[0] as PdfOp.Rect
        near(100.0 * PT, cell.x)
        near(71.75 * PT, cell.y)
        val nested = ops[1] as PdfOp.Line
        near(105.0 * PT, nested.x1)
        near(76.75 * PT, nested.y1)
        val outside = ops[2] as PdfOp.Rect
        near(0.0, outside.x)
    }

    @Test
    fun contentBottomERelativoAOrigemAtual() {
        record {
            page(PdfPageSize.roll(58.0)) {
                rect(0.0, 0.0, 10.0, 20.0, stroke = null, fill = PdfColor.Black)
                offset(0.0, 5.0) { near(15.0, contentBottomMm) }
                near(20.0, contentBottomMm)
                assertEquals(null, heightMm)
                near(58.0, widthMm)
            }
        }
    }

    @Test
    fun retanguloSemTracoNemPreenchimentoNaoDesenhaMasOcupa() {
        val pdf = record { page(PdfPageSize.roll(58.0, 0.0)) { rect(0.0, 0.0, 10.0, 12.0, stroke = null) } }
        assertTrue(pdf.recordedPages[0].ops.isEmpty())
        near(12.0, pdf.pages[0].heightMm)
    }

    @Test
    fun linhaTracejadaGuardaOPadrao() {
        val pdf = record {
            page(210.0, 297.0) { line(0.0, 99.0, 210.0, 99.0, PdfStroke(0.25, dash = PdfDash.CutLine)) }
        }
        val op = pdf.recordedPages[0].ops.single() as PdfOp.Line
        assertEquals(PdfDash(3.0, 2.0), op.stroke.dash)
        near(0.25, op.stroke.widthPt)
    }

    @Test
    fun imagemVaziaNaoGeraOperacao() {
        val pdf = record { page(50.0, 50.0) { image(ByteArray(0), 0.0, 0.0, 10.0, 10.0) } }
        assertTrue(pdf.recordedPages[0].ops.isEmpty())
    }

    @Test
    fun imagemGuardaCaixaEAjuste() {
        val bytes = byteArrayOf(1, 2, 3)
        val pdf = record { page(50.0, 50.0) { image(bytes, 1.0, 2.0, 10.0, 5.0, PdfImageFit.Cover) } }
        val op = pdf.recordedPages[0].ops.single() as PdfOp.Image
        assertEquals(PdfImageFit.Cover, op.fit)
        near(10.0 * PT, op.width)
    }

    @Test
    fun ajusteDeImagemContainCentralizaSemDeformar() {
        val r = imageDrawRect(200.0, 100.0, 0.0, 0.0, 100.0, 100.0, PdfImageFit.Contain)
        near(0.0, r[0]); near(25.0, r[1]); near(100.0, r[2]); near(50.0, r[3])
    }

    @Test
    fun ajusteDeImagemCoverPassaDaCaixa() {
        val r = imageDrawRect(200.0, 100.0, 0.0, 0.0, 100.0, 100.0, PdfImageFit.Cover)
        near(-50.0, r[0]); near(0.0, r[1]); near(200.0, r[2]); near(100.0, r[3])
    }

    @Test
    fun ajusteDeImagemFillEstica() {
        val r = imageDrawRect(200.0, 100.0, 5.0, 6.0, 100.0, 100.0, PdfImageFit.Fill)
        assertEquals(listOf(5.0, 6.0, 100.0, 100.0), r.toList())
    }

    @Test
    fun imagemSemMedidaNaoDesenha() {
        val r = imageDrawRect(0.0, 100.0, 5.0, 6.0, 100.0, 100.0, PdfImageFit.Contain)
        near(0.0, r[2])
    }

    @Test
    fun ellipsizeCasosDeBorda() {
        val m = FakeMeasurer()
        assertEquals("abc", ellipsize("abc", 100.0, style10, m))
        assertEquals("…", ellipsize("abc", 5.0, style10, m))
        assertEquals("", ellipsize("abc", 1.0, style10, m))
    }

    @Test
    fun medidaDeTextoEmMilimetros() {
        record {
            val m = measureText("AB", style10)
            near(10.0 / PT, m.widthMm)
            near(8.0 / PT, m.ascentMm)
            near(2.0 / PT, m.descentMm)
            near(7.0 / PT, m.capHeightMm)
            near(10.0 / PT, m.lineHeightMm)
            page(10.0, 10.0) { near(10.0 / PT, measureText("AB", style10).widthMm) }
        }
    }

    @Test
    fun coresEValidacoes() {
        assertEquals(PdfColor(1f, 0f, 0f), PdfColor.rgb(0xFF0000))
        assertEquals(PdfColor(0f, 0f, 1f, 0f), PdfColor.argb(0x000000FFL))
        assertFailsWith<IllegalArgumentException> { PdfColor(2f, 0f, 0f) }
        assertFailsWith<IllegalArgumentException> { PdfStroke(widthPt = 0.0) }
        assertFailsWith<IllegalArgumentException> { PdfDash(0.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { PdfDash(1.0, 1.0, phaseMm = -1.0) }
        assertFailsWith<IllegalArgumentException> { PdfTextStyle(sizePt = 0.0) }
        assertFailsWith<IllegalArgumentException> { PdfPageSize.fixed(0.0, 10.0) }
        assertFailsWith<IllegalArgumentException> { PdfPageSize.fixed(10.0, 6000.0) }
        assertFailsWith<IllegalArgumentException> { PdfPageHeight.FitContent(bottomPaddingMm = -1.0) }
        assertFailsWith<IllegalArgumentException> { PdfPageHeight.FitContent(minHeightMm = 10.0, maxHeightMm = 5.0) }
        assertFailsWith<IllegalArgumentException> { PdfPageHeight.FitContent(maxHeightMm = 6000.0) }
        assertFailsWith<IllegalArgumentException> { PdfFontFamily.fromBytes("x", ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { PdfFontFamily.fromBytes("x", byteArrayOf(1), ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> {
            record { page(10.0, 10.0) { rect(0.0, 0.0, -1.0, 1.0) } }
        }
        assertFailsWith<IllegalArgumentException> {
            record { page(10.0, 10.0) { rect(0.0, 0.0, 1.0, 1.0, cornerRadiusMm = -1.0) } }
        }
        assertFailsWith<IllegalArgumentException> {
            record { page(10.0, 10.0) { image(byteArrayOf(1), 0.0, 0.0, 0.0, 1.0) } }
        }
        assertFailsWith<IllegalArgumentException> {
            record { page(10.0, 10.0) { textInBox("1", 0.0, 0.0, 0.0, 1.0, style10) } }
        }
    }

    @Test
    fun presetsTemAsMedidasDoPapel() {
        assertEquals(PdfPageSize.fixed(210.0, 297.0), PdfPageSize.A4)
        assertEquals(PdfPageSize.fixed(148.0, 210.0), PdfPageSize.A5)
        assertEquals(215.9, PdfPageSize.LETTER.widthMm)
        assertEquals(58.0, PdfPageSize.THERMAL_58.widthMm)
        assertEquals(80.0, PdfPageSize.THERMAL_80.widthMm)
        assertEquals(PdfPageHeight.FitContent(bottomPaddingMm = 3.0), PdfPageSize.THERMAL_80.height)
        assertEquals("PdfFontFamily(sans-serif)", PdfFontFamily.SansSerif.toString())
        assertEquals("monospace", PdfFontFamily.Monospace.name)
    }

    @Test
    fun retanguloArredondadoGuardaORaioEmPontos() {
        val pdf = record { page(20.0, 20.0) { rect(0.0, 0.0, 10.0, 10.0, cornerRadiusMm = 1.0, fill = PdfColor.White) } }
        val op = pdf.recordedPages[0].ops.single() as PdfOp.Rect
        near(PT, op.radius)
        assertEquals(PdfColor.White, op.fill)
    }
}
