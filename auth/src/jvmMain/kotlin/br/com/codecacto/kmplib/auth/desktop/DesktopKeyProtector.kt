package br.com.codecacto.kmplib.auth.desktop

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.util.Locale

/**
 * Protege a CHAVE do cofre de tokens do desktop (2.353.0) com um segredo que o SO guarda — nunca
 * a chave em claro no disco. O cofre ([desktopSecureTokenStorage]) cifra o conteúdo com AES-256-GCM
 * sob uma chave aleatória; é ESTA chave que passa pelo protetor antes de ir ao arquivo.
 *
 * Implementações da lib: [WindowsDpapiKeyProtector]. Outro SO (Keychain do macOS, Secret Service do
 * Linux) entra implementando esta interface no app — a lib não finge suporte onde não há
 * implementação validada (ver [defaultDesktopKeyProtector]).
 *
 * Contrato: [protect]/[unprotect] lançam em falha; [SecureStorageUnavailableException] = o protetor
 * não existe neste ambiente (o cofre FECHA, nunca grava em claro). Qualquer outra exceção em
 * [unprotect] = o blob não abre aqui (perfil do Windows trocado, arquivo copiado de outro PC).
 */
interface DesktopKeyProtector {
    /** Identificador gravado junto do blob; trocar de protetor invalida o cofre (login de novo). */
    val id: String

    fun protect(secret: ByteArray): ByteArray

    fun unprotect(blob: ByteArray): ByteArray
}

/** O cofre seguro não existe neste ambiente — a operação FECHA (nada é gravado em claro). */
class SecureStorageUnavailableException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** Escopo da DPAPI: quem consegue abrir o blob. */
enum class DpapiScope {
    /**
     * Só o USUÁRIO do Windows que gravou, neste PC (o padrão; app do colaborador). A chave mestra da
     * DPAPI deriva da senha de logon do usuário.
     */
    CURRENT_USER,

    /**
     * Qualquer processo DESTE COMPUTADOR (`CRYPTPROTECT_LOCAL_MACHINE`) — para serviço/totem sem
     * usuário fixo. Proteja a PASTA com ACL (ex.: `%ProgramData%\<app>` só para Administradores e
     * a conta do serviço): o escopo de máquina não separa usuários.
     */
    LOCAL_MACHINE,
}

/**
 * DPAPI do Windows (`CryptProtectData`/`CryptUnprotectData` via `Crypt32Util` do JNA) — a API oficial
 * do Windows para guardar segredo por usuário, a mesma que o Chrome/Edge usam para a chave dos
 * cookies. Sem UI (`CRYPTPROTECT_UI_FORBIDDEN`) e com entropia própria da lib, para outro app do
 * mesmo usuário que chame a DPAPI sem a entropia não abrir o blob.
 */
class WindowsDpapiKeyProtector(
    private val scope: DpapiScope = DpapiScope.CURRENT_USER,
    private val entropy: ByteArray = DEFAULT_ENTROPY,
) : DesktopKeyProtector {

    override val id: String = when (scope) {
        DpapiScope.CURRENT_USER -> "dpapi-user"
        DpapiScope.LOCAL_MACHINE -> "dpapi-machine"
    }

    private val flags: Int = WinCrypt.CRYPTPROTECT_UI_FORBIDDEN or
        (if (scope == DpapiScope.LOCAL_MACHINE) WinCrypt.CRYPTPROTECT_LOCAL_MACHINE else 0)

    override fun protect(secret: ByteArray): ByteArray = guard {
        Crypt32Util.cryptProtectData(secret, entropy, flags, "kmplib", null)
    }

    override fun unprotect(blob: ByteArray): ByteArray = guard {
        Crypt32Util.cryptUnprotectData(blob, entropy, flags, null)
    }

    private inline fun guard(block: () -> ByteArray): ByteArray = try {
        block()
    } catch (e: UnsatisfiedLinkError) {
        throw SecureStorageUnavailableException("DPAPI indisponível (não é Windows?)", e)
    } catch (e: NoClassDefFoundError) {
        throw SecureStorageUnavailableException("JNA ausente do classpath", e)
    }

    companion object {
        /** Entropia default (pública por ser parte do formato; não é segredo — o segredo é o da DPAPI). */
        val DEFAULT_ENTROPY: ByteArray = "br.com.codecacto.kmplib.securestorage.v1".encodeToByteArray()
    }
}

/**
 * O protetor que a lib usa por padrão em cada SO:
 * - **Windows:** [WindowsDpapiKeyProtector] (escopo do usuário);
 * - **macOS / Linux:** `null` — o cofre FECHA ([SecureStorageUnavailableException] ao gravar). Keychain e
 *   Secret Service ficam como lacuna da lib; o app que precisar injeta o seu [DesktopKeyProtector].
 */
fun defaultDesktopKeyProtector(osName: String = System.getProperty("os.name").orEmpty()): DesktopKeyProtector? =
    if (osName.lowercase(Locale.ROOT).startsWith("windows")) WindowsDpapiKeyProtector() else null
