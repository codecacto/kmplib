package br.com.codecacto.kmplib.appupdate

import kotlinx.serialization.Serializable

/**
 * Corpo de resposta 200 de `GET {adminApiBaseUrl}/public/app-version` — casa exatamente o contrato
 * do endpoint PÚBLICO de versão do admin-api.
 *
 * @param action Ação recomendada: `"none"` (sem atualização), `"soft"` (opcional/dispensável),
 *   `"hard"` (obrigatória/bloqueante). Qualquer valor desconhecido é tratado como `"none"`
 *   (fail-safe — nunca trava o app por contrato novo no servidor).
 * @param minVersionCode Menor `versionCode` ainda suportado (abaixo dele a atualização é `hard`).
 * @param latestVersionCode `versionCode` da versão mais recente publicada na loja.
 * @param storeUrl URL da loja (Play Store / App Store) para abrir na ação de atualizar.
 * @param message Mensagem a exibir ao usuário (pode vir do admin; há defaults nos textos da UI).
 * @param latestVersionName Nome legível da versão mais recente (ex.: "1.4.0"), para o banner soft.
 */
@Serializable
data class AppVersionCheckResponse(
    val action: String = ACTION_NONE,
    val minVersionCode: Int? = null,
    val latestVersionCode: Int? = null,
    val storeUrl: String? = null,
    val message: String? = null,
    val latestVersionName: String? = null,
) {
    companion object {
        const val ACTION_NONE: String = "none"
        const val ACTION_SOFT: String = "soft"
        const val ACTION_HARD: String = "hard"
    }
}

/**
 * Resultado da checagem de versão, já mapeado para a decisão de UI.
 *
 * Best-effort: qualquer falha (rede/timeout/contrato inesperado) resolve para [None] — a checagem
 * NUNCA pode bloquear o app por conta própria.
 */
sealed interface AppUpdateStatus {

    /** Não há atualização a sinalizar (ou a checagem falhou — fail-safe). */
    data object None : AppUpdateStatus

    /**
     * Atualização opcional disponível: exibir banner/diálogo dispensável sobre o conteúdo normal.
     *
     * @param storeUrl URL da loja para abrir ao tocar "Atualizar" (pode ser nula se o admin não enviou).
     * @param message Mensagem opcional vinda do servidor (UI usa default se nula/vazia).
     * @param latestVersionName Nome da versão mais recente (opcional), para enriquecer o texto.
     */
    data class Soft(
        val storeUrl: String?,
        val message: String?,
        val latestVersionName: String?,
    ) : AppUpdateStatus

    /**
     * Atualização obrigatória: o app deve renderizar uma tela bloqueante full-screen e impedir o uso
     * até o usuário atualizar.
     *
     * @param storeUrl URL da loja para abrir ao tocar "Atualizar" (pode ser nula).
     * @param message Mensagem opcional vinda do servidor (UI usa default se nula/vazia).
     */
    data class Hard(
        val storeUrl: String?,
        val message: String?,
    ) : AppUpdateStatus
}
