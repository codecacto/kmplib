package br.com.codecacto.kmplib.platform.tts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado de alto nível da síntese de voz ([TtsController]).
 *
 * - [Idle]: parado / pronto (nada sendo falado).
 * - [Speaking]: falando um enunciado agora.
 * - [Error]: falha ao falar (voz indisponível não é erro — ver [TtsVoiceAvailability]).
 */
enum class TtsState {
    Idle,
    Speaking,
    Error
}

/**
 * Gênero preferido da voz de síntese ([TtsController.setVoiceGender]).
 *
 * - [Female]: voz feminina (padrão do ecossistema).
 * - [Male]: voz masculina.
 *
 * A aplicação é **best-effort**: depende das vozes instaladas no dispositivo/motor (Android) ou
 * disponíveis no sistema (iOS). Se não houver voz do gênero pedido para o idioma, a voz padrão é
 * mantida — nunca bloqueia nem lança.
 */
enum class TtsVoiceGender {
    Female,
    Male
}

/**
 * Disponibilidade da voz para um idioma (BCP-47).
 *
 * - [Available]: há voz instalada para o idioma (fala funciona).
 * - [MissingData]: idioma suportado, mas os **dados de voz não estão instalados** — o host pode
 *   oferecer abrir a instalação de voz do sistema (Android `ACTION_INSTALL_TTS_DATA`) via
 *   `getUrlLauncher()`/callback. **Não** é responsabilidade do controller abrir essa tela.
 * - [NotSupported]: o motor de TTS não suporta o idioma.
 */
enum class TtsVoiceAvailability {
    Available,
    MissingData,
    NotSupported
}

/**
 * Controlador de **síntese de voz** (text-to-speech) nativa, multiplataforma.
 *
 * Padrão-ouro = API oficial do SO, via `expect/actual`:
 * - Android: `android.speech.tts.TextToSpeech` + `UtteranceProgressListener`.
 * - iOS: `AVSpeechSynthesizer` + `AVSpeechSynthesisVoice(language:)` (AVFoundation).
 *
 * Serve qualquer app que precise **ler texto em voz alta** (acessibilidade/CAA, leitura assistida).
 * Não grava áudio — é síntese em tempo real (distinto do [br.com.codecacto.kmplib.media.AudioPlayer]).
 *
 * ### Contrato de robustez (best-effort — NADA lança)
 * Voz ausente/idioma não suportado **nunca** bloqueia o app nem lança: o texto continua na tela e a
 * fala é apenas complemento. Falhas de motor viram [TtsState.Error] no [state] (via log leve), não
 * exceção. [isLanguageAvailable] apenas **reporta** ([TtsVoiceAvailability]).
 *
 * Obtenha uma instância via [createTtsController] (ou o helper Compose [rememberTtsController], que
 * chama [release] automaticamente no `onDispose`). Em ViewModels, chame [release] no `onCleared()`.
 *
 * Exemplo:
 * ```kotlin
 * val tts = rememberTtsController()
 * val state by tts.state.collectAsState()
 *
 * scope.launch { tts.speak("Estou com sede", langTag = "pt-BR") }
 * ```
 */
interface TtsController {

    /** Estado atual da síntese. */
    val state: StateFlow<TtsState>

    /**
     * Fala [text] no idioma [langTag] (BCP-47: `pt-BR`, `pt-PT`, `en-US`, `es-ES`).
     *
     * Best-effort: se a voz do idioma não estiver disponível, não fala e **não lança** (o estado
     * volta a [TtsState.Idle]/[TtsState.Error]). Interrompe qualquer fala em andamento (queue flush).
     *
     * @param langTag idioma BCP-47; normalizado internamente ([normalizeTtsLangTag]).
     * @param rate velocidade desta fala; **clampada** em [MIN_TTS_RATE]..[MAX_TTS_RATE]
     *   ([clampTtsRate]). `1f` = velocidade natural. **Default sentinela `Float.NaN`** = "usar a
     *   velocidade corrente" (a definida por [setRate], ou `1f` se nunca setada); passar um valor
     *   explícito sobrepõe a corrente **só nesta fala** e também passa a valer como a nova corrente.
     */
    suspend fun speak(text: String, langTag: String, rate: Float = Float.NaN)

    /** Interrompe imediatamente a fala em andamento e esvazia a fila. Sem efeito se já parado. */
    fun stop()

    /**
     * Reporta a disponibilidade de voz para [langTag] (BCP-47) — **sem** falar nada.
     * Best-effort: nunca lança; motor indisponível → [TtsVoiceAvailability.NotSupported].
     */
    suspend fun isLanguageAvailable(langTag: String): TtsVoiceAvailability

