package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.generated.resources.*
import org.jetbrains.compose.resources.painterResource

/**
 * Botão primário customizado com estado de loading
 *
 * @param text Texto do botão
 * @param onClick Callback de clique
 * @param modifier Modificador customizado
 * @param isLoading Se está em estado de loading
 * @param enabled Se o botão está habilitado
 * @param height Altura do botão
 * @param fontSize Tamanho da fonte
 * @param primaryColor Cor primária do botão
 * @param contentColor Cor do conteúdo (texto/ícone)
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    fontSize: TextUnit = 16.sp,
    primaryColor: Color = Color(0xFF6C63FF),
    contentColor: Color = Color.White
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryColor,
            contentColor = contentColor,
            disabledContainerColor = primaryColor.copy(alpha = 0.6f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Botão outlined (secundário) customizado com estado de loading
 *
 * @param text Texto do botão
 * @param onClick Callback de clique
 * @param modifier Modificador customizado
 * @param isLoading Se está em estado de loading
 * @param enabled Se o botão está habilitado
 * @param height Altura do botão
 * @param fontSize Tamanho da fonte
 * @param primaryColor Cor da borda e do conteúdo
 * @param borderWidth Largura da borda
 */
@Composable
fun AppOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    fontSize: TextUnit = 16.sp,
    primaryColor: Color = Color(0xFF6C63FF),
    borderWidth: Dp = 1.dp
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(borderWidth, primaryColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = primaryColor
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = primaryColor,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Row com ícone e texto para botões sociais
 */
@Composable
fun RowScope.ButtonContent(
    icon: @Composable () -> Unit,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Text(text, fontWeight = FontWeight.Medium)
    }
}

/**
 * Botão de login com Google (ícone fixo)
 *
 * @param text Texto do botão
 * @param onClick Callback de clique
 * @param modifier Modificador customizado
 * @param isLoading Se está em estado de loading
 * @param enabled Se o botão está habilitado
 * @param height Altura do botão
 * @param fontSize Tamanho da fonte
 * @param primaryColor Cor da borda e do conteúdo
 * @param borderWidth Largura da borda
 */
@Composable
fun GoogleLoginButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    fontSize: TextUnit = 16.sp,
    primaryColor: Color = Color(0xFF6C63FF),
    borderWidth: Dp = 1.dp
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(borderWidth, primaryColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = primaryColor
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = primaryColor,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_google),
                    contentDescription = "Google",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified // Mantém cores originais do ícone
                )
                Text(
                    text = text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Botão de login com Apple (ícone fixo)
 *
 * @param text Texto do botão
 * @param onClick Callback de clique
 * @param modifier Modificador customizado
 * @param isLoading Se está em estado de loading
 * @param enabled Se o botão está habilitado
 * @param height Altura do botão
 * @param fontSize Tamanho da fonte
 * @param primaryColor Cor da borda e do conteúdo
 * @param borderWidth Largura da borda
 */
@Composable
fun AppleLoginButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    fontSize: TextUnit = 16.sp,
    primaryColor: Color = Color(0xFF6C63FF),
    borderWidth: Dp = 1.dp
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(borderWidth, primaryColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = primaryColor
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = primaryColor,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_apple),
                    contentDescription = "Apple",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified // Mantém cores originais do ícone
                )
                Text(
                    text = text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
