@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.ui.announcement

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * ───────────────────────────── Avisos / anúncios do app (2.294.0) ─────────────────────────────
 *
 * Um modelo SÓ para tudo o que o produto quer dizer a quem abre o app sem ser erro nem resultado de
 * ação: manutenção programada ("sábado, das 22:00 às 02:00, fica fora do ar"), atualização,
 * novidade, "também temos a versão web" e aviso geral. É o PAR do `Announcement` da weblib — mesmos
 * nomes, mesmo JSON — para que o MESMO cadastro (Nexus → Avisos) sirva ao app e ao site.
 *
 * ### Contrato FUTURO (documentado, ainda não implementado)
 *
 * ```
 * GET {appsApi}/public/announcements?project=<slug>&platform=android|ios|web
 *   → 200 [ Announcement, … ]   (JSON abaixo; ordem do servidor = desempate de prioridade)
 * ```
 *
 * Alimentado pelo Nexus → Avisos. Quando existir, o `fetchAnnouncements(...)` mora no
 * `kmplib-central` (é quem fala com o apps-api) e devolve `List<Announcement>` via
 * [decodeAnnouncements] — o resto (escolha, persistência, cartão) já está aqui e não muda. Até lá o
 * app monta a lista localmente (constante, remote config próprio, backend do produto).
 *
 * ```json
 * {
 *   "id": "manutencao-2026-10-18",
 *   "kind": "maintenance",
 *   "title": "Manutenção programada",
 *   "message": "O app ficará indisponível durante a atualização do servidor.",
 *   "window": { "start": "2026-10-18T22:00:00-03:00", "end": "2026-10-19T02:00:00-03:00" },
 *   "visibleFrom": "2026-10-15T00:00:00Z",
 *   "action": { "label": "Saiba mais", "url": "https://exemplo.codecacto.com.br/status" },
 *   "frequency": "untilDismissed",
 *   "dismissible": true,
 *   "image": { "url": "https://cdn.codecacto.com.br/avisos/manutencao.webp", "alt": "Servidor em manutenção", "focus": "center" },
 *   "presentation": "modal"
 * }
 * ```
 *
 * `image` e `presentation` (2.296.0) são opcionais: app antigo ignora, servidor antigo não manda.
 */

/**
 * O tipo do aviso — decide ícone, cor e prioridade ([announcementPriority]).
 *
 * Serializado em camelCase (`"news"`, `"update"`, `"maintenance"`, `"crossPlatform"`, `"info"`),
 * igual à weblib. Valor desconhecido (servidor mais novo que o app) vira [INFO] em vez de derrubar a
 * lista inteira.
 */
@Serializable(with = AnnouncementKindSerializer::class)
enum class AnnouncementKind(val wireName: String) {
    /** Novidade no app (recurso novo, conteúdo novo). */
    NEWS("news"),

    /** Versão nova disponível / o que mudou. */
    UPDATE("update"),

    /** Manutenção programada — normalmente com [Announcement.window]. */
    MAINTENANCE("maintenance"),

    /** "Também temos a versão web" (no app) / "também temos o app" (no web). */
    CROSS_PLATFORM("crossPlatform"),

    /** Aviso geral, sem categoria. */
    INFO("info"),
    ;

    companion object {
        /** O kind do valor de fio, ou `null` se desconhecido. */
        fun fromWireName(value: String): AnnouncementKind? = entries.firstOrNull { it.wireName == value }
    }
}

/**
 * Com que frequência o aviso volta a aparecer.
 *
 * - [ONCE] — aparece numa sessão só: a primeira em que for exibido. Fica até o fim dessa sessão (ou
 *   até ser dispensado) e não volta.
 * - [UNTIL_DISMISSED] — volta a cada abertura até a pessoa tocar no X (persistido).
 * - [ALWAYS] — volta a cada abertura do app; o X esconde só até a próxima abertura.
 */
@Serializable
enum class AnnouncementFrequency {
    @SerialName("once")
    ONCE,

    @SerialName("untilDismissed")
    UNTIL_DISMISSED,

    @SerialName("always")
    ALWAYS,
}

/**
 * Janela do EVENTO (ex.: a manutenção vai de [start] a [end]). Exibida formatada no fuso e na região
 * do aparelho. No JSON, ISO-8601 com fuso (`2026-10-18T22:00:00-03:00` ou `…Z`).
 *
 * Não confundir com [Announcement.visibleFrom]/[Announcement.visibleUntil], que dizem QUANDO o aviso
 * aparece. Sem `visibleUntil`, o aviso some sozinho quando a janela termina.
 */
