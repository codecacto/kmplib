package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_digitbox_description
import br.com.codecacto.kmplib.generated.resources.kmplib_digitbox_missing_one
import br.com.codecacto.kmplib.generated.resources.kmplib_digitbox_missing_other
import br.com.codecacto.kmplib.generated.resources.kmplib_digitbox_state
import br.com.codecacto.kmplib.generated.resources.kmplib_digitbox_too_many
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------------------------
// Textos
// ---------------------------------------------------------------------------------------------

/**
 * Textos do [DigitBoxField]. Default [rememberDigitBoxTexts] nos 4 idiomas da lib; para trocar um,
 * `.copy(…)`.
 *
 * @param missing "Faltam 2 dígitos" — a mensagem que o APP passa em `errorMessage` depois de um
 *   envio com o campo incompleto (o campo não se marca sozinho enquanto a pessoa digita).
 * @param tooMany "Este campo aceita 4 dígitos; o número colado tem 5" — mostrada pelo próprio campo.
 * @param description "Campo de 4 dígitos" — anunciado pelo [DigitBoxDisplay] sem `label`.
 * @param state "2 de 4 dígitos preenchidos" — estado anunciado pelo leitor de tela.
 */
@Immutable
data class DigitBoxTexts(
    val missing: (missing: Int) -> String,
    val tooMany: (length: Int, pasted: Int) -> String,
    val description: (length: Int) -> String,
    val state: (filled: Int, length: Int) -> String,
)

@Composable
fun rememberDigitBoxTexts(): DigitBoxTexts {
    val one = stringResource(Res.string.kmplib_digitbox_missing_one)
    val other = stringResource(Res.string.kmplib_digitbox_missing_other)
    val tooMany = stringResource(Res.string.kmplib_digitbox_too_many)
    val description = stringResource(Res.string.kmplib_digitbox_description)
    val state = stringResource(Res.string.kmplib_digitbox_state)
    return remember(one, other, tooMany, description, state) {
        DigitBoxTexts(
            missing = { n -> if (n == 1) one else formatDigitBoxTemplate(other, n) },
            tooMany = { length, pasted -> formatDigitBoxTemplate(tooMany, length, pasted) },
            description = { length -> formatDigitBoxTemplate(description, length) },
            state = { filled, length -> formatDigitBoxTemplate(state, filled, length) },
        )
    }
}

/** Medidas padrão das caixas. */
object DigitBoxFieldDefaults {
    /** Largura de cada caixa — encolhe sozinha até [MinBoxWidth] quando a linha não cabe. */
    val BoxWidth: Dp = 48.dp
    val BoxHeight: Dp = 56.dp
    val Spacing: Dp = 8.dp
    val MinBoxWidth: Dp = 28.dp

    /** Teto de caixas: acima disto o campo deixa de ser legível numa linha de telefone. */
    const val MAX_LENGTH: Int = 12
}

// ---------------------------------------------------------------------------------------------
// Campo editável
// ---------------------------------------------------------------------------------------------

/**
 * Campo de **N caixas de dígito** — número de candidato, PIN, código de confirmação, cupom.
 *
 * Por baixo é UM campo de texto só (o `BasicTextField` oficial, com as caixas desenhadas no
 * `decorationBox`), não N campos com foco pulando de um para o outro. Por isso vêm de graça: o
 * teclado numérico, **colar** o número inteiro, apagar voltando a caixa, o gesto de toque em
 * qualquer ponto da linha (o alvo de toque é a linha inteira, não uma caixa de 48 dp) e um único nó
 * para o leitor de tela, que lê **o número inteiro, o rótulo e "2 de 4 dígitos preenchidos"**.
 *
 * **Erro no campo:**
 * - [errorMessage] do app vence — incompleto depois do envio (`texts.missing(n)`), número já usado
 *   em outro cargo ("duplicado"), inexistente. Borda vermelha nas caixas, frase embaixo e
 *   `error()` na semântica.
 * - **Excedente** o campo trata sozinho: colar mais algarismos do que cabe **não corta** (trocaria o
 *   número por outro que a pessoa não escreveu); o valor anterior fica e aparece
 *   [DigitBoxTexts.tooMany] até a próxima edição. Idem se [value] chegar com algarismos demais.
 *
 * ```kotlin
 * var numero by remember { mutableStateOf("") }
 * var tentouSalvar by remember { mutableStateOf(false) }
 * val texts = rememberDigitBoxTexts()
 * DigitBoxField(
 *     value = numero,
 *     onValueChange = { numero = it },
 *     length = 4,
 *     label = "Deputado federal",
 *     autoFocus = true,
 *     errorMessage = when {
 *         jaUsado(numero) -> "Número já usado em outro cargo"
 *         tentouSalvar && numero.length < 4 -> texts.missing(4 - numero.length)
 *         else -> null
 *     },
 * )
 * ```
 *
 * @param value só algarismos (outros caracteres são ignorados na exibição).
 * @param onValueChange recebe sempre só algarismos, no máximo [length].
 * @param length quantidade de caixas (1..[DigitBoxFieldDefaults.MAX_LENGTH]).
 * @param helperText dica neutra embaixo (o erro vence).
 * @param masked mostra "•" no lugar do algarismo (PIN) e marca o campo como senha na semântica.
 * @param autoFocus pede o foco (e o teclado) ao entrar na composição — o caso da folha de digitação.
 * @param onFilled chamado quando a última caixa é preenchida.
 * @param onOverflow chamado com os algarismos colados que não couberam.
 */
