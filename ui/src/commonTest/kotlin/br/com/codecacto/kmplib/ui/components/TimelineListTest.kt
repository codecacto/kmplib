package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Quem pinta o selo de um marco da [TimelineList] (2.181.0).
 *
 * O caso real: uma ocorrência **concluída com atraso** tem `status = Done` (verde) e precisa de um
 * selo em tom de alerta. Antes só existia `badgeColor: Color`, e cor de tema só se lê dentro de um
 * `@Composable` — quem monta a lista é o ViewModel, então o campo era inutilizável ali e o atraso
 * virava texto no subtítulo. Estes testes travam a precedência do `badgeTone` sem quebrar quem já
 * passa `badgeColor`.
 */
class TimelineListBadgeTest {

    private val doStatus = Color.Green
    private val doTom = Color.Yellow
    private val explicita = Color.Blue

    @Test
    fun `sem tom e sem cor, o selo segue o status do item`() {
        assertEquals(doStatus, corDoBadgeDaTimeline(toneColor = null, badgeColor = null, fallback = doStatus))
    }

    @Test
    fun `o tom semantico vence o status`() {
        assertEquals(doTom, corDoBadgeDaTimeline(toneColor = doTom, badgeColor = null, fallback = doStatus))
    }

    @Test
    fun `o tom semantico vence tambem a cor explicita`() {
        assertEquals(doTom, corDoBadgeDaTimeline(toneColor = doTom, badgeColor = explicita, fallback = doStatus))
    }

    @Test
    fun `quem ja passa badgeColor continua mandando no selo`() {
        assertEquals(
            explicita,
            corDoBadgeDaTimeline(toneColor = null, badgeColor = explicita, fallback = doStatus),
        )
    }
}

/**
 * O contrato do [TimelineItem] visto de fora — é ele que o app constrói.
 *
 * O campo novo entrou no FIM da lista de propósito: encaixado ao lado de `badgeColor`, todo
 * consumidor que constrói o item por posição passaria a apontar para o campo errado.
 */
class TimelineItemContratoTest {

    @Test
    fun `badgeTone nasce nulo — item existente nao muda de cor`() {
        val item = TimelineItem(id = "1", title = "Regar as plantas")
        assertNull(item.badgeTone)
        assertNull(item.badgeColor)
        assertEquals(TimelineStatus.None, item.status)
    }

    @Test
    fun `o item posicional de sempre continua significando o mesmo`() {
        val item = TimelineItem(
            "1",
            "Regar as plantas",
            "04/09",
            "Varanda",
            TimelineStatus.Done,
            "concluída",
        )
        assertEquals("04/09", item.dateLabel)
        assertEquals("Varanda", item.subtitle)
        assertEquals(TimelineStatus.Done, item.status)
        assertEquals("concluída", item.badgeLabel)
        assertNull(item.badgeTone)
    }

    @Test
    fun `atraso ganha tom proprio num item concluido`() {
        val item = TimelineItem(
            id = "1",
            title = "Regar as plantas",
            status = TimelineStatus.Done,
            badgeLabel = "concluída com atraso",
            badgeTone = StatusTone.WARNING,
        )
        assertEquals(StatusTone.WARNING, item.badgeTone)
        assertEquals(TimelineStatus.Done, item.status)
    }
}
