package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.auth.desktop.DesktopKeyProtector
import br.com.codecacto.kmplib.auth.desktop.SecureStorageUnavailableException
import br.com.codecacto.kmplib.auth.desktop.defaultDesktopKeyProtector
import br.com.codecacto.kmplib.core.context.DesktopAppContext
import br.com.codecacto.kmplib.core.storage.deleteFileDurably
import br.com.codecacto.kmplib.core.storage.writeFileDurably
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop (2.353.0): [desktopSecureTokenStorage] com a pasta `secure/` dentro da pasta de dados do app
 * (`%LOCALAPPDATA%\<appId>\secure` no Windows) e o protetor de chave do SO ([defaultDesktopKeyProtector]).
 * Exige `initKmpLibCore(DesktopAppInfo(...))` antes da primeira operação.
 */
actual fun secureTokenStorage(serviceName: String): SecureTokenStorage =
    DesktopSecureTokenStorage(
        serviceName = serviceName,
        directory = { DesktopAppContext.appDataDirectory().resolve(DESKTOP_SECURE_DIRECTORY) },
        keyProtector = defaultDesktopKeyProtector(),
    )

/** Subpasta do cofre dentro da pasta de dados do app. */
const val DESKTOP_SECURE_DIRECTORY: String = "secure"

/**
 * Cofre de tokens do DESKTOP (2.353.0), o par do `EncryptedSharedPreferences` (Android) e do Keychain
 * (iOS). **Nunca grava segredo em claro**:
 *
 * - o conteúdo (mapa chave→valor em JSON) vai num arquivo `secure_<serviço>.vault`, cifrado com
 *   **AES-256-GCM** (IV aleatório de 96 bits a cada gravação; o nome do serviço e a versão do formato
 *   entram como dado autenticado — trocar o arquivo de serviço reprova a tag);
 * - a chave AES é aleatória (`SecureRandom`) e vai num `secure_<serviço>.key` **protegida pelo
 *   [keyProtector]** — no Windows, a DPAPI do usuário: copiar a pasta para outro PC ou outro usuário
 *   não abre nada.
 *
 * Semântica idêntica aos actuals Android/iOS (2.292.0):
 * - **gravação durável ou lança**: arquivo temporário + `fsync`/`FlushFileBuffers` + troca atômica
 *   ([writeFileDurably]); `putString`/`remove`/`clear` só retornam com o disco atualizado;
 * - **leitura best-effort**: falha devolve `null`;
 * - **falha passageira não apaga a sessão**: abrir a chave é tentado [OPEN_ATTEMPTS] vezes; só um cofre
 *   comprovadamente irrecuperável (tag GCM recusada, chave que o SO não abre mais) é descartado — o
 *   que força login de novo, nunca crash;
 * - **falha fechada**: sem protetor ([keyProtector] `null`, ou o protetor responde
 *   [SecureStorageUnavailableException]) nada é gravado — `putString` LANÇA e `getString` devolve `null`.
 *
 * Dois processos do mesmo app (bandeja + janela) coordenam pela trava de arquivo `secure_<serviço>.lock`.
 *
 * @param serviceName namespace (mesmo papel do nome do arquivo no Android / `kSecAttrService` no iOS).
 * @param directory pasta do cofre — padrão: `<pasta de dados do app>/secure`. Totem/serviço com
 *   `DpapiScope.LOCAL_MACHINE` passa uma pasta em `%ProgramData%` protegida por ACL.
 * @param keyProtector protetor da chave; `null` = SO sem cofre suportado (falha fechada).
 */
fun desktopSecureTokenStorage(
    serviceName: String = DEFAULT_SECURE_STORAGE_SERVICE,
    directory: Path? = null,
    keyProtector: DesktopKeyProtector? = defaultDesktopKeyProtector(),
): SecureTokenStorage = DesktopSecureTokenStorage(
    serviceName = serviceName,
    directory = { directory ?: DesktopAppContext.appDataDirectory().resolve(DESKTOP_SECURE_DIRECTORY) },
    keyProtector = keyProtector,
)

