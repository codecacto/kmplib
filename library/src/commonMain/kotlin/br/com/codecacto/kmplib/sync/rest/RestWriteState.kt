package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.sync.SyncOpType
import br.com.codecacto.kmplib.sync.db.Synced_entity

/**
 * **Como a escrita chega ao servidor** ([OfflineFirstRestRepository.create]/`update`/`delete`).
 *
 * Existe porque "offline-first" não é só "funciona sem rede": é *a escrita do usuário nunca some*.
 * A diferença entre os dois modos aparece exatamente quando **há rede e o servidor responde erro**.
 */
enum class RestWriteMode {

    /**
     * **Compatível (default).** Tenta a rede primeiro; só grava no espelho o que o servidor
     * confirmou. Sem rede (falha de transporte) cai na outbox.
     *
     * Desde a 2.91.0 a **falha retentável** (5xx/timeout/429/401) também cai na outbox — antes ela
     * fazia a escrita **desaparecer** sem rastro. Já o erro **terminal** (4xx de validação, 403)
     * continua devolvendo `DomainResult.Error` **sem** persistir nada: é o comportamento certo para
     * um formulário, onde o usuário corrige o campo e envia de novo.
     */
    OnlineFirst,

    /**
     * **Local-first de verdade.** A escrita grava no espelho e entra na outbox **antes** de tocar a
     * rede; o resultado do servidor reconcilia depois. Nenhum toque do usuário depende de a
     * requisição terminar — nem de o processo continuar vivo.
     *
     * - sucesso ⇒ a linha vira LIMPA com o modelo do servidor (id e derivados vêm de lá);
     * - falha **retentável** (rede/5xx/timeout/401) ⇒ a linha fica [RestRowState.Pending] e o
     *   [RestCrudSyncEngine] retenta — a chamada devolve `Success` com o modelo local, porque a
     *   escrita **foi aceita** (a UI mostra o estado pendente, não um erro);
     * - falha **terminal** (4xx/403) ou **402 de cota** ⇒ a linha fica [RestRowState.Failed] com o
     *   erro preservado e **visível**, e a chamada devolve `Error`/`Quota`. Nada é revertido em
     *   silêncio; o app decide entre [OfflineFirstRestRepository.requeueFailed] e
     *   [OfflineFirstRestRepository.discardFailed].
     *
     * Escolha para escrita que **é o registro** (marcação de embarque/desembarque, ponto, checklist
     * de campo), onde perder o toque é perder o fato.
     */
    LocalFirst,
}

/**
 * Classificação da falha de uma escrita — decide entre **retentar** e **parar e mostrar**.
 *
 * A distinção é o coração da correção: retentar 4xx de validação em loop nunca converge (e esconde
 * o problema do usuário), enquanto descartar 5xx/timeout **perde a escrita** de quem só pegou um
 * servidor instável.
 */
enum class RestFailureClass {
    /** Sem rede/transporte ([DomainResult.OFFLINE_CODE]) — outbox, retenta ao reconectar. */
    Offline,

    /** 5xx, 408, 429 e 401 pós-refresh — outbox, retenta no próximo ciclo. */
    Retryable,

    /** 4xx de validação/permissão (400/403/404/409/422…) — para, preserva e mostra ao usuário. */
    Terminal,

    /** 402 — cota estourada: semântica própria (Paywall). Não retenta sozinho. */
    Quota,
    ;

    val isRetryable: Boolean get() = this == Offline || this == Retryable
}

/**
 * Classifica um código de erro do [DomainApiClient] (status HTTP ou sentinela).
 *
 * - **401** conta como retentável: o [DomainApiClient] já tentou renovar o token uma vez; se ainda
 *   falhou, a sessão expirou — o certo é preservar a escrita e retentar depois do novo login, não
 *   jogá-la fora.
 * - **429** é retentável (limite de taxa passa), mas **402 não** (cota do plano só passa com upgrade).
 * - Código **negativo diferente** de [DomainResult.OFFLINE_CODE] (ex.: resposta ilegível) é
 *   **terminal**: a requisição pode ter sido aplicada no servidor, e repeti-la duplicaria o registro.
 */
fun classifyRestFailure(code: Int): RestFailureClass = when {
    code == DomainResult.OFFLINE_CODE -> RestFailureClass.Offline
    code == 402 -> RestFailureClass.Quota
    code == 401 || code == 408 || code == 429 -> RestFailureClass.Retryable
    code in 500..599 -> RestFailureClass.Retryable
    else -> RestFailureClass.Terminal
}

/**
 * **Estado de sincronização de UMA linha do espelho** — o que a UI precisa saber para não inventar
 * o próprio overlay ("está indo", "o servidor recusou", "está gravado").
 *
 * Antes da 2.91.0 esse estado não existia na lib e cada app remendava por fora — no "Todos a Bordo"
 * era um overlay em memória que **morria com o processo**, levando junto a marcação recusada.
 */
sealed interface RestRowState {

    /** Gravado e confirmado pelo servidor. */
    data object Synced : RestRowState

    /**
     * Na outbox, aguardando o próximo ciclo de sync. [attempts] > 0 e [lastError] preenchido =
     * já houve tentativa que falhou de forma **retentável** (útil para "tentando enviar…").
     */
    data class Pending(
        val op: SyncOpType,
        val attempts: Int = 0,
        val lastError: String? = null,
    ) : RestRowState

    /**
     * O servidor **recusou** (4xx terminal ou 402). A linha continua no espelho, visível, com o erro
     * preservado — nunca é revertida em silêncio. Só volta a ser enviada por retry explícito
     * ([OfflineFirstRestRepository.requeueFailed]).
     */
    data class Failed(
        val op: SyncOpType,
        val code: Int,
        val message: String?,
        val attempts: Int = 0,
    ) : RestRowState {
        /** `true` se a recusa foi por **cota** (402) — o caminho é o Paywall, não "tentar de novo". */
        val isQuota: Boolean get() = code == 402
    }

    val isSynced: Boolean get() = this is Synced
    val isPending: Boolean get() = this is Pending
    val isFailed: Boolean get() = this is Failed
}

/** Uma linha do espelho com o seu [state] — o par que a lista da UI consome. */
data class RestRow<T>(val model: T, val state: RestRowState) {
    val isSynced: Boolean get() = state.isSynced
    val isPending: Boolean get() = state.isPending
    val isFailed: Boolean get() = state.isFailed
}

/** Deriva o [RestRowState] de uma linha crua do espelho. */
internal fun Synced_entity.toRestRowState(): RestRowState {
    if (dirty == 0L) return RestRowState.Synced
    val op = pending_op?.let { SyncOpType.fromWire(it) } ?: SyncOpType.UPDATE
    val tentativas = attempts.toInt()
    return if (failed != 0L) {
        RestRowState.Failed(
            op = op,
            code = fail_code?.toInt() ?: 0,
            message = last_error,
            attempts = tentativas,
        )
    } else {
        RestRowState.Pending(op = op, attempts = tentativas, lastError = last_error)
    }
}
