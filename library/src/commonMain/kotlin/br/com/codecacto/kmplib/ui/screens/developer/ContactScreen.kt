package br.com.codecacto.kmplib.ui.screens.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.contact.ContactService
import br.com.codecacto.kmplib.mask.PhoneVisualTransformation
import br.com.codecacto.kmplib.mask.filterPhoneInput
import br.com.codecacto.kmplib.validation.EmailValidator
import kotlinx.coroutines.launch

/**
 * Formulário "Entrar em contato" reutilizável (paridade com o `ContactForm` da weblib).
 *
 * Gerencia o próprio estado e envia via [ContactService] (deve estar inicializado). Campos: nome
 * (obrigatório), e-mail (obrigatório), WhatsApp (opcional, mascarado), assunto (opcional) e mensagem
 * (obrigatória). Best-effort: falha de envio mostra snackbar e mantém o formulário.
 *
 * Normalmente aberta a partir da [DeveloperScreen] (botão "Entrar em contato"), mas é pública e pode
 * ser navegada diretamente.
 *
 * @param onBack Callback para voltar à tela anterior
 * @param primaryColor Cor primária (header e botão principal)
 * @param backgroundColor Cor de fundo do conteúdo
 * @param texts Textos customizáveis (suporta i18n)
 * @param defaultName Nome pré-preenchido (ex.: usuário logado)
 * @param defaultEmail E-mail pré-preenchido (ex.: usuário logado)
 * @param defaultWhatsapp WhatsApp pré-preenchido em dígitos (ex.: telefone do perfil)
 * @param onSent Callback opcional chamado após envio com sucesso
 */
@Composable
fun ContactScreen(
    onBack: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    texts: ContactTexts = ContactTexts(),
    defaultName: String? = null,
    defaultEmail: String? = null,
    defaultWhatsapp: String? = null,
    onSent: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(defaultName.orEmpty()) }
    var email by remember { mutableStateOf(defaultEmail.orEmpty()) }
    var whatsapp by remember { mutableStateOf(filterPhoneInput(defaultWhatsapp.orEmpty())) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSent by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf<String?>(null) }
    var whatsappError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            // Header — o inset da status bar é aplicado UMA única vez aqui (antes havia padding
            // duplo: paddingValues no Column + top fixo de 48dp, gerando uma faixa vazia no topo).
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 16.dp + paddingValues.calculateBottomPadding(),
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Nome (obrigatório)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; nameError = null },
                        label = { Text(texts.nameLabel) },
                        placeholder = { Text(texts.namePlaceholder) },
                        isError = nameError != null,
                        supportingText = nameError?.let { error ->
                            { Text(error, color = MaterialTheme.colorScheme.error) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        enabled = !isLoading
                    )

                    // E-mail (obrigatório)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; emailError = null },
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
                        enabled = !isLoading
                    )

                    // WhatsApp (opcional, mascarado)
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = filterPhoneInput(it); whatsappError = null },
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
                        enabled = !isLoading
                    )

                    // Assunto (opcional)
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text(texts.subjectLabel) },
                        placeholder = { Text(texts.subjectPlaceholder) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        enabled = !isLoading
                    )

                    // Mensagem (obrigatória)
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it; messageError = null },
                        label = { Text(texts.messageLabel) },
                        placeholder = { Text(texts.messagePlaceholder) },
                        isError = messageError != null,
                        supportingText = messageError?.let { error ->
                            { Text(error, color = MaterialTheme.colorScheme.error) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 5,
                        maxLines = 10,
                        enabled = !isLoading
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val isFormValid = name.trim().isNotEmpty() &&
                        EmailValidator.isValid(email.trim()) &&
                        message.trim().isNotEmpty() &&
                        (whatsapp.isEmpty() || whatsapp.length == 11)

                    Button(
                        onClick = {
                            if (name.trim().isEmpty()) { nameError = texts.nameError; return@Button }
                            if (!EmailValidator.isValid(email.trim())) { emailError = texts.emailError; return@Button }
                            if (whatsapp.isNotEmpty() && whatsapp.length != 11) { whatsappError = texts.whatsappError; return@Button }
                            if (message.trim().isEmpty()) { messageError = texts.messageError; return@Button }

                            scope.launch {
                                isLoading = true
                                ContactService.send(
                                    name = name.trim(),
                                    email = email.trim(),
                                    message = message.trim(),
                                    whatsapp = whatsapp.trim(),
                                    subject = subject.trim(),
                                ).onSuccess {
                                    isLoading = false
                                    isSent = true
                                    onSent?.invoke()
                                }.onFailure { e ->
                                    isLoading = false
                                    snackbarHostState.showSnackbar(e.message ?: texts.errorMessage)
                                }
                            }
                        },
                        enabled = !isLoading && isFormValid,
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
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = texts.sendButton,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        Text(text = texts.cancelButton, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
