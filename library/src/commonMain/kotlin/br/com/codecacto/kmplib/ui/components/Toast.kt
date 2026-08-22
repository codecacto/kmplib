package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Tipo de toast
 */
enum class ToastType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

/**
 * A **aparência** do toast.
 *
 * [PILL] é o desenho original: pílula sólida colorida com texto branco. [BANNER] desenha o
 * [AppBanner] correspondente ao tipo — o mesmo cartão informativo que as telas já usam para dizer
 * "salvo", só que flutuando e sumindo sozinho.
 *
 * A opção existe porque as duas coisas são pedidas juntas o tempo todo: *"quero um toast, mas com o
 * layout daquele cartãozinho"* (fundador, NeuroCoreX, 22/ago/2026). Sem ela, a saída era um
 * `AppBanner` no meio da coluna, que ocupa espaço permanente para uma confirmação de dois segundos —
 * ou uma pílula que não parece com o resto do produto.
 */
enum class ToastStyle {
    PILL,
    BANNER,
}

/**
 * Dados de configuração do toast
 */
data class ToastData(
    val message: String,
    val type: ToastType = ToastType.INFO,
    val duration: Long = 3000L,
    /** Um título curto acima da mensagem. Só o estilo [ToastStyle.BANNER] o desenha. */
    val title: String? = null,
)

/**
 * State holder para Toast
 */
class ToastState {
    private val _currentToast = mutableStateOf<ToastData?>(null)
    val currentToast: State<ToastData?> = _currentToast

    fun showToast(data: ToastData) {
        _currentToast.value = data
    }

    fun hideToast() {
        _currentToast.value = null
    }

    // Helpers
    fun showSuccess(message: String, duration: Long = 3000L, title: String? = null) {
        showToast(ToastData(message, ToastType.SUCCESS, duration, title))
    }

    fun showError(message: String, duration: Long = 3000L, title: String? = null) {
        showToast(ToastData(message, ToastType.ERROR, duration, title))
    }

    fun showWarning(message: String, duration: Long = 3000L, title: String? = null) {
        showToast(ToastData(message, ToastType.WARNING, duration, title))
    }

    fun showInfo(message: String, duration: Long = 3000L, title: String? = null) {
        showToast(ToastData(message, ToastType.INFO, duration, title))
    }
}

/**
 * Remember ToastState
 */
@Composable
fun rememberToastState(): ToastState {
    return remember { ToastState() }
}

/**
 * Componente de Toast
 *
 * @param toastState Estado do toast
 * @param modifier Modificador customizado
 * @param topPadding Padding do topo (para evitar sobreposição com TopBar)
 */
@Composable
fun ToastHost(
    toastState: ToastState,
    modifier: Modifier = Modifier,
    topPadding: Dp = 16.dp,
    /** [ToastStyle.PILL] (padrão, o desenho de sempre) ou [ToastStyle.BANNER]. */
    style: ToastStyle = ToastStyle.PILL,
) {
    val toast = toastState.currentToast.value

    // Auto-hide após duration
    LaunchedEffect(toast) {
        toast?.let {
            delay(it.duration)
            toastState.hideToast()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        ) {
            toast?.let {
                when (style) {
                    ToastStyle.PILL -> ToastContent(message = it.message, type = it.type)
                    // O MESMO componente das telas, com a largura contida: esticado de ponta a
                    // ponta ele deixa de parecer um aviso flutuante e passa a parecer parte da
                    // página, que é justamente o que o toast não é.
                    ToastStyle.BANNER -> AppBanner(
                        message = it.message,
                        title = it.title,
                        tone = it.type.paraTom(),
                        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
                    )
                }
            }
        }
    }
}

/** O tom do `AppBanner` equivalente — o mapa entre os dois vocabulários, num lugar só. */
private fun ToastType.paraTom(): StatusTone = when (this) {
    ToastType.SUCCESS -> StatusTone.SUCCESS
    ToastType.ERROR -> StatusTone.DANGER
    ToastType.WARNING -> StatusTone.WARNING
    ToastType.INFO -> StatusTone.INFO
}

/**
 * Conteúdo visual do Toast
 */
@Composable
private fun ToastContent(
    message: String,
    type: ToastType
) {
    val (icon, backgroundColor, iconColor) = when (type) {
        ToastType.SUCCESS -> Triple(
            Icons.Default.CheckCircle,
            Color(0xFF10B981),
            Color.White
        )
        ToastType.ERROR -> Triple(
            Icons.Default.Error,
            Color(0xFFEF4444),
            Color.White
        )
        ToastType.WARNING -> Triple(
            Icons.Default.Warning,
            Color(0xFFF59E0B),
            Color.White
        )
        ToastType.INFO -> Triple(
            Icons.Default.Info,
            Color(0xFF3B82F6),
            Color.White
        )
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Text(
            text = message,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
