package br.com.codecacto.kmplib.help

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

private val BOLD_MARK = Regex("""\*\*([^*\n]+?)\*\*""")

/**
 * Converte `**trecho**` em negrito, o único markdown dos `passos` e do `texto` do contrato. Marca sem par
 * fica como veio, e nenhuma outra sintaxe é interpretada.
 */
internal fun helpBoldText(text: String): AnnotatedString = buildAnnotatedString {
    var position = 0
    BOLD_MARK.findAll(text).forEach { match ->
        append(text, position, match.range.first)
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        position = match.range.last + 1
    }
    append(text, position, text.length)
}

/** O texto como a busca o vê: sem os asteriscos, que são marcação e não conteúdo. */
internal fun withoutHelpMarks(text: String): String = text.replace("*", "")
