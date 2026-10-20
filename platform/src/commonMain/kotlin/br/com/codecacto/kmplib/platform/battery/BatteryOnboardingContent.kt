package br.com.codecacto.kmplib.platform.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.codecacto.kmplib.core.locale.appLanguageTag
import br.com.codecacto.kmplib.core.util.BuildInfo

/** Ids estáveis do `BatteryOnboardingContent` para o Maestro (2.308.0). */
object BatteryOnboardingTestTags {
    const val CONTEUDO: String = "bateria-onboarding"
    const val TITULO: String = "bateria-onboarding-titulo"
    const val BTN_PERMITIR: String = "bateria-onboarding-btn-permitir"
    const val PERMITIDO: String = "bateria-onboarding-permitido"
    const val BTN_CONFIGURACOES: String = "bateria-onboarding-btn-configuracoes"
    const val BTN_CONCLUIR: String = "bateria-onboarding-btn-concluir"
}

/**
 * [BatteryOnboardingTexts] no idioma da tela (`appLanguageTag()`), com o nome do app do launcher.
 */
@Composable
fun rememberBatteryOnboardingTexts(
    appName: String? = remember { BuildInfo.appName },
): BatteryOnboardingTexts {
    val tag = remember { appLanguageTag() }
    return remember(tag, appName) { BatteryOnboardingTexts.forLanguage(tag, appName) }
}

/**
 * As instruções de bateria do fabricante deste aparelho (2.308.0) — o miolo; quem chama decide a
 * moldura (tela de onboarding, `AppDialog`, folha).
 *
 * - Título, introdução e os passos do menu do fabricante (HyperOS/MIUI, One UI, Motorola, EMUI/MagicOS;
 *   genérico nos demais Android).
 * - **Permitir em segundo plano**: pedido de isenção de otimização de bateria do sistema
 *   ([openBatteryOptimizationRequest]). Ao voltar (`ON_RESUME`) o estado é relido, e o botão vira
 *   "Uso em segundo plano permitido" quando a isenção foi concedida.
 * - **Abrir configurações do app**: onde ficam os ajustes do fabricante ([openAppBatterySettings]).
 * - **Concluir** → [onDismiss].
 *
 * iOS: não desenha nada (não há restrição de fabricante). Mostre-o quando [needsBatteryOnboarding]
 * for `true` — ou num item de Ajuda, a qualquer momento.
 */
@Composable
fun BatteryOnboardingContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    texts: BatteryOnboardingTexts = rememberBatteryOnboardingTexts(),
) {
    val vendor = remember { deviceBatteryVendor() } ?: return
    var exempt by remember { mutableStateOf(isIgnoringBatteryOptimizations() == true) }
    LifecycleResumeEffect(Unit) {
        exempt = isIgnoringBatteryOptimizations() == true
        onPauseOrDispose { }
    }

    Column(
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth().testTag(BatteryOnboardingTestTags.CONTEUDO),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.BatteryChargingFull,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = texts.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(BatteryOnboardingTestTags.TITULO).semantics { heading() },
        )
        Text(
            text = texts.intro,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = texts.vendorHeading(vendor),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        texts.steps(vendor).forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp),
                )
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        if (exempt) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 48.dp).testTag(BatteryOnboardingTestTags.PERMITIDO),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = texts.exemptionGranted, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Button(
                onClick = { openBatteryOptimizationRequest() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(BatteryOnboardingTestTags.BTN_PERMITIR),
            ) { Text(texts.requestExemption) }
        }
        OutlinedButton(
            onClick = { openAppBatterySettings() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(BatteryOnboardingTestTags.BTN_CONFIGURACOES),
        ) { Text(texts.openAppSettings) }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag(BatteryOnboardingTestTags.BTN_CONCLUIR),
        ) { Text(texts.dismiss) }
    }
}
