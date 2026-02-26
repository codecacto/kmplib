package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Container for form screens that handles keyboard behavior properly:
 * - Scrolls content when keyboard appears (imePadding)
 * - Allows scrolling through form fields
 * - Dismisses keyboard when tapping outside input fields
 *
 * @param modifier Modifier to apply to the container
 * @param horizontalPadding Horizontal padding for the content (default: 24.dp)
 * @param verticalPadding Vertical padding for the content (default: 16.dp)
 * @param horizontalAlignment Horizontal alignment for the column items
 * @param verticalArrangement Vertical arrangement for the column items
 * @param content The form content
 */
@Composable
fun FormContainer(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 24.dp,
    verticalPadding: Dp = 16.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
            .verticalScroll(scrollState)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}
