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
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
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
 * ideal para o `refreshToken` de longa duração. Acessibilidade `AfterFirstUnlock` (disponível em
 * background depois do primeiro desbloqueio, para o refresh proativo funcionar). Nunca lança à UI.
 *
 * A montagem do `CFDictionaryRef` segue o padrão consagrado do `KeychainSettings`
 * (multiplatform-settings): `allocArrayOf` + `CFDictionaryCreate` com os callbacks de tipo CF.
 */
internal class IosSecureTokenStorage(
    private val serviceName: String,
) : SecureTokenStorage {

    override suspend fun getString(key: String): String? = memScoped {
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
        remove(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        memScoped {
            val attributes = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceName.toCFString(),
                kSecAttrAccount to key.toCFString(),
                kSecValueData to CFBridgingRetain(data),
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
            )
            SecItemAdd(attributes, null)
        }
    }

    override suspend fun remove(key: String) {
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
