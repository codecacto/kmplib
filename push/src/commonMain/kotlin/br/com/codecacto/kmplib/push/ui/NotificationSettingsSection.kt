package br.com.codecacto.kmplib.push.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_push_marketing
import br.com.codecacto.kmplib.generated.resources.kmplib_push_settings_title
import br.com.codecacto.kmplib.platform.audience.KmpLibAudience
import br.com.codecacto.kmplib.push.central.PushMarketingPreference
import br.com.codecacto.kmplib.push.central.PushRegistration
import br.com.codecacto.kmplib.ui.components.AppSwitch
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import kotlinx.coroutines.launch

/** Textos da [NotificationSettingsSection]; default no idioma da tela (4 idiomas). */
data class NotificationSettingsTexts(
    val title: String = "Notificações",
    val marketing: String = "Notificações de publicidade",
)

@Composable
fun rememberNotificationSettingsTexts(): NotificationSettingsTexts = NotificationSettingsTexts(
    title = kmpStringResource(Res.string.kmplib_push_settings_title),
    marketing = kmpStringResource(Res.string.kmplib_push_marketing),
)

/** Ids de automação (Maestro) da seção. */
object NotificationSettingsTestTags {
    const val SECAO: String = "notificacoes-secao"
    const val PUBLICIDADE: String = "notificacoes-publicidade"
}

/**
 * Seção **"Notificações"** da tela de Configurações (2.360.0) — padrão de todo app com push: traz
 * **sempre** o interruptor **"Notificações de publicidade"** (ligado por padrão, gravado no aparelho;
 * ao mudar, o `PushRegistration.bind` re-cadastra o aparelho com o `publicidade` novo — a campanha do
 * Nexus só vai a quem deixou ligado). Mesmo visual do `ThemeModeSelector` (título `titleSmall` na cor
 * primária, linhas de 56 dp em que a LINHA inteira é o alvo).
 *
 * O app acrescenta os próprios interruptores na mesma seção pelo [content], com [NotificationSettingRow]:
 *
 * ```kotlin
 * NotificationSettingsSection {
 *     NotificationSettingRow(
 *         title = stringResource(Res.string.lembrete_diario),
 *         checked = state.lembrete,
 *         onCheckedChange = { onAction(Action.Lembrete(it)) },
 *         testTag = "notificacoes-lembrete",
 *     )
 * }
 * ```
 *
 * @param showMarketingToggle default: escondido em app infantil (`AppAudience.KIDS`), que nunca recebe
 *   publicidade (o cadastro manda `publicidade = false` sozinho).
 */
@Composable
fun NotificationSettingsSection(
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    showMarketingToggle: Boolean = !KmpLibAudience.isKids,
    marketing: PushMarketingPreference = PushRegistration.marketing,
    texts: NotificationSettingsTexts = rememberNotificationSettingsTexts(),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val enabled by marketing.enabled.collectAsState()
    val loaded by marketing.isLoaded.collectAsState()
    Column(modifier = modifier.fillMaxWidth().testTag(NotificationSettingsTestTags.SECAO)) {
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
        if (showMarketingToggle) {
            NotificationSettingRow(
                title = texts.marketing,
                checked = enabled,
                onCheckedChange = { value -> scope.launch { marketing.setEnabled(value) } },
                enabled = loaded,
                testTag = NotificationSettingsTestTags.PUBLICIDADE,
            )
        }
        content()
    }
}

/**
 * Uma linha de interruptor da seção de notificações: rótulo (+ [description] opcional) e o
 * [AppSwitch] à direita; a linha inteira é o alvo (papel `Switch` para o leitor de tela).
 */
@Composable
fun NotificationSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AppSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
