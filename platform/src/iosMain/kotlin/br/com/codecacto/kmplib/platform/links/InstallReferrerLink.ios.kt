package br.com.codecacto.kmplib.platform.links

/**
 * Sem equivalente no iOS: a Apple não entrega dado nenhum através da instalação, e casar o clique com
 * a instalação por impressão digital do aparelho é vedado pelas diretrizes da App Store. Ver o KDoc
 * da declaração comum.
 */
actual suspend fun readInstallReferrerLinkOnce(key: String, maxAgeSeconds: Long): String? = null
