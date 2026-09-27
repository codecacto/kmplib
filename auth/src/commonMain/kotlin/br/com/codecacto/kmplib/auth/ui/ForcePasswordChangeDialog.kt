package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.auth.TEMPORARY_PASSWORD
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_first_access_title
import br.com.codecacto.kmplib.generated.resources.kmplib_first_access_description
import br.com.codecacto.kmplib.generated.resources.kmplib_first_access_confirm
import br.com.codecacto.kmplib.generated.resources.kmplib_first_access_greeting
import br.com.codecacto.kmplib.generated.resources.kmplib_first_access_new_password
import br.com.codecacto.kmplib.generated.resources.kmplib_first_access_repeat_password
import br.com.codecacto.kmplib.generated.resources.kmplib_password_required
import br.com.codecacto.kmplib.generated.resources.kmplib_password_same_as_temporary
import br.com.codecacto.kmplib.generated.resources.kmplib_password_min_length
import br.com.codecacto.kmplib.generated.resources.kmplib_password_mismatch
import org.jetbrains.compose.resources.stringResource

/**
 * **Primeiro acesso: o diálogo que não fecha.**
 *
 * A conta foi criada por um administrador com a senha temporária da fábrica, e o titular precisa
 * escolher a dele antes de usar o app. Sem botão de voltar, sem toque fora, sem X.
 *
 * ## Isto é conveniência de UI, não a trava
 *
 * Quem garante a obrigação é o **servidor**: enquanto a senha for a temporária, o access token
 * carrega uma claim e o backend recusa toda rota do produto com `403 PASSWORD_CHANGE_REQUIRED`. Este
 * diálogo existe para a pessoa entender o que fazer — sem ele, ela veria erros sem explicação. Nunca
 * o trate como a segurança do fluxo: o app pode ser modificado e a API pode ser chamada direto.
 *
 * ## Guardar os tokens novos é obrigação de quem chama
 *
 * [onConfirm] deve chamar `OwnAuthApi.firstAccessPasswordChange`, que responde com **tokens novos** —
 * a troca revoga todas as sessões, e o par antigo morre no mesmo instante. Sem substituí-lo no
 * `AuthSessionStore`, a pessoa define a senha e é jogada para a tela de login no toque seguinte, o
 * que lê exatamente como falha.
 *
 * @param minLength mínimo exigido pelo backend (`AuthLocalConfig.minPasswordLength`).
 * @param temporaryPassword para recusar a repetição de imediato, quando o mínimo do projeto a
 *   permitiria.
 * @param errorMessage mensagem devolvida pelo servidor na última tentativa; some quando a pessoa
 *   digita de novo (quem controla é o chamador).
 * @param texts rótulos, saudação e erros de campo (2.219.0). Default: idioma do aparelho.
 *   [title]/[description]/[confirmLabel] seguem como parâmetros próprios, como antes.
 */
