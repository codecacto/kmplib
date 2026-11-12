package br.com.codecacto.kmplib.platform

import android.content.Context
import br.com.codecacto.kmplib.core.util.AppLogger
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Prova de origem do toque no corpo da notificação local (2.357.1) — o que separa o `Intent` que a
 * PRÓPRIA lib montou no `PendingIntent` da notificação de um `Intent` forjado por outro app.
 *
 * **Por que é preciso.** A marca do toque chega na `Activity` de abertura, que é `exported` (é o
 * launcher): qualquer app instalado pode chamá-la com os mesmos extras. Sem prova, ele forjava um
 * [NotificationTap] com o `data` que quisesse, e o app navegava para onde o `data` mandasse.
 *
 * **Por que HMAC e não uma `Activity` trampolim `exported="false"`.** O trampolim também prova a
 * origem, mas põe uma `Activity` a mais no manifesto de todo app (tema transparente, `taskAffinity`,
 * um quadro piscando entre a notificação e a tela) e muda a pilha de tarefas que os apps já
 * conhecem; a recomendação do Android é o `PendingIntent` da notificação abrir direto a `Activity`
 * de destino. O HMAC mantém o caminho de sempre e prova a mesma coisa: só o processo do app conhece
 * a chave, e o conteúdo de um `PendingIntent` não é legível por outro app (nem por quem escuta
 * notificações).
 *
 * **Como.** HMAC-SHA256 sobre uma codificação canônica de `id` + `data` (chaves em ordem, cada campo
 * com o comprimento antes), com chave aleatória de 256 bits por instalação, criada no primeiro uso
 * com [SecureRandom] e guardada em `SharedPreferences` privadas (`MODE_PRIVATE`, `commit()` — a
 * chave tem de estar no disco antes de a notificação que a usa ser publicada). Conferência em tempo
 * constante ([MessageDigest.isEqual]).
 *
 * **Notificação já na bandeja antes do update** não tem a prova: o toque abre o app como sempre, mas
 * não vira [NotificationTap] (descartado, só um aviso no log). Aceitável: o que ainda não disparou é
 * montado de novo na hora de exibir (com a prova), e o lembrete rolante é reagendado a cada abertura.
 */
internal object NotificationTapProof {

    private const val TAG = "NotificationTap"
    private const val PREFS = "kmplib_notification_tap"
    private const val PREF_KEY = "hmac_key_v1"
    private const val ALGORITHM = "HmacSHA256"
    private const val KEY_BYTES = 32
    private const val FORMAT_VERSION = 1

    @Volatile
    private var cachedKey: ByteArray? = null

    /** A chave desta instalação (criada no primeiro uso); `null` se não deu para ler nem criar. */
    fun key(context: Context): ByteArray? {
        cachedKey?.let { return it }
        return synchronized(this) {
            cachedKey ?: runCatching { loadOrCreate(context.applicationContext) }
                .onFailure { AppLogger.w(TAG, "chave do toque indisponível: ${it.javaClass.simpleName}") }
                .getOrNull()
                ?.also { cachedKey = it }
        }
    }

    private fun loadOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(PREF_KEY, null)?.let(::fromHex)?.takeIf { it.size == KEY_BYTES }?.let { return it }
        val fresh = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        check(prefs.edit().putString(PREF_KEY, toHex(fresh)).commit()) { "falha ao gravar a chave" }
        return fresh
    }

    /** A prova de ([id], [data]) com [key]. */
    fun sign(key: ByteArray, id: Int, data: Map<String, String>): ByteArray =
        Mac.getInstance(ALGORITHM).run {
            init(SecretKeySpec(key, ALGORITHM))
            doFinal(canonical(id, data))
        }

    /** `true` só se [proof] é a prova de ([id], [data]) com [key] — comparação em tempo constante. */
    fun verify(key: ByteArray, id: Int, data: Map<String, String>, proof: ByteArray?): Boolean {
        if (proof == null || proof.isEmpty()) return false
        return MessageDigest.isEqual(sign(key, id, data), proof)
    }

    /** Codificação sem ambiguidade: versão, id, nº de pares e cada chave/valor com o comprimento antes. */
    internal fun canonical(id: Int, data: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { s ->
            s.writeInt(FORMAT_VERSION)
            s.writeInt(id)
            s.writeInt(data.size)
            data.entries.sortedBy { it.key }.forEach { (k, v) ->
                val kb = k.encodeToByteArray()
                val vb = v.encodeToByteArray()
                s.writeInt(kb.size); s.write(kb)
                s.writeInt(vb.size); s.write(vb)
            }
        }
        return out.toByteArray()
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun fromHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return runCatching { ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }

    /** Só para teste: troca a chave em memória. */
    internal fun setKeyForTest(key: ByteArray?) {
        cachedKey = key
    }
}
