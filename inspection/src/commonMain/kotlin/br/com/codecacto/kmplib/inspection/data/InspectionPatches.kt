package br.com.codecacto.kmplib.inspection.data

import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.storage.isValidBlobId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/**
 * Junta duas mudanças do MESMO item na ordem em que a pessoa as fez: o que [newer] diz vence; o que ele
 * limpa sai de [older]; o que ele preenche deixa de ser limpo. Função PURA — é o que mantém a fila de
 * mudanças pequena (uma entrada por item) sem perder a última palavra de ninguém.
 */
fun mergeItemPatches(older: ItemPatch, newer: ItemPatch): ItemPatch {
    require(older.id == newer.id) { "patches de itens diferentes" }
    val limpos = newer.clear.toSet()
    val preenchidos = buildSet {
        if (newer.result != null) add(ItemClearFields.RESULT)
        if (newer.functioning != null) add(ItemClearFields.FUNCTIONING)
        if (newer.quantity != null) add(ItemClearFields.QUANTITY)
        if (newer.observation != null) add(ItemClearFields.OBSERVATION)
        if (newer.voiceTranscript != null) add(ItemClearFields.VOICE_TRANSCRIPT)
    }
    fun <T> campo(nome: String, velho: T?, novo: T?): T? = when {
        novo != null -> novo
        nome in limpos -> null
        else -> velho
    }
    val leituras = when {
        older.readings == null -> newer.readings
        newer.readings == null -> older.readings
        else -> older.readings + newer.readings
    }
    return ItemPatch(
        id = newer.id,
        state = newer.state ?: older.state,
        result = campo(ItemClearFields.RESULT, older.result, newer.result),
        functioning = campo(ItemClearFields.FUNCTIONING, older.functioning, newer.functioning),
        quantity = campo(ItemClearFields.QUANTITY, older.quantity, newer.quantity),
        observation = campo(ItemClearFields.OBSERVATION, older.observation, newer.observation),
        answers = newer.answers ?: older.answers,
        voiceTranscript = campo(ItemClearFields.VOICE_TRANSCRIPT, older.voiceTranscript, newer.voiceTranscript),
        readings = leituras,
        clear = ((older.clear.toSet() - preenchidos) + limpos).sorted(),
    )
}

/** Acrescenta [patch] à lista pendente, juntando com a do mesmo item (a ordem dos itens é a da 1ª mudança). */
fun appendItemPatch(pending: List<ItemPatch>, patch: ItemPatch): List<ItemPatch> {
    val i = pending.indexOfFirst { it.id == patch.id }
    if (i < 0) return pending + patch
    return pending.toMutableList().also { it[i] = mergeItemPatches(it[i], patch) }
}

/** O item como fica com [patch] aplicado — a vista OTIMISTA enquanto a mudança não chega ao servidor. */
fun ItemDto.applyPatch(patch: ItemPatch): ItemDto {
    if (patch.id != id) return this
    val limpos = patch.clear.toSet()
    fun <T> campo(nome: String, atual: T?, novo: T?): T? = novo ?: if (nome in limpos) null else atual
    val leituras = patch.readings?.let { mudancas ->
        val m = readings.toMutableMap()
        mudancas.forEach { (k, v) -> if (v == null) m.remove(k) else m[k] = v }
        m.toMap()
    } ?: readings
    return copy(
        state = patch.state ?: state,
        result = campo(ItemClearFields.RESULT, result, patch.result),
        functioning = campo(ItemClearFields.FUNCTIONING, functioning, patch.functioning),
        quantity = campo(ItemClearFields.QUANTITY, quantity, patch.quantity),
        observation = campo(ItemClearFields.OBSERVATION, observation, patch.observation),
        answers = patch.answers?.let { JsonObject(it.filterValues { v -> v !is kotlinx.serialization.json.JsonNull }) } ?: answers,
        voiceTranscript = campo(ItemClearFields.VOICE_TRANSCRIPT, voiceTranscript, patch.voiceTranscript),
        readings = leituras,
    )
}

/** A vistoria com as mudanças pendentes aplicadas por cima (item a item). */
fun InspectionDto.applyPatches(patches: List<ItemPatch>): InspectionDto {
    if (patches.isEmpty()) return this
    val porItem = patches.associateBy { it.id }
    return copy(groups = groups.map { g -> g.copy(items = g.items.map { item -> porItem[item.id]?.let(item::applyPatch) ?: item }) })
}

/**
 * Onde as mudanças de item ainda não enviadas ficam — **no disco**, para sobreviver ao processo morto
 * (vistoria no subsolo, sem sinal, app fechado pelo sistema). Uma lista por vistoria.
 */
interface InspectionPatchStore {
    suspend fun load(inspectionId: String): List<ItemPatch>

    /** Grava a lista INTEIRA (vazia = apaga). `false` = não gravou (disco cheio): a tela avisa. */
    suspend fun save(inspectionId: String, patches: List<ItemPatch>): Boolean
}

/** [InspectionPatchStore] no [BlobStore] privado do app (fora do backup), um JSON por vistoria. */
class BlobInspectionPatchStore(private val blobs: BlobStore) : InspectionPatchStore {
    private val serializer = ListSerializer(ItemPatch.serializer())

    override suspend fun load(inspectionId: String): List<ItemPatch> {
        val id = blobId(inspectionId) ?: return emptyList()
        val bytes = blobs.read(id) ?: return emptyList()
        return runCatching { InspectionJson.decodeFromString(serializer, bytes.decodeToString()) }.getOrDefault(emptyList())
    }

    override suspend fun save(inspectionId: String, patches: List<ItemPatch>): Boolean {
        val id = blobId(inspectionId) ?: return false
        if (patches.isEmpty()) {
            blobs.delete(id)
            return true
        }
        return blobs.write(id, InspectionJson.encodeToString(serializer, patches).encodeToByteArray())
    }

    private fun blobId(inspectionId: String): String? = "insp-patches-$inspectionId.json".takeIf { isValidBlobId(it) }
}

/** Em memória — teste e produto que não quer durabilidade (as mudanças morrem com o processo). */
class InMemoryInspectionPatchStore : InspectionPatchStore {
    private val mapa = mutableMapOf<String, List<ItemPatch>>()
    override suspend fun load(inspectionId: String): List<ItemPatch> = mapa[inspectionId].orEmpty()
    override suspend fun save(inspectionId: String, patches: List<ItemPatch>): Boolean {
        if (patches.isEmpty()) mapa.remove(inspectionId) else mapa[inspectionId] = patches
        return true
    }
}
