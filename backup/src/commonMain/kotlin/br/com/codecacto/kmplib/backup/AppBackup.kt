@file:OptIn(kotlin.time.ExperimentalTime::class)

package br.com.codecacto.kmplib.backup

import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `manifesto.json` da cópia. Nomes de campo em português: é o contrato do arquivo (fixo, D10 do estudo
 * da família): `app` (slug), `esquema` (versão do banco do app), `gerado_em` (ISO-8601 UTC), `versao_app`.
 */
@Serializable
data class BackupManifest(
    @SerialName("app") val app: String,
    @SerialName("esquema") val schema: Int,
    @SerialName("gerado_em") val generatedAt: String,
    @SerialName("versao_app") val appVersion: String,
    /** Versão do FORMATO do arquivo (não do banco). */
    @SerialName("formato") val format: Int = BackupArchive.FORMAT_VERSION,
) {
    /** O instante de [generatedAt], se legível. */
    val generatedInstant: Instant? get() = runCatching { Instant.parse(generatedAt) }.getOrNull()
}

/** O conteúdo de uma cópia: manifesto, `dados.json` (texto JSON do app) e os anexos (`anexos/<nome>`). */
class BackupContent(
    val manifest: BackupManifest,
    val data: String,
    val attachments: Map<String, ByteArray> = emptyMap(),
)

/** Montagem e leitura do arquivo `.copia` (ZIP). Puro. */
object BackupArchive {
    const val MANIFEST = "manifesto.json"
    const val DATA = "dados.json"
    const val ATTACHMENTS_DIR = "anexos/"
    const val FORMAT_VERSION = 1

    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Os bytes do arquivo. */
    fun write(content: BackupContent): ByteArray {
        val entries = buildList {
            add(MANIFEST to json.encodeToString(BackupManifest.serializer(), content.manifest).encodeToByteArray())
            add(DATA to content.data.encodeToByteArray())
            content.attachments.forEach { (name, bytes) -> add("$ATTACHMENTS_DIR$name" to bytes) }
        }
        return ZipStore.write(entries)
    }

    /** Lê o arquivo; lança [BackupFormatException] se não for uma cópia válida. */
    fun read(bytes: ByteArray): BackupContent {
        val entries = ZipStore.read(bytes)
        val manifestBytes = entries[MANIFEST] ?: throw BackupFormatException("sem manifesto")
        val manifest = runCatching { json.decodeFromString(BackupManifest.serializer(), manifestBytes.decodeToString()) }
            .getOrElse { throw BackupFormatException("manifesto ilegível") }
        if (manifest.format > FORMAT_VERSION) throw BackupFormatException("formato mais novo que esta versão")
        val data = entries[DATA]?.decodeToString() ?: throw BackupFormatException("sem dados")
        val attachments = entries.filterKeys { it.startsWith(ATTACHMENTS_DIR) && it.length > ATTACHMENTS_DIR.length }
            .mapKeys { it.key.removePrefix(ATTACHMENTS_DIR) }
        return BackupContent(manifest, data, attachments)
    }

    /** `<slug>-DD-MM-AAAA.copia` (o nome que a folha "Cópia pronta" mostra e o seletor propõe). */
    fun fileName(appSlug: String, date: LocalDate): String =
        "$appSlug-${date.dayOfMonth.toString().padStart(2, '0')}-${date.monthNumber.toString().padStart(2, '0')}-${date.year}.copia"

    /** Extensão do arquivo. */
    const val EXTENSION = "copia"

    /** MIME do salvar/compartilhar — genérico, para o sistema não trocar a extensão. */
    const val MIME_TYPE = "application/octet-stream"
}

/**
 * O que o APP fornece para a cópia: exportar tudo, migrar esquema antigo e trocar tudo numa transação.
 *
 * - [exportData]: todas as tabelas do usuário como um texto JSON (o formato é do app).
 * - [exportAttachments]: arquivos a levar (`nome` → bytes) — fotos de comprovante, quadro de sermão.
 * - [migrate]: leva `dados.json` do esquema [fromSchema] ao [schemaVersion] atual (só é chamado se for menor).
 * - [replaceAll]: apaga e grava tudo **numa transação** (SQLDelight `transaction {}`): ou fica tudo
 *   novo, ou nada muda. Lançar exceção aqui = restauração falhou, nada foi alterado.
 *
 * O que a regra do app manda NÃO guardar (ex.: marcas da Confissão) simplesmente não entra no export.
 */
