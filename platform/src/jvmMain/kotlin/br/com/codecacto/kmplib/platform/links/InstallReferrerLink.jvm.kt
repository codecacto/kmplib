package br.com.codecacto.kmplib.platform.links

// Desktop: não há loja nem Install Referrer.
actual suspend fun peekInstallReferrerLink(key: String, maxAgeSeconds: Long): String? = null

actual fun markInstallReferrerLinkConsumed() = Unit
