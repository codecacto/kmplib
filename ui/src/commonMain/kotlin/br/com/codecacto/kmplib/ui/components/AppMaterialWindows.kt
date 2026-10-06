package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import br.com.codecacto.kmplib.platform.automation.exposeTestTagsAsResourceId

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Janelas do Material3 / Compose que o Maestro ENXERGA (2.257.0)
//
// No Android, `AlertDialog`, `BasicAlertDialog`, `Dialog`, `DatePickerDialog`, `ModalBottomSheet`,
// `DropdownMenu`, `ExposedDropdownMenu` e `Popup` abrem OUTRA JANELA — outro `AndroidComposeView`, com
// árvore de semântica própria. O `testTagsAsResourceId` que o `AppTheme` / `WithTestTagsAsResourceId`
// liga na raiz do app NÃO atravessa para ela: todo `testTag` lá dentro sai com `resource-id=""` e o
// Maestro não toca em nada (Prospecta: opções da categoria; Meu Fisio: menu ⋮ e diálogo de excluir;
// LocAki escreveu um `LocakiAlertDialog` só para isto — 06/out/2026).
//
// Cada função abaixo tem a **mesma assinatura** do original (mesmos nomes, ordem e defaults) e só
// acrescenta `Modifier.exposeTestTagsAsResourceId()` no nó-raiz da janela. Migrar é trocar o nome:
//
//     AlertDialog(        →  AppAlertDialog(
//     BasicAlertDialog(   →  AppBasicAlertDialog(
//     Dialog(             →  AppDialog(
//     DatePickerDialog(   →  AppDatePickerDialog(
//     ModalBottomSheet(   →  AppModalBottomSheet(
//     DropdownMenu(       →  AppDropdownMenu(
//     ExposedDropdownMenu(→  AppExposedDropdownMenu(      (dentro do ExposedDropdownMenuBox)
//     Popup(              →  AppPopup(
//
// `AppAlertDialog`, `AppDialog` e `AppDatePickerDialog` JÁ existiam com outra forma (os diálogos
// prontos da lib: `show`, `title: String`…). As versões daqui são SOBRECARGAS — o 1º parâmetro é uma
// função (`onDismissRequest`), não `Boolean`/`LocalDate?`, então o compilador nunca confunde as duas.
//
// No iOS o modificador é o próprio `Modifier` (a `testTag` já vira `accessibilityIdentifier` em
// qualquer janela) — as funções delegam ao original sem custo.
//
// Não acrescentam id nenhum: o `testTag` é do app (no `modifier` ou nos filhos). Também não mudam
// comportamento (teclado, insets, cores) — por isso são seguras de trocar por `sed`.
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * [AlertDialog] do Material3 com os `testTag` visíveis ao Maestro no Android. Mesma API — ver o
 * cabeçalho deste arquivo. O diálogo pronto da lib (`show`, `title: String`, `message`) continua
 * sendo a outra sobrecarga de `AppAlertDialog`.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.exposeTestTagsAsResourceId(),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** [BasicAlertDialog] do Material3 com os `testTag` visíveis ao Maestro no Android. Mesma API. */
@ExperimentalMaterial3Api
@Composable
fun AppBasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.exposeTestTagsAsResourceId(),
        properties = properties,
        content = content,
    )
}

/**
 * [Dialog] (`androidx.compose.ui.window`) com os `testTag` visíveis ao Maestro no Android. Mesma API.
 *
 * O `Dialog` não tem `modifier`: o conteúdo vai dentro de um `Box` com `propagateMinConstraints`,
 * que repassa as restrições da janela sem mudar o tamanho de nada. O diálogo pronto da lib
 * (`show`, `title`, conteúdo em coluna) continua sendo a outra sobrecarga de `AppDialog`.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        // Sem `modifier` no `Dialog`/`Popup`: um nó só com a flag. `propagateMinConstraints`
        // repassa as restrições da janela ao conteúdo — o tamanho medido é o mesmo de antes.
        Box(modifier = Modifier.exposeTestTagsAsResourceId(), propagateMinConstraints = true) { content() }
    }
}

/**
 * [DatePickerDialog] do Material3 com os `testTag` visíveis ao Maestro no Android (botões e o
 * `DatePicker` de dentro). Mesma API. O seletor pronto da lib (`selectedDate`, `onDateSelected`)
 * continua sendo a outra sobrecarga de `AppDatePickerDialog`.
 */
