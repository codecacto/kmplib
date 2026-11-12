package br.com.codecacto.kmplib.bible

import br.com.codecacto.kmplib.core.crypto.sha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Um banco de tradução embarcado no app (`composeResources/files/<fileName>`).
 *
 * @param fileName nome do arquivo (ex.: `"biblia-blivre.db"`) — também o nome da cópia no armazenamento.
 * @param version identificador da versão do arquivo embarcado (ex.: a data do `gerado_em`, ou o
 *   `versionCode` do app). Mudou → copia de novo. `null` = a lib calcula o SHA-256 dos bytes a cada
 *   abertura (custa ler o arquivo inteiro; prefira passar uma versão).
 * @param load lê os bytes do recurso: `{ Res.readBytes("files/biblia-blivre.db") }` — o `Res` é do APP.
 */
class BibleAsset(
    val fileName: String,
    val version: String? = null,
    val load: suspend () -> ByteArray,
)

/**
 * Instala (copia) e abre os bancos embarcados.
 *
 * O SQLite precisa de um ARQUIVO; o recurso do Compose é um item do pacote do app (asset no Android,
 * bundle no iOS). Na 1ª abertura — e sempre que a [BibleAsset.version] mudar — os bytes são copiados
 * para um diretório privado do app, **fora do backup** (é conteúdo reproduzível: o app o traz de novo),
 * com gravação atômica (arquivo temporário + troca), e a versão fica gravada ao lado. Nas outras
 * aberturas, nada é lido do recurso.
 */
object BibleAssets {

    /** Copia [asset] se faltar ou se a versão mudou; devolve o caminho do arquivo pronto. */
    suspend fun install(asset: BibleAsset, directory: String = bibleDefaultDirectory()): String = withContext(Dispatchers.Default) {
        require(asset.fileName.isNotBlank() && '/' !in asset.fileName && ".." !in asset.fileName) { "nome de arquivo inválido" }
        bibleEnsureDirectory(directory)
        val path = "$directory/${asset.fileName}"
        val versionPath = "$path.versao"
        var bytes: ByteArray? = null
        val wanted = asset.version ?: sha256Hex(asset.load().also { bytes = it })
        val installed = bibleReadText(versionPath)?.trim()
        if (installed != wanted || !bibleFileExists(path)) {
            bibleWriteAtomically(path, bytes ?: asset.load())
            bibleWriteAtomically(versionPath, wanted.encodeToByteArray())
        }
        path
    }

    /** [install] + [BibleDatabase.open]. */
    suspend fun open(asset: BibleAsset, directory: String = bibleDefaultDirectory()): BibleDatabase =
        BibleDatabase.open(install(asset, directory))
}

/** Diretório padrão das cópias: privado do app, fora do backup. */
expect fun bibleDefaultDirectory(): String

internal expect fun bibleEnsureDirectory(path: String)

internal expect fun bibleFileExists(path: String): Boolean

internal expect fun bibleReadText(path: String): String?

/** Grava num temporário ao lado e troca pelo definitivo (nunca deixa arquivo pela metade). */
internal expect fun bibleWriteAtomically(path: String, bytes: ByteArray)
