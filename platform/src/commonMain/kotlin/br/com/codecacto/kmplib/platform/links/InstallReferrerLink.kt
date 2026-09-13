package br.com.codecacto.kmplib.platform.links

import io.ktor.http.decodeURLQueryComponent

/** Chave, dentro do `referrer` da Play, que carrega o link a abrir depois da instalação. */
const val INSTALL_REFERRER_LINK_KEY: String = "cc_link"

/** Janela padrão entre o clique no link da loja e a primeira abertura do app: 24 horas. */
const val INSTALL_REFERRER_MAX_AGE_SECONDS: Long = 24L * 60 * 60

/**
 * **O link que a pessoa abriu ANTES de instalar o app** (deferred deep link, 2.201.0) — lido UMA vez
 * por instalação.
 *
 * O fluxo: alguém recebe `https://<site>/parceiros/123`, não tem o app, cai na página do site, toca
 * em "Google Play". O botão leva a
 * `play.google.com/store/apps/details?id=<pkg>&referrer=cc_link%3D<url codificada>`; a Play guarda o
 * `referrer`, e a primeira abertura do app o recupera aqui. Entregue a [IncomingLinks.deliver], o
 * link abre a empresa como se a pessoa tivesse tocado nele com o app já instalado.
 *
 * - **Android:** Play Install Referrer API (`com.android.installreferrer`), a forma oficial. Marca
 *   a leitura como feita depois de uma resposta DEFINITIVA (ok, ou recurso inexistente no aparelho);
 *   falha passageira (Play indisponível, tempo esgotado) tenta de novo na abertura seguinte.
 * - **iOS: devolve sempre `null`.** A Apple não oferece meio de atravessar a instalação com um dado
 *   — não há equivalente ao `referrer`, e casar clique com instalação por impressão digital do
 *   aparelho é vedado pelas diretrizes da App Store. No iOS o link só abre o app quando ele já está
 *   instalado (Universal Link).
 *
 * @param key chave dentro do `referrer` (default [INSTALL_REFERRER_LINK_KEY]).
 * @param maxAgeSeconds clique mais antigo que isto é ignorado (default 24 h): quem instalou o app
 *   por um link há três meses e só agora recebeu esta versão não deve ser levado àquela empresa.
 * @return a URL decodificada, ou `null` (sem link, já lido, velho demais, ou plataforma sem suporte).
 */
expect suspend fun readInstallReferrerLinkOnce(
    key: String = INSTALL_REFERRER_LINK_KEY,
    maxAgeSeconds: Long = INSTALL_REFERRER_MAX_AGE_SECONDS,
): String?

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