interface BackupSource {
    val appSlug: String
    val schemaVersion: Int
    val appVersion: String

    suspend fun exportData(): String
    suspend fun exportAttachments(): Map<String, ByteArray> = emptyMap()
    suspend fun migrate(data: String, fromSchema: Int): String = data
    suspend fun replaceAll(data: String, attachments: Map<String, ByteArray>)
}

/** Uma cópia gerada: o nome proposto e os bytes. */
class BackupFile(val fileName: String, val bytes: ByteArray, val manifest: BackupManifest)

/** Por que a restauração não aconteceu (nada foi alterado em nenhum dos casos). */
enum class BackupRestoreError {
    /** "Este arquivo é de outro aplicativo." */
    WRONG_APP,

    /** Arquivo corrompido, truncado ou não é uma cópia. */
    CORRUPT,

    /** Cópia de versão MAIS NOVA do app (esquema maior): atualize o app. */
    NEWER_SCHEMA,

    /** O app recusou ao gravar (a transação desfez tudo). */
    FAILED,
}

/** Resultado da leitura prévia (antes de confirmar) e da restauração. */
sealed interface BackupReadResult {
    data class Ready(val content: BackupContent) : BackupReadResult
    data class Rejected(val error: BackupRestoreError) : BackupReadResult
}

/** Resultado de [AppBackupManager.restore]. */
sealed interface BackupRestoreResult {
    data class Restored(val manifest: BackupManifest) : BackupRestoreResult
    data class Failed(val error: BackupRestoreError) : BackupRestoreResult
}

/**
 * A cópia de segurança do app: gerar o arquivo e restaurar (com conferência de app e esquema e a troca
 * transacional delegada ao [source]). Combine com `rememberBackupFileActions` para salvar/enviar/escolher.
 */
class AppBackupManager(
    private val source: BackupSource,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    /** Gera a cópia de agora. */
    suspend fun create(): BackupFile {
        val now = clock.now()
        val manifest = BackupManifest(source.appSlug, source.schemaVersion, now.toString(), source.appVersion)
        val bytes = BackupArchive.write(BackupContent(manifest, source.exportData(), source.exportAttachments()))
        return BackupFile(BackupArchive.fileName(source.appSlug, now.toLocalDateTime(timeZone()).date), bytes, manifest)
    }

    /**
     * Lê e confere [bytes] SEM gravar nada — para o diálogo "Restaurar esta cópia?" mostrar a data. A
     * data local da cópia: `manifest.generatedInstant`.
     */
    fun inspect(bytes: ByteArray): BackupReadResult {
        val content = try {
            BackupArchive.read(bytes)
        } catch (_: BackupFormatException) {
            return BackupReadResult.Rejected(BackupRestoreError.CORRUPT)
        }
        if (content.manifest.app != source.appSlug) return BackupReadResult.Rejected(BackupRestoreError.WRONG_APP)
        if (content.manifest.schema > source.schemaVersion) return BackupReadResult.Rejected(BackupRestoreError.NEWER_SCHEMA)
        return BackupReadResult.Ready(content)
    }

    /** Restaura [bytes]: confere, migra o esquema antigo e troca tudo (transação do app). */
    suspend fun restore(bytes: ByteArray): BackupRestoreResult {
        val content = when (val r = inspect(bytes)) {
            is BackupReadResult.Rejected -> return BackupRestoreResult.Failed(r.error)
            is BackupReadResult.Ready -> r.content
        }
        return try {
            val data = if (content.manifest.schema < source.schemaVersion) source.migrate(content.data, content.manifest.schema) else content.data
            source.replaceAll(data, content.attachments)
            BackupRestoreResult.Restored(content.manifest)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            BackupRestoreResult.Failed(BackupRestoreError.FAILED)
        }
    }
}