@Serializable
data class AnnouncementWindow(
    @Serializable(with = AnnouncementInstantSerializer::class)
    val start: Instant,
    @Serializable(with = AnnouncementInstantSerializer::class)
    val end: Instant,
)

/**
 * Onde fica o assunto da foto — decide o recorte vertical quando a foto não é quadrada (o diálogo
 * mostra 1:1 recortando, nunca esticando). No fio: `"center"` | `"top"` | `"bottom"`; valor
 * desconhecido cai em [CENTER].
 */
@Serializable(with = AnnouncementImageFocusSerializer::class)
enum class AnnouncementImageFocus(val wireName: String) {
    CENTER("center"),
    TOP("top"),
    BOTTOM("bottom"),
    ;

    companion object {
        fun fromWireName(value: String): AnnouncementImageFocus? = entries.firstOrNull { it.wireName == value }
    }
}

/**
 * Foto opcional do comunicado (2.296.0), exibida no topo do [AnnouncementDialog]. O banner não a usa.
 *
 * @property url URL **https** absoluta (R2/CDN do Nexus). Outro esquema é descartado na leitura
 *   ([decodeAnnouncements]) e ignorado na tela ([Announcement.displayImage]) — cai na faixa colorida.
 * @property alt texto alternativo; `null`/vazio = imagem decorativa (o leitor de tela pula).
 * @property focus recorte vertical ([AnnouncementImageFocus]).
 */
@Serializable
data class AnnouncementImage(
    val url: String,
    val alt: String? = null,
    val focus: AnnouncementImageFocus = AnnouncementImageFocus.CENTER,
)

/**
 * Como o comunicado aparece (2.296.0). No fio: `"modal"` | `"banner"`; ausente ou desconhecido =
 * [MODAL] (decisão do fundador, 12/out/2026).
 *
 * - [MODAL] — diálogo centralizado em retrato, com foto opcional ([AnnouncementDialogHost]).
 * - [BANNER] — o cartão discreto no topo da tela ([AnnouncementBanner]).
 */
@Serializable(with = AnnouncementPresentationSerializer::class)
enum class AnnouncementPresentation(val wireName: String) {
    MODAL("modal"),
    BANNER("banner"),
    ;

    companion object {
        fun fromWireName(value: String): AnnouncementPresentation? = entries.firstOrNull { it.wireName == value }
    }
}

/** `true` se [url] é uma URL https absoluta com host — o único esquema aceito para a foto. */
fun isSecureAnnouncementImageUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) return false
    val host = trimmed.substring(8).substringBefore('/').substringBefore('?').substringBefore('#')
    return host.isNotBlank() && trimmed.none { it.isWhitespace() }
}

/** Botão de ação do aviso: [label] no botão, [url] aberta pelo navegador do sistema (ou link do app). */
@Serializable
data class AnnouncementAction(
    val label: String,
    val url: String,
)

/**
 * Um aviso para mostrar no topo de uma tela — par do `Announcement` da weblib.
 *
 * @property id identidade estável. É por ela que "visto" e "dispensado" são lembrados no aparelho —
 *   mudar o texto mantendo o id NÃO reapresenta o aviso; para reapresentar, use um id novo.
 * @property kind tipo — ícone, cor e prioridade.
 * @property title título curto (obrigatório).
 * @property message corpo opcional.
 * @property window janela do evento (manutenção), exibida no fuso do aparelho.
 * @property visibleFrom antes disso o aviso não aparece (null = já).
 * @property visibleUntil depois disso não aparece (null = sem fim; com [window], até o fim dela).
 * @property action botão opcional.
 * @property frequency ver [AnnouncementFrequency].
 * @property dismissible `false` esconde o X (ex.: manutenção EM ANDAMENTO). No modal, só BLOQUEIA
 *   durante a janela do evento ([isBlockingAt]); antes dela, é fechável por sessão.
 * @property image foto opcional do modal (2.296.0).
 * @property presentation modal (default) ou banner (2.296.0).
 */
