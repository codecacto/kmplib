package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.brdata.Address
import br.com.codecacto.kmplib.brdata.BrazilianCities
import br.com.codecacto.kmplib.brdata.BrazilianStates
import br.com.codecacto.kmplib.brdata.CepLookupResult
import br.com.codecacto.kmplib.brdata.filterUfInput
import br.com.codecacto.kmplib.brdata.mergedWith
import br.com.codecacto.kmplib.mask.filterCepInput
import kotlinx.coroutines.launch

/**
 * Bloco de endereço — CEP, logradouro, número, complemento, bairro, cidade e UF.
 *
 * **O par do `AddressFields` da weblib**, campo a campo e com a mesma semântica. Endereço é o mesmo
 * formulário em todo produto que cobra: sete campos, uma máscara, um autopreenchimento por CEP e a
 * lista de estados. Escrito por app, ele diverge em detalhes que ninguém revisa — a máscara que
 * aceita letra, a UF que vira campo livre, o número que ninguém lembra de focar.
 *
 * ## O autopreenchimento é conveniência e NUNCA trava
 *
 * Digitado o CEP completo, [onCepLookup] é chamado e o que voltar preenche **só o que está em
 * branco** (ver `Address.mergedWith`). Se a busca falhar, demorar ou não achar, os campos seguem
 * editáveis e o formulário continua utilizável — um cadastro que só se preenche quando um serviço de
 * terceiro responde é um cadastro que trava quando ele cai, e num celular em rede móvel isso não é
 * hipótese.
 *
 * **O transporte é do consumidor**, de propósito: a lib não escolhe o serviço de CEP. Um cliente
 * embutido amarraria todo app a um fornecedor que só se troca publicando na loja. O caminho
 * recomendado é o backend do produto expor a consulta.
 *
 * O **número** não é preenchido pelo CEP (nenhum serviço sabe) e por isso ganha o foco assim que a
 * busca volta: é o único campo que sobra para digitar.
 *
 * ## Onde ele é obrigatório
 *
 * Para **pré-preencher o checkout** de um gateway. Medido contra o Asaas em ago/2026: sem endereço
 * do pagador ele recusa qualquer pré-preenchimento, e a página pergunta tudo de novo a quem já está
 * logado. ⚠️ O endereço sozinho **não basta** — o pré-preenchimento é tudo-ou-nada e inclui nome
 * completo, CPF, e-mail e celular.
 */
