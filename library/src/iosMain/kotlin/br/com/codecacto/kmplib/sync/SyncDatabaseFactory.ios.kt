@file:OptIn(ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.sync

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.db.SyncDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Banco de sync no iOS.
 *
 * O arquivo vive em `Library/Application Support/[SYNC_DATABASE_DIRECTORY]/<name>/` — **um diretório
 * por banco** — e não mais no diretório default do SQLiter. Duas razões:
 *
 * 1. **Application Support é o lugar que a Apple define** para arquivo de apoio criado pelo app
 *    (`Documents` é do usuário e aparece no app Arquivos; `Caches` é purgável). É onde o
 *    `core/storage/BlobStore` já grava — a lib para de espalhar estado por diretórios diferentes.
 * 2. **A marcação "fora do backup" precisa de um diretório nosso.** O SQLite em WAL mantém `-wal` e
 *    `-shm` ao lado do `.db`; marcar só o `.db` deixaria as escritas mais recentes indo para o iCloud.
 *    Marcando o **diretório**, os três arquivos ficam cobertos — e como o caminho não depende do flag,
 *    ligar/desligar `excludeFromBackup` numa versão futura do app **não** perde o banco.
 *
 * **Base já instalada:** se existir um banco no local antigo (o default do SQLiter) e o novo ainda não
 * existir, o arquivo é **adotado** (movido, com `-wal`/`-shm`) na primeira abertura. Se o movimento
 * falhar, a lib abre **no local antigo** em vez de começar do zero — perder o espelho e a outbox do
 * usuário seria pior que ficar no diretório errado — e registra no log que a exclusão de backup não
 * pôde ser aplicada.
 */
actual fun createSyncDatabase(name: String, excludeFromBackup: Boolean): SyncDatabase {
    val basePath = prepareSyncDatabaseDirectory(name, excludeFromBackup)
    val driver = NativeSqliteDriver(
        schema = SyncDatabase.Schema,
        name = name,
        onConfiguration = { config ->
            if (basePath == null) {
                config
            } else {
                config.copy(extendedConfig = config.extendedConfig.copy(basePath = basePath))
            }
        },
    )
    return SyncDatabase(driver)
}

private const val TAG = "SyncDatabase"

/** Sufixos que o SQLite mantém ao lado do arquivo principal em modo WAL. */
private val SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")

private val fileManager: NSFileManager get() = NSFileManager.defaultManager

/**
 * Prepara (e devolve) o diretório do banco. `null` = deixa o driver decidir — acontece só quando o
 * sistema não informa o Application Support, caso em que a exclusão de backup **não** é aplicada e
 * isso é registrado como erro (promessa de privacidade que falha calada é pior que erro visível).
 */
private fun prepareSyncDatabaseDirectory(name: String, excludeFromBackup: Boolean): String? {
    val root = searchPath(NSApplicationSupportDirectory)
    if (root == null) {
        if (excludeFromBackup) {
            AppLogger.e(
                TAG,
                "Application Support indisponível: o banco '$name' ficará no diretório default e " +
                    "NÃO foi excluído do backup do iCloud.",
            )
        }
        return null
    }

    val dir = "$root/$SYNC_DATABASE_DIRECTORY/${syncDatabaseDirectoryName(name)}"
    if (!ensureDirectory(dir)) {
        if (excludeFromBackup) {
            AppLogger.e(
                TAG,
                "não foi possível criar '$dir': o banco '$name' ficará no diretório default e NÃO " +
                    "foi excluído do backup do iCloud.",
            )
        }
        return null
    }

    val legacyDir = adoptLegacyDatabase(dir, name)
    if (legacyDir != null) {
        if (excludeFromBackup) {
            AppLogger.e(
                TAG,
                "banco antigo em '$legacyDir' não pôde ser movido; abrindo lá para não perder o " +
                    "dado do usuário. A exclusão do backup do iCloud NÃO está ativa.",
            )
        }
        return legacyDir
    }

    applyBackupExclusion(dir, excludeFromBackup)
    return dir
}

/**
 * Move para [targetDir] um banco que tenha ficado no local antigo.
 *
 * @return `null` quando nada havia a mover **ou** quando o movimento deu certo (ou seja: pode usar
 *   [targetDir]); o diretório legado quando existe dado lá que **não** pôde ser movido.
 */
private fun adoptLegacyDatabase(targetDir: String, name: String): String? {
    if (fileManager.fileExistsAtPath("$targetDir/$name")) return null

    val legacyDir = legacyDirectoryCandidates()
        .firstOrNull { fileManager.fileExistsAtPath("$it/$name") }
        ?: return null

    if (legacyDir == targetDir) return null

    val moved = fileManager.moveItemAtPath("$legacyDir/$name", "$targetDir/$name", null)
    if (!moved) {
        AppLogger.w(TAG, "falha ao mover o banco '$name' de '$legacyDir' para '$targetDir'")
        return legacyDir
    }
    SIDECAR_SUFFIXES.forEach { suffix ->
        val origem = "$legacyDir/$name$suffix"
        if (fileManager.fileExistsAtPath(origem)) {
            // Perder um -wal deixaria as últimas escritas de fora; é falha visível, não silenciosa.
            if (!fileManager.moveItemAtPath(origem, "$targetDir/$name$suffix", null)) {
                AppLogger.w(TAG, "falha ao mover '$name$suffix' de '$legacyDir'")
            }
        }
    }
    AppLogger.i(TAG, "banco '$name' movido de '$legacyDir' para '$targetDir'")
    return null
}

/**
 * Locais onde um banco criado por versões anteriores da lib pode estar: o default do SQLiter mudou de
 * lugar entre versões e a lib nunca o fixou, então conferir alguns caminhos é mais honesto que
 * assumir um só e começar do zero em quem estava no outro.
 */
private fun legacyDirectoryCandidates(): List<String> {
    val roots = listOfNotNull(
        searchPath(NSDocumentDirectory),
        searchPath(NSApplicationSupportDirectory),
        searchPath(NSLibraryDirectory),
        searchPath(NSCachesDirectory),
    )
    return roots.flatMap { listOf(it, "$it/databases") }
}

private fun applyBackupExclusion(directory: String, exclude: Boolean) {
    // Aplica nos dois sentidos: desligar o flag numa versão futura devolve o diretório ao backup.
    val url = NSURL.fileURLWithPath(directory)
    val ok = url.setResourceValue(
        NSNumber.numberWithBool(exclude),
        NSURLIsExcludedFromBackupKey,
        null,
    )
    if (!ok && exclude) {
        AppLogger.e(
            TAG,
            "não foi possível marcar '$directory' como excluído do backup do iCloud.",
        )
    }
}

private fun ensureDirectory(path: String): Boolean {
    if (fileManager.fileExistsAtPath(path)) return true
    return fileManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
}

private fun searchPath(directory: ULong): String? =
    NSSearchPathForDirectoriesInDomains(
        directory = directory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String
