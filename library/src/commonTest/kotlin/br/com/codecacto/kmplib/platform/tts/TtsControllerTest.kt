package br.com.codecacto.kmplib.platform.tts

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testes da lógica PURA do TTS (sem motor nativo): normalização de langTag BCP-47, clamp de rate
 * e mapeamento de disponibilidade de voz do código do Android.
 */
class TtsControllerTest {

    // --- normalizeTtsLangTag ---

    @Test
    fun `normaliza underscore e caixa para BCP-47 canonico`() {
        assertEquals("pt-BR", normalizeTtsLangTag("pt_br"))
        assertEquals("pt-BR", normalizeTtsLangTag("PT-Br"))
        assertEquals("es-ES", normalizeTtsLangTag(" es_ES "))
        assertEquals("en-US", normalizeTtsLangTag("EN-us"))
    }

    @Test
    fun `normaliza idioma sem regiao`() {
        assertEquals("en", normalizeTtsLangTag("EN"))
        assertEquals("pt", normalizeTtsLangTag("pt"))
    }

    @Test
    fun `normaliza os quatro idiomas do ecossistema`() {
        assertEquals("pt-BR", normalizeTtsLangTag("pt-BR"))
        assertEquals("pt-PT", normalizeTtsLangTag("pt-pt"))
        assertEquals("en-US", normalizeTtsLangTag("en-us"))
        assertEquals("es-ES", normalizeTtsLangTag("es-es"))
    }

    @Test
    fun `entrada vazia ou em branco vira string vazia`() {
        assertEquals("", normalizeTtsLangTag(""))
        assertEquals("", normalizeTtsLangTag("   "))
        assertEquals("", normalizeTtsLangTag("-"))
    }

    // --- clampTtsRate ---

    @Test
    fun `clampTtsRate respeita o intervalo`() {
        assertEquals(MIN_TTS_RATE, clampTtsRate(0.1f))
        assertEquals(MAX_TTS_RATE, clampTtsRate(5f))
        assertEquals(1f, clampTtsRate(1f))
        assertEquals(1.5f, clampTtsRate(1.5f))
    }

    @Test
    fun `clampTtsRate trata valores nao finitos como 1f`() {
        assertEquals(1f, clampTtsRate(Float.NaN))
        assertEquals(1f, clampTtsRate(Float.POSITIVE_INFINITY))
        assertEquals(1f, clampTtsRate(Float.NEGATIVE_INFINITY))
    }

    // --- resolveTtsSpeakRate (setRate valendo nas falas seguintes) ---

    @Test
    fun `speak sem rate usa a velocidade corrente definida por setRate`() {
        // Simula setRate(1.5f) seguido de speak(...) sem rate (NaN = usar corrente).
        val currentRate = clampTtsRate(1.5f)
        assertEquals(1.5f, resolveTtsSpeakRate(Float.NaN, currentRate))
    }

    @Test
    fun `speak com rate explicito sobrepoe a corrente`() {
        val currentRate = clampTtsRate(1.5f)
        assertEquals(0.8f, resolveTtsSpeakRate(0.8f, currentRate))
    }

    @Test
    fun `speak sem rate e sem setRate previo usa 1f`() {
        // currentRate inicial do controller = 1f.
        assertEquals(1f, resolveTtsSpeakRate(Float.NaN, 1f))
    }

    @Test
    fun `resolveTtsSpeakRate clampa tanto a corrente quanto o explicito`() {
        assertEquals(MAX_TTS_RATE, resolveTtsSpeakRate(Float.NaN, 9f))
        assertEquals(MIN_TTS_RATE, resolveTtsSpeakRate(0.1f, 1f))
    }

    // --- ttsAvailabilityFromAndroidCode ---

    @Test
    fun `mapeia missing data`() {
        assertEquals(
            TtsVoiceAvailability.MissingData,
            ttsAvailabilityFromAndroidCode(AndroidTtsLangResult.LANG_MISSING_DATA)
        )
    }

    @Test
    fun `mapeia not supported`() {
        assertEquals(
            TtsVoiceAvailability.NotSupported,
            ttsAvailabilityFromAndroidCode(AndroidTtsLangResult.LANG_NOT_SUPPORTED)
        )
    }

    @Test
    fun `mapeia disponivel para available country e country-var`() {
        assertEquals(
            TtsVoiceAvailability.Available,
            ttsAvailabilityFromAndroidCode(AndroidTtsLangResult.LANG_AVAILABLE)
        )
        assertEquals(
            TtsVoiceAvailability.Available,
            ttsAvailabilityFromAndroidCode(AndroidTtsLangResult.LANG_COUNTRY_AVAILABLE)
        )
        assertEquals(
            TtsVoiceAvailability.Available,
            ttsAvailabilityFromAndroidCode(AndroidTtsLangResult.LANG_COUNTRY_VAR_AVAILABLE)
        )
    }

    @Test
    fun `codigo negativo desconhecido vira not supported`() {
        assertEquals(TtsVoiceAvailability.NotSupported, ttsAvailabilityFromAndroidCode(-99))
    }
}
