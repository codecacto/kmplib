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
 * Toda operação vale para a **conta corrente** (o `accountScope` do espelho): é quem está saindo
 * ou excluindo a conta. Nenhuma lança — falha parcial vem em [LocalPurgeReport.failures].
 */
interface AccountLocalDataPurger {

    /**
     * O que ainda **não subiu** da conta corrente — para o app avisar ANTES de sair
     * ("3 alterações e 2 fotos ainda não foram enviadas") e deixar a pessoa escolher entre
     * esperar, manter ou descartar.
     */
    suspend fun pendingChanges(): LocalPendingChanges

    /**
     * Apaga **tudo** o que a conta corrente tem no aparelho: espelho, outbox, fila de upload com os
     * binários, cursores, remap de ids e as cópias de compartilhamento. É o passo local da
     * **exclusão de conta** — o `AccountDeletionService` chama sozinho quando recebe o purger.
     */
    suspend fun purgeAccount(): LocalPurgeReport

    /**
     * Limpeza de **logout**. O espelho sincronizado (o que já está no servidor e volta no próximo
     * login) sai sempre; o que ainda não subiu segue [pending]:
     * - [SignOutPendingPolicy.Keep] — a outbox e as fotos pendentes **ficam**, isoladas pelo escopo
     *   de conta, e sobem quando a mesma conta entrar de novo. Nada é perdido.
     * - [SignOutPendingPolicy.Discard] — sai tudo, como na exclusão de conta.
     *
     * Consulte [pendingChanges] antes: se estiver vazio, as duas políticas dão no mesmo.
     */
    suspend fun purgeOnSignOut(pending: SignOutPendingPolicy): LocalPurgeReport
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
