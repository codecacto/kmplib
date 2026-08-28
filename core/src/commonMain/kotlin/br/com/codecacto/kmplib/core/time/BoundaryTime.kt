package br.com.codecacto.kmplib.core.time

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Fronteira de tempo (**boundary timestamp**) — conversão canônica entre o **instante** trafegado
 * na API e o `Long` de **epoch millis (UTC)** usado no domínio dos apps.
 *
 * ## Por que existe
 * O contrato da onda de onboarding fixa **epoch millis (`Long`)** para instantes (`createdAt`,
 * `updatedAt`, `paidAt`, ...). Vários backends, porém, emitem **ISO-8601 string**
 * (`"2026-07-08T12:00:00Z"`). Cada app remendou isso por conta própria (`isoToMillis` no MinhasHoras,
 * `DomainMappers.isoToEpoch` no MinhaOS, recarimbar com `currentTimeMillis()` no PapelStudio — este
 * último **corrompe o dado**, pois inventa um instante novo). Aqui fica a conversão única, tolerante
 * na leitura e **canônica na escrita** (sempre epoch millis).
 *
 * ## Regra de ouro (Postel na fronteira)
 * - **Ler:** aceita número JSON (millis), string numérica (millis) e ISO-8601 (com ou sem offset).
 * - **Escrever:** SEMPRE número JSON de epoch millis. A lib nunca emite ISO no fio.
 * - **Nunca inventa instante.** Valor ausente/ilegível vira `null` (ou `0L` no helper "or zero"),
 *   jamais `agora`.
 *
 * ## Instante ≠ data de calendário (INEGOCIÁVEL)
 * Este módulo trata **instantes** (um ponto na linha do tempo — `createdAt`, `paidAt`, `syncedAt`).
 *
 * **Datas de calendário** — aniversário, vencimento de boleto, data da consulta, competência — **NÃO**
 * são instantes: não têm hora nem fuso, e convertê-las para millis desloca o dia conforme o
 * timezone do device (o clássico "aniversário virou dia 07 no fuso -03"). Modele-as como
 * `kotlinx.datetime.LocalDate`, serialize-as como `"yyyy-MM-dd"` com o serializer OFICIAL do
 * kotlinx-datetime (`LocalDate` já é `@Serializable` no formato ISO) e formate na UI com
 * `DateFormatters` (dd/MM/yyyy). **A lib não recria esse serializer de propósito** — usar o oficial
 * é o padrão-ouro:
 *
 * ```kotlin
 * @Serializable
 * data class Boleto(
 *     @Serializable(with = EpochMillisSerializer::class) val createdAt: Long, // INSTANTE
 *     val vencimento: LocalDate,                                              // DATA DE CALENDÁRIO
 * )
 * ```
 *
 * Por isso [parseBoundaryMillis] **rejeita** (`null`) uma string só-data `"2026-07-08"`: ela é uma
 * data de calendário chegando no campo errado, não um instante à meia-noite de algum fuso.
 */
object BoundaryTime {

    /** Sentinela de "sem instante" no domínio dos apps (`0L`). */
    const val EPOCH_UNSET: Long = 0L

    /** ISO-8601 sem offset (`2026-07-08T12:00:00` / `...:00.123`) — interpretado como **UTC**. */
    private val ISO_NO_OFFSET = Regex("""^\d{4}-\d{2}-\d{2}[Tt ]\d{2}:\d{2}(:\d{2})?(\.\d{1,9})?$""")

    /** Só dígitos (com sinal opcional) — epoch millis vindo como string. */
    private val NUMERIC = Regex("""^-?\d{1,19}$""")

