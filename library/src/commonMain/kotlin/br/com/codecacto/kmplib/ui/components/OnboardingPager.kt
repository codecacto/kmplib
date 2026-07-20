package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact
import kotlinx.coroutines.launch

/**
 * Uma página (slide) do [OnboardingPager]. **Config-driven** — o app injeta o conteúdo; a lib só
 * dispõe/anima. A ilustração é opcional e pode ser um ícone simples ([icon]) OU um slot Composable
 * arbitrário ([illustration], ex.: uma imagem/Lottie/vetor do app). Se ambos forem nulos, o slide
 * mostra só título + descrição.
 *
 * @param title Título do slide (herói).
 * @param description Texto de apoio.
 * @param icon Ícone opcional exibido no topo (fallback simples de ilustração).
 * @param illustration Slot de ilustração opcional (recebe um [Modifier] a aplicar na raiz). Tem
 *   prioridade sobre [icon].
 */
@Immutable
class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector? = null,
    val illustration: (@Composable (Modifier) -> Unit)? = null,
)

/**
 * Textos i18n do [OnboardingPager] (defaults pt-BR). O app injeta traduções.
 */
@Immutable
data class OnboardingTexts(
    val skip: String = "Pular",
    val next: String = "Próximo",
    val back: String = "Voltar",
    val finish: String = "Começar",
)

// --- Lógica pura (testável, sem Compose) ------------------------------------

/** É o último slide? (`total <= 0` ⇒ trata como sem próximo). */
fun onboardingIsLastPage(index: Int, total: Int): Boolean =
    total <= 0 || index >= total - 1

/** Rótulo do botão primário: [OnboardingTexts.finish] no último slide, senão [OnboardingTexts.next]. */
fun onboardingPrimaryLabel(index: Int, total: Int, texts: OnboardingTexts): String =
    if (onboardingIsLastPage(index, total)) texts.finish else texts.next

/** Mostra o "Pular" enquanto não for o último slide (nada a pular no fim). */
fun onboardingShowSkip(index: Int, total: Int): Boolean =
    !onboardingIsLastPage(index, total)

/** Próximo índice, clampado em `[0, total-1]` (nunca estoura). */
fun onboardingNextIndex(index: Int, total: Int): Int =
    (index + 1).coerceIn(0, (total - 1).coerceAtLeast(0))

/** Índice anterior, clampado em `>= 0`. */
fun onboardingPreviousIndex(index: Int): Int = (index - 1).coerceAtLeast(0)

// --- UI ----------------------------------------------------------------------

/**
 * **Carrossel de introdução (onboarding) config-driven** — a maior duplicação real do portfólio
 * (17 apps o reimplementaram à mão). Um [HorizontalPager] de [pages] com indicadores (bolinhas),
 * botão **Pular** (some no último slide), **Próximo** e, no último slide, **Começar** ([onFinish]).
 *
 * - **Tema 100% via tokens** ([MaterialTheme]/AppTheme) — a cor de destaque pode ser sobreposta por
 *   [primaryColor] (default = `colorScheme.primary`); zero hardcode.
 * - **Responsivo** via [LocalIsCompact] (paddings/tamanhos maiores no expandido).
 * - **Stateless quanto a navegação:** o app decide o que [onFinish] faz (marcar prefs + navegar).
 *   [onSkip] default = [onFinish] (pular = concluir o onboarding).
 *
 * @param pages slides (≥1).
 * @param onFinish concluído (último slide ou "Começar"). O app persiste "onboarding visto" e navega.
 * @param modifier modificador externo.
 * @param texts textos i18n.
 * @param primaryColor cor de destaque (botão/indicador ativo). `null` ⇒ `colorScheme.primary`.
 * @param onSkip ação do "Pular". `null` ⇒ usa [onFinish].
 * @param showIndicators exibe as bolinhas de posição.
 * @param showSkip exibe o "Pular" (ainda respeitando "some no último slide").
 * @param pagerState estado do pager (hoistável); default cria e lembra o seu.
 */
@Composable
fun OnboardingPager(
    pages: List<OnboardingPage>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    texts: OnboardingTexts = OnboardingTexts(),
    primaryColor: Color? = null,
    onSkip: (() -> Unit)? = null,
    showIndicators: Boolean = true,
    showSkip: Boolean = true,
    pagerState: PagerState = rememberPagerState(pageCount = { pages.size }),
) {
    if (pages.isEmpty()) return
    val accent = primaryColor ?: MaterialTheme.colorScheme.primary
    val compact = LocalIsCompact.current
    val scope = rememberCoroutineScope()
    val total = pages.size
    val current = pagerState.currentPage
    val skipAction = onSkip ?: onFinish

    Column(modifier = modifier.fillMaxSize()) {
        // Barra superior: "Pular" alinhado à direita (some no último slide).
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (showSkip && onboardingShowSkip(current, total)) {
                TextButton(
                    onClick = skipAction,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Text(text = texts.skip, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = if (compact) 24.dp else 64.dp),
        ) { pageIndex ->
            OnboardingSlide(page = pages[pageIndex], accent = accent, compact = compact)
        }

        if (showIndicators) {
            PageIndicators(
                total = total,
                current = current,
                accent = accent,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }

        // Botão primário (Próximo / Começar).
        AppButton(
            text = onboardingPrimaryLabel(current, total, texts),
            onClick = {
                if (onboardingIsLastPage(current, total)) {
                    onFinish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(onboardingNextIndex(current, total)) }
                }
            },
            modifier = Modifier.padding(horizontal = if (compact) 24.dp else 64.dp, vertical = 8.dp),
            primaryColor = accent,
        )
        Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
    }
}

@Composable
private fun OnboardingSlide(page: OnboardingPage, accent: Color, compact: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val illustrationSize = if (compact) 160.dp else 220.dp
        when {
            page.illustration != null -> page.illustration.invoke(Modifier.size(illustrationSize))
            page.icon != null -> Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(if (compact) 96.dp else 128.dp),
            )
        }
        if (page.illustration != null || page.icon != null) {
            Spacer(Modifier.height(if (compact) 32.dp else 48.dp))
        }
        Text(
            text = page.title,
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageIndicators(total: Int, current: Int, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp)
            val color by animateColorAsState(
                if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(8.dp)
                    .width(width)
                    .clip(if (selected) RoundedCornerShape(4.dp) else CircleShape)
                    .background(color),
            )
        }
    }
}
