@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf

import kotlinx.cinterop.CValue
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.UIImage

/**
 * Render nativo do PDF do relatório financeiro no iOS via `UIGraphicsPDFRenderer` (UIKit),
 * espelhando o MESMO layout lógico do Android ([AndroidFinanceReportPdfGenerator]): cabeçalho
 * (logo+empresa+período/emissão), seção **Recebimentos** + total recebido, seção **Contas a
 * receber** + total a receber, com **paginação automática** (a linha de cabeçalho da tabela é
 * repetida no topo de cada nova página). A4 a 72pt (595 × 842 pt).
 *
 * Modelado nos geradores iOS já em produção (`DocumentPdfGenerator.ios.kt`,
 * `VaccinationCardPdfGenerator.ios.kt`) — NÃO é placeholder. A baseline da grade Android é
 * convertida para o topo da caixa que o iOS ancora via `ascender` da `UIFont`.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** o ambiente atual é Linux (targets Apple SKIPPED);
 * validação visual precisa ser feita em macOS/CI.
 */
class IosFinanceReportPdfGenerator : FinanceReportPdfGenerator {

    private companion object {
        const val PAGE_W = 595.0
        const val PAGE_H = 842.0
        const val MARGIN = 40.0
    }

    override fun generate(data: FinanceReportPdfData): ByteArray {
        val left = MARGIN
        val right = PAGE_W - MARGIN

        val ink = rgb(0x1A, 0x1A, 0x1A)
        val muted = rgb(0x6B, 0x6B, 0x6B)
        val line = rgb(0xDD, 0xDD, 0xDD)
        val headerBg = rgb(0xF2, 0xF4, 0xF7)
        val totalBg = rgb(0x1A, 0x1A, 0x1A)
        val totalText = rgb(0xFF, 0xFF, 0xFF)

        fun font(size: Double, bold: Boolean): UIFont =
            if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)

        fun attrs(color: UIColor, size: Double, bold: Boolean): Map<Any?, *> = mapOf<Any?, Any?>(
            NSFontAttributeName to font(size, bold),
            NSForegroundColorAttributeName to color,
        )

