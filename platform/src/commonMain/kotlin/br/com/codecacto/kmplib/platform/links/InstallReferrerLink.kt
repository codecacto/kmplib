package br.com.codecacto.kmplib.platform.links

import io.ktor.http.decodeURLQueryComponent

/** Chave, dentro do `referrer` da Play, que carrega o link a abrir depois da instalação. */
const val INSTALL_REFERRER_LINK_KEY: String = "cc_link"

/** Janela padrão entre o clique no link da loja e a primeira abertura do app: 24 horas. */
const val INSTALL_REFERRER_MAX_AGE_SECONDS: Long = 24L * 60 * 60

/**
 * **O link que a pessoa abriu ANTES de instalar o app** (deferred deep link) — e que continua
 * pendente **até o app confirmar que o usou** (2.203.0).
 *
 * O fluxo: alguém recebe `https://<site>/parceiros/123`, não tem o app, cai na página do site, toca
 * em "Google Play". O botão leva a
 * `play.google.com/store/apps/details?id=<pkg>&referrer=cc_link%3D<url codificada>`; a Play guarda o
 * `referrer`, e a primeira abertura do app o recupera aqui.
 *
 * ### Espiar, usar, confirmar — nessa ordem
 * ```kotlin
 * LaunchedEffect(Unit) { peekInstallReferrerLink()?.let(IncomingLinks::deliver) }
 * // … no ponto em que o destino do link ABRIU de fato (a tela montou, depois do login se ele exigir):
 * markInstallReferrerLinkConsumed()
 * ```
 * A Play é consultada **uma vez por instalação**; o link que ela devolve fica **gravado** e esta
 * função o devolve em toda abertura até [markInstallReferrerLinkConsumed]. É o que protege o caso
 * que a leitura única perdia: a primeira abertura leva ao destino, o destino pede login, o login abre
 * a folha do Google — e o sistema mata o processo com o app em segundo plano. Com a leitura marcada
 * como feita logo na consulta, o link sumia ali; agora a abertura seguinte o entrega de novo.
 *
 * **Confirme no DESTINO, não na entrega.** Confirmar logo depois de `deliver` recria o defeito:
 * entregar não é abrir.
 *
 * - **Android:** Play Install Referrer API (`com.android.installreferrer`), a forma oficial. A consulta
 *   só é dada como feita depois de uma resposta DEFINITIVA (ok, ou recurso inexistente no aparelho);
 *   falha passageira (Play indisponível, tempo esgotado) tenta de novo na abertura seguinte.
 * - **iOS: devolve sempre `null`.** A Apple não oferece meio de atravessar a instalação com um dado
 *   — não há equivalente ao `referrer`, e casar clique com instalação por impressão digital do
 *   aparelho é vedado pelas diretrizes da App Store. No iOS o link só abre o app quando ele já está
 *   instalado (Universal Link).
 *
 * @param key chave dentro do `referrer` (default [INSTALL_REFERRER_LINK_KEY]).
 * @param maxAgeSeconds clique mais antigo que isto é ignorado (default 24 h) — conferido também sobre
 *   o link **pendente**: quem abandonou o login e só voltou ao app uma semana depois não é levado
 *   àquela empresa. Link vencido é descartado.
 * @return a URL decodificada, ou `null` (sem link, já confirmado, velho demais, ou plataforma sem
 *   suporte).
 */
expect suspend fun peekInstallReferrerLink(
    key: String = INSTALL_REFERRER_LINK_KEY,
    maxAgeSeconds: Long = INSTALL_REFERRER_MAX_AGE_SECONDS,
): String?

/**
 * Confirma que o link de [peekInstallReferrerLink] foi usado: ele deixa de ser devolvido, e a Play
 * não é consultada de novo. Chamar quando o destino **abriu** — ver o KDoc de lá. Idempotente; no
 * iOS, não faz nada.
 */
expect fun markInstallReferrerLinkConsumed()

/**
 * Leitura única: devolve o link e já o dá como usado.
 *
 * Mantida com o comportamento de sempre para quem já a chama, mas é a forma que perde o link quando
 * o processo morre entre a entrega e a abertura do destino (a folha de login do Google é o caso
 * comum).
 */