internal class DesktopSecureTokenStorage(
    private val serviceName: String,
    private val directory: () -> Path,
    private val keyProtector: DesktopKeyProtector?,
    private val retryBackoffMillis: Long = DEFAULT_RETRY_BACKOFF_MS,
) : SecureTokenStorage {

    private val baseName = "secure_" + serviceName.replace(Regex("[^A-Za-z0-9_]"), "_")
    private val lock = Mutex()
    private val random = SecureRandom()
    private val json = Json
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    private val aad = "kmplib-securestorage|$FORMAT_VERSION|$serviceName".encodeToByteArray()

    /**
     * A chave aberta, junto do arquivo de onde saiu: a DPAPI é chamada uma vez por processo, e outro
     * processo que tenha trocado a chave (cofre descartado e recriado) é percebido pela troca do arquivo.
     */
    @Volatile
    private var cachedKey: Pair<ByteArray, ByteArray>? = null

    private fun dir(): Path = directory()
    private fun vaultFile(): Path = dir().resolve("$baseName.vault")
    private fun keyFile(): Path = dir().resolve("$baseName.key")
    private fun lockFile(): Path = dir().resolve("$baseName.lock")

    // ---- API ------------------------------------------------------------------------------------

    override suspend fun getString(key: String): String? = try {
        io { load()[key] }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG, "Leitura do cofre falhou: ${e::class.simpleName}")
        null
    }

    override suspend fun putString(key: String, value: String) {
        val protector = requireProtector()
        io { save(protector, load() + (key to value)) }
    }

    override suspend fun remove(key: String) {
        io {
            val atual = load()
            if (key !in atual) return@io
            val novo = atual - key
            if (novo.isEmpty()) deleteFileDurably(vaultFile()) else save(requireProtector(), novo)
        }
    }

    override suspend fun clear() {
        io { deleteFileDurably(vaultFile()) }
    }

    // ---- Núcleo ----------------------------------------------------------------------------------

    private fun requireProtector(): DesktopKeyProtector = keyProtector ?: throw SecureStorageUnavailableException(
        "Cofre seguro indisponível neste sistema operacional; o token NÃO foi gravado.",
    )

    /** Roda [block] em IO, sob a trava do processo E a trava de arquivo entre processos. */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            Files.createDirectories(dir())
            FileChannel.open(lockFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { ch ->
                ch.lock().use { block() }
            }
        }
    }

    /** O mapa decifrado; vazio se não há cofre. Cofre irrecuperável é descartado aqui. */
    private suspend fun load(): Map<String, String> {
        val vault = vaultFile()
        if (!Files.isRegularFile(vault)) return emptyMap()
        val protector = keyProtector ?: return emptyMap() // falha fechada: não lê o que não sabe abrir
        if (!Files.isRegularFile(keyFile())) {
            AppLogger.e(TAG, "Cofre sem chave; descartando.")
            discard()
            return emptyMap()
        }
        val chave = try {
            openKey(protector)
        } catch (e: SecureStorageUnavailableException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Chave do cofre irrecuperável (${e::class.simpleName}); descartando.")
            discard()
            return emptyMap()
        }
        return try {
            val texto = decrypt(chave, Files.readAllBytes(vault))
            json.decodeFromString(mapSerializer, texto.decodeToString())
        } catch (e: GeneralSecurityException) {
            AppLogger.e(TAG, "Cofre adulterado ou corrompido (${e::class.simpleName}); descartando.")
            discard()
            emptyMap()
        } catch (e: IllegalArgumentException) {
            AppLogger.e(TAG, "Cofre ilegível (${e::class.simpleName}); descartando.")
            discard()
            emptyMap()
        }
    }

    /** Abre a chave pelo protetor, com nova tentativa para falha passageira do SO. */
    private suspend fun openKey(protector: DesktopKeyProtector): ByteArray {
        val bytes = Files.readAllBytes(keyFile())
        cachedKey?.let { (arquivo, chave) -> if (arquivo.contentEquals(bytes)) return chave }
        val (protectorId, blob) = parseKeyFile(bytes)
        if (protectorId != protector.id) throw GeneralSecurityException("protetor diferente: $protectorId")
        var ultimo: Exception? = null
        for (tentativa in 1..OPEN_ATTEMPTS) {
            try {
                val chave = protector.unprotect(blob)
                if (chave.size != KEY_BYTES) throw GeneralSecurityException("chave de tamanho inválido")
                cachedKey = bytes to chave
                return chave
            } catch (e: SecureStorageUnavailableException) {
                throw e
            } catch (e: GeneralSecurityException) {
                throw e
            } catch (e: Exception) {
                ultimo = e
                AppLogger.w(TAG, "Chave do cofre não abriu (tentativa $tentativa de $OPEN_ATTEMPTS): ${e::class.simpleName}")
                if (tentativa < OPEN_ATTEMPTS) delay(retryBackoffMillis * tentativa)
            }
        }
        throw ultimo ?: IllegalStateException("chave não abriu")
    }

    private suspend fun save(protector: DesktopKeyProtector, conteudo: Map<String, String>) {
        val chave = if (Files.isRegularFile(keyFile())) {
            try {
                openKey(protector)
            } catch (e: SecureStorageUnavailableException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Chave do cofre irrecuperável ao gravar (${e::class.simpleName}); recriando.")
                discard()
                newKey(protector)
            }
        } else {
            newKey(protector)
        }
        val claro = json.encodeToString(mapSerializer, conteudo).encodeToByteArray()
        writeFileDurably(vaultFile(), encrypt(chave, claro))
    }

    /** Gera a chave, protege pelo SO e grava (antes do cofre — cofre sem chave é descartado). */
    private fun newKey(protector: DesktopKeyProtector): ByteArray {
        val chave = ByteArray(KEY_BYTES).also(random::nextBytes)
        val blob = protector.protect(chave)
        val arquivo = buildKeyFile(protector.id, blob)
        writeFileDurably(keyFile(), arquivo)
        cachedKey = arquivo to chave
        return chave
    }

    private fun discard() {
        cachedKey = null
        runCatching { deleteFileDurably(vaultFile()) }
        runCatching { deleteFileDurably(keyFile()) }
    }

    private fun encrypt(chave: ByteArray, claro: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(chave, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        val cifrado = cipher.doFinal(claro)
        return ByteBuffer.allocate(VAULT_MAGIC.size + 1 + IV_BYTES + cifrado.size)
            .put(VAULT_MAGIC).put(FORMAT_VERSION).put(iv).put(cifrado).array()
    }

    private fun decrypt(chave: ByteArray, arquivo: ByteArray): ByteArray {
        val cabecalho = VAULT_MAGIC.size + 1
        if (arquivo.size < cabecalho + IV_BYTES + TAG_BITS / 8 ||
            !arquivo.copyOfRange(0, VAULT_MAGIC.size).contentEquals(VAULT_MAGIC) ||
            arquivo[VAULT_MAGIC.size] != FORMAT_VERSION
        ) {
            throw GeneralSecurityException("formato de cofre desconhecido")
        }
        val iv = arquivo.copyOfRange(cabecalho, cabecalho + IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(chave, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(arquivo, cabecalho + IV_BYTES, arquivo.size - cabecalho - IV_BYTES)
    }

    companion object {
        private const val TAG = "SecureTokenStorage"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        internal const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val FORMAT_VERSION: Byte = 1
        private val VAULT_MAGIC = "KMPV".encodeToByteArray()
        private val KEY_MAGIC = "KMPK".encodeToByteArray()

        /** Tentativas de abrir a chave antes de dar o cofre como irrecuperável (igual ao Android). */
        internal const val OPEN_ATTEMPTS = 3
        internal const val DEFAULT_RETRY_BACKOFF_MS = 150L

        internal fun buildKeyFile(protectorId: String, blob: ByteArray): ByteArray {
            val id = protectorId.encodeToByteArray()
            require(id.size in 1..255) { "id de protetor inválido" }
            return ByteBuffer.allocate(KEY_MAGIC.size + 2 + id.size + blob.size)
                .put(KEY_MAGIC).put(FORMAT_VERSION).put(id.size.toByte()).put(id).put(blob).array()
        }

        internal fun parseKeyFile(bytes: ByteArray): Pair<String, ByteArray> {
            val base = KEY_MAGIC.size + 2
            if (bytes.size < base + 1 || !bytes.copyOfRange(0, KEY_MAGIC.size).contentEquals(KEY_MAGIC) ||
                bytes[KEY_MAGIC.size] != FORMAT_VERSION
            ) {
                throw GeneralSecurityException("formato de chave desconhecido")
            }
            val idLen = bytes[KEY_MAGIC.size + 1].toInt() and 0xFF
            if (idLen == 0 || bytes.size <= base + idLen) throw GeneralSecurityException("chave truncada")
            val id = bytes.copyOfRange(base, base + idLen).decodeToString()
            return id to bytes.copyOfRange(base + idLen, bytes.size)
        }
    }
}