@Composable
fun AddressFields(
    value: Address,
    onValueChange: (Address) -> Unit,
    modifier: Modifier = Modifier,
    /** Rótulo do bloco. `null` remove o título. */
    title: String? = "Endereço",
    enabled: Boolean = true,
    /**
     * Busca o endereço pelo CEP, disparada quando ele chega a 8 dígitos.
     *
     * Devolva `null` para "não achei" — o usuário preenche à mão, e nada trava. Exceção lançada
     * aqui é **engolida** pelo mesmo motivo: a busca não pode derrubar o formulário.
     */
    onCepLookup: (suspend (String) -> CepLookupResult?)? = null,
    /** Erro por campo. Só o que veio é marcado. */
    errors: AddressErrors = AddressErrors(),
) {
    var buscando by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    val focoDoNumero = remember { FocusRequester() }
    /** Evita repetir a busca do MESMO CEP a cada tecla depois do 8º dígito. */
    var ultimoBuscado by remember { mutableStateOf("") }
    var focarNumero by remember { mutableStateOf(false) }

    LaunchedEffect(focarNumero) {
        if (focarNumero) {
            runCatching { focoDoNumero.requestFocus() }
            focarNumero = false
        }
    }

    // `padding(vertical = 8.dp)`: o bloco precisa se DESTACAR dos campos vizinhos. Entre os sete
    // campos daqui há 12dp, o mesmo respiro que o formulário usa entre um campo e outro — sem esta
    // folga, o campo logo abaixo de "Estado / Cidade" (a senha, no cadastro) parece a última linha
    // do endereço, e quem preenche não percebe que o bloco acabou.
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // CEP com o texto MORANDO NO CAMPO (2.262.1): era `AppTextField(value = value.cep)` e, com o
        // endereço vindo do StateFlow do ViewModel, o iOS engolia dígitos na digitação rápida
        // (docs/42). Filtro (só algarismos, até 8) e máscara (00000-000) são do próprio campo —
        // `InputTransformation`/`OutputTransformation`, a forma oficial do TextField com estado.
        CepField(
            cep = value.cep,
            onCepChange = { digitos ->
                // Um `onValueChange` só: montar o próximo estado a partir de `value` duas vezes no
                // mesmo evento faz a segunda chamada desfazer a primeira.
                onValueChange(value.copy(cep = digitos))
                if (digitos.length == DIGITOS_DO_CEP && onCepLookup != null && digitos != ultimoBuscado) {
                    ultimoBuscado = digitos
                    escopo.launch {
                        buscando = true
                        // Silencioso de propósito: a busca é conveniência, e um erro aqui não pode
                        // impedir o cadastro — os campos continuam editáveis e a pessoa digita.
                        val achado = runCatching { onCepLookup(digitos) }.getOrNull()
                        buscando = false
                        if (achado != null) {
                            onValueChange(value.copy(cep = digitos).mergedWith(achado))
                            focarNumero = true
                        }
                    }
                }
            },
            // A dica NEUTRA — não `errorMessage`, que pintaria o campo de vermelho durante uma
            // operação normal. É o que o `helperText` (2.129.0) veio resolver.
            helperText = if (buscando) "Buscando endereço…" else null,
            errorMessage = errors.cep,
            enabled = enabled,
        )
        AppTextField(
            value = value.logradouro,
            onValueChange = { onValueChange(value.copy(logradouro = it)) },
            keepTextLocally = true,
            label = "Logradouro",
            placeholder = "Rua, avenida, travessa…",
            errorMessage = errors.logradouro,
            enabled = enabled,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = value.numero,
                onValueChange = { onValueChange(value.copy(numero = it)) },
                keepTextLocally = true,
                modifier = Modifier.weight(1f).focusRequester(focoDoNumero).focusable(),
                label = "Número",
                placeholder = FormPlaceholders.ADDRESS_NUMBER,
                errorMessage = errors.numero,
                enabled = enabled,
            )
            AppTextField(
                value = value.complemento,
                onValueChange = { onValueChange(value.copy(complemento = it)) },
                keepTextLocally = true,
                modifier = Modifier.weight(1f),
                label = "Complemento",
                placeholder = "Apto, bloco…",
                enabled = enabled,
            )
        }
        AppTextField(
            value = value.bairro,
            onValueChange = { onValueChange(value.copy(bairro = it)) },
            keepTextLocally = true,
            label = "Bairro",
            errorMessage = errors.bairro,
            enabled = enabled,
        )
        // **O ESTADO vem antes da cidade**, e não o contrário. A cidade depende dele: é o estado que
        // decide quais nomes existem, então perguntar a cidade primeiro é pedir a resposta antes da
        // pergunta — e era o que esta ordem fazia, com o agravante de a cidade ser campo livre.
        //
        // UF em PICKER, não em campo livre: valor fora da lista é 400 no servidor, e digitar duas
        // letras é o tipo de campo em que o erro só aparece na hora de salvar. Trocar de estado
        // **limpa a cidade** — deixá-la faria "Santos/BA" existir sem ninguém notar.
        AppPickerField(
            value = value.uf,
            onValueChange = { sigla ->
                val nova = filterUfInput(sigla)
                onValueChange(
                    if (nova == value.uf) value else value.copy(uf = nova, cidade = ""),
                )
            },
            options = ESTADOS,
            label = "Estado",
            placeholder = "Escolha o estado",
            errorMessage = errors.uf,
            enabled = enabled,
            sheetTitle = "Estado",
        )

        // A cidade é **escolhida de uma lista com BUSCA**, não digitada. São os municípios do IBGE
        // daquela UF (até 853, em MG) — rolar isso atrás de um nome não é escolher, é procurar
        // agulha; e digitar livre traz de volta o que o picker da UF existe para impedir: grafia
        // divergente ("Sao Paulo", "S. Paulo", "sao paulo") no mesmo campo, para o mesmo lugar.
        AppPickerField(
            value = value.cidade,
            onValueChange = { onValueChange(value.copy(cidade = it)) },
            options = remember(value.uf) {
                BrazilianCities.getByState(value.uf).map { PickerOption(it.name, it.name) }
            },
            label = "Cidade",
            // Sem UF escolhida não há lista, e o placeholder diz por quê em vez de abrir um sheet
            // vazio.
            placeholder = if (value.uf.isBlank()) "Escolha o estado primeiro" else "Escolha a cidade",
            errorMessage = errors.cidade,
            enabled = enabled && value.uf.isNotBlank(),
            sheetTitle = "Cidade",
            searchable = true,
            searchPlaceholder = "Buscar cidade",
        )
    }
}

/** Erro por campo do [AddressFields]. `complemento` não entra: é o único que nunca é obrigatório. */
data class AddressErrors(
    val cep: String? = null,
    val logradouro: String? = null,
    val numero: String? = null,
    val bairro: String? = null,
    val cidade: String? = null,
    val uf: String? = null,
)

/** As 27 unidades federativas, no formato do picker. Lista da lib, nunca digitada por app. */
private val ESTADOS: List<PickerOption> =
    BrazilianStates.all.map { PickerOption(value = it.abbreviation, label = "${it.abbreviation} · ${it.name}") }

private const val DIGITOS_DO_CEP = 8

/**
 * O campo de CEP do [AddressFields] — mesmo desenho do `AppTextField`, mas com o texto no
 * `TextFieldState` ([rememberSyncedTextFieldState]): o teclado escreve no mesmo quadro, o ViewModel só
 * recebe os algarismos, e um CEP que muda de fora (limpar, preencher) reescreve o campo.
 */
@Composable
private fun CepField(
    cep: String,
    onCepChange: (String) -> Unit,
    helperText: String?,
    errorMessage: String?,
    enabled: Boolean,
) {
    val state = rememberSyncedTextFieldState(cep, onCepChange)
    OutlinedTextField(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("CEP") },
        placeholder = { Text("00000-000") },
        supportingText = (errorMessage ?: helperText)?.let { { Text(it) } },
        isError = errorMessage != null,
        inputTransformation = CepInputTransformation,
        outputTransformation = CepOutputTransformation,
        keyboardOptions = appKeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorLabelColor = MaterialTheme.colorScheme.error,
        ),
    )
}

/**
 * Só algarismos, no máximo 8 ([filterCepInput]). Colar "78000-000" vira "78000000"; o 9º dígito
 * digitado fica de fora. Testado em `CepTransformationsTest`.
 */
internal object CepInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val proposed = asCharSequence().toString()
        val digits = filterCepInput(proposed)
        if (digits != proposed) {
            replace(0, length, digits)
            placeCursorAtEnd()
        }
    }
}

/** Máscara de exibição 00000-000 — o hífen é só visual; o estado guarda os 8 algarismos. */
internal object CepOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (length > 5) insert(5, "-")
    }
}
