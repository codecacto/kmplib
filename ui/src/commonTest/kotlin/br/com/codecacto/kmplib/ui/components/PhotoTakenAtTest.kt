package br.com.codecacto.kmplib.ui.components

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class PhotoTakenAtTest {

    @Test
    fun `data EXIF no formato padrao`() {
        assertEquals(LocalDateTime(2026, 11, 3, 14, 5, 12), parseExifDateTime("2026:11:03 14:05:12"))
    }

    @Test
    fun `data EXIF tolerante ao que aparece em arquivo de verdade`() {
        val esperado = LocalDateTime(2026, 11, 3, 14, 5, 12)
        assertEquals(esperado, parseExifDateTime("2026-11-03T14:05:12"))
        assertEquals(esperado, parseExifDateTime("2026/11/03 14:05:12"))
        assertEquals(esperado, parseExifDateTime("2026:11:03 14:05:12\u0000"))
        assertEquals(esperado, parseExifDateTime("  2026:11:03 14:05:12  "))
        assertEquals(esperado, parseExifDateTime("2026:11:03 14:05:12.345"))
        assertEquals(LocalDateTime(2026, 11, 3, 14, 5, 0), parseExifDateTime("2026:11:03 14:05"))
    }

    @Test
    fun `data EXIF vazia zerada ou invalida e nula`() {
        assertNull(parseExifDateTime(null))
        assertNull(parseExifDateTime(""))
        assertNull(parseExifDateTime("    :  :     :  :  "))
        assertNull(parseExifDateTime("0000:00:00 00:00:00"))
        assertNull(parseExifDateTime("2026:13:03 14:05:12"))
        assertNull(parseExifDateTime("2026:02:30 14:05:12"))
        assertNull(parseExifDateTime("2026:11:03 25:05:12"))
        assertNull(parseExifDateTime("ontem"))
    }

    @Test
    fun `segundo bissexto vira 59 em vez de descartar a data`() {
        assertEquals(LocalDateTime(2016, 12, 31, 23, 59, 59), parseExifDateTime("2016:12:31 23:59:60"))
    }

    @Test
    fun `fuso EXIF`() {
        assertEquals(UtcOffset(hours = -4), parseExifUtcOffset("-04:00"))
        assertEquals(UtcOffset(hours = 5, minutes = 30), parseExifUtcOffset("+05:30"))
        assertEquals(UtcOffset(hours = -3, minutes = -30), parseExifUtcOffset("-0330"))
        assertEquals(UtcOffset(hours = 2), parseExifUtcOffset("+02"))
        assertEquals(UtcOffset.ZERO, parseExifUtcOffset("Z"))
        assertNull(parseExifUtcOffset(null))
        assertNull(parseExifUtcOffset("   :  "))
        assertNull(parseExifUtcOffset("+25:00"))
        assertNull(parseExifUtcOffset("04:00"))
    }

    @Test
    fun `fromExif prefere o original e usa o fuso do mesmo campo`() {
        val r = PhotoTakenAt.fromExif(
            dateTimeOriginal = "2026:11:03 14:05:12",
            offsetTimeOriginal = "-04:00",
            dateTimeDigitized = "2026:11:03 14:05:13",
            offsetTimeDigitized = "-03:00",
            dateTime = "2026:11:20 09:00:00",
            offsetTime = "-03:00",
        )!!
        assertEquals(PhotoTakenAtSource.EXIF_ORIGINAL, r.source)
        assertEquals(LocalDateTime(2026, 11, 3, 14, 5, 12), r.dateTime)
        assertEquals(UtcOffset(hours = -4), r.utcOffset)
        assertEquals(LocalDate(2026, 11, 3), r.date)
        assertEquals(Instant.parse("2026-11-03T18:05:12Z"), r.toInstantOrNull())
    }

    @Test
    fun `fromExif cai para digitalizada e depois para modificada`() {
        val digit = PhotoTakenAt.fromExif(dateTimeOriginal = "0000:00:00 00:00:00", dateTimeDigitized = "2026:01:02 03:04:05")!!
        assertEquals(PhotoTakenAtSource.EXIF_DIGITIZED, digit.source)
        assertNull(digit.utcOffset)
        assertNull(digit.toInstantOrNull())

        val mod = PhotoTakenAt.fromExif(dateTimeOriginal = null, dateTime = "2025:12:31 23:59:59", offsetTime = "+00:00")!!
        assertEquals(PhotoTakenAtSource.EXIF_MODIFIED, mod.source)
        assertEquals(UtcOffset.ZERO, mod.utcOffset)

        assertNull(PhotoTakenAt.fromExif(dateTimeOriginal = null))
    }

    @Test
    fun `sem fuso o instante usa o fuso de reserva`() {
        val r = PhotoTakenAt(LocalDateTime(2026, 11, 3, 23, 30), null, PhotoTakenAtSource.EXIF_ORIGINAL)
        assertEquals(Instant.parse("2026-11-04T03:30:00Z"), r.toInstant(TimeZone.of("America/Cuiaba")))
        // A DATA mostrada continua a do lugar da foto: 03/11, não 04/11.
        assertEquals(LocalDate(2026, 11, 3), r.date)
    }

    @Test
    fun `fromInstant guarda o relogio e o fuso daquele instante`() {
        val r = PhotoTakenAt.fromInstant(Instant.parse("2026-11-04T02:00:00Z"), PhotoTakenAtSource.CAMERA, TimeZone.of("America/Cuiaba"))
        assertEquals(LocalDateTime(2026, 11, 3, 22, 0), r.dateTime)
        assertEquals(UtcOffset(hours = -4), r.utcOffset)
        assertEquals(Instant.parse("2026-11-04T02:00:00Z"), r.toInstantOrNull())
    }

    @Test
    fun `preferencia entre fontes`() {
        val exif = PhotoTakenAt(LocalDateTime(2026, 1, 1, 10, 0), null, PhotoTakenAtSource.EXIF_ORIGINAL)
        val biblioteca = PhotoTakenAt(LocalDateTime(2026, 1, 2, 10, 0), UtcOffset.ZERO, PhotoTakenAtSource.PHOTO_LIBRARY)
        val modificada = PhotoTakenAt(LocalDateTime(2026, 3, 1, 10, 0), null, PhotoTakenAtSource.EXIF_MODIFIED)
        assertEquals(exif, preferredPhotoTakenAt(biblioteca, exif, modificada))
        assertEquals(biblioteca, preferredPhotoTakenAt(modificada, biblioteca))
        assertEquals(modificada, preferredPhotoTakenAt(null, modificada))
        assertNull(preferredPhotoTakenAt(null, null))
    }

    @Test
    fun `camera usa o disparo do EXIF ou o instante e sempre se declara camera`() {
        val tz = TimeZone.of("America/Cuiaba")
        val agora = Instant.parse("2026-11-03T18:10:00Z")
        val exif = PhotoTakenAt(LocalDateTime(2026, 11, 3, 14, 5, 12), null, PhotoTakenAtSource.EXIF_ORIGINAL)
        val r = cameraPhotoTakenAt(exif, agora, tz)
        assertEquals(PhotoTakenAtSource.CAMERA, r.source)
        assertEquals(exif.dateTime, r.dateTime)
        assertEquals(UtcOffset(hours = -4), r.utcOffset)

        val semExif = cameraPhotoTakenAt(null, agora, tz)
        assertEquals(LocalDateTime(2026, 11, 3, 14, 10), semExif.dateTime)
        assertEquals(PhotoTakenAtSource.CAMERA, semExif.source)

        // DateTime (arquivo modificado) não é o disparo: vale o instante.
        val modificada = exif.copy(source = PhotoTakenAtSource.EXIF_MODIFIED)
        assertEquals(semExif, cameraPhotoTakenAt(modificada, agora, tz))
    }

    @Test
    fun `PickedImage compara a data e o anexo expoe a data da foto`() {
        val data = PhotoTakenAt(LocalDateTime(2026, 11, 3, 14, 5), null, PhotoTakenAtSource.EXIF_ORIGINAL)
        val a = PickedImage(byteArrayOf(1, 2), 10, 20, takenAt = data)
        assertEquals(a, PickedImage(byteArrayOf(1, 2), 10, 20, takenAt = data))
        assertEquals(a.hashCode(), PickedImage(byteArrayOf(1, 2), 10, 20, takenAt = data).hashCode())
        assertNotEquals(a, PickedImage(byteArrayOf(1, 2), 10, 20))
        assertNull(PickedImage(byteArrayOf(1, 2), 10, 20).takenAt)
        assertEquals(data, PickedAttachment.Photo(a, AttachmentSource.GALLERY, "f.jpg").takenAt)
    }
}