    /**
     * Define a velocidade padrão das próximas falas (**clampada** em
     * [MIN_TTS_RATE]..[MAX_TTS_RATE]). Não afeta a fala já em andamento.
     */
    fun setRate(rate: Float)

    /**
     * Define o gênero preferido da voz ([TtsVoiceGender.Female] por padrão). Guarda o estado e o
     * aplica nas **próximas** falas (não afeta a fala já em andamento). Segue o mesmo padrão de
     * [setRate].
     *
     * **Best-effort:** a seleção depende das vozes instaladas — no Android das `Voice`s de
     * `TextToSpeech.getVoices()` para o locale; no iOS das `AVSpeechSynthesisVoice.speechVoices()`.
     * Se não houver voz do gênero pedido para o idioma, a voz padrão é mantida (nunca lança nem
     * silencia a fala).
     */
    fun setVoiceGender(gender: TtsVoiceGender)

    /**
     * Libera os recursos nativos do motor de TTS. Após chamar, crie outro via [createTtsController].
     * Obrigatório no `onCleared()` do ViewModel (ou use [rememberTtsController]).
     */
    fun release()
}

/** Velocidade mínima aceita de fala. Valores menores são elevados a este piso. */
const val MIN_TTS_RATE: Float = 0.5f

/** Velocidade máxima aceita de fala. Valores maiores são reduzidos a este teto. */
const val MAX_TTS_RATE: Float = 2.0f

/**
 * Clampa a velocidade de fala em [MIN_TTS_RATE]..[MAX_TTS_RATE]. Entrada não-finita → `1f`.
 * Regra pura, testável (commonTest).
 */
fun clampTtsRate(rate: Float): Float =
    if (rate.isNaN() || rate.isInfinite()) 1f else rate.coerceIn(MIN_TTS_RATE, MAX_TTS_RATE)

/**
 * Resolve a velocidade efetiva de uma chamada de `speak`, dado o [requestedRate] (parâmetro da
 * chamada, `Float.NaN` = "usar a corrente") e o [currentRate] (definido por `setRate`).
 *
 * Se [requestedRate] for `NaN`, usa o [currentRate]; caso contrário, usa o [requestedRate]. O
 * resultado é sempre **clampado** ([clampTtsRate]). Garante que a velocidade escolhida via `setRate`
 * valha nas falas seguintes que não passam `rate`. Regra pura, testável (commonTest).
 */
fun resolveTtsSpeakRate(requestedRate: Float, currentRate: Float): Float =
    if (requestedRate.isNaN()) clampTtsRate(currentRate) else clampTtsRate(requestedRate)

/**
 * Normaliza uma tag de idioma para a forma BCP-47 canônica esperada pelos motores nativos:
 * `idioma` em minúsculas + `região` (se houver) em MAIÚSCULAS, separados por `-`.
 *
 * Aceita entrada com `_` (formato `Locale` Java) ou `-`, espaços e caixa variada:
 * `"pt_br"` → `"pt-BR"`, `"PT-Br"` → `"pt-BR"`, `"en"` → `"en"`, `" es_ES "` → `"es-ES"`.
 * Entrada vazia/em branco → `""`. Regra pura, testável (commonTest).
 */
fun normalizeTtsLangTag(langTag: String): String {
    val trimmed = langTag.trim()
    if (trimmed.isEmpty()) return ""
    val parts = trimmed.split('-', '_').filter { it.isNotBlank() }
    if (parts.isEmpty()) return ""
    val language = parts[0].lowercase()
    if (parts.size == 1) return language
    val region = parts[1].uppercase()
    return "$language-$region"
}

/**
 * Códigos de disponibilidade de idioma do `TextToSpeech` do Android (espelhados em commonMain para
 * manter o mapeamento **puro e testável**, sem depender do SDK Android no `commonTest`).
 */
object AndroidTtsLangResult {
    const val LANG_NOT_SUPPORTED: Int = -2
    const val LANG_MISSING_DATA: Int = -1
    const val LANG_AVAILABLE: Int = 0
    const val LANG_COUNTRY_AVAILABLE: Int = 1
    const val LANG_COUNTRY_VAR_AVAILABLE: Int = 2
}

/**
 * Mapeia o código de `TextToSpeech.isLanguageAvailable` para [TtsVoiceAvailability].
 * `LANG_MISSING_DATA` → [TtsVoiceAvailability.MissingData]; `LANG_NOT_SUPPORTED` (ou qualquer
 * código desconhecido negativo) → [TtsVoiceAvailability.NotSupported]; caso contrário
 * (`LANG_AVAILABLE`/`COUNTRY`/`COUNTRY_VAR`) → [TtsVoiceAvailability.Available]. Pura, testável.
 */
