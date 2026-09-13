package br.com.codecacto.kmplib.platform.links

import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **A porta única dos links que chegam de fora** (2.201.0) — App Link/Universal Link, link aberto
 * com o app em segundo plano e o link recuperado depois da instalação ([peekInstallReferrerLink]).
 *
 * É o padrão que a JetBrains recomenda para deep link em Compose Multiplatform: a plataforma
 * **entrega** a URL aqui (Android pelo `Intent`, iOS pelo `onOpenURL` do SwiftUI) e o app a
 * **consome** quando está pronto para navegar, com `navController.navigate(NavUri(url))`.
 *
 * ## Por que o app consome, e não a plataforma navega
 *
 * Deixar o `NavHost` tratar o `Intent` sozinho abre o destino **em cima da abertura** (Splash):
 * voltar dali mostra a abertura de novo, e a sessão pode nem ter sido restaurada ainda. Com a URL
 * guardada aqui, o app espera a pilha chegar à tela principal e só então navega — e o "voltar"
 * leva ao início do app, não para fora dele. No Android isso exige **limpar o `data` do `Intent`**
 * depois de entregar, senão o `NavHost` abre o destino também (e duas vezes).
 *
 * ## Um link por vez
 *
 * [pending] guarda o **último** link entregue. Dois links antes de o app estar pronto não fazem
 * sentido para quem os abriu: vale o mais recente, que é o que a pessoa acabou de tocar.
 *
 * ```kotlin
 * // Android — MainActivity.onCreate/onNewIntent
 * if (IncomingLinks.deliver(intent?.data?.toString())) intent.data = null
 *
 * // iOS — função chamada pelo Swift: `.onOpenURL { MainViewControllerKt.entregarLink(url: $0.absoluteString) }`
 * fun entregarLink(url: String) { IncomingLinks.deliver(url) }
 *
 * // commonMain — onde o NavHost vive
 * val link by IncomingLinks.pending.collectAsState()
 * LaunchedEffect(link, entradaAtual) {
 *     val url = link ?: return@LaunchedEffect
 *     if (aindaAbrindo) return@LaunchedEffect
 *     IncomingLinks.consume(url)
 *     if (IncomingLinks.belongsTo(url, setOf(host))) navController.navigate(NavUri(url))
 * }
 * ```
 */
object IncomingLinks {

    private val _pending = MutableStateFlow<String?>(null)

    /** O link esperando o app ficar pronto para navegar; `null` quando não há nenhum. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /**
     * Guarda [url] para o app consumir. Aceita só `http`/`https` absoluta com host — o resto
     * (esquema do login do Google, `null`, texto qualquer) é recusado e devolve `false`, o que deixa
     * a plataforma decidir se aquela URL era de outra pessoa.
     */
    fun deliver(url: String?): Boolean {
        val limpo = url?.trim().orEmpty()
        if (hostOf(limpo) == null) return false
        _pending.value = limpo
        return true
    }

    /**
     * Retira [url] da espera. Só retira se ainda for ELE: um link novo entregue entre a leitura e o
     * consumo não é apagado por quem consumiu o anterior.
     */
    fun consume(url: String) {
        _pending.compareAndSet(url, null)
    }

    /**
     * `true` quando o host de [url] é um de [hosts] (sem diferença de caixa, e `www.` equivale ao
     * domínio sem ele). **Confira antes de navegar**: a porta aceita qualquer `https`, e é o app
     * que sabe quais domínios são dele.
     */
    fun belongsTo(url: String, hosts: Set<String>): Boolean {
        val host = hostOf(url)?.removePrefix("www.") ?: return false
        return hosts.any { it.trim().lowercase().removePrefix("www.") == host }
    }

    /** Limpa a espera. Para teste. */
    internal fun reset() {
        _pending.value = null
    }
}

/** O host (minúsculo) de uma URL `http(s)` absoluta, ou `null` se ela não for uma. */
internal fun hostOf(url: String): String? {
    val esquema = when {
        url.startsWith("https://", ignoreCase = true) -> "https://"
        url.startsWith("http://", ignoreCase = true) -> "http://"
        else -> return null
    }
    // A autoridade tem de estar ESCRITA. O `Url` do Ktor preenche host ausente com `localhost`, então
    // `https://` e `https:///parceiros/1` passariam como links válidos para um host que ninguém digitou.
    val autoridade = url.substring(esquema.length).takeWhile { it != '/' && it != '?' && it != '#' }
    if (autoridade.isBlank()) return null
    return runCatching { Url(url).host.lowercase() }.getOrNull()?.takeIf { it.isNotBlank() }
}
