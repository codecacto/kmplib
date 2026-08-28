package br.com.codecacto.kmplib.core.storage

/**
 * **Armazenamento durável de binários no disco privado do app** — o lugar onde uma foto (ou qualquer
 * anexo) espera enquanto não sobe.
 *
 * Existe porque `ByteArray` em memória **não sobrevive ao processo**: até a 2.103.0 as filas de upload
 * da lib ([RestUploadQueue][br.com.codecacto.kmplib.sync.rest.RestUploadQueue] e
 * [UploadQueue][br.com.codecacto.kmplib.firebase.storage.UploadQueue]) guardavam os bytes num
 * `mutableMapOf`, e o usuário que cadastrava um item com foto **sem sinal** e fechava o app perdia a
 * foto em silêncio — o registro subia depois, sem ela.
 *
 * ### Onde os bytes ficam (padrão-ouro de cada plataforma)
 * - **Android:** `context.filesDir/<diretório>` — armazenamento **interno privado**. Deliberadamente
 *   **NÃO** é o `cacheDir`: o sistema apaga o cache sob pressão de espaço, que é exatamente o cenário
 *   em que a foto sumiria sem ninguém saber.
 * - **iOS:** `Application Support/<diretório>` — **NÃO** é `Caches` (purgável pelo sistema) nem
 *   `Documents` (visível ao usuário no app Arquivos quando o app declara compartilhamento; a fila de
 *   upload é estado interno, não documento do usuário).
 *
 * O conteúdo **não** é excluído do backup de propósito: um binário que só existe na fila é a **única**
 * cópia do usuário; excluí-lo do backup faria a restauração do aparelho perdê-lo.
 *
 * ### Não é um sistema de arquivos
 * A superfície é chave→bytes, sem diretórios, sem streaming e sem caminhos que o app componha. O `id`
 * vira **nome de arquivo**, então só ids seguros são aceitos ([isValidBlobId]) — id inválido é
 * **recusado** (nunca "sanitizado"), porque sanitizar silenciosamente pode fazer dois ids diferentes
 * apontarem para o mesmo arquivo e uma foto sobrescrever a outra.
 *
 * Todas as operações tocam o disco e por isso são `suspend`. Nenhuma lança: falha vira `false`/`null`
 * com log — quem chama decide o que dizer ao usuário.
 */
interface BlobStore {

    /**
     * Grava (ou substitui) o binário de [id].
     *
     * @return `false` se o [id] é inválido, o conteúdo é vazio ou a escrita falhou (disco cheio,
     *   permissão). **Quem chama precisa tratar o `false`:** é o momento em que ainda dá para avisar
     *   "não consegui guardar a foto", em vez de descobrir depois que ela não existe.
     */
    suspend fun write(id: String, bytes: ByteArray): Boolean

    /** Bytes de [id], ou `null` se não existe/ilegível. */
    suspend fun read(id: String): ByteArray?

    /** `true` se há binário gravado sob [id]. */
    suspend fun exists(id: String): Boolean

    /** Tamanho em bytes de [id] (`0` se não existe). */
    suspend fun sizeOf(id: String): Long

    /** Apaga [id]. `true` se havia algo para apagar. Idempotente. */
    suspend fun delete(id: String): Boolean

    /** Ids atualmente gravados. Base da varredura de órfãos do dono da fila. */
    suspend fun ids(): List<String>

    /** Espaço total ocupado — para a tela de diagnóstico ("12 MB aguardando envio"). */
    suspend fun totalBytes(): Long = ids().sumOf { sizeOf(it) }
}

/**
 * Cria o [BlobStore] da plataforma sobre [directoryName] (subpasta do diretório privado do app).
 *
 * **Android:** exige `KmpLib.init(context)` (ou `KmpLib.initSync(context)`) no `Application.onCreate()`
 * — o mesmo pré-requisito do banco de sync. Sem isso, falha alto no bootstrap, nunca em silêncio na
 * hora de guardar a foto do usuário.
 */
expect fun createBlobStore(directoryName: String = DEFAULT_BLOB_DIRECTORY): BlobStore

/** Diretório default dos binários da lib. */
const val DEFAULT_BLOB_DIRECTORY: String = "kmplib_blobs"

/** Tamanho máximo de um id de blob (nome de arquivo) — folga confortável em qualquer FS. */
const val MAX_BLOB_ID_LENGTH: Int = 120

/**
 * `true` se [id] pode virar nome de arquivo com segurança: só `A-Z a-z 0-9 . _ -`, não vazio, no
 * máximo [MAX_BLOB_ID_LENGTH] caracteres e **nunca** `.`/`..` (que escapariam do diretório).
 *
 * Recusar em vez de sanitizar é decisão de segurança **e** de integridade: `foto/1` e `foto:1`
 * sanitizados viram o mesmo nome, e a segunda foto sobrescreveria a primeira sem erro nenhum.
 */
fun isValidBlobId(id: String): Boolean {
    if (id.isEmpty() || id.length > MAX_BLOB_ID_LENGTH) return false
    if (id == "." || id == "..") return false
    return id.all { it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '_' || it == '-' }
}

/**
 * [BlobStore] em memória — para `commonTest` e para quem quer a fila **sem** durabilidade
 * (deliberadamente: o que ele guarda morre com o processo, que é justamente o defeito que o
 * [BlobStore] existe para corrigir).
 */
class InMemoryBlobStore : BlobStore {
    private val blobs = LinkedHashMap<String, ByteArray>()

    override suspend fun write(id: String, bytes: ByteArray): Boolean {
        if (!isValidBlobId(id) || bytes.isEmpty()) return false
        blobs[id] = bytes.copyOf()
        return true
    }

    override suspend fun read(id: String): ByteArray? = blobs[id]?.copyOf()

    override suspend fun exists(id: String): Boolean = blobs.containsKey(id)

    override suspend fun sizeOf(id: String): Long = blobs[id]?.size?.toLong() ?: 0L

    override suspend fun delete(id: String): Boolean = blobs.remove(id) != null

    override suspend fun ids(): List<String> = blobs.keys.toList()
}
