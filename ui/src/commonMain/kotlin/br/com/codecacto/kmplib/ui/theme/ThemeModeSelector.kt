package br.com.codecacto.kmplib.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_theme_dark
import br.com.codecacto.kmplib.generated.resources.kmplib_theme_light
import br.com.codecacto.kmplib.generated.resources.kmplib_theme_system
import br.com.codecacto.kmplib.generated.resources.kmplib_theme_title
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Textos do [ThemeModeSelector]. Os defaults literais são pt-BR (teste/preview); na tela use
 * [rememberThemeModeTexts], que segue o idioma do aparelho (pt-BR / pt-PT / en / es).
 */
data class ThemeModeTexts(
    val title: String = "Tema",
    val system: String = "Padrão do sistema",
    val light: String = "Claro",
    val dark: String = "Escuro",
) {
    /** O rótulo de [mode]. */
    fun labelOf(mode: ThemeMode): String = when (mode) {
        ThemeMode.SYSTEM -> system
        ThemeMode.LIGHT -> light
        ThemeMode.DARK -> dark
    }
}

/** [ThemeModeTexts] no idioma do aparelho. */
@Composable
fun rememberThemeModeTexts(): ThemeModeTexts = ThemeModeTexts(
    title = kmpStringResource(Res.string.kmplib_theme_title),
    system = kmpStringResource(Res.string.kmplib_theme_system),
    light = kmpStringResource(Res.string.kmplib_theme_light),
    dark = kmpStringResource(Res.string.kmplib_theme_dark),
)

/**
 * Ids do [ThemeModeSelector] para os flows Maestro — `tema` (o grupo) e `tema-<opção>`
 * (`tema-sistema`, `tema-claro`, `tema-escuro`). Estáveis: renomear quebra flow sem erro de build.
 */
object ThemeModeTestTags {
    const val GRUPO: String = "tema"

    /** O id da opção [mode]. */
    fun option(mode: ThemeMode): String = when (mode) {
        ThemeMode.SYSTEM -> "tema-sistema"
        ThemeMode.LIGHT -> "tema-claro"
        ThemeMode.DARK -> "tema-escuro"
    }
}

/**
 * A escolha de tema **para a tela de Configurações** — as três opções em rádio, na ordem
 * "Padrão do sistema → Claro → Escuro" (Material, *Dark theme*; Apple HIG, *Dark Mode*).
 *
 * É o ÚNICO lugar em que o app oferece trocar o tema (padrão da fábrica, fundador, 13/out/2026):
 * **não existe botão fixo de alternar claro/escuro na barra do topo em app** — isso é convenção de
 * site. O default é seguir o sistema ([ThemeMode.SYSTEM]).
 *
 * ```kotlin
 * val controller: ThemeModeController = koinInject()
 * val mode by controller.mode.collectAsState()
 * ThemeModeSelector(selected = mode, onSelect = controller::setMode)
 * ```
 *
 * Acessível: grupo com `selectableGroup`, cada linha inteira é o alvo (≥ 56 dp) com papel de
 * rádio — o leitor de tela anuncia "Escuro, botão de opção, selecionado, 3 de 3".
 *
 * @param selected o modo vigente.
 * @param onSelect chamado com a opção tocada (também quando já é a vigente — idempotente).
 * @param showTitle desenha o título "Tema" acima das opções (cabeçalho de seção). `false` quando a
 *   tela já tem o próprio cabeçalho.
 * @param texts rótulos; default no idioma do aparelho.
 */
@Composable
fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    texts: ThemeModeTexts = rememberThemeModeTexts(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showTitle) {
            Text(
                text = texts.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup().testTag(ThemeModeTestTags.GRUPO),
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(mode) },
                        )
                        .testTag(ThemeModeTestTags.option(mode))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // `onClick = null`: a LINHA é o alvo; o rádio só desenha (senão são dois alvos).
                    RadioButton(selected = isSelected, onClick = null)
                    Text(
                        text = texts.labelOf(mode),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