@ExperimentalMaterial3Api
@Composable
fun AppDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    shape: Shape = DatePickerDefaults.shape,
    tonalElevation: Dp = DatePickerDefaults.TonalElevation,
    colors: DatePickerColors = DatePickerDefaults.colors(),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit,
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.exposeTestTagsAsResourceId(),
        dismissButton = dismissButton,
        shape = shape,
        tonalElevation = tonalElevation,
        colors = colors,
        properties = properties,
        content = content,
    )
}

/**
 * [ModalBottomSheet] do Material3 com os `testTag` visíveis ao Maestro no Android. Mesma API.
 * A folha pronta da lib, com `isVisible`, é o [AppBottomSheet].
 */
@ExperimentalMaterial3Api
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.exposeTestTagsAsResourceId(),
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = content,
    )
}

/**
 * [DropdownMenu] do Material3 com os `testTag` visíveis ao Maestro no Android — o menu ⋮ e as
 * opções de um seletor. Mesma API. Campo-com-menu pronto da lib: `AppDropdownField`.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.exposeTestTagsAsResourceId(),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}

/**
 * `ExposedDropdownMenu` do Material3 com os `testTag` visíveis ao Maestro no Android. Mesma API,
 * chamada no mesmo lugar (dentro do `ExposedDropdownMenuBox { }`):
 * ```kotlin
 * ExposedDropdownMenuBox(expanded, onExpandedChange) {
 *     OutlinedTextField(…, modifier = Modifier.menuAnchor(…))
 *     AppExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
 *         DropdownMenuItem(text = { Text(op) }, onClick = { … }, modifier = Modifier.testTag("categoria-$id"))
 *     }
 * }
 * ```
 */
@ExperimentalMaterial3Api
@Composable
fun ExposedDropdownMenuBoxScope.AppExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    matchAnchorWidth: Boolean = true,
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.exposeTestTagsAsResourceId(),
        scrollState = scrollState,
        matchAnchorWidth = matchAnchorWidth,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}

/** [Popup] (`androidx.compose.ui.window`) com os `testTag` visíveis ao Maestro no Android. Mesma API. */
@Composable
fun AppPopup(
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset(0, 0),
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit,
) {
    Popup(alignment = alignment, offset = offset, onDismissRequest = onDismissRequest, properties = properties) {
        // Sem `modifier` no `Dialog`/`Popup`: um nó só com a flag. `propagateMinConstraints`
        // repassa as restrições da janela ao conteúdo — o tamanho medido é o mesmo de antes.
        Box(modifier = Modifier.exposeTestTagsAsResourceId(), propagateMinConstraints = true) { content() }
    }
}

/** [Popup] posicionado por [PopupPositionProvider], com os `testTag` visíveis ao Maestro. Mesma API. */
@Composable
fun AppPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)? = null,
    properties: PopupProperties = PopupProperties(),
    content: @Composable () -> Unit,
) {
    Popup(popupPositionProvider = popupPositionProvider, onDismissRequest = onDismissRequest, properties = properties) {
        // Sem `modifier` no `Dialog`/`Popup`: um nó só com a flag. `propagateMinConstraints`
        // repassa as restrições da janela ao conteúdo — o tamanho medido é o mesmo de antes.
        Box(modifier = Modifier.exposeTestTagsAsResourceId(), propagateMinConstraints = true) { content() }
    }
}