@Serializable
data class Announcement(
    val id: String,
    val kind: AnnouncementKind,
    val title: String,
    val message: String? = null,
    val window: AnnouncementWindow? = null,
    @Serializable(with = AnnouncementInstantSerializer::class)
    val visibleFrom: Instant? = null,
    @Serializable(with = AnnouncementInstantSerializer::class)
    val visibleUntil: Instant? = null,
    val action: AnnouncementAction? = null,
    val frequency: AnnouncementFrequency = AnnouncementFrequency.ONCE,
    val dismissible: Boolean = true,
    @Serializable(with = AnnouncementImageFieldSerializer::class)
    val image: AnnouncementImage? = null,
    val presentation: AnnouncementPresentation = AnnouncementPresentation.MODAL,
)

/** A foto que a tela usa: [Announcement.image] só se a URL for https ([isSecureAnnouncementImageUrl]). */
val Announcement.displayImage: AnnouncementImage?
    get() = image?.takeIf { isSecureAnnouncementImageUrl(it.url) }

/**
 * O `Json` dos avisos: tolerante (campo novo do servidor é ignorado; `frequency` desconhecida cai no
 * default), escreve sem os defaults.
 */
val AnnouncementJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = false
}

/**
 * Lê a resposta de `GET /public/announcements` (um array JSON). Um item malformado (sem `id`, data
 * inválida…) é DESCARTADO sozinho — um aviso quebrado no painel não pode apagar os outros. Texto que
 * não é array devolve lista vazia.
 */
fun decodeAnnouncements(json: String): List<Announcement> {
    val root: JsonElement = runCatching { AnnouncementJson.parseToJsonElement(json) }.getOrNull()
        ?: return emptyList()
    val array = root as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        runCatching { AnnouncementJson.decodeFromJsonElement(Announcement.serializer(), element) }
            .getOrNull()
            ?.takeIf { it.id.isNotBlank() && it.title.isNotBlank() }
    }
}

/** Serializa a lista no mesmo formato de [decodeAnnouncements]. */
fun encodeAnnouncements(announcements: List<Announcement>): String =
    AnnouncementJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Announcement.serializer()), announcements)

internal object AnnouncementKindSerializer : KSerializer<AnnouncementKind> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.announcement.AnnouncementKind", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AnnouncementKind) = encoder.encodeString(value.wireName)

    override fun deserialize(decoder: Decoder): AnnouncementKind =
        AnnouncementKind.fromWireName(decoder.decodeString()) ?: AnnouncementKind.INFO
}

internal object AnnouncementImageFocusSerializer : KSerializer<AnnouncementImageFocus> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.announcement.AnnouncementImageFocus", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AnnouncementImageFocus) = encoder.encodeString(value.wireName)

    override fun deserialize(decoder: Decoder): AnnouncementImageFocus =
        AnnouncementImageFocus.fromWireName(decoder.decodeString()) ?: AnnouncementImageFocus.CENTER
}

internal object AnnouncementPresentationSerializer : KSerializer<AnnouncementPresentation> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.announcement.AnnouncementPresentation", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AnnouncementPresentation) = encoder.encodeString(value.wireName)

    override fun deserialize(decoder: Decoder): AnnouncementPresentation =
        AnnouncementPresentation.fromWireName(decoder.decodeString()) ?: AnnouncementPresentation.MODAL
}

/**
 * O campo `image`, tolerante: objeto malformado, sem `url` ou com URL que não é https vira `null` —
 * perde-se SÓ a foto (o modal cai na faixa colorida), nunca o aviso inteiro.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object AnnouncementImageFieldSerializer : KSerializer<AnnouncementImage?> {
    private val delegate = AnnouncementImage.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: AnnouncementImage?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(delegate, value)
    }

    override fun deserialize(decoder: Decoder): AnnouncementImage? {
        val json = decoder as? JsonDecoder
            ?: return decoder.decodeNullableSerializableValue(delegate)?.takeIf { isSecureAnnouncementImageUrl(it.url) }
        val element = json.decodeJsonElement()
        if (element is JsonNull || element !is JsonObject) return null
        val url = (element["url"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim() ?: return null
        if (!isSecureAnnouncementImageUrl(url)) return null
        val alt = (element["alt"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
        val focus = (element["focus"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.let { AnnouncementImageFocus.fromWireName(it) } ?: AnnouncementImageFocus.CENTER
        return AnnouncementImage(url = url, alt = alt, focus = focus)
    }
}

/** ISO-8601 com fuso na leitura (`-03:00` ou `Z`); sai sempre em UTC (`…Z`). */
internal object AnnouncementInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("br.com.codecacto.kmplib.ui.announcement.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
