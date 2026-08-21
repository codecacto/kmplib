package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Uma opção do [AppPickerField]. [value] é o que volta ao servidor; [label] é o que se lê. */
data class PickerOption(val value: String, val label: String)

/**
 * Campo de escolha ÚNICA a partir de uma lista **longa** — estado, categoria, país.
 *
 * ## Por que ele existe (⟦gap fechado em 2.129.0⟧)
 *
 * A lib tinha três formas de escolher e nenhuma servia a uma lista de 27+ itens num formulário:
 * `FilterChipRow` é escolha única mas em `LazyRow`, que **rola de lado** e esconde o que não coube —
 * a pessoa escolhe entre as três opções que enxerga sem saber que havia outras; `AppMultiSelect` e
 * `MultiSelectList` são múltipla escolha. O resultado é que cada app resolvia com um campo de texto
 * de duas letras e uma validação própria — foi o que o NeuroCoreX teve de fazer com a UF.
 *
 * ## Sheet, não menu suspenso
 *
 * Num celular, menu suspenso cobre o campo que está sendo preenchido e some com um toque fora. O
 * sheet ocupa a metade de baixo, é rolável e tem alvo de 48 dp por linha — e o item escolhido vem
 * marcado, que é como a pessoa confere sem fechar e reabrir.
 *
 * O campo em si é **somente leitura**: quem edita é o sheet. Deixar digitar aqui traria de volta o
 * problema que este componente resolve — valor livre que o servidor recusa.
 */
@Composable
fun AppPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<PickerOption>,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    /** Título do sheet. Em branco, repete o [label]. */
    sheetTitle: String? = null,
    /**
     * Campo de busca no topo do sheet, filtrando por [PickerOption.label] — **acento e caixa não
     * contam**, e o pedaço basta ("mar" acha "Marabá" e "Santa Maria").
     *
     * Ligue quando a lista passar de umas três dezenas: rolar 850 municípios atrás de um nome é
     * conferência, não escolha. Para as 27 UFs, deixe desligado — o campo de busca ali seria um
     * toque a mais para uma lista que cabe em duas telas.
     */
    searchable: Boolean = false,
    /** Placeholder do campo de busca. */
    searchPlaceholder: String = "Buscar",
) {
    var aberto by remember { mutableStateOf(false) }
    var busca by remember { mutableStateOf("") }
    val selecionada = options.firstOrNull { it.value == value }

    // Reabrir é começar de novo: o filtro da consulta anterior escondendo três quartos da lista é
    // o tipo de estado preso que faz a pessoa concluir que "a cidade dela não está aí".
    LaunchedEffect(aberto) { if (!aberto) busca = "" }

    val visiveis = remember(options, busca, searchable) {
        if (!searchable || busca.isBlank()) {
            options
        } else {
            val alvo = busca.semAcento()
            options.filter { it.label.semAcento().contains(alvo) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // O clique é do BOX, não do campo: `OutlinedTextField` desabilitado não recebe clique, e
            // habilitado abriria o teclado. Este é o arranjo que dá campo somente-leitura clicável.
            .clickable(enabled = enabled, role = Role.Button) { aberto = true }
            .semantics {
                contentDescription = listOfNotNull(label, selecionada?.label).joinToString(": ")
            },
    ) {
        AppTextField(
            value = selecionada?.label.orEmpty(),
            onValueChange = {},
            label = label,
            placeholder = placeholder,
            helperText = helperText,
            errorMessage = errorMessage,
            // Desabilitado de propósito: é o que impede o teclado de abrir e a digitação livre.
            enabled = false,
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        )
    }

    AppBottomSheet(isVisible = aberto, onDismiss = { aberto = false }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = sheetTitle ?: label.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
            if (searchable) {
                AppTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    placeholder = searchPlaceholder,
                    leadingIcon = Icons.Default.Search,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            HorizontalDivider()
            if (visiveis.isEmpty()) {
                // Lista vazia depois de filtrar diz o que houve. Sem isto, o sheet abre num vão
                // branco e a leitura é "quebrou".
                Text(
                    text = "Nada encontrado para “$busca”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(visiveis, key = { it.value }) { opcao ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                onValueChange(opcao.value)
                                aberto = false
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(text = opcao.label, style = MaterialTheme.typography.bodyLarge)
                        if (opcao.value == value) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.CenterEnd),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * Minúsculas e sem acento — a forma em que duas grafias da mesma palavra se encontram.
 *
 * "Sao" precisa achar "São" e "TAUBATE" precisa achar "Taubaté": quem digita num teclado de celular
 * não põe acento, e uma busca que exige o til é uma busca que não acha nada.
 */
private fun String.semAcento(): String {
    val comAcento = "áàâãäéèêëíìîïóòôõöúùûüçñÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇÑ"
    val sem = "aaaaaeeeeiiiiooooouuuucnAAAAAEEEEIIIIOOOOOUUUUCN"
    return buildString(length) {
        for (c in this@semAcento) {
            val i = comAcento.indexOf(c)
            append(if (i >= 0) sem[i] else c)
        }
    }.lowercase()
}
