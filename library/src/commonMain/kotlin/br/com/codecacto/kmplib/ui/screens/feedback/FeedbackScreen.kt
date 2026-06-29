package br.com.codecacto.kmplib.ui.screens.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.feedback.FeedbackMotivo
import br.com.codecacto.kmplib.feedback.FeedbackService
import br.com.codecacto.kmplib.feedback.FeedbackSource
import br.com.codecacto.kmplib.mask.PhoneVisualTransformation
import br.com.codecacto.kmplib.mask.filterPhoneInput
import br.com.codecacto.kmplib.validation.EmailValidator
import kotlinx.coroutines.launch

/**
 * Tela completa de feedback com formulário.
 *
 * Gerencia seu próprio estado internamente e envia o feedback
 * via [FeedbackService] (deve estar inicializado). Mensagem e WhatsApp
 * são obrigatórios para permitir retorno ao usuário.
 *
 * ```kotlin
 * FeedbackScreen(
 *     onBack = { navController.popBackStack() },
 *     primaryColor = Color(0xFF6D28D9),
 *     texts = FeedbackTexts(title = "Send Feedback")
 * )
 * ```
 *
 * O feedback é IDENTIFICADO: nome (opcional), e-mail (opcional) e WhatsApp (OBRIGATÓRIO) podem ser
 * pré-preenchidos via [defaultName]/[defaultEmail]/[defaultWhatsapp] quando o app já conhece o
 * usuário. Se vazios, o usuário digita.
 *
 * @param onBack Callback para voltar à tela anterior
 * @param primaryColor Cor primária (header, botões, radio buttons)
 * @param backgroundColor Cor de fundo do conteúdo
 * @param cardColor Cor de fundo dos cards
 * @param texts Textos customizáveis (suporta i18n)
 * @param defaultName Nome pré-preenchido (opcional; ex.: nome do usuário logado)
 * @param defaultEmail E-mail pré-preenchido (opcional; ex.: e-mail do usuário logado)
 * @param defaultWhatsapp WhatsApp pré-preenchido em dígitos (opcional; ex.: telefone do perfil)
 * @param onFeedbackSent Callback opcional chamado após envio com sucesso
 */
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    cardColor: Color = MaterialTheme.colorScheme.surface,
    texts: FeedbackTexts = FeedbackTexts(),
    defaultName: String? = null,
    defaultEmail: String? = null,
    defaultWhatsapp: String? = null,
    onFeedbackSent: (() -> Unit)? = null
) {
    var selectedMotivo by remember { mutableStateOf<FeedbackMotivo?>(null) }
    var mensagem by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf(defaultName.orEmpty()) }
    var email by remember { mutableStateOf(defaultEmail.orEmpty()) }
    var whatsapp by remember { mutableStateOf(filterPhoneInput(defaultWhatsapp.orEmpty())) }
    var isLoading by remember { mutableStateOf(false) }
    var isSent by remember { mutableStateOf(false) }
    var motivoError by remember { mutableStateOf<String?>(null) }
    var mensagemError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var whatsappError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .background(backgroundColor)
        ) {
            // Header — vai até o topo (atrás da status bar); o inset entra como padding interno,
            // uma única vez (antes havia padding duplo: paddingValues no Column + top fixo de 48dp).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(primaryColor)
                    .padding(horizontal = 16.dp)
                    .padding(top = paddingValues.calculateTopPadding() + 12.dp, bottom = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = texts.backContentDescription,
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = texts.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = texts.subtitle,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (isSent) {
                // Success Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = primaryColor
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = texts.successTitle,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = texts.successMessage,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text(
                            text = texts.continueButton,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
            // Form Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Motivo Selection Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = texts.motivoLabel,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (motivoError != null) {
                            Text(
                                text = motivoError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }

                        val motivoLabels = mapOf(
                            FeedbackMotivo.SUGESTAO to texts.motivoSugestao,
                            FeedbackMotivo.BUG to texts.motivoBug,
                            FeedbackMotivo.RECLAMACAO to texts.motivoReclamacao,
                            FeedbackMotivo.DUVIDA to texts.motivoDuvida,
                            FeedbackMotivo.ELOGIO to texts.motivoElogio,
                            FeedbackMotivo.OUTRO to texts.motivoOutro
                        )

                        FeedbackMotivo.entries.forEach { motivo ->
                            val selected = selectedMotivo == motivo
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .selectable(
                                        selected = selected,
                                        onClick = {
                                            selectedMotivo = motivo
                                            motivoError = null
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = primaryColor
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = motivoLabels[motivo] ?: motivo.label,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Mensagem TextField
                OutlinedTextField(
                    value = mensagem,
                    onValueChange = {
                        mensagem = it
                        mensagemError = null
                    },
                    label = { Text(texts.mensagemLabel) },
                    placeholder = { Text(texts.mensagemPlaceholder) },
                    isError = mensagemError != null,
                    supportingText = mensagemError?.let { error ->
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 6,
                    maxLines = 10,
                    enabled = !isLoading && !isSent
                )

                // Nome field (opcional)
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text(texts.nomeLabel) },
                    placeholder = { Text(texts.nomePlaceholder) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    enabled = !isLoading && !isSent
                )

                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                    },
                    label = { Text(texts.emailLabel) },
                    placeholder = { Text(texts.emailPlaceholder) },
                    isError = emailError != null,
                    supportingText = emailError?.let { error ->
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isLoading && !isSent
                )

                // WhatsApp field
                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = {
                        whatsapp = filterPhoneInput(it)
                        whatsappError = null
                    },
                    label = { Text(texts.whatsappLabel) },
                    placeholder = { Text(texts.whatsappPlaceholder) },
                    isError = whatsappError != null,
                    supportingText = whatsappError?.let { error ->
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    },
                    visualTransformation = PhoneVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = !isLoading && !isSent
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Submit Button
                val isFormValid = selectedMotivo != null &&
                    mensagem.trim().isNotEmpty() &&
                    (email.trim().isEmpty() || EmailValidator.isValid(email.trim())) &&
                    whatsapp.length == 11
                Button(
                    onClick = {
                        // Validate
                        if (selectedMotivo == null) {
                            motivoError = texts.motivoError
                            return@Button
                        }
                        if (mensagem.trim().isEmpty()) {
                            mensagemError = texts.mensagemError
                            return@Button
                        }
                        if (email.trim().isNotEmpty() && !EmailValidator.isValid(email.trim())) {
                            emailError = texts.emailError
                            return@Button
                        }
                        if (whatsapp.length != 11) {
                            whatsappError = texts.whatsappError
                            return@Button
                        }

                        scope.launch {
                            isLoading = true
                            FeedbackService.sendFeedback(
                                source = FeedbackSource.FEEDBACK_SCREEN,
                                motivo = selectedMotivo!!.valor,
                                mensagem = mensagem.trim(),
                                nome = nome.trim(),
                                email = email.trim(),
                                whatsapp = whatsapp.trim()
                            ).onSuccess {
                                isLoading = false
                                isSent = true
                                onFeedbackSent?.invoke()
                            }.onFailure { e ->
                                isLoading = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        e.message ?: texts.errorMessage
                                    )
                                }
                            }
                        }
                    },
                    enabled = !isLoading && !isSent && isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Feedback,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = texts.submitButton,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Cancel Button
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Text(
                        text = texts.cancelButton,
                        fontSize = 16.sp
                    )
                }
            }
            } // else (form)
        }
    }
}