@Deprecated(
    "Dá o link como usado ANTES de ele abrir: se o processo morrer no caminho (login pelo Google), o " +
        "destino se perde. Use peekInstallReferrerLink() e chame markInstallReferrerLinkConsumed() " +
        "quando o destino abrir.",
)
suspend fun readInstallReferrerLinkOnce(
    key: String = INSTALL_REFERRER_LINK_KEY,
    maxAgeSeconds: Long = INSTALL_REFERRER_MAX_AGE_SECONDS,
): String? = peekInstallReferrerLink(key, maxAgeSeconds)?.also { markInstallReferrerLinkConsumed() }

/**
 * Tira o link de dentro do `referrer` bruto da Play (`cc_link=https%3A%2F%2F…&utm_source=…`).
 * Pura, para teste. `null` quando a chave não está lá ou o valor fica vazio.
 */
fun parseInstallReferrerLink(referrer: String?, key: String = INSTALL_REFERRER_LINK_KEY): String? {
    if (referrer.isNullOrBlank()) return null
    return referrer.split('&').firstNotNullOfOrNull { par ->
        val corte = par.indexOf('=')
        if (corte <= 0 || par.substring(0, corte) != key) return@firstNotNullOfOrNull null
        runCatching { par.substring(corte + 1).decodeURLQueryComponent() }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}

/**
 * `true` quando o clique ainda vale. Instante `0` é "a Play não informou" e **vale** — sem ele não há
 * como medir, e recusar tiraria o link justamente de quem acabou de instalar.
 */
fun isInstallReferrerFresh(clickEpochSeconds: Long, nowEpochSeconds: Long, maxAgeSeconds: Long): Boolean {
    if (clickEpochSeconds <= 0L) return true
    return nowEpochSeconds - clickEpochSeconds in 0..maxAgeSeconds
}

// ------------------------------------------------------------------------------ máquina de estado

/** O que a consulta à loja respondeu. */
internal sealed interface InstallReferrerAnswer {
    data class Ok(val referrer: String?, val clickEpochSeconds: Long, val installBeginEpochSeconds: Long) :
        InstallReferrerAnswer

    /** O aparelho não tem o serviço (loja que não é a Play, aparelho sem Play): não adianta insistir. */
    data object Definitive : InstallReferrerAnswer

    /** Play indisponível agora, conexão caiu, tempo esgotado: tenta na próxima abertura. */
    data object Transient : InstallReferrerAnswer
}

/** O que precisa sobreviver ao processo. No Android, `SharedPreferences`. */
internal interface InstallReferrerLinkStore {
    /** A loja já deu resposta definitiva (com ou sem link). */
    val consulted: Boolean

    /** O link devolvido e ainda não confirmado, ou `null`. */
    val pendingUrl: String?

    /** O instante de referência do link pendente (clique, início da instalação ou 1ª leitura). */
    val pendingEpochSeconds: Long

    /** Grava, **numa escrita só**, que a loja respondeu e qual link ficou pendente (`null` = nenhum). */
    fun recordAnswer(pendingUrl: String?, epochSeconds: Long)

    /** Esquece o link pendente, mantendo a consulta como feita. */
    fun clearPending()
}

/**
 * O coração de [peekInstallReferrerLink], sem plataforma: pendente vale até ser confirmado ou vencer;
 * a loja é consultada só enquanto não houve resposta definitiva.
 */
internal suspend fun peekInstallReferrerLinkWith(
    store: InstallReferrerLinkStore,
    nowEpochSeconds: Long,
    key: String,
    maxAgeSeconds: Long,
    query: suspend () -> InstallReferrerAnswer,
): String? {
    store.pendingUrl?.let { pendente ->
        if (isInstallReferrerFresh(store.pendingEpochSeconds, nowEpochSeconds, maxAgeSeconds)) return pendente
        store.clearPending()
        return null
    }
    if (store.consulted) return null

    return when (val resposta = query()) {
        InstallReferrerAnswer.Transient -> null
        InstallReferrerAnswer.Definitive -> {
            store.recordAnswer(null, 0L)
            null
        }
        is InstallReferrerAnswer.Ok -> {
            // Sem o clique, o início da instalação; sem os dois, a própria leitura — assim o link
            // pendente sempre tem um instante para vencer, em vez de ficar valendo para sempre.
            val instante = resposta.clickEpochSeconds.takeIf { it > 0 }
                ?: resposta.installBeginEpochSeconds.takeIf { it > 0 }
                ?: nowEpochSeconds
            val link = parseInstallReferrerLink(resposta.referrer, key)
                ?.takeIf { isInstallReferrerFresh(instante, nowEpochSeconds, maxAgeSeconds) }
            store.recordAnswer(link, instante)
            link
        }
    }
}
