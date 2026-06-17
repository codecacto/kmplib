package br.com.codecacto.kmplib.appupdate

import io.ktor.client.HttpClient

/**
 * Configuração do módulo **appupdate** (Force Update / atualização obrigatória).
 *
 * Deve ser construída no ponto de entrada do app e passada ao [AppUpdateService] /
 * [AppUpdateGate]. Consome o endpoint PÚBLICO do admin-api
 * `GET {adminApiBaseUrl}/public/app-version?project={slug}&platform={android|ios}&versionCode={int}`.
 *
 * ## De onde tirar o [currentVersionCode]
 * - **Android:** `BuildConfig.VERSION_CODE` (Int).
 * - **iOS:** `CFBundleVersion` do `Info.plist`, convertido para `Int`
 *   (ex.: `NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion")?.toString()?.toIntOrNull()`).
 *
 * @param projectSlug Slug do app no catálogo central (ex.: "super-8"). OBRIGATÓRIO — vai como
 *   `?project=`.
 * @param httpClient `HttpClient` (Ktor) do app. Ktor core puro — NÃO exige ContentNegotiation
 *   (a desserialização é feita manualmente, como no `feedback`).
 * @param currentVersionCode `versionCode` instalado (Android `VERSION_CODE` / iOS `CFBundleVersion`).
 * @param adminApiBaseUrl Base URL do admin-api (sem barra final). Default de produção.
 * @param platform Plataforma (`"android"`|`"ios"`). Default derivado de `currentPlatform`
 *   (expect/actual). Sobrescreva apenas em cenários de teste.
 */
data class AppUpdateConfig(
    val projectSlug: String,
    val httpClient: HttpClient,
    val currentVersionCode: Int,
    val adminApiBaseUrl: String = DEFAULT_ADMIN_API_BASE_URL,
    val platform: String = currentPlatform,
) {
    companion object {
        const val DEFAULT_ADMIN_API_BASE_URL: String = "https://admin-api.codecacto.com.br"
    }
}

/** Plataforma atual (`"android"`|`"ios"`), resolvida por expect/actual. */
internal expect val currentPlatform: String