    /**
     * Converte um valor de fronteira em **epoch millis**, ou `null` se ausente/ilegível.
     *
     * Aceita:
     * - `1751976000000` (epoch millis já correto — passa direto);
     * - `"1751976000000"` (epoch millis como string);
     * - `"2026-07-08T12:00:00Z"` / `"...+00:00"` / `"...-03:00"` (ISO-8601 com offset);
     * - `"2026-07-08T12:00:00"` / `"2026-07-08 12:00"` (ISO sem offset ⇒ **UTC**, documentado).
     *
     * Rejeita (⇒ `null`): `null`, vazio, `"2026-07-08"` (data de calendário — ver KDoc do objeto),
     * e qualquer texto não parseável. **Nunca lança.**
     */
    fun parseMillis(value: String?): Long? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null

        if (NUMERIC.matches(raw)) return raw.toLongOrNull()

        runCatching { Instant.parse(raw).toEpochMilliseconds() }.getOrNull()?.let { return it }

        if (ISO_NO_OFFSET.matches(raw)) {
            val normalized = raw.replace(' ', 'T').let { if (it.count { c -> c == ':' } == 1) "$it:00" else it }
            return runCatching {
                LocalDateTime.parse(normalized).toInstant(TimeZone.UTC).toEpochMilliseconds()
            }.getOrNull()
        }

        return null
    }

    /**
     * Igual a [parseMillis], mas degrada para [EPOCH_UNSET] (`0L`) — substitui o `isoToMillis`
     * copiado nos apps. Use quando o domínio já trata `0` como "sem data".
     */
    fun parseMillisOrUnset(value: String?): Long = parseMillis(value) ?: EPOCH_UNSET

    /**
     * Epoch millis → ISO-8601 UTC com milissegundos explícitos (`"2026-07-08T12:00:00.000Z"`).
     * Só para backends legados que ainda **exigem** ISO no corpo; o padrão é enviar o `Long`.
     */
    fun formatIsoUtc(millis: Long): String {
        val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC)
        fun Int.pad(n: Int) = toString().padStart(n, '0')
        return "${dt.year.pad(4)}-${dt.monthNumber.pad(2)}-${dt.dayOfMonth.pad(2)}" +
            "T${dt.hour.pad(2)}:${dt.minute.pad(2)}:${dt.second.pad(2)}" +
            ".${(dt.nanosecond / 1_000_000).pad(3)}Z"
    }

    /** `true` se [millis] representa um instante realmente preenchido (`> 0`). */
    fun isSet(millis: Long?): Boolean = millis != null && millis > EPOCH_UNSET

    /** Uso interno dos serializers: aceita número OU string. */
    internal fun fromJsonPrimitive(primitive: JsonPrimitive): Long? =
        if (primitive.isString) parseMillis(primitive.content) else primitive.longOrNull
}

/**
 * Serializer canônico de **instante de fronteira**: desserializa `Long` (millis), string numérica ou
 * ISO-8601; serializa **sempre** como número de epoch millis.
 *
 * Valor ausente/ilegível ⇒ [BoundaryTime.EPOCH_UNSET] (`0L`) — nunca `agora`, nunca exceção. Para
 * preservar o `null`, use [EpochMillisOrNullSerializer].
 *
 * ```kotlin
 * @Serializable
 * data class LancamentoDto(
 *     val id: String,
 *     @Serializable(with = EpochMillisSerializer::class) val createdAt: Long,
 *     @Serializable(with = EpochMillisOrNullSerializer::class) val deletedAt: Long? = null,
 * )
 * ```
 */
object EpochMillisSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.core.time.EpochMillis", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)

    override fun deserialize(decoder: Decoder): Long {
        val json = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val element = json.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return BoundaryTime.EPOCH_UNSET
        if (primitive is JsonNull) return BoundaryTime.EPOCH_UNSET
        return BoundaryTime.fromJsonPrimitive(primitive) ?: BoundaryTime.EPOCH_UNSET
    }
}

/**
 * Variante nullable de [EpochMillisSerializer]: `null`/`""`/ilegível preservam `null` (não viram `0`).
 * Serializa como número de epoch millis (ou `null`).
 */
object EpochMillisOrNullSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.core.time.EpochMillisOrNull", PrimitiveKind.LONG)
            .nullable

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val element = json.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return BoundaryTime.fromJsonPrimitive(primitive)
    }
}
