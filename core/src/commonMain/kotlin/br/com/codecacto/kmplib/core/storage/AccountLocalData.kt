package br.com.codecacto.kmplib.core.storage

/**
 * **O que a conta deixou no aparelho, e como tirar** (2.217.0).
 *
 * Existe porque apagar a conta no servidor não apaga nada do aparelho. Até a 2.216.0 o
 * `AccountDeletionService` fazia o wipe server-side, encerrava a sessão e parava aí: o **espelho**
 * do sync (o dado da conta, legível no SQLite), a **outbox** (o que ainda não subiu), os **binários**
 * da fila de upload (fotos) e as cópias feitas para compartilhar continuavam no disco. Num app
 * clínico isso é dado de saúde de uma conta que o titular mandou excluir.
 *
 * O contrato mora no `core` porque quem **chama** (o serviço de exclusão, no `auth`) e quem **sabe
 * apagar** (o sync, dono do espelho e da fila) são artefatos diferentes, e nenhum dos dois depende
 * do outro. A implementação padrão é o `SyncAccountDataPurger` do `kmplib-sync`.
 *
 * ### A conta é SEMPRE nomeada (2.218.0)
 * Até a 2.217.0 a limpeza valia para "a conta corrente" — o `accountScope` do espelho **no instante
 * da limpeza**. Só que é justamente no logout/exclusão que o app troca esse escopo (o `signOut`
 * dispara o ouvinte de sessão, que chama `setAccountScope(null)`), e a limpeza passava a varrer o
 * bucket errado — o vazio — e devolver `failures = 0`, com o dado clínico da conta que saiu intacto
 * no disco. Agora quem limpa **diz de quem**: capture [activeAccountId] **antes** de encerrar a
 * sessão e passe-o a [purgeAccount]/[purgeOnSignOut]. Conta em branco é **recusada** e contada como
 * falha — nunca "limpeza completa de nada".
 *
 * Nenhuma operação lança — falha parcial vem em [LocalPurgeReport.failures].
 */
interface AccountLocalDataPurger {

    /**
     * A conta dona do dado local **agora** (o `accountScope` do espelho), ou `null` quando não há
     * titular declarado. É o valor a capturar **antes** do `signOut`/wipe e passar às limpezas.
     */
    fun activeAccountId(): String?

    /**
     * O que ainda **não subiu** da conta corrente — para o app avisar ANTES de sair
     * ("3 alterações e 2 fotos ainda não foram enviadas") e deixar a pessoa escolher entre
     * esperar, manter ou descartar.
     */
    suspend fun pendingChanges(): LocalPendingChanges

    /**
     * Apaga **tudo** o que a conta [accountId] tem no aparelho: espelho, outbox, fila de upload com
     * os binários, cursores, remap de ids, cópias de compartilhamento e o cache de memória das fotos
     * privadas. Vale para a conta nomeada **mesmo que o escopo corrente já seja outro**. É o passo
     * local da **exclusão de conta** — o `AccountDeletionService` chama sozinho quando recebe o purger.
     *
     * `accountId` em branco é recusado (nada é apagado; `failures = 1`).
     */
    suspend fun purgeAccount(accountId: String): LocalPurgeReport

    /**
     * Limpeza de **logout** da conta [accountId]. O espelho sincronizado (o que já está no servidor e
     * volta no próximo login) sai sempre; o que ainda não subiu segue [pending]:
     * - [SignOutPendingPolicy.Keep] — a outbox e as fotos pendentes **ficam**, isoladas pelo escopo
     *   de conta, e sobem quando a mesma conta entrar de novo. Nada é perdido. Exige que [accountId]
     *   ainda seja o escopo corrente (a triagem "o que é pendência" lê o bucket corrente); se não for,
     *   **recusa sem apagar nada** e conta a falha.
     * - [SignOutPendingPolicy.Discard] — sai tudo, como em [purgeAccount].
     *
     * Consulte [pendingChanges] antes: se estiver vazio, as duas políticas dão no mesmo.
     */
    suspend fun purgeOnSignOut(accountId: String, pending: SignOutPendingPolicy): LocalPurgeReport

    /**
     * Roda [block] com a sincronização **segurada**: o ciclo em curso termina, e nenhum push/pull nem
     * envio da fila de upload começa até [block] acabar. As limpezas chamadas por dentro não esperam
     * a si mesmas.
     *
     * É o que fecha a janela da exclusão de conta: entre o `DELETE` no servidor e a limpeza local, um
     * ciclo disparado pela volta da rede subiria a outbox — e **recriaria no servidor** o dado da
     * conta que acabou de ser apagada (o access token segue válido por alguns minutos). O
     * `AccountDeletionService` segura desde **antes** do `DELETE` até o fim da limpeza.
     *
     * Default: roda [block] direto (implementação sem motor de sync).
     */
    suspend fun <T> withSyncPaused(block: suspend () -> T): T = block()

    /** Removido na 2.218.0: limpava o escopo do instante, que o app já pode ter trocado. */
    @Deprecated(
        "Nomeie a conta: capture activeAccountId() ANTES do signOut e passe-a.",
        ReplaceWith("purgeAccount(accountId)"),
        level = DeprecationLevel.ERROR,
    )
    suspend fun purgeAccount(): LocalPurgeReport = LocalPurgeReport(failures = 1)

    /** Removido na 2.218.0: limpava o escopo do instante, que o app já pode ter trocado. */
    @Deprecated(
        "Nomeie a conta: capture activeAccountId() ANTES do signOut e passe-a.",
        ReplaceWith("purgeOnSignOut(accountId, pending)"),
        level = DeprecationLevel.ERROR,
    )
    suspend fun purgeOnSignOut(pending: SignOutPendingPolicy): LocalPurgeReport = LocalPurgeReport(failures = 1)
}

/** O que acontece, no logout, com o que ainda não subiu. */
enum class SignOutPendingPolicy {
    /** Mantém outbox e fila de upload da conta (isoladas), para subirem no próximo login dela. */
    Keep,

    /** Descarta — o que não subiu é perdido. Só com a pessoa avisada ([LocalPendingChanges]). */
    Discard,
}

/**
 * Pendências locais da conta corrente.
 *
 * @param records alterações de registro na outbox (criar/editar/excluir), inclusive as recusadas
 *   pelo servidor que ainda esperam nova tentativa.
 * @param uploads arquivos na fila de upload (pendentes ou recusados).
 */
data class LocalPendingChanges(val records: Int, val uploads: Int) {
    val total: Int get() = records + uploads
    val isEmpty: Boolean get() = total == 0

    companion object {
        val NONE: LocalPendingChanges = LocalPendingChanges(0, 0)
    }
}

/**
 * Resultado de uma limpeza local.
 *
 * @param discardedPending pendências (registros + arquivos) que foram descartadas sem subir.
 * @param keptPending pendências que ficaram no aparelho ([SignOutPendingPolicy.Keep]).
 * @param removedSyncedRows linhas já sincronizadas apagadas do espelho (`-1` = a implementação
 *   apagou em bloco e não sabe contar).
 * @param sharedFilesDeleted cópias de compartilhamento apagadas.
 * @param failures etapas que falharam (log já feito, sem PII). `0` = limpeza completa.
 */
data class LocalPurgeReport(
    val discardedPending: Int = 0,
    val keptPending: Int = 0,
    val removedSyncedRows: Int = 0,
    val sharedFilesDeleted: Int = 0,
    val failures: Int = 0,
) {
    val isComplete: Boolean get() = failures == 0
}
