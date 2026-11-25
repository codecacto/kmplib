package br.com.codecacto.kmplib.testing.net

/**
 * Como a rede falha numa chamada do [ServidorDeTeste] (2.381.0). Cada falha reproduz um caminho que o aparelho
 * vive de verdade — e que o fake de repositório escrito à mão pula, porque ele não passa pelo `DomainApiClient`
 * (onde mora a classificação do erro) nem pela política de nova tentativa do `createHttpClient`.
 *
 * | Falha | O servidor gravou? | O que o app recebe |
 * |---|---|---|
 * | [SemConexao] | **não** (a rota não roda) | falha de transporte antes da resposta |
 * | [GravouERespostaSePerdeu] | **sim** (a rota roda) | falha de transporte no lugar da resposta |
 * | [CorpoCortado] | **sim** | o status e os cabeçalhos, e o corpo cai no meio |
 * | [Demora] | **sim** | a resposta só depois de [Demora.ms] — além do teto do cliente, vira timeout |
 *
 * As três primeiras chegam como `kotlinx.io.IOException` — a família que a nova tentativa da lib reconhece como
 * transitória. Por isso um **GET** (ou um POST marcado `idempotent()`) com uma falha agendada costuma terminar
 * BEM, na 2ª tentativa; um POST que grava, não — e é isso que o teste da tela deve provar (ver os 4 caminhos em
 * [br.com.codecacto.kmplib.testing.vm.Caminho]).
 */
sealed interface FalhaDeRede {

    /** A conexão cai ANTES de o servidor ver o pedido: nada gravado, a rota NÃO roda, a chamada não entra em `chamadas`. */
    data object SemConexao : FalhaDeRede

    /**
     * A rota RODA (o servidor grava) e a resposta se perde no caminho de volta. É o caso do cancelar do LocAki
     * 1.8.0: a locação estava cancelada no servidor e o app dizia "sem conexão". A tela certa confere o estado
     * no servidor (ou trata a repetição como sucesso) em vez de mandar a pessoa tentar de novo.
     */
    data object GravouERespostaSePerdeu : FalhaDeRede

    /**
     * A rota RODA e responde; o status e os cabeçalhos chegam, e o corpo é cortado depois de [bytes] bytes (o
     * canal fecha com erro). É o "200 no log e `unexpected end of stream` na leitura" da rede móvel.
     */
    data class CorpoCortado(val bytes: Int = 0) : FalhaDeRede {
        init {
            require(bytes >= 0) { "bytes não pode ser negativo: $bytes" }
        }
    }

    /**
     * A rota RODA e a resposta só sai depois de [ms] milissegundos. Acima do `requestTimeoutMillis` do cliente
     * (30 s no `createHttpClient`), a chamada acaba em timeout — e o timeout NÃO é repetido pela lib.
     *
     * Com o engine no dispatcher do teste (`servidor.domainApiClient(dispatcher = …)`), a espera é em tempo
     * VIRTUAL: o teste não dorme de verdade.
     */
    data class Demora(val ms: Long) : FalhaDeRede {
        init {
            require(ms >= 0) { "ms não pode ser negativo: $ms" }
        }
    }
}
