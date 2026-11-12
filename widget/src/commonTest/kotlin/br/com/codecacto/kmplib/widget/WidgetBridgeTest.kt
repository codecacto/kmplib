@file:OptIn(kotlin.time.ExperimentalTime::class)

package br.com.codecacto.kmplib.widget

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetBridgeTest {
    @Test
    fun `proxima meia noite no fuso local com folga`() {
        val tz = TimeZone.of("America/Cuiaba")
        // 23:30 em Cuiabá (UTC−4) → meia-noite local = 04:00Z do dia seguinte, +60 s.
        assertEquals(Instant.parse("2026-11-12T04:01:00Z"), nextMidnight(Instant.parse("2026-11-12T03:30:00Z"), tz))
        assertEquals(Instant.parse("2026-11-12T04:00:00Z"), nextMidnight(Instant.parse("2026-11-11T05:00:00Z"), tz, marginSeconds = 0))
    }
}