@Composable
fun DigitBoxField(
    value: String,
    onValueChange: (String) -> Unit,
    length: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    errorMessage: String? = null,
    helperText: String? = null,
    enabled: Boolean = true,
    masked: Boolean = false,
    autoFocus: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFilled: ((String) -> Unit)? = null,
    onOverflow: ((String) -> Unit)? = null,
    boxWidth: Dp = DigitBoxFieldDefaults.BoxWidth,
    boxHeight: Dp = DigitBoxFieldDefaults.BoxHeight,
    spacing: Dp = DigitBoxFieldDefaults.Spacing,
    texts: DigitBoxTexts = rememberDigitBoxTexts(),
) {
    require(length in 1..DigitBoxFieldDefaults.MAX_LENGTH) {
        "DigitBoxField aceita de 1 a ${DigitBoxFieldDefaults.MAX_LENGTH} caixas (veio $length)"
    }
    val allDigits = value.filter { it in '0'..'9' }
    val shown = allDigits.take(length)
    var pastedTooMany by remember(length) { mutableStateOf<Int?>(null) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val shownError = errorMessage
        ?: pastedTooMany?.let { texts.tooMany(length, it) }
        ?: allDigits.length.takeIf { it > length }?.let { texts.tooMany(length, it) }

    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    val stateText = texts.state(shown.length, length)

    // O texto do campo é desenhado pelas caixas; o do próprio campo, a seleção e o cursor ficam
    // invisíveis (o cursor "real" seria um traço solto na frente da primeira caixa).
    CompositionLocalProvider(LocalTextSelectionColors provides InvisibleSelection) {
        BasicTextField(
            value = TextFieldValue(shown, selection = TextRange(shown.length)),
            onValueChange = { proposed ->
                when (val result = applyDigitBoxInput(shown, proposed.text, length)) {
                    is DigitBoxInput.Accepted -> {
                        pastedTooMany = null
                        if (result.value != shown) {
                            onValueChange(result.value)
                            if (result.value.length == length) onFilled?.invoke(result.value)
                        }
                    }
                    is DigitBoxInput.Overflow -> {
                        pastedTooMany = result.attemptedDigits.length
                        onOverflow?.invoke(result.attemptedDigits)
                    }
                    DigitBoxInput.Ignored -> Unit
                }
            },
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    // O número é o texto do próprio campo (lido inteiro); o rótulo é o `Text` do
                    // decorationBox, fundido neste nó como no TextField do Material; o estado
                    // ("2 de 4 dígitos preenchidos") também diz o tamanho quando não há rótulo.
                    stateDescription = stateText
                    if (shownError != null) error(shownError)
                    if (masked) password()
                },
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = appKeyboardOptions(
                keyboardType = if (masked) KeyboardType.NumberPassword else KeyboardType.Number,
                imeAction = imeAction,
                autoCorrect = false,
            ),
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                DigitBoxLayout(
                    digits = shown,
                    length = length,
                    label = label,
                    supportingText = shownError ?: helperText,
                    isError = shownError != null,
                    activeIndex = if (focused && enabled) digitBoxActiveIndex(shown, length) else null,
                    enabled = enabled,
                    masked = masked,
                    boxWidth = boxWidth,
                    boxHeight = boxHeight,
                    spacing = spacing,
                    innerTextField = innerTextField,
                )
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Só exibição
// ---------------------------------------------------------------------------------------------

/**
 * As mesmas caixas, **só para mostrar** — a linha de um cargo numa lista, que abre a folha de
 * digitação ao toque. Com [onClick], a linha inteira é o botão (papel `Button` na semântica), e o
 * leitor de tela lê rótulo, número e estado num anúncio só.
 */
@Composable
fun DigitBoxDisplay(
    value: String,
    length: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    errorMessage: String? = null,
    helperText: String? = null,
    masked: Boolean = false,
    onClick: (() -> Unit)? = null,
    boxWidth: Dp = DigitBoxFieldDefaults.BoxWidth,
    boxHeight: Dp = DigitBoxFieldDefaults.BoxHeight,
    spacing: Dp = DigitBoxFieldDefaults.Spacing,
    texts: DigitBoxTexts = rememberDigitBoxTexts(),
) {
    require(length in 1..DigitBoxFieldDefaults.MAX_LENGTH) {
        "DigitBoxDisplay aceita de 1 a ${DigitBoxFieldDefaults.MAX_LENGTH} caixas (veio $length)"
    }
    val allDigits = value.filter { it in '0'..'9' }
    val shown = allDigits.take(length)
    val shownError = errorMessage ?: allDigits.length.takeIf { it > length }?.let { texts.tooMany(length, it) }
    val description = listOfNotNull(
        label ?: texts.description(length),
        shown.takeIf { it.isNotEmpty() && !masked },
    ).joinToString(", ")
    val stateText = texts.state(shown.length, length)

    val clickModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .then(clickModifier)
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = stateText
                if (shownError != null) error(shownError)
            },
    ) {
        DigitBoxLayout(
            digits = shown,
            length = length,
            label = label,
            supportingText = shownError ?: helperText,
            isError = shownError != null,
            activeIndex = null,
            enabled = true,
            masked = masked,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            spacing = spacing,
            innerTextField = null,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Desenho
// ---------------------------------------------------------------------------------------------

private val InvisibleSelection = TextSelectionColors(handleColor = Color.Transparent, backgroundColor = Color.Transparent)

private val BoxShape = RoundedCornerShape(10.dp)

@Composable
private fun DigitBoxLayout(
    digits: String,
    length: Int,
    label: String?,
    supportingText: String?,
    isError: Boolean,
    activeIndex: Int?,
    enabled: Boolean,
    masked: Boolean,
    boxWidth: Dp,
    boxHeight: Dp,
    spacing: Dp,
    innerTextField: (@Composable () -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        BoxWithConstraints {
            // Encolhe as caixas quando a linha não cabe (5 caixas num telefone de 320 dp com margem).
            val fitting = (maxWidth - spacing * (length - 1)) / length
            val width = minOf(boxWidth, fitting).coerceAtLeast(DigitBoxFieldDefaults.MinBoxWidth)
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    repeat(length) { index ->
                        val digit = digits.getOrNull(index)
                        val active = index == activeIndex
                        val borderColor = when {
                            !enabled -> colors.onSurface.copy(alpha = 0.12f)
                            isError -> colors.error
                            active -> colors.primary
                            else -> colors.outline
                        }
                        val borderWidth = if ((active || isError) && enabled) 2.dp else 1.dp
                        Box(
                            modifier = Modifier
                                .size(width, boxHeight)
                                .background(colors.surface, BoxShape)
                                .border(BorderStroke(borderWidth, borderColor), BoxShape)
                                .clearAndSetSemantics { },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (digit != null) {
                                Text(
                                    text = if (masked) "•" else digit.toString(),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFeatureSettings = "tnum",
                                        textAlign = TextAlign.Center,
                                    ),
                                    color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                                )
                            }
                        }
                    }
                }
                if (innerTextField != null) {
                    // O campo de texto real, invisível, por cima das caixas: recebe o toque, o
                    // teclado e o "colar" em qualquer ponto da linha.
                    Box(Modifier.matchParentSize()) { innerTextField() }
                }
            }
        }
        if (supportingText != null) {
            Spacer(Modifier.height(6.dp))
            // O erro já vai em `error()` na semântica — não é lido em dobro. A dica é lida.
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colors.error else colors.onSurfaceVariant,
                modifier = if (isError) Modifier.clearAndSetSemantics { } else Modifier,
            )
        }
    }
}
