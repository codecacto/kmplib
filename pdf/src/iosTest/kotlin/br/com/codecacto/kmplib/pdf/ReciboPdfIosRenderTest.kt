@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package br.com.codecacto.kmplib.pdf

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Render de verdade de um PDF LEGADO (`IosPdfCanvas`, o mesmo núcleo dos 9 geradores) no simulador,
 * relido com o PDFKit. Até a 2.226.0 a cor do texto ia ao CoreText como embrulho Kotlin e todo
 * `CTLineDraw` matava o processo — o recibo é o caso mínimo que passa por esse caminho.
 */
class ReciboPdfIosRenderTest {

    @Test
    fun reciboGeraPdfComTextoPesquisavel() {
        val data = ReciboPdfData(
            emitente = ReciboParte(nome = "Diana Souza", documento = "CPF: 123.***.***-09"),
            pagador = ReciboParte(nome = "Joao da Silva", documento = "CPF: 987.***.***-00"),
            valorFormatado = "R$ 120,00",
            valorPorExtenso = "cento e vinte reais",
            descricao = "aula de violao",
            localData = "Sao Paulo, 6 de junho de 2026.",
            numeroRecibo = "0001",
            dataHoraEmissao = "06/06/2026 14:32",
        )
        val bytes = generateReciboPdf(data, watermark = false)
        assertTrue(bytes.size > 4 && bytes.decodeToString(0, 5) == "%PDF-", "não é PDF")

        val document = assertNotNull(PDFDocument(data = bytes.toNSData()))
        assertTrue(document.pageCount() >= 1uL)
        val text = document.string ?: ""
        assertTrue(text.contains("Diana Souza"), "texto do recibo embutido e pesquisável: '$text'")
    }
}

private fun ByteArray.toNSData(): NSData =
    usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
