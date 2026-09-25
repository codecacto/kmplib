@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, UnsafeNumber::class)

package br.com.codecacto.kmplib.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSLock
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

actual fun secureTokenStorage(serviceName: String): SecureTokenStorage =
    IosSecureTokenStorage(serviceName)

/**
 * Cofre seguro no iOS via **Keychain Services** (`kSecClassGenericPassword`) — o cofre nativo do SO,
 * ideal para o `refreshToken` de longa duração. Acessibilidade **`AfterFirstUnlockThisDeviceOnly`**
 * (2.214.0): disponível em background depois do primeiro desbloqueio, para o refresh proativo
 * funcionar, e **preso a este aparelho** — o item não viaja no backup nem é restaurado em outro
 * iPhone. Até a 2.213.0 era `AfterFirstUnlock`, que acompanha o backup cifrado: restaurar num
 * aparelho novo levava a sessão junto. Itens gravados com o atributo antigo são migrados no lugar
 * (ver [migrarAcessibilidade]). Nunca lança à UI.
 *
 * A montagem do `CFDictionaryRef` segue o padrão consagrado do `KeychainSettings`
 * (multiplatform-settings): `allocArrayOf` + `CFDictionaryCreate` com os callbacks de tipo CF.
 */
internal class IosSecureTokenStorage(
    private val serviceName: String,
) : SecureTokenStorage {

    private val trava = NSLock()
    private var instalacaoConferida = false
    private var acessibilidadeMigrada = false

    /**
     * Descarta a sessão que sobreviveu à DESINSTALAÇÃO do app.
     *
     * O iOS apaga o `UserDefaults` quando o app é removido, mas **não** apaga o Keychain. Sem esta
     * conferência, quem desinstala e instala de novo abre o app com o `refreshToken` antigo: pula
     * onboarding e login e cai direto numa tela de dentro (no Cidade Conectada, o cadastro do
     * bairro). A marca vive no `UserDefaults` — se ela não está lá, esta é a primeira abertura desta
     * instalação, e o que houver no Keychain sob este `serviceName` é de uma instalação anterior.
     *
     * Roda antes da PRIMEIRA operação do cofre, sob trava: nenhuma leitura concorrente enxerga o
     * token antigo enquanto ele é apagado, e nada depende de o `AppDelegate` ter rodado antes do
     * Kotlin. Só apaga os itens deste `serviceName`, nunca o Keychain inteiro do app.
     *
     * ⚠️ Na versão do app que traz isto pela primeira vez a marca ainda não existe, então **quem só
     * ATUALIZA também é deslogado uma vez**. É o preço de não confundir reinstalação com
     * atualização: o `UserDefaults` não serve de prova, porque SDKs gravam nele antes da primeira
     * leitura da sessão.
     *
     * Na mesma trava roda a [migrarAcessibilidade], para nenhuma leitura acontecer antes dela.
     */
    private fun prepararCofre() {
        trava.lock()
        try {
            if (!instalacaoConferida) {
                val defaults = NSUserDefaults.standardUserDefaults
                val marca = "br.com.codecacto.kmplib.auth.instalacao.$serviceName"
                if (!defaults.boolForKey(marca)) {
                    apagarItensDoServico()
                    defaults.setBool(true, marca)
                }
                instalacaoConferida = true
            }
            if (!acessibilidadeMigrada) {
                acessibilidadeMigrada = migrarAcessibilidade()
            }
        } finally {
            trava.unlock()
        }
    }

    /**
     * Leva para `AfterFirstUnlockThisDeviceOnly` os itens deste `serviceName` gravados até a
     * 2.213.0 com `AfterFirstUnlock` — **sem deslogar ninguém**: `SecItemUpdate` altera o atributo
     * do item no lugar (é a forma documentada pela Apple de mudar a acessibilidade), e o token
     * continua legível o tempo todo. Em item que já está no atributo novo, é no-op.
     *
     * Devolve `true` quando terminou (`errSecSuccess`, ou `errSecItemNotFound` = não havia item).
     * Qualquer outro status — tipicamente `errSecInteractionNotAllowed`, o app acordado em
     * background antes do primeiro desbloqueio — devolve `false`, e a próxima operação do cofre
     * tenta de novo. Nada é apagado por aqui.
     */
    private fun migrarAcessibilidade(): Boolean = memScoped {
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName.toCFString(),
        )
        val novosAtributos = cfDictionaryOf(
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        val status = SecItemUpdate(query, novosAtributos)
        status == errSecSuccess || status == errSecItemNotFound
    }

    override suspend fun getString(key: String): String? {
        prepararCofre()
        return lerDoKeychain(key)
    }

    private fun lerDoKeychain(key: String): String? = memScoped {
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName.toCFString(),
            kSecAttrAccount to key.toCFString(),
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data, NSUTF8StringEncoding) as String?
    }

    override suspend fun putString(key: String, value: String) {
        prepararCofre()
        remove(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        memScoped {
            val attributes = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceName.toCFString(),
                kSecAttrAccount to key.toCFString(),
                kSecValueData to CFBridgingRetain(data),
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )
            SecItemAdd(attributes, null)
        }
    }

    override suspend fun remove(key: String) {
        prepararCofre()
        memScoped {
            val query = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceName.toCFString(),
                kSecAttrAccount to key.toCFString(),
            )
            val status = SecItemDelete(query)
            check(status == errSecSuccess || status == errSecItemNotFound)
        }
    }

    override suspend fun clear() {
        prepararCofre()
        apagarItensDoServico()
    }

    private fun apagarItensDoServico() {
        memScoped {
            val query = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceName.toCFString(),
            )
            SecItemDelete(query)
        }
    }

    private fun String.toCFString(): CFTypeRef? = CFBridgingRetain(this as NSString)

    private fun MemScope.cfDictionaryOf(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val keys = allocArrayOf(pairs.map { it.first })
        val values = allocArrayOf(pairs.map { it.second })
        return CFDictionaryCreate(
            allocator = null,
            keys = keys.reinterpret(),
            values = values.reinterpret(),
            numValues = pairs.size.convert(),
            keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
            valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
        )
    }
}
