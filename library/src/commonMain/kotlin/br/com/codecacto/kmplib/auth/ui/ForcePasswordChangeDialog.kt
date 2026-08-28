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
    title: String = "Defina a sua senha",
    description: String =
        "Sua conta foi criada com uma senha provisória. Escolha uma senha só sua para continuar.",
    confirmLabel: String = "Salvar e continuar",
) {
    var senha by remember { mutableStateOf("") }
    var confirmacao by remember { mutableStateOf("") }
    var enviado by remember { mutableStateOf(false) }

    val erroSenha = when {
        senha.isBlank() -> "A senha é obrigatória"
        senha == temporaryPassword -> "Escolha uma senha diferente da temporária"
        senha.length < minLength -> "A senha deve ter ao menos $minLength caracteres"
        else -> null
    }
    val erroConfirmacao =
        if (confirmacao.isNotEmpty() && senha != confirmacao) "As senhas não conferem" else null

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
                ?.let { "Olá, ${it.substringBefore(' ')}!" }
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
                label = "Nova senha",
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
                label = "Repita a nova senha",
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

/** Tags de teste — o E2E precisa alcançar os campos sem depender do texto exibido. */
const val TAG_NOVA_SENHA: String = "force_password_new"
const val TAG_CONFIRMACAO: String = "force_password_confirm"
const val TAG_CONFIRMAR: String = "force_password_submit"
