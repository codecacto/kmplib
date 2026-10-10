package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.sync.direct.DirectUploadOutbox
import br.com.codecacto.kmplib.core.storage.AccountLocalDataPurger
import br.com.codecacto.kmplib.core.storage.LocalPendingChanges
import br.com.codecacto.kmplib.core.storage.LocalPurgeReport
import br.com.codecacto.kmplib.core.storage.SignOutPendingPolicy
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.clearCameraCaptureFiles
import br.com.codecacto.kmplib.platform.clearKmpLibTemporaryFiles
import br.com.codecacto.kmplib.ui.components.clearPrivatePhotoMemoryCache
import br.com.codecacto.kmplib.sync.rest.RestCrudSyncEngine
import br.com.codecacto.kmplib.sync.rest.RestUploadOutbox
import br.com.codecacto.kmplib.sync.db.Synced_entity
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
 *   (PDF/imagem exportados) e, desde a 2.280.0, os temporários de PDF e impressão
 *   (`clearKmpLibTemporaryFiles`), em qualquer idade. Vale nos dois modos: não são pendência de ninguém.
 *   Os originais de câmera e o cache de memória das fotos privadas saem **sempre**, com ou sem a
 *   flag (2.218.1): são imagem crua da conta, não cópia exportada.
 * @param engine o motor REST do app, se houver: a limpeza roda dentro de
 *   [RestCrudSyncEngine.runExclusive], e [withSyncPaused] o segura.
 * ### Rascunho local que o `Keep` também guarda (2.273.0)
 *
 * O `Keep` preserva o que é **pendência da fila** (linha suja). Uma linha **limpa** gravada por
 * `LocalRepository` — o treino em andamento, um rascunho de formulário — não é pendência para o
 * espelho, e por isso saía junto com o que já está no servidor. Quando a sessão cai sem ação da
 * pessoa (refresh vencido, senha trocada noutro aparelho), isso apagava o trabalho em curso dela.
 * Declare o que é rascunho local da conta:
 *
 * ```kotlin
 * SyncAccountDataPurger(
 *     store = get(),
 *     engine = get(),
 *     keepLocalEntities = setOf("active_run"),          // a entidade inteira
 *     // ou, fino: keepLocalRow = { it.entity == "draft" && it.server_id == null },
 * )
 * ```
 *
 * Vale **só** para `purgeOnSignOut(conta, Keep)`. `Discard` e [purgeAccount] (exclusão de conta)
 * continuam apagando tudo, rascunho incluído. As linhas mantidas ficam no bucket da conta (isoladas
 * pelo escopo) e reaparecem quando a MESMA conta entrar de novo; o [LocalPurgeReport.removedSyncedRows]
 * não as conta.
 *
 * @param directUploadOutboxes as filas de envio direto ([DirectUploadOutbox], 2.287.0 — o vídeo que
 *   sobe direto ao storage). Contam como pendência em [pendingChanges]; saem com a conta em
 *   [purgeAccount] e no `Discard`; no `Keep` ficam (isoladas pela conta) e sobem quando ela voltar.
 *   [withSyncPaused] as segura também.
 * @param extraCleanup o que o app guarda FORA do espelho (DataStore da conta, outro `BlobStore`,
 *   arquivo próprio). Roda por último. Exceção aqui conta como falha, sem abortar o resto.
 * @param keepLocalEntities nomes de entidade ([SyncableEntity.name]) cujas linhas ficam no aparelho
 *   no logout com [SignOutPendingPolicy.Keep], mesmo limpas. Default vazio = comportamento de antes.
 * @param keepLocalRow predicado para manter linhas avulsas no mesmo caso (ex.: só o rascunho sem id
 *   de servidor). Soma-se a [keepLocalEntities]. Exceção aqui conta como falha e **não apaga nada**
 *   do espelho naquela limpeza (na dúvida, o rascunho fica).
 */
class SyncAccountDataPurger(
    private val store: SyncStore,
    private val uploadOutboxes: List<RestUploadOutbox> = emptyList(),
    private val clearSharedFiles: Boolean = true,
    private val engine: RestCrudSyncEngine? = null,
    private val keepLocalEntities: Set<String> = emptySet(),
    private val keepLocalRow: (Synced_entity) -> Boolean = { false },
    // Antes do extraCleanup de propósito: quem passa a limpeza do app como lambda final continua compilando.
    private val directUploadOutboxes: List<DirectUploadOutbox> = emptyList(),
    private val extraCleanup: suspend (LocalPurgeScope) -> Unit = {},
) : AccountLocalDataPurger {

    override fun activeAccountId(): String? =
        store.accountScope.value.takeUnless { it.isBlank() || it == SyncStore.NO_ACCOUNT }

    override suspend fun pendingChanges(): LocalPendingChanges {
        val uploads = uploadOutboxes.sumOf { store.getDirty(it.entity).size }
        val registros = (store.countDirty().toInt() - uploads).coerceAtLeast(0)
        val diretos = activeAccountId()?.let { conta -> directUploadOutboxes.sumOf { it.pendingCount(conta) } } ?: 0
        return LocalPendingChanges(records = registros, uploads = uploads + diretos)
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
        val segurarDiretas: suspend () -> T = directUploadOutboxes.foldRight(block) { fila, interno ->
            { fila.withDrainPaused(interno) }
        }
        val segurarFilas: suspend () -> T = uploadOutboxes.foldRight(segurarDiretas) { fila, interno ->
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
        directUploadOutboxes.forEach { fila ->
            if (etapa("fila de envio direto") { fila.purgeAccount(accountId) } == null) falhas++
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
                keepsLocalDraft(linha, keepLocalEntities, keepLocalRow) ||
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
        val etapas = purgeLocalFileSteps(clearSharedFiles)
        val compartilhados = if (PurgeLocalFileStep.SHARED_FILES in etapas) {
            // 2.280.0: todos os temporários da lib (compartilhamento + PDF aberto + impressão).
            etapa("arquivos temporários") { clearKmpLibTemporaryFiles(0L) }
        } else {
            0
        }
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

/** Arquivos locais que o [SyncAccountDataPurger] limpa além do espelho e das filas. */
internal enum class PurgeLocalFileStep { SHARED_FILES, CAMERA_ORIGINALS, PRIVATE_PHOTO_CACHE }

/**
 * `clearSharedFiles` controla **só** as cópias de compartilhamento. Até a 2.218.0 a flag em `false`
 * pulava também os originais de câmera e o cache de fotos privadas (2.218.1).
 */
internal fun purgeLocalFileSteps(clearSharedFiles: Boolean): Set<PurgeLocalFileStep> =
    buildSet {
        if (clearSharedFiles) add(PurgeLocalFileStep.SHARED_FILES)
        add(PurgeLocalFileStep.CAMERA_ORIGINALS)
        add(PurgeLocalFileStep.PRIVATE_PHOTO_CACHE)
    }

/**
 * A linha é rascunho local que o `Keep` guarda? Entidade declarada inteira, ou o predicado do app.
 * Exceção do predicado PROPAGA de propósito: a etapa do espelho a trata como falha e, como o
 * `deleteSyncedRows` roda numa transação, nada é apagado — perder o rascunho por um predicado com
 * defeito seria o pior desfecho.
 */
internal fun keepsLocalDraft(
    row: Synced_entity,
    keepLocalEntities: Set<String>,
    keepLocalRow: (Synced_entity) -> Boolean,
): Boolean = row.entity in keepLocalEntities || keepLocalRow(row)
