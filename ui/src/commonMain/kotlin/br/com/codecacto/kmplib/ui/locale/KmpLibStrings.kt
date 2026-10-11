package br.com.codecacto.kmplib.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.locale.KmpLibLocales
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.jetbrains.compose.resources.stringResource

/*
 * # O resolvedor ÚNICO dos textos da lib (2.288.0, GAP-PT-M31)
 *
 * Todo texto da kmplib passa por aqui — `kmpStringResource` no lugar de `stringResource`,
 * `kmpGetString` no lugar de `getString`. Fora de um caso, os dois só delegam ao `compose-resources`
 * (idioma do aparelho, a pasta que ele escolher). O caso: o app declarou em
 * `KmpLibLocales.configure` que NÃO traduz o idioma em que o aparelho está, e a lib traduz — aí a
 * pasta que o `compose-resources` escolheria para a lib (`values-en`) diverge da que ele escolhe para
 * o app (`values`, pt-BR), e a lib entrega a base pt-BR, tirada da tabela gerada do próprio
 * `values/strings.xml` (`kmplibBaseString`).
 *
 * Por que um resolvedor central e não "reescrever cada texto": são ~600 leituras espalhadas por 15
 * módulos, entre composables, `remember…Texts()` e os `load…Texts()` que os ViewModels chamam fora
 * da composição. Passando todas por um ponto só, a regra vale para a lib inteira — inclusive para o
 * texto que entrar amanhã — e o próximo ajuste é numa função, não em 600 lugares.
 *
 * Por que não escolher a pasta pela API do `compose-resources`: a montagem de um
 * `ResourceEnvironment` com outro idioma exige `LanguageQualifier`/`RegionQualifier`, marcados
 * `@InternalResourceApi` — a mesma família do `LocalComposeEnvironment` que a constituição proíbe
 * (quebra a cada bump, sem substituto público: CMP-8376). A tabela gerada do XML usa só API pública
 * (`StringResource.key`) e dado nosso.
 *
 * São públicas porque os módulos irmãos (`kmplib-monetization`, `kmplib-auth`, …) leem o `Res` do
 * `kmplib-ui`; o app não precisa delas — o `Res` dele é outro.
 */

/**
 * `stringResource` da lib, no idioma que o APP mostra (ver o cabeçalho do arquivo). Use no lugar de
 * `stringResource` em todo texto da kmplib.
 */
@Composable
fun kmpStringResource(resource: StringResource): String {
    if (rememberUsesBaseTexts()) kmplibBaseString(resource.key)?.let { return it }
    return stringResource(resource)
}

/** `stringResource(resource, *formatArgs)` da lib — `%1$s`/`%1$d` substituídos como no Compose. */
@Composable
fun kmpStringResource(resource: StringResource, vararg formatArgs: Any): String {
    if (rememberUsesBaseTexts()) kmplibBaseString(resource.key)?.let { return formatResourceArgs(it, formatArgs) }
    return stringResource(resource, *formatArgs)
}

/**
 * `getString` da lib — para o texto que nasce FORA da composição (`load…Texts()`, mensagem de erro
 * de compra montada no ViewModel). Mesma regra do [kmpStringResource].
 */
suspend fun kmpGetString(resource: StringResource): String {
    if (KmpLibLocales.shouldUseBaseTexts()) kmplibBaseString(resource.key)?.let { return it }
    return getString(resource)
}

/** `getString(resource, *formatArgs)` da lib. */
suspend fun kmpGetString(resource: StringResource, vararg formatArgs: Any): String {
    if (KmpLibLocales.shouldUseBaseTexts()) kmplibBaseString(resource.key)?.let { return formatResourceArgs(it, formatArgs) }
    return getString(resource, *formatArgs)
}

/**
 * A decisão de usar a base, lembrada por ambiente de recurso: o `rememberResourceEnvironment()` é o
 * mesmo gatilho com que o `stringResource` se recompõe quando o idioma muda (Android 13+ por app),
 * então a decisão acompanha — e não se refaz a cada recomposição das ~600 leituras.
 */
@Composable
private fun rememberUsesBaseTexts(): Boolean {
    val environment = rememberResourceEnvironment()
    val supported = KmpLibLocales.supported
    return remember(environment, supported) { KmpLibLocales.shouldUseBaseTexts() }
}

private val SIMPLE_FORMAT = Regex("""%(\d+)\$[ds]""")

/**
 * Substitui `%1$s`/`%2$d`… pelos [args] — a mesma regra do `compose-resources` (só a forma
 * posicional `%n$s`/`%n$d`). Índice sem argumento fica como está, em vez de derrubar a tela.
 */
internal fun formatResourceArgs(template: String, args: Array<out Any>): String =
    SIMPLE_FORMAT.replace(template) { m ->
        args.getOrNull(m.groupValues[1].toInt() - 1)?.toString() ?: m.value
    }
