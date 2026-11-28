package br.com.codecacto.kmplib.ui.components

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// =================================================================================================
// Data em que a foto foi TIRADA (2.383.0).
//
// O seletor recodifica a foto em JPEG sem metadados (sem GPS, sem modelo) — e, com isso, a data da
// captura também se perdia. Quem monta relatório com fotos datadas (o relatório mensal do professor,
// no Mais que atleta) obrigava a pessoa a digitar a data de cada foto. Agora a data é lida da fonte
// ANTES da recodificação e viaja ao lado dos bytes, em `PickedImage.takenAt`; o JPEG continua limpo.
// =================================================================================================

/** De onde veio a data de [PhotoTakenAt] — da mais confiável para a menos. */
enum class PhotoTakenAtSource {
    /** A foto acabou de ser tirada pela câmera aberta pelo app: o instante da captura. */
    CAMERA,

    /** EXIF `DateTimeOriginal` (+ `OffsetTimeOriginal`): o momento do disparo, gravado pela câmera. */
    EXIF_ORIGINAL,

    /** EXIF `DateTimeDigitized` (+ `OffsetTimeDigitized`): quando a imagem foi digitalizada — numa câmera, o disparo. */
    EXIF_DIGITIZED,

    /**
     * iOS: `PHAsset.creationDate` da fototeca (inclui o "Ajustar data e hora" do app Fotos). Só é lida
     * se o app JÁ tem acesso de leitura à fototeca — a lib nunca pede essa permissão só por causa da data.
     */
    PHOTO_LIBRARY,

    /**
     * EXIF `DateTime` (TIFF): a última vez que o ARQUIVO foi alterado. Numa foto editada ou reexportada
     * pode ser bem depois do disparo — por isso é a última opção.
     */
    EXIF_MODIFIED,
}

/**
 * Quando a foto foi tirada.
 *
 * A EXIF guarda o **relógio de parede** do lugar em que a foto foi tirada ("2026:11:03 14:05:12") e,
 * nas câmeras mais novas, o fuso à parte (`OffsetTimeOriginal` = "-04:00"). Por isso o dado principal é
 * [dateTime] — é ele que vai para a tela e para o relatório ("03/11/2026") — e o instante absoluto só
 * existe quando o fuso é conhecido ([toInstantOrNull]). Converter um horário sem fuso para outro fuso
 * erraria a data de uma foto tirada perto da meia-noite.
 *
 * @property dateTime data e hora locais em que a foto foi tirada (relógio do lugar).
 * @property utcOffset o fuso daquela hora, quando a fonte informa; `null` = desconhecido.
 * @property source de onde a data veio.
 */
data class PhotoTakenAt(
    val dateTime: LocalDateTime,
    val utcOffset: UtcOffset?,
    val source: PhotoTakenAtSource,
) {
    /** Só a data — o que um relatório costuma mostrar. */
    val date: LocalDate get() = dateTime.date

    /** O instante absoluto, quando o fuso é conhecido; `null` sem fuso (não se inventa um). */
    fun toInstantOrNull(): Instant? = utcOffset?.let { dateTime.toInstant(it) }

    /**
     * O instante absoluto, usando [fallbackTimeZone] quando a fonte não informou o fuso. Para gravar no
     * servidor; para mostrar, prefira [dateTime].
     */
    fun toInstant(fallbackTimeZone: TimeZone = TimeZone.currentSystemDefault()): Instant =
        toInstantOrNull() ?: dateTime.toInstant(fallbackTimeZone)

    companion object {
        /** Um instante (câmera, fototeca) no relógio de [timeZone] — com o fuso daquele instante. */
        fun fromInstant(
            instant: Instant,
            source: PhotoTakenAtSource,
            timeZone: TimeZone = TimeZone.currentSystemDefault(),
        ): PhotoTakenAt = PhotoTakenAt(
            dateTime = instant.toLocalDateTime(timeZone),
            utcOffset = timeZone.offsetAt(instant),
            source = source,
        )

        /**
         * A data a partir dos campos EXIF, como as plataformas os devolvem (texto): `DateTimeOriginal`,
         * depois `DateTimeDigitized`, depois `DateTime`. Campo vazio, zerado ("0000:00:00 00:00:00") ou
         * ilegível é ignorado e passa ao próximo; o fuso só é usado se for o DO MESMO campo.
         *
         * @return `null` quando nenhum dos três é uma data válida.
         */
        fun fromExif(
            dateTimeOriginal: String?,
            offsetTimeOriginal: String? = null,
            dateTimeDigitized: String? = null,
            offsetTimeDigitized: String? = null,
            dateTime: String? = null,
            offsetTime: String? = null,
        ): PhotoTakenAt? {
            val candidatos = listOf(
                Triple(dateTimeOriginal, offsetTimeOriginal, PhotoTakenAtSource.EXIF_ORIGINAL),
                Triple(dateTimeDigitized, offsetTimeDigitized, PhotoTakenAtSource.EXIF_DIGITIZED),
                Triple(dateTime, offsetTime, PhotoTakenAtSource.EXIF_MODIFIED),
            )
            for ((valor, fuso, origem) in candidatos) {
                val quando = parseExifDateTime(valor) ?: continue
                return PhotoTakenAt(quando, parseExifUtcOffset(fuso), origem)
            }
            return null
        }
    }
}

