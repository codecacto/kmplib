package br.com.codecacto.kmplib.familia.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.text.matchesSearch
import br.com.codecacto.kmplib.familia.icons.FamilyIcons
import br.com.codecacto.kmplib.familia.theme.FamilyTheme
import br.com.codecacto.kmplib.ui.components.rememberSyncedTextFieldState

/**
 * Campo (`.inp` / textarea + `.lab`): altura 56, raio 16, borda 1,5 `ln` (foco `ac`); [minLines] > 1 =
 * textarea (mín. 120). O texto MORA NO CAMPO (`rememberSyncedTextFieldState`): o ViewModel recebe cada
 * mudança, e o iPhone não perde letra em digitação rápida.
 *
 * [placeholder] é instrução ou formato, nunca dado de exemplo (regra da fábrica). [error] = a frase do
 * erro, embaixo do campo, com a borda vermelha.
 */
@Composable
fun FamilyTextField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) Int.MAX_VALUE else 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    error: String? = null,
    saveable: Boolean = true,
    testTag: String? = null,
) {
    val c = FamilyTheme.colors
    val state = rememberSyncedTextFieldState(text, onTextChange, saveable)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    val errorColor = br.com.codecacto.kmplib.ui.theme.AppColors.current.error
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = FamilyTheme.type.lab, color = c.mu)
        val multi = minLines > 1
        BasicTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (multi) 120.dp else 56.dp)
                .clip(shape)
                .background(c.sf)
                .border(1.5.dp, if (error != null) errorColor else if (focused) c.ac else c.ln, shape)
                .let { if (testTag != null) it.testTag(testTag) else it }
                .semantics {
                    if (label != null) contentDescription = label
                    if (error != null) error(error)
                },
            textStyle = FamilyTheme.type.body.copy(color = c.fg),
            cursorBrush = SolidColor(c.ac),
            keyboardOptions = keyboardOptions,
            lineLimits = if (multi) TextFieldLineLimits.MultiLine(minHeightInLines = minLines, maxHeightInLines = maxLines) else TextFieldLineLimits.SingleLine,
            interactionSource = interaction,
            decorator = { inner ->
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (multi) 14.dp else 0.dp).heightIn(min = if (multi) 92.dp else 56.dp),
                    contentAlignment = if (multi) Alignment.TopStart else Alignment.CenterStart,
                ) {
                    if (state.text.isEmpty() && placeholder != null) Text(placeholder, style = FamilyTheme.type.body, color = c.mu)
                    inner()
                }
            },
        )
        if (error != null) Text(error, style = FamilyTheme.type.sm, color = errorColor)
    }
}

/**
 * Busca (`.srch`): altura 56, raio 18, lupa à esquerda, "x" para limpar. O texto mora no campo
 * (sem perder letras no iOS). Para filtrar uma lista local, [familyMatches] (por partes, sem acento).
 */
@Composable
fun FamilySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    clearLabel: String = "Limpar",
    testTag: String = "busca-campo",
) {
    val c = FamilyTheme.colors
    val state = rememberSyncedTextFieldState(query, onQueryChange, saveable = false)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape).background(c.sf).border(1.5.dp, c.ln, shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(FamilyIcons.Search, null, tint = c.mu, modifier = Modifier.size(22.dp))
        BasicTextField(
            state = state,
            modifier = Modifier.weight(1f).testTag(testTag).semantics { contentDescription = placeholder },
            textStyle = FamilyTheme.type.body.copy(color = c.fg),
            cursorBrush = SolidColor(c.ac),
            lineLimits = TextFieldLineLimits.SingleLine,
            decorator = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) Text(placeholder, style = FamilyTheme.type.body, color = c.mu)
                    inner()
                }
            },
        )
        if (state.text.isNotEmpty()) {
            FamilyIconButton(FamilyIcons.Close, clearLabel, onClick = { state.edit { replace(0, length, "") } }, tint = c.mu, testTag = "$testTag-limpar")
        }
    }
}

/**
 * Filtro por partes, sem acento, com as palavras somadas: "jose" acha "José", "pastor" acha "pastores",
 * "amor deus" exige as duas (em qualquer dos [fields]). É o `matchesSearch` do kmplib-core.
 */
fun familyMatches(query: String, vararg fields: String?): Boolean = matchesSearch(query, *fields)
