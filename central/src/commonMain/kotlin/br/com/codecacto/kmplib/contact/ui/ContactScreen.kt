package br.com.codecacto.kmplib.ui.screens.developer

import br.com.codecacto.kmplib.ui.screens.feedback.isCompleteWhatsapp
import br.com.codecacto.kmplib.mask.PhoneInputFormat
import br.com.codecacto.kmplib.ui.locale.rememberDevicePhoneInputFormat
import androidx.compose.foundation.background
import br.com.codecacto.kmplib.ui.components.appKeyboardOptions
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
import br.com.codecacto.kmplib.contact.contactInitialMessage
import br.com.codecacto.kmplib.contact.contactInitialSubject
import br.com.codecacto.kmplib.contact.contactSubjectOptions
import br.com.codecacto.kmplib.ui.components.AppDropdownField
import br.com.codecacto.kmplib.ui.components.PickerOption
import br.com.codecacto.kmplib.ui.screens.espacoAcimaDoRodape
import br.com.codecacto.kmplib.validation.EmailValidator
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import br.com.codecacto.kmplib.platform.automation.TopBarTestTags

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
 * @param bottomBar Rodapé fixo, opcional — o lugar do banner de house ad. Vai direto para o
 *   `bottomBar` do `Scaffold` interno, e o conteúdo já desconta a altura dele (o botão de enviar
 *   termina COLADO ao topo do rodapé, nunca por baixo). Vazio por default: quem não passa nada
 *   continua exatamente como antes. A [DeveloperScreen] repassa o dela para cá.
 * @param initialSubject Assunto com que a tela ABRE (2.226.0) — editável, vai no campo `subject`
 *   do `POST /contact/v1`. Ex.: "Erro em dado de candidato" num "Informar erro" contextual.
 * @param initialMessage Texto inicial da mensagem (2.226.0) — editável, a pessoa completa ou
 *   apaga. Não é aparado: termine em `"\n\n"` para o cursor cair embaixo do contexto. Só dado que
 *   a pessoa pode ver e mandar — nunca dado privado dela que ela não escolheu enviar.
 * @param subjects Assuntos escolhíveis (2.226.0). Com lista, o campo de assunto vira um seletor
 *   (`AppDropdownField`) em vez de texto livre; `null`/vazia = texto livre, como antes. Um
 *   [initialSubject] fora da lista entra no topo dela (nunca some em silêncio). Continua opcional:
 *   sem escolha, o envio sai sem assunto.
 * @param onSent Callback opcional chamado após envio com sucesso
 */
@Composable
fun ContactScreen(
    onBack: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    texts: ContactTexts? = null,
    defaultName: String? = null,
    defaultEmail: String? = null,
    defaultWhatsapp: String? = null,
    bottomBar: @Composable () -> Unit = {},
    /**
     * Formato do WhatsApp (2.219.0). Default: o da região do aparelho — brasileiro no Brasil,
     * internacional E.164 fora dele (é como o número é enviado).
     */
    phoneFormat: PhoneInputFormat = rememberDevicePhoneInputFormat(),
    initialSubject: String? = null,
    initialMessage: String? = null,
    subjects: List<String>? = null,
    onSent: (() -> Unit)? = null,
) {
    @Suppress("NAME_SHADOWING")
    val texts: ContactTexts = texts ?: rememberContactTexts(phoneFormat)
    var name by remember { mutableStateOf(defaultName.orEmpty()) }
    var email by remember { mutableStateOf(defaultEmail.orEmpty()) }
    var whatsapp by remember { mutableStateOf(phoneFormat.fromStoredValue(defaultWhatsapp)) }
    var subject by remember { mutableStateOf(contactInitialSubject(initialSubject)) }
    var message by remember { mutableStateOf(contactInitialMessage(initialMessage)) }
    val subjectOptions = remember(subjects, initialSubject) {
        contactSubjectOptions(subjects, initialSubject)?.map { PickerOption(value = it, label = it) }
    }
    var isLoading by remember { mutableStateOf(false) }
    var isSent by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var messageError by remember { mutableStateOf<String?>(null) }
    var whatsappError by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // A folga do rodapé entra AQUI, no contêiner, e não só no formulário: a tela de
                // sucesso ("mensagem enviada") também é conteúdo, e sem isto o botão "Continuar"
                // dela nascia por baixo do banner.
                .padding(bottom = espacoAcimaDoRodape(paddingValues))
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TopBarTestTags.VOLTAR)) {
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
                        .padding(16.dp),
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
                        keyboardOptions = appKeyboardOptions(keyboardType = KeyboardType.Text),
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
                        keyboardOptions = appKeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !isLoading
                    )

                    // WhatsApp (opcional, mascarado)
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = phoneFormat.filter(it); whatsappError = null },
                        label = { Text(texts.whatsappLabel) },
                        placeholder = { Text(texts.whatsappPlaceholder) },
                        isError = whatsappError != null,
                        supportingText = whatsappError?.let { error ->
                            { Text(error, color = MaterialTheme.colorScheme.error) }
                        },
                        visualTransformation = phoneFormat.visualTransformation,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        keyboardOptions = appKeyboardOptions(keyboardType = KeyboardType.Phone),
                        enabled = !isLoading
                    )

                    // Assunto (opcional) — seletor quando o app passa `subjects`, texto livre senão.
                    if (subjectOptions != null) {
                        AppDropdownField(
                            value = subject,
                            onValueChange = { subject = it },
                            options = subjectOptions,
                            label = texts.subjectLabel,
                            placeholder = texts.subjectPlaceholder,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        )
                    } else {
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
                    }

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
                        (whatsapp.isEmpty() || phoneFormat.isCompleteWhatsapp(whatsapp))

                    Button(
                        onClick = {
                            if (name.trim().isEmpty()) { nameError = texts.nameError; return@Button }
                            if (!EmailValidator.isValid(email.trim())) { emailError = texts.emailError; return@Button }
                            if (whatsapp.isNotEmpty() && !phoneFormat.isCompleteWhatsapp(whatsapp)) { whatsappError = texts.whatsappError; return@Button }
                            if (message.trim().isEmpty()) { messageError = texts.messageError; return@Button }

                            scope.launch {
                                isLoading = true
                                ContactService.send(
                                    name = name.trim(),
                                    email = email.trim(),
                                    message = message.trim(),
                                    whatsapp = if (whatsapp.isEmpty()) "" else phoneFormat.toSubmitValue(whatsapp),
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
