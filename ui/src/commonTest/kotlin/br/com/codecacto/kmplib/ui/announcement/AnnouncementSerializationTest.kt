@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.ui.announcement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class AnnouncementSerializationTest {

    @Test
    fun kinds_sao_serializados_em_camel_case_como_na_weblib() {
        val expected = mapOf(
            AnnouncementKind.NEWS to "news",
            AnnouncementKind.UPDATE to "update",
            AnnouncementKind.MAINTENANCE to "maintenance",
            AnnouncementKind.CROSS_PLATFORM to "crossPlatform",
            AnnouncementKind.INFO to "info",
        )
        expected.forEach { (kind, wire) ->
            val json = AnnouncementJson.encodeToString(AnnouncementKind.serializer(), kind)
            assertEquals("\"$wire\"", json)
            assertEquals(kind, AnnouncementJson.decodeFromString(AnnouncementKind.serializer(), json))
        }
        assertEquals(AnnouncementKind.entries.size, expected.size)
    }

    @Test
    fun kind_desconhecido_vira_info_em_vez_de_derrubar_a_lista() {
        val list = decodeAnnouncements("""[{"id":"a","kind":"promo","title":"Oi"}]""")
        assertEquals(AnnouncementKind.INFO, list.single().kind)
    }

    @Test
    fun frequencias_tem_os_nomes_do_contrato_e_desconhecida_cai_no_default() {
        val list = decodeAnnouncements(
            """[
              {"id":"a","kind":"news","title":"A","frequency":"once"},
              {"id":"b","kind":"news","title":"B","frequency":"untilDismissed"},
              {"id":"c","kind":"news","title":"C","frequency":"always"},
              {"id":"d","kind":"news","title":"D","frequency":"weekly"},
              {"id":"e","kind":"news","title":"E"}
            ]""",
        )
        assertEquals(
            listOf(
                AnnouncementFrequency.ONCE,
                AnnouncementFrequency.UNTIL_DISMISSED,
                AnnouncementFrequency.ALWAYS,
                AnnouncementFrequency.ONCE,
                AnnouncementFrequency.ONCE,
            ),
            list.map { it.frequency },
        )
    }

    @Test
    fun documento_completo_le_janela_com_fuso_e_campos_opcionais() {
        val a = decodeAnnouncements(
            """[{
              "id": "manutencao-1",
              "kind": "maintenance",
              "title": "Manutenção programada",
              "message": "Servidor em atualização.",
              "window": {"start": "2026-10-18T22:00:00-03:00", "end": "2026-10-19T02:00:00-03:00"},
              "visibleFrom": "2026-10-15T00:00:00Z",
              "visibleUntil": "2026-10-19T05:00:00Z",
              "action": {"label": "Saiba mais", "url": "https://exemplo.com/status"},
              "frequency": "untilDismissed",
              "dismissible": false,
              "campoNovoDoServidor": 42
            }]""",
        ).single()
        assertEquals(Instant.parse("2026-10-19T01:00:00Z"), a.window!!.start)
        assertEquals(Instant.parse("2026-10-19T05:00:00Z"), a.window.end)
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), a.visibleFrom)
        assertEquals(AnnouncementAction("Saiba mais", "https://exemplo.com/status"), a.action)
        assertEquals(false, a.dismissible)
        assertEquals("Servidor em atualização.", a.message)
    }

    @Test
    fun item_malformado_e_descartado_sem_levar_os_outros() {
        val list = decodeAnnouncements(
            """[
              {"id":"ok","kind":"info","title":"Fica"},
              {"kind":"info","title":"Sem id"},
              {"id":"data","kind":"info","title":"Data ruim","visibleFrom":"ontem"},
              {"id":"vazio","kind":"info","title":"  "},
              "texto solto",
              {"id":"ok2","kind":"update","title":"Também fica"}
            ]""",
        )
        assertEquals(listOf("ok", "ok2"), list.map { it.id })
    }

    @Test
    fun texto_que_nao_e_array_devolve_lista_vazia() {
        assertTrue(decodeAnnouncements("""{"id":"a"}""").isEmpty())
        assertTrue(decodeAnnouncements("não é json").isEmpty())
        assertTrue(decodeAnnouncements("").isEmpty())
    }

    @Test
    fun ida_e_volta_preserva_o_aviso() {
        val original = listOf(
            Announcement(
                id = "x",
                kind = AnnouncementKind.CROSS_PLATFORM,
                title = "Também no navegador",
                window = AnnouncementWindow(Instant.parse("2026-10-18T10:00:00Z"), Instant.parse("2026-10-18T11:00:00Z")),
                action = AnnouncementAction("Abrir", "https://app.exemplo.com"),
                frequency = AnnouncementFrequency.ALWAYS,
            ),
        )
        val json = encodeAnnouncements(original)
        assertTrue("\"crossPlatform\"" in json)
        assertTrue("\"always\"" in json)
        assertTrue("\"2026-10-18T10:00:00Z\"" in json)
        assertEquals(original, decodeAnnouncements(json))
    }
}
