package br.com.codecacto.kmplib.ui.components.auth

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import br.com.codecacto.kmplib.ui.components.AppTextField
import br.com.codecacto.kmplib.mask.PhoneVisualTransformation

/**
 * Campo de email pré-configurado
 */
@Composable
fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Email",
    placeholder: String = "seu@email.com",
    errorMessage: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    primaryColor: Color = Color(0xFF6C63FF),
    borderColor: Color = Color(0xFFE0E0E0),
    labelColor: Color = Color(0xFF9E9E9E)
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = Icons.Default.Email,
        keyboardType = KeyboardType.Email,
        imeAction = imeAction,
        keyboardActions = keyboardActions,
        errorMessage = errorMessage,
        enabled = enabled,
        primaryColor = primaryColor,
        borderColor = borderColor,
        labelColor = labelColor
    )
}

/**
 * Campo de senha pré-configurado
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Senha",
    placeholder: String = "••••••••",
    errorMessage: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    primaryColor: Color = Color(0xFF6C63FF),
    borderColor: Color = Color(0xFFE0E0E0),
    labelColor: Color = Color(0xFF9E9E9E)
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = Icons.Default.Lock,
        isPassword = true,
        imeAction = imeAction,
        keyboardActions = keyboardActions,
        errorMessage = errorMessage,
        enabled = enabled,
        primaryColor = primaryColor,
        borderColor = borderColor,
        labelColor = labelColor
    )
}

/**
 * Campo de nome pré-configurado
 */
@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Nome completo",
    placeholder: String = "João Silva",
    errorMessage: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    primaryColor: Color = Color(0xFF6C63FF),
    borderColor: Color = Color(0xFFE0E0E0),
    labelColor: Color = Color(0xFF9E9E9E)
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = Icons.Default.Person,
        keyboardType = KeyboardType.Text,
        imeAction = imeAction,
        keyboardActions = keyboardActions,
        errorMessage = errorMessage,
        enabled = enabled,
        primaryColor = primaryColor,
        borderColor = borderColor,
        labelColor = labelColor
    )
}

/**
 * Campo de telefone pré-configurado com máscara
 */
@Composable
fun PhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Telefone",
    placeholder: String = "(11) 98765-4321",
    errorMessage: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = PhoneVisualTransformation(),
    primaryColor: Color = Color(0xFF6C63FF),
    borderColor: Color = Color(0xFFE0E0E0),
    labelColor: Color = Color(0xFF9E9E9E)
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = Icons.Default.Phone,
        keyboardType = KeyboardType.Phone,
        imeAction = imeAction,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        errorMessage = errorMessage,
        enabled = enabled,
        primaryColor = primaryColor,
        borderColor = borderColor,
        labelColor = labelColor
    )
}
