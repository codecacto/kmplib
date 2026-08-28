package br.com.codecacto.kmplib.core.time

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fronteira de tempo: leitura tolerante (millis/ISO), escrita canônica (millis), e a fronteira
 * explícita com **datas de calendário** (rejeitadas).
 */
class BoundaryTimeTest {

    // 2026-07-08T12:00:00Z
    private val millis = 1_783_512_000_000L

    @Serializable
    private data class Dto(
        @Serializable(with = EpochMillisSerializer::class) val createdAt: Long = 0L,
        @Serializable(with = EpochMillisOrNullSerializer::class) val deletedAt: Long? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseMillis_accepts_epoch_millis_string() {
        assertEquals(millis, BoundaryTime.parseMillis(millis.toString()))
    }

    @Test
    fun parseMillis_accepts_iso_with_offset() {
        assertEquals(millis, BoundaryTime.parseMillis("2026-07-08T12:00:00Z"))
        assertEquals(millis, BoundaryTime.parseMillis("2026-07-08T09:00:00-03:00"))
        assertEquals(millis, BoundaryTime.parseMillis("2026-07-08T12:00:00.000Z"))
    }

    @Test
    fun parseMillis_treats_iso_without_offset_as_utc() {
        assertEquals(millis, BoundaryTime.parseMillis("2026-07-08T12:00:00"))
        assertEquals(millis, BoundaryTime.parseMillis("2026-07-08 12:00"))
    }

    @Test
    fun parseMillis_rejects_calendar_date_and_garbage() {
        // Data de calendário (aniversário/vencimento) NÃO é instante — não vira meia-noite de fuso algum.
        assertNull(BoundaryTime.parseMillis("2026-07-08"))
        assertNull(BoundaryTime.parseMillis("ontem"))
        assertNull(BoundaryTime.parseMillis(""))
        assertNull(BoundaryTime.parseMillis(null))
    }

    @Test
    fun parseMillisOrUnset_never_invents_now() {
        assertEquals(BoundaryTime.EPOCH_UNSET, BoundaryTime.parseMillisOrUnset(null))
        assertEquals(BoundaryTime.EPOCH_UNSET, BoundaryTime.parseMillisOrUnset("lixo"))
        assertEquals(millis, BoundaryTime.parseMillisOrUnset("2026-07-08T12:00:00Z"))
    }

    @Test
    fun formatIsoUtc_round_trips() {
        assertEquals("2026-07-08T12:00:00.000Z", BoundaryTime.formatIsoUtc(millis))
        assertEquals("2026-07-08T12:00:00.123Z", BoundaryTime.formatIsoUtc(millis + 123))
        assertEquals(millis + 123, BoundaryTime.parseMillis(BoundaryTime.formatIsoUtc(millis + 123)))
    }

    @Test
    fun serializer_reads_number_string_and_iso() {
        assertEquals(millis, json.decodeFromString<Dto>("""{"createdAt":$millis}""").createdAt)
        assertEquals(millis, json.decodeFromString<Dto>("""{"createdAt":"$millis"}""").createdAt)
        assertEquals(
            millis,
            json.decodeFromString<Dto>("""{"createdAt":"2026-07-08T12:00:00Z"}""").createdAt,
        )
    }

    @Test
    fun serializer_degrades_unset_never_throws() {
        assertEquals(0L, json.decodeFromString<Dto>("""{"createdAt":null}""").createdAt)
        assertEquals(0L, json.decodeFromString<Dto>("""{"createdAt":"amanhã"}""").createdAt)
    }

    @Test
    fun nullable_serializer_preserves_null() {
        assertNull(json.decodeFromString<Dto>("""{"createdAt":1,"deletedAt":null}""").deletedAt)
        assertNull(json.decodeFromString<Dto>("""{"createdAt":1,"deletedAt":"xx"}""").deletedAt)
        assertEquals(millis, json.decodeFromString<Dto>("""{"createdAt":1,"deletedAt":"2026-07-08T12:00:00Z"}""").deletedAt)
    }

    @Test
    fun serializer_always_writes_epoch_millis_number() {
        val encoded = json.encodeToString(Dto(createdAt = millis, deletedAt = millis))
        assertTrue(encoded.contains("\"createdAt\":$millis"), encoded)
        assertTrue(encoded.contains("\"deletedAt\":$millis"), encoded)
    }

    @Test
    fun isSet_only_for_positive_instants() {
        assertTrue(BoundaryTime.isSet(millis))
        assertTrue(!BoundaryTime.isSet(0L))
        assertTrue(!BoundaryTime.isSet(null))
    }
}
