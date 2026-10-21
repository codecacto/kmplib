package br.com.codecacto.kmplib.ui.layout

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_nav_badge_count_one
import br.com.codecacto.kmplib.generated.resources.kmplib_nav_badge_count_other
import br.com.codecacto.kmplib.generated.resources.kmplib_nav_badge_count_overflow
import br.com.codecacto.kmplib.generated.resources.kmplib_nav_badge_dot
import br.com.codecacto.kmplib.ui.components.BottomNavTestTags
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Selo de um [AdaptiveDestination] (2.314.0): **contador** ([Count]) ou **ponto** ([Dot]).
 *
 * Desenhado com o `Badge`/`BadgedBox` do Material 3 sobre o ícone, nos três modos do
 * [AdaptiveScaffold] (barra inferior, rail, rail largo). Para o leitor de tela o item inteiro passa
 * a ser lido como "Alertas, 3 notificações" — o número entra na descrição, o selo visual fica mudo
 * (sem isso o TalkBack/VoiceOver leria "Alertas 3", sem dizer 3 o quê).
 */
@Immutable
sealed interface NavBadge {
    /** Descrição própria para o leitor de tela ("3 alertas abertos"); `null` = o texto padrão. */
    val contentDescription: String?

    /**
     * Contador. `count <= 0` não desenha nada (zerar o contador some com o selo); acima de [max]
     * aparece "99+".
     */
    data class Count(
        val count: Int,
        val max: Int = 99,
        override val contentDescription: String? = null,
    ) : NavBadge

    /** Ponto sem número: "tem novidade aqui". */
    data class Dot(override val contentDescription: String? = null) : NavBadge
}

/** Texto do selo de contador: `null` = não desenhar; "7"; "99+". Regra pura. */
fun navBadgeLabel(count: Int, max: Int = 99): String? = when {
    count <= 0 -> null
    count > max -> "$max+"
    else -> count.toString()
}

/** O selo aparece? (`Count` zerado não aparece). */
fun NavBadge?.isVisible(): Boolean = when (this) {
    null -> false
    is NavBadge.Dot -> true
    is NavBadge.Count -> count > 0
}

/**
 * Textos de acessibilidade do selo. Padrões com `%1$s` = rótulo do destino e `%2$d` = número.
 * Default nos 4 idiomas por [rememberAdaptiveNavBadgeTexts].
 */
@Immutable
data class AdaptiveNavBadgeTexts(
    val countOnePattern: String = "%1\$s, 1 notificação",
    val countOtherPattern: String = "%1\$s, %2\$d notificações",
    val countOverflowPattern: String = "%1\$s, mais de %2\$d notificações",
    val dotPattern: String = "%1\$s, há novidades",
) {
    /** Descrição do item com o selo; `null` quando o selo não aparece (lê-se só o rótulo). */
    fun describe(label: String, badge: NavBadge?): String? {
        if (!badge.isVisible()) return null
        badge!!.contentDescription?.let { return "$label, $it" }
        val pattern = when (badge) {
            is NavBadge.Dot -> dotPattern
            is NavBadge.Count -> when {
                badge.count > badge.max -> countOverflowPattern
                badge.count == 1 -> countOnePattern
                else -> countOtherPattern
            }
        }
        val numero = when (badge) {
            is NavBadge.Dot -> 0
            is NavBadge.Count -> if (badge.count > badge.max) badge.max else badge.count
        }
        return pattern.replace("%1\$s", label).replace("%2\$d", numero.toString())
    }
}

/** [AdaptiveNavBadgeTexts] no idioma da tela. */
@Composable
fun rememberAdaptiveNavBadgeTexts(): AdaptiveNavBadgeTexts = AdaptiveNavBadgeTexts(
    countOnePattern = kmpStringResource(Res.string.kmplib_nav_badge_count_one),
    countOtherPattern = kmpStringResource(Res.string.kmplib_nav_badge_count_other),
    countOverflowPattern = kmpStringResource(Res.string.kmplib_nav_badge_count_overflow),
    dotPattern = kmpStringResource(Res.string.kmplib_nav_badge_dot),
)

/** Ícone do destino com o selo por cima (M3 `BadgedBox`); o selo é mudo para o leitor de tela. */
@Composable
internal fun AdaptiveBadgedIcon(
    destinationId: String,
    badge: NavBadge?,
    icon: @Composable () -> Unit,
) {
    if (!badge.isVisible()) {
        icon()
        return
    }
    BadgedBox(
        badge = {
            val mudo = Modifier.clearAndSetSemantics { testTag = BottomNavTestTags.badge(destinationId) }
            when (badge) {
                is NavBadge.Dot -> Badge(modifier = mudo)
                is NavBadge.Count -> Badge(modifier = mudo) {
                    Text(navBadgeLabel(badge.count, badge.max).orEmpty())
                }
                null -> Unit
            }
        },
    ) { icon() }
}
