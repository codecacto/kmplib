package br.com.codecacto.kmplib.pix

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Régua COMUM do BR Code Pix entre as três libs** (2.329.0).
 *
 * `pix-brcode.fixtures.json` é COPIADO sem edição da weblib (`src/pix/__fixtures__/`) — o mesmo
 * arquivo que a backlib copia — e vira fonte do `commonTest` na compilação (roda no Android E no
 * iOS). Payload que não reproduz aqui é divergência entre as implementações: a correção é na
 * implementação errada (padrão BACEN, CRC-16/CCITT-FALSE), **nunca** na cópia. Mudar a régua é
 * mudar nas três, no mesmo dia (e subir `version`).
 */
class PixBrCodeFixturesTest {

    private val fixturesVersion = 1

    private val root: JsonObject = Json.parseToJsonElement(PIX_BRCODE_FIXTURES_JSON).jsonObject

    private fun group(name: String) = root.getValue(name).jsonArray.map { it.jsonObject }

    private fun JsonObject.str(key: String): String? =
        this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

    private fun chargeOf(case: JsonObject): PixCharge {
        val c = case.getValue("charge").jsonObject
        return PixCharge(
            key = c.str("key")!!,
            merchantName = c.str("merchantName")!!,
            merchantCity = c.str("merchantCity")!!,
            amount = c.str("amount"),
            txid = c.str("txid"),
            description = c.str("description"),
        )
    }

    /** `EmptyKey` → `EMPTY_KEY`: o nome do erro no contrato comum. */
    private fun PixBrCodeError.wireName(): String =
        name.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

    @Test
    fun versaoEGruposPresentes() {
        assertEquals(fixturesVersion, root.getValue("version").jsonPrimitive.int)
        assertTrue(group("payloads").size >= 10)
        assertTrue(group("errors").size >= 6)
    }

    @Test
    fun crc16BateComOCheckValueEComOManualDoBacen() {
        for (c in group("crc16")) {
            assertEquals(c.str("crc"), PixCrc.compute(c.str("input")!!), c.str("source"))
        }
        assertTrue(group("crc16").any { it.str("crc") == "1D3D" }, "âncora no exemplo do Manual do BR Code")
    }

    @Test
    fun reproduzCadaPayload() {
        val divergentes = group("payloads").mapNotNull { case ->
            val esperado = case.str("payload")
            val obtido = buildPixBrCode(chargeOf(case))
            if (obtido == PixBrCodeResult.Ok(esperado!!)) null else "${case.str("id")}: esperado $esperado, obtido $obtido"
        }
        assertTrue(divergentes.isEmpty(), divergentes.joinToString("\n"))
    }

    @Test
    fun cadaPayloadReleLimpoPeloParser() {
        for (case in group("payloads")) {
            val payload = case.str("payload")!!
            assertTrue(PixCrc.isValid(payload), "${case.str("id")}: CRC")
            val lido = parseBrCode(payload)
            assertTrue(lido.isValidPix, "${case.str("id")}: $lido")
        }
    }

    @Test
    fun recusaCadaErroComOMotivoDoContrato() {
        val divergentes = group("errors").mapNotNull { case ->
            val esperado = case.str("error")
            val obtido = (buildPixBrCode(chargeOf(case)) as? PixBrCodeResult.Invalid)?.error?.wireName()
            if (obtido == esperado) null else "${case.str("id")}: esperado $esperado, obtido $obtido"
        }
        assertTrue(divergentes.isEmpty(), divergentes.joinToString("\n"))
    }

    @Test
    fun normalizaValorComoAsOutrasLibs() {
        val divergentes = group("amountInputs").mapNotNull { case ->
            val esperado = case.str("normalized")
            val obtido = normalizeAmount(case.str("input")!!)
            if (obtido == esperado) null else "\"${case.str("input")}\": esperado $esperado, obtido $obtido"
        }
        assertTrue(divergentes.isEmpty(), divergentes.joinToString("\n"))
    }
}
