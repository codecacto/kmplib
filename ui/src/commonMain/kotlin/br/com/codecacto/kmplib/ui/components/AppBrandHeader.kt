package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.platform.brand.rememberAppIconPainter

/**
 * Cabeçalho de **marca do app**: o ícone (cantos arredondados) e, embaixo, o nome.
 *
 * É o que a `LoginScreen` da lib desenha no topo quando o app não passa `logo` nem título
 * (2.241.0) — até então a tela nascia só com os campos e um vazio em cima, sem dizer de qual app
 * era (Meu Estacionamento, 02/out/2026; 38 apps no mesmo estado). Público para a tela de entrada
 * ESCRITA no app (boas-vindas, login próprio sem protótipo) usar a mesma marca.
 *
 * Os dois defaults vêm do SISTEMA, sem o app repassar nada: o ícone é o do launcher
 * ([rememberAppIconPainter]) e o nome é o rótulo do app ([BuildInfo.appName]) — o que também dá o
 * nome certo por idioma e por flavor. Sem ícone, sai só o nome; sem os dois, não desenha nada.
 *
 * @param appName nome do app. Default: o rótulo que o sistema mostra.
 * @param icon ícone/logo. Default: o ícone do app.
 * @param tagline frase curta abaixo do nome (o que o app faz). Opcional.
 * @param nameColor cor do nome. Default: `onBackground` do tema.
 * @param taglineColor cor da frase. Default: `onSurfaceVariant` do tema.
 */
@Composable
fun AppBrandHeader(
    modifier: Modifier = Modifier,
    appName: String? = BuildInfo.appName,
    icon: Painter? = rememberAppIconPainter(),
    tagline: String? = null,
    nameColor: Color = MaterialTheme.colorScheme.onBackground,
    taglineColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val name = appName?.trim().orEmpty()
    if (icon == null && name.isEmpty()) return
    Column(
        modifier = modifier.testTag(AppBrandTestTags.MARCA),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Image(
                painter = icon,
                // Decorativo: o nome logo abaixo é o que o leitor de tela anuncia.
                contentDescription = if (name.isEmpty()) "Logo" else null,
                modifier = AppBrandDefaults.iconModifier,
            )
        }
        if (name.isNotEmpty()) {
            Text(
                text = name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = nameColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (!tagline.isNullOrBlank()) {
            Text(
                text = tagline,
                fontSize = 14.sp,
                color = taglineColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Medidas do cabeçalho de marca. */
object AppBrandDefaults {
    /** Lado do ícone. */
    val IconSize = 88.dp

    /** Raio dos cantos (~22% do lado — a proporção do ícone de app no iOS). */
    val IconCornerRadius = 20.dp

    /**
     * O `Modifier` do ícone: [IconSize] com os cantos arredondados. Use como `logoModifier` ao
     * passar o ícone do app como `logo` da `LoginScreen`/`RegisterScreen`.
     */
    val iconModifier: Modifier = Modifier.size(IconSize).clip(RoundedCornerShape(IconCornerRadius))
}

/** Ids de automação do cabeçalho de marca. */
object AppBrandTestTags {
    /** O bloco inteiro (ícone + nome). */
    const val MARCA: String = "app-marca"
}
