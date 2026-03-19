package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.codecacto.kmplib.platform.getUrlLauncher
import kotlinx.coroutines.delay

/**
 * Estado interno do fluxo de avaliação.
 */
private enum class ReviewStep {
    Initial,
    Feedback,
    ThankYou
}

/**
 * Dialog de avaliação do app com fluxo completo.
 *
 * Fluxo:
 * 1. Pergunta se o usuário está gostando da experiência
 * 2. Se "Sim" → Redireciona para a loja (Play Store / App Store)
 * 3. Se "Não" → Exibe campo de texto para feedback → Tela de agradecimento
 *
 * Uso:
 * ```kotlin
 * var showReview by remember { mutableStateOf(false) }
 *
 * AppReviewDialog(
 *     show = showReview,
 *     onDismiss = { showReview = false },
 *     onFeedbackSubmit = { feedback ->
 *         // Enviar feedback para o backend
 *     },
 *     androidPackage = "com.example.app",
 *     iosAppId = "123456789"
 * )
 * ```
 *
 * @param show Se o dialog está visível
 * @param onDismiss Callback ao fechar o dialog
 * @param onFeedbackSubmit Callback com o texto do feedback negativo
 * @param androidPackage Package name do app no Android (opcional, usa o atual se null)
 * @param iosAppId ID do app na App Store (opcional)
 * @param primaryColor Cor primária dos botões
 * @param thankYouAutoDismissMs Tempo em ms para fechar automaticamente a tela de agradecimento (0 = não fecha)
 * @param title Título da pergunta inicial
 * @param subtitle Subtítulo da pergunta inicial
 * @param negativeTitle Título da tela de feedback negativo
 * @param negativeSubtitle Subtítulo da tela de feedback negativo
 * @param feedbackPlaceholder Placeholder do campo de feedback
 * @param feedbackButtonText Texto do botão de enviar feedback
 * @param thankYouTitle Título da tela de agradecimento
 * @param thankYouMessage Mensagem da tela de agradecimento
 * @param yesText Texto do botão "Sim"
 * @param noText Texto do botão "Não"
 */
@Composable
fun AppReviewDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onFeedbackSubmit: (String) -> Unit,
    androidPackage: String? = null,
    iosAppId: String? = null,
    primaryColor: Color = Color(0xFF3B82F6),
    thankYouAutoDismissMs: Long = 2500L,
    title: String = "Está gostando da experiência?",
    subtitle: String = "Sua opinião é muito importante para nós!",
    negativeTitle: String = "Que pena! \uD83D\uDE14",
    negativeSubtitle: String = "Como podemos melhorar?",
    feedbackPlaceholder: String = "Conte-nos o que poderia ser melhor...",
    feedbackButtonText: String = "Enviar Feedback",
    thankYouTitle: String = "Obrigado! \uD83C\uDF89",
    thankYouMessage: String = "Seu feedback é muito valioso para nós",
    yesText: String = "Sim",
    noText: String = "Não"
) {
    var currentStep by remember { mutableStateOf(ReviewStep.Initial) }
    var feedbackText by remember { mutableStateOf("") }

    // Reset state when dialog is shown/hidden
    LaunchedEffect(show) {
        if (!show) {
            currentStep = ReviewStep.Initial
            feedbackText = ""
        }
    }

    // Auto-dismiss after thank you
    LaunchedEffect(currentStep) {
        if (currentStep == ReviewStep.ThankYou && thankYouAutoDismissMs > 0) {
            delay(thankYouAutoDismissMs)
            onDismiss()
        }
    }

    if (show) {
        Dialog(
            onDismissRequest = {
                if (currentStep != ReviewStep.ThankYou) {
                    onDismiss()
                }
            },
            properties = DialogProperties(
                dismissOnClickOutside = currentStep != ReviewStep.ThankYou,
                dismissOnBackPress = currentStep != ReviewStep.ThankYou
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn() + slideInHorizontally { it / 3 } togetherWith
                                fadeOut() + slideOutHorizontally { -it / 3 }
                    }
                ) { step ->
                    when (step) {
                        ReviewStep.Initial -> InitialStep(
                            title = title,
                            subtitle = subtitle,
                            primaryColor = primaryColor,
                            yesText = yesText,
                            noText = noText,
                            onYes = {
                                try {
                                    val urlLauncher = getUrlLauncher()
                                    urlLauncher.openStorePage(
                                        androidPackage = androidPackage,
                                        iosAppId = iosAppId
                                    )
                                } catch (_: Exception) { }
                                onDismiss()
                            },
                            onNo = {
                                currentStep = ReviewStep.Feedback
                            }
                        )

                        ReviewStep.Feedback -> FeedbackStep(
                            title = negativeTitle,
                            subtitle = negativeSubtitle,
                            placeholder = feedbackPlaceholder,
                            buttonText = feedbackButtonText,
                            primaryColor = primaryColor,
                            feedbackText = feedbackText,
                            onFeedbackTextChange = { feedbackText = it },
                            onSubmit = {
                                onFeedbackSubmit(feedbackText)
                                currentStep = ReviewStep.ThankYou
                            }
                        )

                        ReviewStep.ThankYou -> ThankYouStep(
                            title = thankYouTitle,
                            message = thankYouMessage
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InitialStep(
    title: String,
    subtitle: String,
    primaryColor: Color,
    yesText: String,
    noText: String,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Star icon in circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = primaryColor
            )
        }

        // Title
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Subtitle
        Text(
            text = subtitle,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // "Não" button - outlined
            OutlinedButton(
                onClick = onNo,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ThumbDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = noText,
                    fontWeight = FontWeight.Medium
                )
            }

            // "Sim" button - filled
            Button(
                onClick = onYes,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ThumbUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = yesText,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FeedbackStep(
    title: String,
    subtitle: String,
    placeholder: String,
    buttonText: String,
    primaryColor: Color,
    feedbackText: String,
    onFeedbackTextChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Thumbs down icon in circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F0F0)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ThumbDown,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color(0xFF9E9E9E)
            )
        }

        // Title
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Subtitle
        Text(
            text = subtitle,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Text area
        OutlinedTextField(
            value = feedbackText,
            onValueChange = onFeedbackTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color(0xFFBDBDBD)
                )
            },
            minLines = 4,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = Color(0xFFE0E0E0)
            )
        )

        // Submit button
        val isEnabled = feedbackText.isNotBlank()
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = isEnabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFE0E0E0),
                disabledContentColor = Color(0xFF9E9E9E)
            )
        ) {
            Text(
                text = buttonText,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun ThankYouStep(
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Green star icon in circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color(0xFF4CAF50)
            )
        }

        // Title
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Message
        Text(
            text = message,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
