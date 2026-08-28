package br.com.codecacto.kmplib.qr

import java.io.File
import kotlin.test.Test

/**
 * ANDAIME TEMPORÁRIO — despeja matrizes para decodificação por leitor externo (jsQR).
 * Não faz parte da suíte; será removido.
 */
class QrMatrixDumpForExternalDecoder {
    @Test
    fun dump() {
        val out = System.getenv("QR_DUMP_PATH") ?: return
        val payloads = listOf(
            "01234567",
            "HELLO WORLD",
            "https://codecacto.com.br/confere-qr",
            "Cofre do Café — 12 plaquinhas (acentuação e emoji ✔)",
            "confere-qr:v1:" + (1..40).joinToString(";") { "p$it:00020126580014br.gov.bcb.pix" },
            "9".repeat(300),
            "x".repeat(1200),
        )
        val entries = mutableListOf<String>()
        for (payload in payloads) {
            for (level in QrErrorCorrection.entries) {
                val qr = encodeQrOrNull(payload, level) ?: continue
                val rows = (0 until qr.size).map { y ->
                    (0 until qr.size).joinToString("") { x -> if (qr.isDark(x, y)) "1" else "0" }
                }
                val esc = payload.replace("\\", "\\\\").replace("\"", "\\\"")
                entries += """{"text":"$esc","level":"${level.name}","version":${qr.version},""" +
                    """"mask":${qr.mask},"size":${qr.size},"rows":[${rows.joinToString(","){"\"$it\""}}]}"""
            }
        }
        File(out).writeText("[" + entries.joinToString(",\n") + "]")
    }
}
