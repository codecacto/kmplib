package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.core.storage.AccountLocalDataPurger
import br.com.codecacto.kmplib.core.storage.LocalPendingChanges
import br.com.codecacto.kmplib.core.storage.LocalPurgeReport
import br.com.codecacto.kmplib.core.storage.SignOutPendingPolicy
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.clearCameraCaptureFiles
import br.com.codecacto.kmplib.platform.getShareHandler
import br.com.codecacto.kmplib.ui.components.clearPrivatePhotoMemoryCache
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
 * Implementação padrão do [AccountLocalDataPurger] sobre o espelho do sync (2.217.0; conta nomeada
 * e pausa do motor desde a 2.218.0).
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
 * // Logout — a conta é capturada ANTES de encerrar a sessão
 * val conta = purger.activeAccountId() ?: return
 * val pendente = purger.pendingChanges()
 * if (!pendente.isEmpty) { /* perguntar: esperar enviar, manter ou descartar */ }
 * purger.withSyncPaused {
 *     auth.signOut()
 *     purger.purgeOnSignOut(conta, SignOutPendingPolicy.Keep)
 *     engine.setAccountScope(null)
 * }
 * ```
 *
 * **Ordem recomendada — vale também para o app que liga o escopo à SESSÃO** (um ouvinte de
 * `currentUser` que chama `setAccountScope(null)` no logout): capture [activeAccountId] **antes** do
 * `signOut`, e passe esse id. A limpeza apaga a conta NOMEADA, então não importa se o ouvinte já
 * trocou o escopo. Com [SignOutPendingPolicy.Keep] a conta ainda precisa ser a corrente (a triagem
 * do que é pendência lê o bucket corrente): segure o motor com [withSyncPaused] e troque o escopo
 * **depois** da limpeza — ou use `Discard`, que não depende disso. Se o escopo já mudou, o `Keep`
 * **recusa** (nada apagado, `failures = 1`), em vez de limpar a conta errada e dizer que deu certo.
 *
 * Encerre a sessão **antes** de limpar: com o token ainda válido, um ciclo disparado pela volta da
 * rede repovoaria o espelho logo depois da limpeza. [withSyncPaused] segura o motor e as filas de
 * upload pelo bloco inteiro.
 *
 * @param store o espelho — o MESMO dos repositórios.
 * @param uploadOutboxes as filas de upload do app. Os binários delas só saem por aqui: apagar só as
 *   linhas deixaria as fotos no disco (a varredura de órfãos recolheria depois, mas "depois" não é
 *   exclusão). As linhas carregam o `payload_json` com os `formFields` do multipart — saem junto.
 * @param clearSharedFiles apaga as cópias que o `ShareHandler` materializou para compartilhar
 *   (PDF/imagem exportados), em qualquer idade. Vale nos dois modos: não são pendência de ninguém.
 *   Com ele vão também os originais de câmera esquecidos e o cache de memória das fotos privadas.
 * @param engine o motor REST do app, se houver: a limpeza roda dentro de
 *   [RestCrudSyncEngine.runExclusive], e [withSyncPaused] o segura.
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

    override fun activeAccountId(): String? =
        store.accountScope.value.takeUnless { it.isBlank() || it == SyncStore.NO_ACCOUNT }

    override suspend fun pendingChanges(): LocalPendingChanges {
        val uploads = uploadOutboxes.sumOf { store.getDirty(it.entity).size }
        val registros = (store.countDirty().toInt() - uploads).coerceAtLeast(0)
        return LocalPendingChanges(records = registros, uploads = uploads)
    }

    override suspend fun purgeAccount(accountId: String): LocalPurgeReport {
        if (!contaValida(accountId)) return recusada()
        return withSyncPaused { purgeEverything(accountId) }
    }

    override suspend fun purgeOnSignOut(accountId: String, pending: SignOutPendingPolicy): LocalPurgeReport {
        if (!contaValida(accountId)) return recusada()
        return withSyncPaused {
            when (pending) {
                SignOutPendingPolicy.Discard -> purgeEverything(accountId)
                SignOutPendingPolicy.Keep ->
                    if (store.accountScope.value != accountId) {
                        // A triagem de pendência lê o bucket corrente: com outro titular, "apagar o
                        // sincronizado" apagaria o espelho de OUTRA conta.
                        AppLogger.e(TAG, "limpeza de logout recusada: o escopo do espelho já é outra conta")
                        recusada()
                    } else {
                        purgeSyncedOnly()
                    }
            }
        }
    }

    /**
     * Segura o motor ([RestCrudSyncEngine.runExclusive]) **e** a drenagem de cada fila de upload
     * ([RestUploadOutbox.withDrainPaused] — o `drainNow()` da tela não passa pelo motor). Reentrante:
     * as limpezas chamadas por dentro não esperam a si mesmas.
     */
    override suspend fun <T> withSyncPaused(block: suspend () -> T): T {
        val segurarFilas: suspend () -> T = uploadOutboxes.foldRight(block) { fila, interno ->
            { fila.withDrainPaused(interno) }
        }
        return engine?.runExclusive(segurarFilas) ?: segurarFilas()
    }

    private fun contaValida(accountId: String): Boolean =
        accountId.isNotBlank() && accountId != SyncStore.NO_ACCOUNT

    private fun recusada(): LocalPurgeReport {
        AppLogger.e(TAG, "limpeza local recusada: conta em branco ou fora do escopo; nada foi apagado")
        return LocalPurgeReport(failures = 1)
    }

    private suspend fun purgeEverything(accountId: String): LocalPurgeReport {
        var falhas = 0
        // Contagem só é possível no bucket corrente; de outra conta, fica 0 (informativo).
        val pendentes = if (store.accountScope.value == accountId) {
            etapa("contar pendências") { pendingChanges().total } ?: run { falhas++; 0 }
        } else {
            0
        }

        uploadOutboxes.forEach { outbox ->
            if (etapa("fila de upload") { outbox.purgeAccount(accountId) } == null) falhas++
        }
        if (etapa("espelho da conta") { store.deleteAccountData(accountId) } == null) falhas++
        // Binário que ficou sem linha (payload antigo, processo morto no meio): a varredura considera
        // todas as contas, então só apaga o que ninguém referencia.
        uploadOutboxes.forEach { outbox ->
            if (etapa("órfãos da fila") { outbox.sweepOrphanBlobs() } == null) falhas++
        }

        val compartilhados = arquivosLocais().also { if (it == null) falhas++ } ?: 0
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

        val compartilhados = arquivosLocais().also { if (it == null) falhas++ } ?: 0
        if (etapa("limpeza do app") { extraCleanup(LocalPurgeScope.SyncedOnly) } == null) falhas++

        return LocalPurgeReport(
            keptPending = pendentes,
            removedSyncedRows = removidas,
            sharedFilesDeleted = compartilhados,
            failures = falhas,
        )
    }

    /**
     * Cópias de compartilhamento, originais de câmera e o cache de memória das fotos privadas. Devolve
     * quantas cópias de compartilhamento saíram, ou `null` se alguma das três etapas falhou.
     */
    private suspend fun arquivosLocais(): Int? {
        if (!clearSharedFiles) return 0
        val compartilhados = etapa("arquivos compartilhados") { getShareHandler().clearSharedFiles(0L) }
        val camera = etapa("originais da câmera") { clearCameraCaptureFiles(0L) }
        val fotos = etapa("cache de fotos privadas") { clearPrivatePhotoMemoryCache() }
        return if (camera == null || fotos == null) null else compartilhados
    }

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