fun ttsAvailabilityFromAndroidCode(code: Int): TtsVoiceAvailability = when (code) {
    AndroidTtsLangResult.LANG_MISSING_DATA -> TtsVoiceAvailability.MissingData
    AndroidTtsLangResult.LANG_NOT_SUPPORTED -> TtsVoiceAvailability.NotSupported
    AndroidTtsLangResult.LANG_AVAILABLE,
    AndroidTtsLangResult.LANG_COUNTRY_AVAILABLE,
    AndroidTtsLangResult.LANG_COUNTRY_VAR_AVAILABLE -> TtsVoiceAvailability.Available
    else -> if (code < 0) TtsVoiceAvailability.NotSupported else TtsVoiceAvailability.Available
}

/**
 * Deduz o gênero sugerido por um **nome de voz** do motor (heurística pura, case-insensitive).
 *
 * Nomes de `Voice` do Android costumam embutir dicas de gênero, ex.: `pt-br-x-afs#female_1-local`,
 * `en-us-x-sfg#male_2-network`, `pt-br-x-pfm-local`. A ordem de checagem evita a armadilha de
 * `"female"` conter a substring `"male"`: a palavra `female` é testada **antes** de `male`, e os
 * códigos curtos (`#f`/`-f`/`_f` e `#m`/`-m`/`_m`) só depois das palavras completas.
 *
 * @return [TtsVoiceGender] sugerido, ou `null` se o nome não carrega dica de gênero.
 */
fun ttsVoiceGenderHint(name: String): TtsVoiceGender? {
    val n = name.lowercase()
    return when {
        n.contains("female") -> TtsVoiceGender.Female
        n.contains("male") -> TtsVoiceGender.Male
        n.contains("#f") || n.contains("-f") || n.contains("_f") -> TtsVoiceGender.Female
        n.contains("#m") || n.contains("-m") || n.contains("_m") -> TtsVoiceGender.Male
        else -> null
    }
}

/**
 * Escolhe, entre os [names] de vozes disponíveis, o nome da voz do [gender] pedido para o idioma
 * [langTag] (BCP-47). Função **pura e testável** — a impl Android converte o nome escolhido de volta
 * na `Voice` correspondente e faz `engine.voice = ...`.
 *
 * Preferência: (1) voz do gênero cujo nome casa com o **locale exato** (`pt-BR`); (2) voz do gênero
 * do **mesmo idioma** (`pt-*`, ex.: `pt-PT` quando se pediu `pt-BR`). Se nenhuma voz do gênero for
 * encontrada para o idioma, retorna `null` — o chamador deve **manter a voz padrão** (best-effort,
 * nunca quebra). Idioma vazio → escolhe a primeira voz do gênero em [names].
 *
 * @return nome da voz escolhida, ou `null` para manter a voz padrão.
 */
fun pickVoiceName(names: List<String>, gender: TtsVoiceGender, langTag: String): String? {
    if (names.isEmpty()) return null
    val lang = normalizeTtsLangTag(langTag).lowercase()
    if (lang.isEmpty()) {
        return names.firstOrNull { ttsVoiceGenderHint(it) == gender }
    }
    val languageOnly = lang.substringBefore('-')
    // 1) locale exato do gênero pedido.
    names.firstOrNull { it.lowercase().startsWith(lang) && ttsVoiceGenderHint(it) == gender }
        ?.let { return it }
    // 2) mesmo idioma (qualquer região) do gênero pedido.
    names.firstOrNull {
        val n = it.lowercase()
        (n.startsWith("$languageOnly-") || n == languageOnly) && ttsVoiceGenderHint(it) == gender
    }?.let { return it }
    // Nenhuma voz do gênero para o idioma → mantém a voz padrão.
    return null
}

/**
 * Cria a implementação de [TtsController] para a plataforma atual.
 *
 * No Android requer que [br.com.codecacto.kmplib.KmpLib.init] (ou `TtsControllerHolder.init`) tenha
 * sido chamado no `Application.onCreate()` para fornecer o `Context`.
 */
expect fun createTtsController(): TtsController

/**
 * Cria e memoriza um [TtsController] no escopo do composable atual, liberando-o automaticamente
 * ([TtsController.release]) quando o composable sai da composição (`onDispose`).
 */
@Composable
fun rememberTtsController(): TtsController {
    val controller = remember { createTtsController() }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}
