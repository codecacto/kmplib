package br.com.codecacto.kmplib.core.util

/**
 * Plataforma em que o app está rodando: `"android"` ou `"ios"`.
 *
 * É a string que a fábrica manda ao servidor — `?platform=` na checagem de versão, no catálogo de
 * anúncios e na grade "Desenvolvido por CodeCacto" —, por isso o valor é minúsculo e fixo: mudá-lo
 * quebra a consulta do outro lado.
 *
 * Mora em `core` porque três módulos independentes precisam dele. Antes era `internal` dentro de
 * `appupdate`, e funcionava só enquanto a lib era um módulo só: `internal` não atravessa fronteira
 * de módulo Gradle, e na modularização `ads` e `developer` pararam de enxergá-lo.
 */
expect val currentPlatform: String
