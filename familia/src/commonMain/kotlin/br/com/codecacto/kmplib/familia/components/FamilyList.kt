package br.com.codecacto.kmplib.familia.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.familia.icons.FamilyIcons
import br.com.codecacto.kmplib.familia.theme.FamilyTheme

/** Uma linha registrada no [FamilyListScope]. */
internal class FamilyListEntry(val key: Any?, val content: @Composable (isLast: Boolean) -> Unit)

/**
 * Construtor de linhas da [FamilyList] — as linhas sabem se são a última (o separador `ln` some nela,
 * como no `.row:last-child` do protótipo).
 */
class FamilyListScope internal constructor() {
    internal val entries = mutableListOf<FamilyListEntry>()

    /** Linha padrão — ver [FamilyRow]. */
    fun row(
        title: String,
        subtitle: String? = null,
        leading: (@Composable () -> Unit)? = null,
        trailing: (@Composable RowScope.() -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        enabled: Boolean = true,
        testTag: String? = null,
        role: Role = Role.Button,
        key: Any? = testTag,
    ) {
        entries += FamilyListEntry(key) { last ->
            FamilyRow(title, subtitle, leading = leading, trailing = trailing, onClick = onClick, enabled = enabled, divider = !last, testTag = testTag, role = role)
        }
    }

    /** Conteúdo livre como linha (recebe `isLast` para desenhar ou não o separador). */
    fun custom(key: Any? = null, content: @Composable (isLast: Boolean) -> Unit) {
        entries += FamilyListEntry(key, content)
    }
}

/**
 * Lista (`.lst`): cartão raio 22, borda `ln`, padding 2×14; as linhas vêm do [content] (DSL, como a
 * `LazyColumn`): `FamilyList { row(…); row(…) }`. Para lista longa use `LazyColumn` com [FamilyRow].
 */
@Composable
fun FamilyList(modifier: Modifier = Modifier, content: FamilyListScope.() -> Unit) {
    val c = FamilyTheme.colors
    val scope = FamilyListScope().apply(content)
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(c.sf).border(1.dp, c.ln, shape).padding(horizontal = 14.dp, vertical = 2.dp)) {
        scope.entries.forEachIndexed { i, e -> e.content(i == scope.entries.lastIndex) }
    }
}

/**
 * Linha (`.row`): altura mín. 62, gap 12, título `.row-t` + subtítulo `.row-s`, separador `ln` embaixo
 * ([divider]). Clicável inteira quando há [onClick] (regra da fábrica: linha clicável inteira).
 */
@Composable
fun FamilyRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    testTag: String? = null,
    role: Role = Role.Button,
) {
    val c = FamilyTheme.colors
    Column(modifier.fillMaxWidth()) {
        var m = Modifier.fillMaxWidth().heightIn(min = 62.dp)
        if (onClick != null) m = m.clickable(enabled = enabled, role = role, onClick = onClick)
        if (testTag != null) m = m.testTag(testTag)
        Row(
            m.alpha(if (enabled) 1f else 0.45f).padding(horizontal = 2.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading != null) leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = FamilyTheme.type.rowT, color = c.fg)
                if (subtitle != null) Text(subtitle, style = FamilyTheme.type.rowS, color = c.mu)
            }
            if (trailing != null) trailing()
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.ln))
    }
}

/** A seta `›` da linha que abre outra tela (decorativa). */
@Composable
fun FamilyRowChevron() {
    Icon(FamilyIcons.Chevron, null, tint = FamilyTheme.colors.mu, modifier = Modifier.size(24.dp).clearAndSetSemantics { })
}

/** Selo de ícone padrão da linha (46). */
@Composable
fun FamilyRowBadge(icon: ImageVector) = FamilyBadge(icon)
