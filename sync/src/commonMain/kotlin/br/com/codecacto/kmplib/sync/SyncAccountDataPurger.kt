package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.core.storage.AccountLocalDataPurger
import br.com.codecacto.kmplib.core.storage.LocalPendingChanges
import br.com.codecacto.kmplib.core.storage.LocalPurgeReport
import br.com.codecacto.kmplib.core.storage.SignOutPendingPolicy
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.getShareHandler
import br.com.codecacto.kmplib.sync.rest.RestCrudSyncEngine
import br.com.codecacto.kmplib.sync.rest.RestUploadOutbox
import kotlin.coroutines.cancellation.CancellationException

/** O que uma limpeza local cobre — informado ao [SyncAccountDataPurger.extraCleanup]. */
enum class LocalPurgeScope {
    /** Exclusão de conta, ou logout descartando pendências: sai tudo da conta. */
    Everything,

    /** Logout mantendo pendências: sai só o que já está no servidor. */
    SyncedOnly,
}

/**
 * Implementação padrão do [AccountLocalDataPurger] sobre o espelho do sync (2.217.0).
 *
 * ```kotlin
 * // Koin
 * single<AccountLocalDataPurger> {
 *     SyncAccountDataPurger(
 *         store = get(),
 *         uploadOutboxes = listOf(get<RestUploadOutbox>()),
 *         engine = get<RestCrudSyncEngine>(),
 *     )
 * }
 * single {
 *     AccountDeletionService(api = get(), auth = get(), credencialSaiNoWipe = true, localData = get())
 * }
 *
 * // Logout
 * val pendente = purger.pendingChanges()
 * if (!pendente.isEmpty) { /* perguntar: esperar enviar, manter ou descartar */ }
 * auth.signOut()
 * purger.purgeOnSignOut(SignOutPendingPolicy.Keep)
 * engine.setAccountScope(null)
 * ```
 *
 * **Chame com o escopo da conta que está saindo ainda ativo** (antes de `setAccountScope(null)` /
 * do login seguinte) — a limpeza vale para a conta corrente do [store].
 *
 * **No logout, encerre a sessão ANTES de limpar** (`auth.signOut()` → `purgeOnSignOut` →
 * `engine.setAccountScope(null)`): com o token ainda válido, um ciclo disparado pela volta da rede
 * repovoaria o espelho logo depois da limpeza. Informe o [engine] para a limpeza esperar o ciclo em
 * curso e segurar o próximo; a fila de upload já se protege sozinha (espera a drenagem).
 *
 * @param store o espelho — o MESMO dos repositórios.
 * @param uploadOutboxes as filas de upload do app. Os binários delas só saem por aqui: apagar só as
 *   linhas deixaria as fotos no disco (a varredura de órfãos recolheria depois, mas "depois" não é
 *   exclusão).
 * @param clearSharedFiles apaga as cópias que o `ShareHandler` materializou para compartilhar
 *   (PDF/imagem exportados), em qualquer idade. Vale nos dois modos: não são pendência de ninguém.
 * @param engine o motor REST do app, se houver: a limpeza roda dentro de
 *   [RestCrudSyncEngine.runExclusive].
 * @param extraCleanup o que o app guarda FORA do espelho (DataStore da conta, outro `BlobStore`,
 *   arquivo próprio). Roda por último. Exceção aqui conta como falha, sem abortar o resto.
 */
class SyncAccountDataPurger(
    private val store: SyncStore,
    private val uploadOutboxes: List<RestUploadOutbox> = emptyList(),
    private val clearSharedFiles: Boolean = true,
    private val engine: RestCrudSyncEngine? = null,
    private val extraCleanup: suspend (LocalPurgeScope) -> Unit = {},
) : AccountLocalDataPurger {

    override suspend fun pendingChanges(): LocalPendingChanges {
        val uploads = uploadOutboxes.sumOf { store.getDirty(it.entity).size }
        val registros = (store.countDirty().toInt() - uploads).coerceAtLeast(0)
        return LocalPendingChanges(records = registros, uploads = uploads)
    }

    override suspend fun purgeAccount(): LocalPurgeReport = exclusivo { purgeEverything() }

    override suspend fun purgeOnSignOut(pending: SignOutPendingPolicy): LocalPurgeReport =
        exclusivo {
            when (pending) {
                SignOutPendingPolicy.Discard -> purgeEverything()
                SignOutPendingPolicy.Keep -> purgeSyncedOnly()
            }
        }

    private suspend fun exclusivo(block: suspend () -> LocalPurgeReport): LocalPurgeReport =
        engine?.runExclusive(block) ?: block()

    private suspend fun purgeEverything(): LocalPurgeReport {
        var falhas = 0
        val pendentes = etapa("contar pendências") { pendingChanges().total }
            ?: run { falhas++; 0 }

        uploadOutboxes.forEach { outbox ->
            if (etapa("fila de upload") { outbox.purgeCurrentAccount() } == null) falhas++
        }
        val conta = store.accountScope.value
        if (etapa("espelho da conta") { store.deleteAccountData(conta) } == null) falhas++
        // Binário que ficou sem linha (payload antigo, processo morto no meio): a varredura considera
        // todas as contas, então só apaga o que ninguém referencia.
        uploadOutboxes.forEach { outbox ->
            if (etapa("órfãos da fila") { outbox.sweepOrphanBlobs() } == null) falhas++
        }

        val compartilhados = compartilhados().also { if (it == null) falhas++ } ?: 0
        if (etapa("limpeza do app") { extraCleanup(LocalPurgeScope.Everything) } == null) falhas++

        return LocalPurgeReport(
            discardedPending = pendentes,
            removedSyncedRows = -1,
            sharedFilesDeleted = compartilhados,
            failures = falhas,
        )
    }

    private suspend fun purgeSyncedOnly(): LocalPurgeReport {
        var falhas = 0
        val pendentes = etapa("contar pendências") { pendingChanges().total }
            ?: run { falhas++; 0 }

        // Donos de foto pendente ficam: é por eles que a foto acha o id do servidor.
        val donos = uploadOutboxes.flatMap { it.pendingOwnerHandles() }.toSet()
        val removidas = etapa("espelho sincronizado") {
            store.deleteSyncedRows { linha ->
                donos.any { (entidade, handle) ->
                    linha.entity == entidade &&
                        (handle == linha.local_id || handle == linha.server_id || handle == linha.client_id)
                }
            }
        } ?: run { falhas++; 0 }

        val compartilhados = compartilhados().also { if (it == null) falhas++ } ?: 0
        if (etapa("limpeza do app") { extraCleanup(LocalPurgeScope.SyncedOnly) } == null) falhas++

        return LocalPurgeReport(
            keptPending = pendentes,
            removedSyncedRows = removidas,
            sharedFilesDeleted = compartilhados,
            failures = falhas,
        )
    }

    private suspend fun compartilhados(): Int? =
        if (!clearSharedFiles) 0 else etapa("arquivos compartilhados") { getShareHandler().clearSharedFiles(0L) }

    /** Roda uma etapa sem deixar a falha dela impedir as seguintes. `null` = falhou (já logado). */
    private suspend fun <T> etapa(nome: String, bloco: suspend () -> T): T? =
        try {
            bloco()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Sem PII: nem id de conta, nem caminho — só a etapa.
            AppLogger.e(TAG, "limpeza local: falhou a etapa '$nome'", e)
            null
        }

    private companion object {
        const val TAG = "AccountLocalData"
    }
}
