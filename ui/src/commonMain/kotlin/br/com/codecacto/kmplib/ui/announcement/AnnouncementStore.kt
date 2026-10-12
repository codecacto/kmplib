@file:OptIn(ExperimentalTime::class)

package br.com.codecacto.kmplib.ui.announcement

import br.com.codecacto.kmplib.core.prefs.AppPreferences
import br.com.codecacto.kmplib.core.prefs.appPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Onde o aparelho lembra o que já viu e o que já dispensou, por id de aviso.
 *
 * É memória do APARELHO, não da conta: dispensar a manutenção num celular não a dispensa no outro, e
 * sair da conta não reapresenta o que já foi lido. Produção: [PreferencesAnnouncementStore]; teste e
 * `@Preview`: [InMemoryAnnouncementStore].
 */
interface AnnouncementStore {
    /** O que se lembra de [id] ([AnnouncementRecord.NONE] se nada). */
    suspend fun read(id: String): AnnouncementRecord

    /** Grava a primeira exibição (não sobrescreve uma anterior). */
    suspend fun markSeen(id: String, at: Instant)

    /** Grava a dispensa. */
    suspend fun markDismissed(id: String, at: Instant)

    /** Esquece [id] — o aviso volta a ser inédito. */
    suspend fun forget(id: String)
}

/**
 * [AnnouncementStore] sobre as [AppPreferences] da lib (SharedPreferences no Android, NSUserDefaults
 * no iOS) — o mesmo mecanismo de preferências que o resto da kmplib usa. Duas chaves `Long` (epoch
 * millis) por aviso: `<prefix><id>.seen` e `<prefix><id>.dismissed`.
 *
 * No Android exige `initKmpLibCore(context)` (ou `KmpLib.init`) antes do primeiro uso, como toda
 * preferência da lib.
 */
class PreferencesAnnouncementStore(
    private val preferences: AppPreferences = appPreferences(),
    private val keyPrefix: String = DEFAULT_KEY_PREFIX,
) : AnnouncementStore {

    override suspend fun read(id: String): AnnouncementRecord = AnnouncementRecord(
        seenAt = readInstant(seenKey(id)),
        dismissedAt = readInstant(dismissedKey(id)),
    )

    override suspend fun markSeen(id: String, at: Instant) {
        if (!preferences.has(seenKey(id))) preferences.setLong(seenKey(id), at.toEpochMilliseconds())
    }

    override suspend fun markDismissed(id: String, at: Instant) {
        preferences.setLong(dismissedKey(id), at.toEpochMilliseconds())
    }

    override suspend fun forget(id: String) {
        preferences.remove(seenKey(id))
        preferences.remove(dismissedKey(id))
    }

    private suspend fun readInstant(key: String): Instant? =
        if (preferences.has(key)) Instant.fromEpochMilliseconds(preferences.getLong(key)) else null

    private fun seenKey(id: String) = "$keyPrefix$id.seen"
    private fun dismissedKey(id: String) = "$keyPrefix$id.dismissed"

    companion object {
        const val DEFAULT_KEY_PREFIX: String = "kmplib.announcement."
    }
}

/** [AnnouncementStore] em memória — teste, `@Preview` e modo demonstração. Morre com o processo. */
class InMemoryAnnouncementStore : AnnouncementStore {
    private val mutex = Mutex()
    private val records = HashMap<String, AnnouncementRecord>()

    override suspend fun read(id: String): AnnouncementRecord =
        mutex.withLock { records[id] ?: AnnouncementRecord.NONE }

    override suspend fun markSeen(id: String, at: Instant) = mutex.withLock {
        val current = records[id] ?: AnnouncementRecord.NONE
        if (current.seenAt == null) records[id] = current.copy(seenAt = at)
    }

    override suspend fun markDismissed(id: String, at: Instant) = mutex.withLock {
        records[id] = (records[id] ?: AnnouncementRecord.NONE).copy(dismissedAt = at)
    }

    override suspend fun forget(id: String) = mutex.withLock { records.remove(id); Unit }
}