/** A posição de cada origem na preferência de [preferredPhotoTakenAt] (menor = melhor). */
private fun PhotoTakenAtSource.rank(): Int = when (this) {
    PhotoTakenAtSource.CAMERA -> 0
    PhotoTakenAtSource.EXIF_ORIGINAL -> 1
    PhotoTakenAtSource.EXIF_DIGITIZED -> 2
    PhotoTakenAtSource.PHOTO_LIBRARY -> 3
    PhotoTakenAtSource.EXIF_MODIFIED -> 4
}

/**
 * Entre várias leituras da mesma foto (EXIF dos bytes, fototeca), a de origem mais confiável — a
 * ordem de [PhotoTakenAtSource]. Nulos são ignorados; nenhuma → `null`. Pura e testada.
 */
internal fun preferredPhotoTakenAt(vararg candidates: PhotoTakenAt?): PhotoTakenAt? =
    candidates.filterNotNull().minByOrNull { it.source.rank() }

/**
 * A data da foto tirada pela câmera aberta pelo app: o disparo gravado pela própria câmera (EXIF
 * original, com o fuso do aparelho quando o arquivo não o traz — a foto acabou de ser tirada aqui) ou,
 * sem ele, [now]. A origem é sempre [PhotoTakenAtSource.CAMERA].
 */
internal fun cameraPhotoTakenAt(
    exif: PhotoTakenAt?,
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): PhotoTakenAt {
    if (exif != null && exif.source == PhotoTakenAtSource.EXIF_ORIGINAL) {
        val fuso = exif.utcOffset ?: timeZone.offsetAt(exif.dateTime.toInstant(timeZone))
        return PhotoTakenAt(exif.dateTime, fuso, PhotoTakenAtSource.CAMERA)
    }
    return PhotoTakenAt.fromInstant(now, PhotoTakenAtSource.CAMERA, timeZone)
}

/**
 * `"AAAA:MM:DD HH:MM:SS"` (o formato da EXIF 2.32, §4.6.5) → [LocalDateTime]. Tolerante ao que se vê
 * em arquivo de verdade: `-` ou `/` no lugar de `:` na data, `T` no lugar do espaço, segundos ausentes,
 * fração de segundo, `NUL`/espaços no fim. Vazio, só separadores ("    :  :     :  :  "), ano zero e
 * data/hora fora da faixa → `null`.
 */
internal fun parseExifDateTime(value: String?): LocalDateTime? {
    val texto = value?.trim { it.isWhitespace() || it == '\u0000' } ?: return null
    val m = EXIF_DATE_TIME.matchEntire(texto) ?: return null
    val (ano, mes, dia, hora, minuto) = m.destructured
    val segundo = m.groupValues[6].ifEmpty { "0" }.toInt()
    val a = ano.toInt()
    if (a < 1800) return null // "0000:00:00 00:00:00" é o "sem data" das câmeras
    return try {
        LocalDateTime(a, mes.toInt(), dia.toInt(), hora.toInt(), minuto.toInt(), segundo.coerceAtMost(59))
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** `"+HH:MM"`/`"-HH:MM"` (EXIF `OffsetTime*`), também `+HHMM`, `+HH` e `Z` → [UtcOffset]; ilegível → `null`. */
internal fun parseExifUtcOffset(value: String?): UtcOffset? {
    val texto = value?.trim { it.isWhitespace() || it == '\u0000' } ?: return null
    if (texto.equals("Z", ignoreCase = true)) return UtcOffset.ZERO
    val m = EXIF_OFFSET.matchEntire(texto) ?: return null
    val sinal = if (m.groupValues[1] == "-") -1 else 1
    val horas = m.groupValues[2].toInt()
    val minutos = m.groupValues[3].ifEmpty { "0" }.toInt()
    if (horas > 18 || minutos > 59) return null
    return try {
        UtcOffset(hours = sinal * horas, minutes = sinal * minutos)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private val EXIF_DATE_TIME =
    Regex("""(\d{4})[:/-](\d{1,2})[:/-](\d{1,2})[ T]+(\d{1,2}):(\d{1,2})(?::(\d{1,2})(?:[.,]\d+)?)?""")

private val EXIF_OFFSET = Regex("""([+-])(\d{2}):?(\d{2})?""")
