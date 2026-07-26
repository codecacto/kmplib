package br.com.codecacto.kmplib.ui.components.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Link "Esqueci minha senha"
 */
@Composable
fun ForgotPasswordLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Esqueci minha senha",
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(0.dp),
            enabled = enabled
        ) {
            Text(
                text = text,
                color = color,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Links de navegação entre Login/Registro
 */
@Composable
fun AuthNavigationLink(
    promptText: String,
    linkText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    promptColor: Color = Color(0xFF757575),
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = promptText,
            fontSize = 14.sp,
            color = promptColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(0.dp),
            enabled = enabled
        ) {
            Text(
                text = linkText,
                color = linkColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Checkbox de aceitar termos e política
 */
@Composable
fun TermsCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    termsText: String = "Termos de Uso",
    privacyText: String = "Política de Privacidade",
    prefixText: String = "Eu aceito os ",
    andText: String = " e ",
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = Color(0xFF757575)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = primaryColor
            )
        )
        Column(
            modifier = Modifier.padding(start = 8.dp, top = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prefixText,
                    fontSize = 14.sp,
                    color = textColor
                )
                TextButton(
                    onClick = onTermsClick,
                    contentPadding = PaddingValues(0.dp),
                    enabled = enabled
                ) {
                    Text(
                        text = termsText,
                        fontSize = 14.sp,
                        color = primaryColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = andText,
                    fontSize = 14.sp,
                    color = textColor
                )
                TextButton(
                    onClick = onPrivacyClick,
                    contentPadding = PaddingValues(0.dp),
                    enabled = enabled
                ) {
                    Text(
                        text = privacyText,
                        fontSize = 14.sp,
                        color = primaryColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Divider com texto no centro (para "ou continue com")
 */
@Composable
fun OrDivider(
    text: String = "ou continue com",
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE0E0E0),
    textColor: Color = Color(0xFF757575)
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = color
        )
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 12.sp,
            color = textColor
        )
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = color
        )
    }
}
