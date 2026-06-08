package br.com.codecacto.kmplib.ads.router

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Qual backend de publicidade um app usa pra um determinado formato.
 *
 * Configurado remotamente pelo admin via Firestore (`app_ad_configs/{appId}`),
 * lido em runtime pelo [AdRouter] e despachado pelos composables
 * [ManagedBannerAd] / [ManagedInterstitialAd].
 *
 * Os `@SerialName` lowercase batem com o que o painel web grava no Firestore
 * — sem isso a desserializacao silenciosamente cai no default.
 */
@Serializable
enum class AdProvider {
    /** Mostra anuncios do AdMob (pacote `firebase/ads`). Requer `AdConfig` no init. */
    @SerialName("admob") ADMOB,

    /** Mostra anuncios proprios (pacote `ads/custom`). Requer `CustomAdManager.initialize`. */
    @SerialName("custom") CUSTOM,

    /** Nao mostra nada nesse formato. */
    @SerialName("off") OFF;

    companion object {
        /**
         * Parsea strings tolerantes a case/espacos. Default [OFF] pra qualquer
         * valor desconhecido — postura conservadora pra evitar mostrar ads
         * involuntariamente quando o admin digita algo errado.
         */
        fun fromString(value: String?): AdProvider = when (value?.trim()?.uppercase()) {
            "ADMOB" -> ADMOB
            "CUSTOM" -> CUSTOM
            "OFF", null, "" -> OFF
            else -> OFF
        }
    }
}
