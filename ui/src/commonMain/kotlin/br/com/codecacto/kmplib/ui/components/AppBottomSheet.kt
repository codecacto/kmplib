package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

/**
 * Bottom sheet modal baseado no [ModalBottomSheet] do Material3.
 *
 * Só compõe o sheet quando [isVisible] é true. O conteúdo é renderizado dentro
 * de um [ColumnScope].
 *
 * @param isVisible Se o sheet está visível
 * @param onDismiss Callback quando o sheet é fechado (gesto ou clique fora)
 * @param skipPartiallyExpanded Se deve abrir diretamente expandido
 * @param content Conteúdo do sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (isVisible) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = skipPartiallyExpanded
        )
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            content = content
        )
    }
}
