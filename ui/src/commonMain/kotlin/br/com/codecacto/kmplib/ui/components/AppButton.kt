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
import androidx.compose.ui.text.style.TextAlign
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
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            // ALTURA MÍNIMA, não fixa (2.142.0). Com `.height()` o botão recusava crescer e o texto
            // de duas linhas era CORTADO no meio da segunda — sem aviso, sem erro, e só em quem
            // tinha rótulo longo ou tela estreita. Botão de meia largura numa `Row` é o caso comum,
            // e era exatamente onde o corte aparecia. Rótulo de uma linha continua nos 56.dp.
            //
            // Vale para os cinco botões deste arquivo: o defeito era do padrão, não de um deles.
            .heightIn(min = height),
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
                fontWeight = FontWeight.Medium,
                // Quebrando em duas linhas, sem isto a segunda alinha à ESQUERDA e o rótulo fica
                // torto dentro de um botão que é simétrico.
                textAlign = TextAlign.Center
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
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    borderWidth: Dp = 1.dp
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height),
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
                fontWeight = FontWeight.Medium,
                // Quebrando em duas linhas, sem isto a segunda alinha à ESQUERDA e o rótulo fica
                // torto dentro de um botão que é simétrico.
                textAlign = TextAlign.Center
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
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    borderWidth: Dp = 1.dp
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height),
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
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    borderWidth: Dp = 1.dp
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height),
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

/**
 * Botão secundário menor com borda sutil e texto em cor secundária.
 *
 * @param text Texto do botão
 * @param onClick Callback de clique
 * @param modifier Modificador customizado
 * @param enabled Se o botão está habilitado
 * @param isLoading Se está em estado de loading
 * @param height Altura do botão
 * @param fontSize Tamanho da fonte
 * @param borderColor Cor da borda
 * @param contentColor Cor do conteúdo (texto/ícone)
 */
@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    height: Dp = 48.dp,
    fontSize: TextUnit = 14.sp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                // Quebrando em duas linhas, sem isto a segunda alinha à ESQUERDA e o rótulo fica
                // torto dentro de um botão que é simétrico.
                textAlign = TextAlign.Center
            )
        }
    }
}
