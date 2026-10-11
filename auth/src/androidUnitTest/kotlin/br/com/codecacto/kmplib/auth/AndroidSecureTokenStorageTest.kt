package br.com.codecacto.kmplib.auth

import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * O cofre do Android (2.292.0): gravação DURÁVEL e abertura que não apaga a sessão por falha passageira.
 *
 * O `SharedPreferences` falso separa o que está na MEMÓRIA do processo do que está no DISCO, como o
 * real: `apply()` só põe na memória (o disco vem depois, numa fila que morre com o processo) e
 * `commit()` grava no disco antes de retornar. "Matar o processo" é abrir uma instância nova sobre o
 * mesmo [Disk] — só o que foi ao disco sobrevive. Era exatamente aqui que o `force-stop` logo depois
 * de um refresh rotativo deixava o par velho no cofre.
 */
class AndroidSecureTokenStorageTest {

    /** O arquivo no disco — sobrevive à morte do processo. */
    private class Disk {
        val data = HashMap<String, String>()
        var commitFailuresLeft = 0
    }

    /** Uma "vida" do processo: memória própria, semeada do disco ao abrir. */
    private class FakePrefs(private val disk: Disk) : SharedPreferences {
        private val memory = HashMap(disk.data)

        override fun getString(key: String?, defValue: String?): String? = memory[key] ?: defValue
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun getAll(): MutableMap<String, *> = HashMap(memory)
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String?, defValue: Int) = defValue
        override fun getLong(key: String?, defValue: Long) = defValue
        override fun getFloat(key: String?, defValue: Float) = defValue
        override fun getBoolean(key: String?, defValue: Boolean) = defValue
        override fun contains(key: String?) = memory.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class FakeEditor : SharedPreferences.Editor {
            private val puts = HashMap<String, String?>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor { puts[key] = value; return this }
            override fun remove(key: String): SharedPreferences.Editor { puts[key] = null; return this }
            override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = this
            override fun putLong(key: String?, value: Long) = this
            override fun putFloat(key: String?, value: Float) = this
            override fun putBoolean(key: String?, value: Boolean) = this

            private fun applyTo(map: MutableMap<String, String>) {
                if (clearAll) map.clear()
                puts.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }

            /** Só memória: a ida ao disco ficaria numa fila que o `force-stop` mata. */
            override fun apply() = applyTo(memory)

            override fun commit(): Boolean {
                if (disk.commitFailuresLeft > 0) {
                    disk.commitFailuresLeft--
                    return false
                }
                applyTo(memory)
                applyTo(disk.data)
                return true
            }
        }
    }

    private class Harness(val disk: Disk = Disk()) {
        var opens = 0
        var discards = 0
        var openFailuresLeft = 0

        /** Um processo novo sobre o mesmo disco. */
        fun newProcess() = AndroidSecureTokenStorage(
            serviceName = "kmplib.ownauth",
            opener = {
                opens++
                if (openFailuresLeft > 0) {
                    openFailuresLeft--
                    throw java.security.KeyStoreException("Keystore ocupado (simulado)")
                }
                FakePrefs(disk)
            },
            discarder = { discards++; disk.data.clear() },
            retryBackoffMillis = 0,
        )
    }

    @Test
    fun `o par gravado sobrevive a morte do processo logo depois da gravacao`() = runTest {
        val h = Harness()
        h.newProcess().putString("own_auth_session", "r2")
        // force-stop imediato: nenhuma fila de apply() teria tido tempo de rodar.
        assertEquals("r2", h.newProcess().getString("own_auth_session"))
    }

    @Test
    fun `logout e duravel - a sessao nao ressuscita na proxima abertura`() = runTest {
        val h = Harness()
        h.newProcess().putString("own_auth_session", "r1")
        val vida = h.newProcess()
        vida.remove("own_auth_session")
        assertNull(h.newProcess().getString("own_auth_session"))

        h.newProcess().putString("own_auth_session", "r1")
        h.newProcess().clear()
        assertNull(h.newProcess().getString("own_auth_session"))
    }

    @Test
    fun `commit que falha uma vez e repetido e grava`() = runTest {
        val h = Harness()
        h.disk.commitFailuresLeft = 1
        h.newProcess().putString("own_auth_session", "r2")
        assertEquals("r2", h.newProcess().getString("own_auth_session"))
    }

    @Test
    fun `commit que falha sempre LANCA - o chamador fica sabendo que o disco nao tem o par`() = runTest {
        val h = Harness()
        h.disk.commitFailuresLeft = 99
        assertFailsWith<IllegalStateException> { h.newProcess().putString("own_auth_session", "r2") }
    }

    @Test
    fun `falha passageira do Keystore ao abrir NAO apaga a sessao`() = runTest {
        val h = Harness()
        h.newProcess().putString("own_auth_session", "r1")

        h.openFailuresLeft = AndroidSecureTokenStorage.OPEN_ATTEMPTS - 1
        assertEquals("r1", h.newProcess().getString("own_auth_session"))
        assertEquals(0, h.discards)
    }

    @Test
    fun `cofre que nunca abre e descartado e recriado vazio - sem crash`() = runTest {
        val h = Harness()
        h.newProcess().putString("own_auth_session", "r1")

        h.openFailuresLeft = AndroidSecureTokenStorage.OPEN_ATTEMPTS
        assertNull(h.newProcess().getString("own_auth_session"))
        assertEquals(1, h.discards)
    }

    @Test
    fun `logout que nao grava descarta o cofre - fail-closed`() = runTest {
        val h = Harness()
        h.newProcess().putString("own_auth_session", "r1")

        val vida = h.newProcess()
        h.disk.commitFailuresLeft = 99
        vida.clear()

        assertEquals(1, h.discards)
        h.disk.commitFailuresLeft = 0
        assertNull(h.newProcess().getString("own_auth_session"), "a sessão ressuscitou depois do logout")
    }

    @Test
    fun `o arquivo cifrado e aberto UMA vez por processo`() = runTest {
        val h = Harness()
        val vida = h.newProcess()
        vida.putString("a", "1")
        vida.getString("a")
        vida.remove("a")
        vida.putString("a", "2")
        assertEquals(1, h.opens)
    }
}
