package br.com.codecacto.kmplib.familia

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.core.locale.KmpLibLocales
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Texto do kit no idioma que o APP mostra: app BR (`KmpLibLocales.configure(listOf(PT_BR))`) em aparelho
 * em inglês recebe a base pt-BR, como as telas da lib (`kmpStringResource`). App GLOBAL: o do aparelho.
 */
@Composable
internal fun familiaString(resource: StringResource, vararg args: Any): String {
    if (KmpLibLocales.shouldUseBaseTexts()) familiaBaseString(resource.key)?.let { return format(it, args) }
    return if (args.isEmpty()) stringResource(resource) else stringResource(resource, *args)
}

/** O mesmo, fora de composição. */
internal suspend fun familiaGetString(resource: StringResource, vararg args: Any): String {
    if (KmpLibLocales.shouldUseBaseTexts()) familiaBaseString(resource.key)?.let { return format(it, args) }
    return if (args.isEmpty()) getString(resource) else getString(resource, *args)
}

private val ARG = Regex("""%(\d+)\$[ds]""")

private fun format(template: String, args: Array<out Any>): String =
    ARG.replace(template) { m -> args.getOrNull(m.groupValues[1].toInt() - 1)?.toString() ?: m.value }
