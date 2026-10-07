package br.com.codecacto.kmplib.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.mask.PhoneInputFormat
import br.com.codecacto.kmplib.platform.deviceRegion
import br.com.codecacto.kmplib.ui.components.FormPlaceholders
import br.com.codecacto.kmplib.generated.resources.kmplib_phone_placeholder
import br.com.codecacto.kmplib.core.locale.RegionalFormat
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_date_year_letter
import br.com.codecacto.kmplib.generated.resources.kmplib_error_network
import br.com.codecacto.kmplib.generated.resources.kmplib_error_quota_reached
import br.com.codecacto.kmplib.generated.resources.kmplib_error_rate_limited
import br.com.codecacto.kmplib.generated.resources.kmplib_error_server
import br.com.codecacto.kmplib.generated.resources.kmplib_error_session_expired
import br.com.codecacto.kmplib.generated.resources.kmplib_reauth_required
import br.com.codecacto.kmplib.generated.resources.kmplib_locale_tag
import br.com.codecacto.kmplib.sync.rest.DomainApiTexts
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/*
 * # Os textos da lib no idioma da tela, também FORA da composição
 *
 * As telas e componentes da lib leem os recursos com `stringResource` (idioma do aparelho, 4 idiomas
 * da fábrica). Mas há texto que nasce longe da tela — a mensagem de erro que o cliente HTTP monta, a
 * que o own-auth devolve, a frase da compra recusada — e ali não há composição. Para esses, cada
 * módulo expõe um `load…Texts()` `suspend` que lê os MESMOS recursos com `getString`.
 *
 * Todos caem nos defaults pt-BR da `data class` se a leitura falhar (teste de JVM sem recursos,
 * por exemplo): mensagem de erro nunca pode ser o motivo de outro erro.
 */

/**
 * O idioma da pasta de recursos que o `compose-resources` escolheu para ESTA execução — `pt-BR`,
 * `en`, `es` ou `pt-PT`. É a resposta exata de "em que idioma a tela da lib está", e o valor certo
 * para o `Accept-Language` e para o `locale` do cadastro.
 *
 * Difere de `appLanguageTag()` (`kmplib-core`) só no caso em que o app traduz um idioma que a lib não
 * traduz; ali, prefira o recurso do próprio app.
 */
suspend fun uiLanguageTag(): String =
    try {
        getString(Res.string.kmplib_locale_tag)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        br.com.codecacto.kmplib.core.locale.appLanguageTag()
    }

/**
 * [DomainApiTexts] no idioma da tela — o `textsProvider` do `DomainApiClient`:
 * ```kotlin
 * DomainApiClient(http, auth, baseUrl, textsProvider = { loadDomainApiTexts() })
 * ```
 */
suspend fun loadDomainApiTexts(): DomainApiTexts =
    try {
        val servidor = getString(Res.string.kmplib_error_server)
        DomainApiTexts(
            offline = getString(Res.string.kmplib_error_network),
            rateLimited = getString(Res.string.kmplib_error_rate_limited),
            sessionExpired = getString(Res.string.kmplib_error_session_expired),
            reauthRequired = getString(Res.string.kmplib_reauth_required),
            quotaReached = getString(Res.string.kmplib_error_quota_reached),
            serverError = { codigo -> formatStatusTemplate(servidor, codigo) },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        DomainApiTexts()
    }

/**
 * Placeholder de FORMATO do campo de data, na ordem da região e com a letra de "ano" do idioma da
 * tela: `dd/mm/aaaa` (BR), `mm/dd/yyyy` (US), `dd.mm.yyyy` (DE). É o default do `AppDatePicker`.
 */
@Composable
fun rememberDatePlaceholder(): String =
    RegionalFormat.datePlaceholder(stringResource(Res.string.kmplib_date_year_letter).firstOrNull() ?: 'y')

/**
 * O [PhoneInputFormat] da **região do aparelho** — o brasileiro de sempre no Brasil (e quando a
 * região é desconhecida), o internacional E.164 fora dele. É o default dos campos de WhatsApp da
 * `FeedbackScreen` e da `ContactScreen`.
 */
@Composable
fun rememberDevicePhoneInputFormat(): PhoneInputFormat = remember { PhoneInputFormat.forRegion(deviceRegion()) }

/**
 * Placeholder do campo de telefone para [format]: o FORMATO brasileiro (`(00) 00000-0000`) no modo
 * BR; fora dele, a instrução "Digite seu telefone" no idioma do aparelho — formato internacional
 * varia por país, e placeholder nunca é número de exemplo.
 */
@Composable
fun rememberPhonePlaceholder(format: PhoneInputFormat): String =
    if (format.isBrazilian) FormPlaceholders.PHONE else stringResource(Res.string.kmplib_phone_placeholder)

/**
 * Substitui o `%1$d` de um modelo lido **sem** argumento (o `getString`/`stringResource` sem args
 * devolve o modelo cru). Público para os módulos irmãos da lib; o app não precisa dele.
 */
fun formatStatusTemplate(template: String, value: Int): String = template.replace("%1\$d", value.toString())

/** Substitui `%1$d` e `%2$d` de um modelo lido sem argumento ("3 de 12"). */
fun formatTwoNumberTemplate(template: String, first: Int, second: Int): String =
    template.replace("%1\$d", first.toString()).replace("%2\$d", second.toString())