@Composable
fun ForcePasswordChangeDialog(
    show: Boolean,
    onConfirm: (newPassword: String) -> Unit,
    modifier: Modifier = Modifier,
    userName: String? = null,
    minLength: Int = 8,
    temporaryPassword: String = TEMPORARY_PASSWORD,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    title: String = stringResource(Res.string.kmplib_first_access_title),
    description: String = stringResource(Res.string.kmplib_first_access_description),
    confirmLabel: String = stringResource(Res.string.kmplib_first_access_confirm),
    texts: ForcePasswordChangeTexts = rememberForcePasswordChangeTexts(),
) {
    var senha by remember { mutableStateOf("") }
    var confirmacao by remember { mutableStateOf("") }
    var enviado by remember { mutableStateOf(false) }

    val erroSenha = when {
        senha.isBlank() -> texts.passwordRequired
        senha == temporaryPassword -> texts.sameAsTemporary
        senha.length < minLength -> texts.minLength(minLength)
        else -> null
    }
    val erroConfirmacao =
        if (confirmacao.isNotEmpty() && senha != confirmacao) texts.passwordMismatch else null

    AppDialog(
        show = show,
        // Não há para onde ir: o `onDismiss` do Compose ainda é chamado em alguns caminhos de
        // sistema, e ignorá-lo é o que mantém a obrigação de pé.
        onDismiss = {},
        modifier = modifier,
        // As duas saídas do Compose, fechadas de propósito. Vêm ABERTAS por default, e qualquer uma
        // delas transformaria "obrigatório" em sugestão — a pessoa ficaria num app cujas telas todas
        // respondem 403, sem nada explicando o motivo.
        dismissOnClickOutside = false,
        dismissOnBackPress = false,
    ) {
        Text(
            text = userName?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { texts.greeting(it.substringBefore(' ')) }
                ?: title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = senha,
                onValueChange = { senha = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = TAG_NOVA_SENHA },
                label = texts.newPasswordLabel,
                isPassword = true,
                imeAction = ImeAction.Next,
                // Vermelho só DEPOIS do envio — marcar enquanto a pessoa digita é ruído.
                errorMessage = if (enviado) erroSenha else null,
            )
            AppTextField(
                value = confirmacao,
                onValueChange = { confirmacao = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = TAG_CONFIRMACAO },
                label = texts.repeatPasswordLabel,
                isPassword = true,
                imeAction = ImeAction.Done,
                errorMessage = if (enviado) erroConfirmacao else null,
            )
        }

        // Junto do botão, onde o olho já está depois do toque — nunca no alto do cartão.
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Button(
            onClick = {
                enviado = true
                if (erroSenha == null && erroConfirmacao == null && confirmacao.isNotEmpty()) {
                    onConfirm(senha)
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = TAG_CONFIRMAR },
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxWidth(0.06f),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(confirmLabel)
            }
        }
    }
}

/**
 * Textos do [ForcePasswordChangeDialog] além de título/descrição/botão (2.219.0 — antes, pt-BR fixo).
 * Os defaults literais são pt-BR; na tela use [rememberForcePasswordChangeTexts].
 */
data class ForcePasswordChangeTexts(
    val newPasswordLabel: String = "Nova senha",
    val repeatPasswordLabel: String = "Repita a nova senha",
    /** Título quando há nome: recebe o PRIMEIRO nome. */
    val greeting: (firstName: String) -> String = { nome -> "Olá, $nome!" },
    val passwordRequired: String = "A senha é obrigatória",
    val sameAsTemporary: String = "Escolha uma senha diferente da temporária",
    val minLength: (min: Int) -> String = { min -> "A senha deve ter ao menos $min caracteres" },
    val passwordMismatch: String = "As senhas não conferem",
)

/** [ForcePasswordChangeTexts] no idioma do aparelho (pt-BR, en, es, pt-PT). */
@Composable
fun rememberForcePasswordChangeTexts(): ForcePasswordChangeTexts {
    // Modelos lidos sem argumento (com `%1$s`/`%1$d`) e preenchidos no lambda.
    val saudacao = stringResource(Res.string.kmplib_first_access_greeting)
    val minimo = stringResource(Res.string.kmplib_password_min_length)
    return ForcePasswordChangeTexts(
        newPasswordLabel = stringResource(Res.string.kmplib_first_access_new_password),
        repeatPasswordLabel = stringResource(Res.string.kmplib_first_access_repeat_password),
        greeting = { nome -> saudacao.replace("%1\$s", nome) },
        passwordRequired = stringResource(Res.string.kmplib_password_required),
        sameAsTemporary = stringResource(Res.string.kmplib_password_same_as_temporary),
        minLength = { min -> minimo.replace("%1\$d", min.toString()) },
        passwordMismatch = stringResource(Res.string.kmplib_password_mismatch),
    )
}

/** Tags de teste — o E2E precisa alcançar os campos sem depender do texto exibido. */
const val TAG_NOVA_SENHA: String = "force_password_new"
const val TAG_CONFIRMACAO: String = "force_password_confirm"
const val TAG_CONFIRMAR: String = "force_password_submit"
