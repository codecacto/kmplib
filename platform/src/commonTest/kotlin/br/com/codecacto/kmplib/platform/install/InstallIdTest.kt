package br.com.codecacto.kmplib.platform.install

import br.com.codecacto.kmplib.core.prefs.InMemoryAppPreferences
import br.com.codecacto.kmplib.core.util.isCanonicalUuid
import br.com.codecacto.kmplib.platform.audience.AppAudience
import br.com.codecacto.kmplib.platform.audience.KmpLibAudience
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallIdTest {

    @AfterTest
    fun limpa() = InstallId.resetForTesting()

    @Test
    fun geraUuidV4EGuarda() = runTest {
        val prefs = InMemoryAppPreferences()
        InstallId.useStorage(prefs)
        InstallId.isAutomation = { false }
        val id = InstallId.get()!!
        assertTrue(isCanonicalUuid(id))
        assertEquals('4', id[14], "versão 4")
        assertEquals(id, prefs.getString(InstallId.KEY))
        assertEquals(id, InstallId.get())
    }

    @Test
    fun releOGuardadoEmOutroProcesso() = runTest {
        val prefs = InMemoryAppPreferences()
        prefs.setString(InstallId.KEY, "0B3C1D2E-4F5A-4B6C-8D7E-9F0A1B2C3D4E")
        InstallId.useStorage(prefs)
        assertEquals("0b3c1d2e-4f5a-4b6c-8d7e-9f0a1b2c3d4e", InstallId.get())
    }

    @Test
    fun valorInvalidoGeraNovo() = runTest {
        val prefs = InMemoryAppPreferences()
        prefs.setString(InstallId.KEY, "lixo")
        InstallId.useStorage(prefs)
        val id = InstallId.get()!!
        assertNotEquals("lixo", id)
        assertTrue(isCanonicalUuid(id))
    }

    @Test
    fun automacaoNaoMandaId() = runTest {
        InstallId.useStorage(InMemoryAppPreferences())
        InstallId.isAutomation = { true }
        assertNull(InstallId.forTelemetry())
        InstallId.isAutomation = { false }
        assertTrue(InstallId.forTelemetry() != null)
    }

    @Test
    fun appInfantilNaoMandaNemCriaId() = runTest {
        val prefs = InMemoryAppPreferences()
        InstallId.useStorage(prefs)
        InstallId.isAutomation = { false }
        InstallId.isKidsAudience = { true }
        assertNull(InstallId.forTelemetry())
        assertEquals("", prefs.getString(InstallId.KEY), "nenhum id é gravado em app infantil")
        InstallId.isKidsAudience = { false }
        assertTrue(InstallId.forTelemetry() != null)
    }

    @Test
    fun appInfantilPeloKmpLibAudience() = runTest {
        InstallId.useStorage(InMemoryAppPreferences())
        InstallId.isAutomation = { false }
        KmpLibAudience.configure(AppAudience.KIDS)
        try {
            assertNull(InstallId.forTelemetry())
        } finally {
            KmpLibAudience.configure(AppAudience.GENERAL)
        }
    }

    @Test
    fun doisAparelhosDoisIds() {
        assertNotEquals(newRandomUuidV4(), newRandomUuidV4())
    }
}
