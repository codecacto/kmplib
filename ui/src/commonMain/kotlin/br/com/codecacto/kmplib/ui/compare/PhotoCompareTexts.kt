package br.com.codecacto.kmplib.ui.compare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_after
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_after_badge
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_before
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_before_badge
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_close
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_empty
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_fullscreen
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_load_error
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_loading
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_missing_photo
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_mode_side_by_side
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_mode_slider
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_modes
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_reset_zoom
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_single_hint
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_single_title
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_slider_between
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_slider_value
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_timeline
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_title
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_view_fallback
import br.com.codecacto.kmplib.generated.resources.kmplib_compare_views
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Textos do comparador ([PhotoCompare], [CompareTimeline], [PhotoCompareDialog]) — os MESMOS do
 * `PHOTO_COMPARE_LABELS` da weblib, nos 4 idiomas da lib. Default [rememberPhotoCompareTexts]; para
 * trocar uma frase, `.copy(…)`.
 *
 * As datas chegam JÁ formatadas; as porcentagens chegam inteiras.
 */
@Immutable
data class PhotoCompareTexts(
    /** Título com o par: `("12/03/2026", "12/10/2026")`. */
    val title: (before: String, after: String) -> String,
    /** Título quando só existe UMA sessão com foto. */
    val singleTitle: (date: String) -> String,
    /** Com uma sessão só, não há o que comparar — a consequência que não se adivinha. */
    val singleHint: String,
    /** Nome acessível do seletor de modo. */
    val modes: String,
    val modeSlider: String,
    val modeSideBySide: String,
    /** Nome acessível do seletor de vista. */
    val views: String,
    /** Vista sem `view` nem `label` (1-based). */
    val viewFallback: (position: Int) -> String,
    /** Papel da sessão na linha do tempo. */
    val before: String,
    val after: String,
    /** Rótulo sobre a foto de antes / de depois. */
    val beforeBadge: (date: String) -> String,
    val afterBadge: (date: String) -> String,
    /** Nome acessível da divisória com as datas do par. */
    val sliderBetween: (before: String, after: String) -> String,
    /** Estado anunciado da divisória: quanto de cada foto está à vista. */
    val sliderValue: (before: Int, after: Int) -> String,
    /** Nome acessível da linha do tempo. */
    val timeline: String,
    /** A sessão não tem a foto desta vista. */
    val missingPhoto: String,
    /** A foto não carregou (URL vencida, 404, rede). */
    val loadError: String,
    /** Nenhuma sessão com foto. */
    val empty: String,
    /** Nome acessível do esqueleto de carregamento. */
    val loading: String,
    /** Botão de tela cheia ("mostrar ao paciente"). Só no mobile. */
    val fullScreen: String,
    /** Fechar a tela cheia. */
    val close: String,
    /** Ação do leitor de tela que desfaz o zoom. */
    val resetZoom: String,
)

/** Os textos do comparador no idioma da tela. */
@Composable
fun rememberPhotoCompareTexts(): PhotoCompareTexts {
    val title = kmpStringResource(Res.string.kmplib_compare_title)
    val singleTitle = kmpStringResource(Res.string.kmplib_compare_single_title)
    val singleHint = kmpStringResource(Res.string.kmplib_compare_single_hint)
    val modes = kmpStringResource(Res.string.kmplib_compare_modes)
    val modeSlider = kmpStringResource(Res.string.kmplib_compare_mode_slider)
    val modeSideBySide = kmpStringResource(Res.string.kmplib_compare_mode_side_by_side)
    val views = kmpStringResource(Res.string.kmplib_compare_views)
    val viewFallback = kmpStringResource(Res.string.kmplib_compare_view_fallback)
    val before = kmpStringResource(Res.string.kmplib_compare_before)
    val after = kmpStringResource(Res.string.kmplib_compare_after)
    val beforeBadge = kmpStringResource(Res.string.kmplib_compare_before_badge)
    val afterBadge = kmpStringResource(Res.string.kmplib_compare_after_badge)
    val sliderBetween = kmpStringResource(Res.string.kmplib_compare_slider_between)
    val sliderValue = kmpStringResource(Res.string.kmplib_compare_slider_value)
    val timeline = kmpStringResource(Res.string.kmplib_compare_timeline)
    val missingPhoto = kmpStringResource(Res.string.kmplib_compare_missing_photo)
    val loadError = kmpStringResource(Res.string.kmplib_compare_load_error)
    val empty = kmpStringResource(Res.string.kmplib_compare_empty)
    val loading = kmpStringResource(Res.string.kmplib_compare_loading)
    val fullScreen = kmpStringResource(Res.string.kmplib_compare_fullscreen)
    val close = kmpStringResource(Res.string.kmplib_compare_close)
    val resetZoom = kmpStringResource(Res.string.kmplib_compare_reset_zoom)
    return remember(
        title, singleTitle, singleHint, modes, modeSlider, modeSideBySide, views, viewFallback, before, after,
        beforeBadge, afterBadge, sliderBetween, sliderValue, timeline, missingPhoto, loadError, empty, loading,
        fullScreen, close, resetZoom,
    ) {
        PhotoCompareTexts(
            title = { a, b -> formatCompareTemplate(title, a, b) },
            singleTitle = { d -> formatCompareTemplate(singleTitle, d) },
            singleHint = singleHint,
            modes = modes,
            modeSlider = modeSlider,
            modeSideBySide = modeSideBySide,
            views = views,
            viewFallback = { n -> formatCompareTemplate(viewFallback, n) },
            before = before,
            after = after,
            beforeBadge = { d -> formatCompareTemplate(beforeBadge, d) },
            afterBadge = { d -> formatCompareTemplate(afterBadge, d) },
            sliderBetween = { a, b -> formatCompareTemplate(sliderBetween, a, b) },
            sliderValue = { a, b -> formatCompareTemplate(sliderValue, a, b) },
            timeline = timeline,
            missingPhoto = missingPhoto,
            loadError = loadError,
            empty = empty,
            loading = loading,
            fullScreen = fullScreen,
            close = close,
            resetZoom = resetZoom,
        )
    }
}