        val format = UIGraphicsPDFRendererFormat()
        val bounds: CValue<CGRect> = CGRectMake(0.0, 0.0, PAGE_W, PAGE_H)
        val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = format)

        val nsData = renderer.PDFDataWithActions { ctx ->
            val pdf = ctx ?: return@PDFDataWithActions
            pdf.beginPage()
            val cg = pdf.CGContext

            var y = MARGIN

            fun textWidth(text: String, size: Double, bold: Boolean): Double {
                val s = NSString.create(string = text)
                return s.sizeWithAttributes(attrs(ink, size, bold)).useContents { width }
            }

            fun drawText(text: String, x: Double, baselineY: Double, color: UIColor, size: Double, bold: Boolean) {
                val s = NSString.create(string = text)
                val topY = baselineY - font(size, bold).ascender
                s.drawAtPoint(CGPointMake(x, topY), withAttributes = attrs(color, size, bold))
            }

            fun drawRight(text: String, rightX: Double, baselineY: Double, color: UIColor, size: Double, bold: Boolean) {
                drawText(text, rightX - textWidth(text, size, bold), baselineY, color, size, bold)
            }

            fun drawLine(x1: Double, y1: Double, x2: Double, y2: Double, color: UIColor, w: Double) {
                cg ?: return
                CGContextSetStrokeColorWithColor(cg, color.CGColor)
                CGContextSetLineWidth(cg, w)
                CGContextMoveToPoint(cg, x1, y1)
                CGContextAddLineToPoint(cg, x2, y2)
                CGContextStrokePath(cg)
            }

            fun fillRect(x: Double, yy: Double, w: Double, h: Double, color: UIColor) {
                cg ?: return
                CGContextSetFillColorWithColor(cg, color.CGColor)
                CGContextFillRect(cg, CGRectMake(x, yy, w, h))
            }

            fun truncate(text: String, maxW: Double, size: Double): String {
                if (maxW <= 0.0) return ""
                if (textWidth(text, size, false) <= maxW) return text
                val ell = "…"
                var end = text.length
                while (end > 0 && textWidth(text.substring(0, end) + ell, size, false) > maxW) end--
                return text.substring(0, end).trimEnd() + ell
            }

            fun newPage() {
                pdf.beginPage()
                y = MARGIN
            }

            // --- Header --------------------------------------------------------
            run {
                var textX = left
                data.company.logoBytes?.let { bytes ->
                    val img = UIImage.imageWithData(bytes.toNSData())
                    if (img != null) {
                        val (iw, ih) = img.size.useContents { width to height }
                        if (iw > 0.0 && ih > 0.0) {
                            val ratio = minOf(64.0 / iw, 64.0 / ih, 1.0)
                            val dw = iw * ratio; val dh = ih * ratio
                            img.drawInRect(CGRectMake(left, MARGIN, dw, dh))
                            textX = left + dw + 14.0
                        }
                    }
                }
                var hy = MARGIN + 16.0
                drawText(data.company.name, textX, hy, ink, 18.0, true)
                data.company.phone?.let { hy += 14.0; drawText(it, textX, hy, muted, 10.0, false) }
                var ry = MARGIN + 16.0
                drawRight("Relatório financeiro", right, ry, ink, 14.0, true)
                ry += 14.0; drawRight(data.periodLabel, right, ry, muted, 10.0, false)
                ry += 14.0; drawRight(data.generatedAtLabel, right, ry, muted, 10.0, false)
                val bottom = maxOf(hy, ry) + 12.0
                drawLine(left, bottom, right, bottom, line, 1.0)
                y = bottom + 6.0
            }
            y += 16.0

            fun sectionTitle(title: String) {
                y += 12.0
                drawText(title, left, y, ink, 12.0, true)
                y += 8.0
            }

            fun emptyRow(text: String) {
                y += 14.0
                drawText(text, left + 6.0, y, muted, 10.0, false)
                y += 8.0
            }

            fun totalBox(label: String, total: String) {
                y += 6.0
                y += 12.0
                val boxH = 30.0
                val boxLeft = right - 240.0
                fillRect(boxLeft, y, right - boxLeft, boxH, totalBg)
                val cy = y + 20.0
                drawText(label, boxLeft + 12.0, cy, totalText, 10.0, true)
                drawRight(OsPdfFormat.money(total), right - 12.0, cy, totalText, 14.0, true)
                y += boxH
            }

            // --- R-A: Recebimentos --------------------------------------------
            val colNumA = left + 6.0
            val colClientA = left + 36.0
            val colPaidA = right - 200.0
            val colStatusA = right - 120.0
            val colAmountA = right - 6.0

            fun receiptsHeader() {
                val headerH = 20.0
                fillRect(left, y, right - left, headerH, headerBg)
                val b = y + 14.0
                drawText("Nº", colNumA, b, muted, 8.5, true)
                drawText("CLIENTE", colClientA, b, muted, 8.5, true)
                drawText("PAGO EM", colPaidA, b, muted, 8.5, true)
                drawText("STATUS", colStatusA, b, muted, 8.5, true)
                drawRight("RECEBIDO", colAmountA, b, muted, 8.5, true)
                y += headerH
            }

            sectionTitle("RECEBIMENTOS")
            if (data.receipts.isEmpty()) {
                emptyRow("Nenhum recebimento no período.")
            } else {
                receiptsHeader()
                for (r in data.receipts) {
                    if (y > PAGE_H - MARGIN - 60.0) {
                        newPage()
                        receiptsHeader()
                    }
                    val b = y + 13.0
                    drawText(r.number.toString(), colNumA, b, ink, 9.5, false)
                    drawText(truncate(r.clientName, colPaidA - colClientA - 6.0, 9.5), colClientA, b, ink, 9.5, false)
                    drawText(truncate(r.paidAtLabel, colStatusA - colPaidA - 6.0, 9.5), colPaidA, b, ink, 9.5, false)
                    drawText(truncate(r.paymentStatusLabel, colAmountA - 60.0 - colStatusA - 6.0, 9.5), colStatusA, b, ink, 9.5, false)
                    drawRight(OsPdfFormat.money(r.amountReceived), colAmountA, b, ink, 9.5, false)
                    y += 18.0
                    drawLine(left, y, right, y, line, 0.5)
                }
            }
            totalBox("TOTAL RECEBIDO", data.totalReceived)
            y += 24.0

            // --- R-B: Contas a receber ----------------------------------------
            if (y > PAGE_H - MARGIN - 120.0) newPage()

            val colNumB = left + 6.0
            val colClientB = left + 36.0
            val colStatusB = right - 250.0
            val colTotalB = right - 168.0
            val colRecvB = right - 88.0
            val colBalanceB = right - 6.0

            fun receivablesHeader() {
                val headerH = 20.0
                fillRect(left, y, right - left, headerH, headerBg)
                val b = y + 14.0
                drawText("Nº", colNumB, b, muted, 8.5, true)
                drawText("CLIENTE", colClientB, b, muted, 8.5, true)
                drawText("STATUS", colStatusB, b, muted, 8.5, true)
                drawRight("TOTAL", colTotalB, b, muted, 8.5, true)
                drawRight("RECEB.", colRecvB, b, muted, 8.5, true)
                drawRight("SALDO", colBalanceB, b, muted, 8.5, true)
                y += headerH
            }

            sectionTitle("CONTAS A RECEBER")
            if (data.receivables.isEmpty()) {
                emptyRow("Nenhuma conta a receber.")
            } else {
                receivablesHeader()
                for (r in data.receivables) {
                    if (y > PAGE_H - MARGIN - 60.0) {
                        newPage()
                        receivablesHeader()
                    }
                    val b = y + 13.0
                    drawText(r.number.toString(), colNumB, b, ink, 9.5, false)
                    drawText(truncate(r.clientName, colStatusB - colClientB - 6.0, 9.5), colClientB, b, ink, 9.5, false)
                    drawText(truncate(r.statusLabel, colTotalB - 60.0 - colStatusB - 6.0, 9.5), colStatusB, b, ink, 9.5, false)
                    drawRight(OsPdfFormat.money(r.total), colTotalB, b, ink, 9.5, false)
                    drawRight(OsPdfFormat.money(r.amountReceived), colRecvB, b, ink, 9.5, false)
                    drawRight(OsPdfFormat.money(r.balance), colBalanceB, b, ink, 9.5, false)
                    y += 18.0
                    drawLine(left, y, right, y, line, 0.5)
                }
            }
            totalBox("TOTAL A RECEBER", data.totalReceivable)
        }

        return nsData.toByteArray()
    }

    private fun rgb(r: Int, g: Int, b: Int): UIColor =
        UIColor.colorWithRed(r / 255.0, g / 255.0, b / 255.0, 1.0)
}

actual fun createFinanceReportPdfGenerator(): FinanceReportPdfGenerator =
    IosFinanceReportPdfGenerator()

// --- ByteArray <-> NSData (file-private; espelham os helpers do ReciboPdf.ios) ---

private fun ByteArray.toNSData(): NSData = memScoped {
    if (isEmpty()) return@memScoped NSData.create(bytes = null, length = 0u)
    val ptr = allocArray<kotlinx.cinterop.ByteVar>(size)
    for (i in indices) ptr[i] = this@toNSData[i]
    NSData.create(bytes = ptr, length = size.toULong())
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val out = ByteArray(length)
    if (length == 0) return out
    memScoped {
        val buffer = allocArray<kotlinx.cinterop.ByteVar>(length)
        platform.posix.memcpy(buffer, this@toByteArray.bytes, this@toByteArray.length)
        for (i in 0 until length) out[i] = buffer[i]
    }
    return out
}
