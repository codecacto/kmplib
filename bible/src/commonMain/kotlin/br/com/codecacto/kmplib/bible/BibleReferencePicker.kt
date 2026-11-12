package br.com.codecacto.kmplib.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Cores do seletor — ele não tem cor própria: quem chama passa as do app. */
@Immutable
data class BibleReferencePickerColors(
    val content: Color,
    val muted: Color,
    val divider: Color,
    val cellBackground: Color,
    val cellContent: Color,
    val selectedBackground: Color,
    val selectedContent: Color,
)

/** Textos do seletor (os rótulos, não os nomes dos livros, que vêm do [BibleLanguage]). */
@Immutable
data class BibleReferencePickerTexts(
    val oldTestament: String,
    val newTestament: String,
    val chapter: String,
    val verse: String,
    val back: String,
) {
    companion object {
        /** Os textos no [language]. */
        fun of(language: BibleLanguage): BibleReferencePickerTexts = when (language) {
            BibleLanguage.PT_BR, BibleLanguage.PT_PT ->
                BibleReferencePickerTexts("Antigo Testamento", "Novo Testamento", "Capítulo", "Versículo", "Voltar")
            BibleLanguage.EN -> BibleReferencePickerTexts("Old Testament", "New Testament", "Chapter", "Verse", "Back")
            BibleLanguage.ES -> BibleReferencePickerTexts("Antiguo Testamento", "Nuevo Testamento", "Capítulo", "Versículo", "Volver")
        }
    }
}

/** Em que passo o seletor está. */
enum class BibleReferencePickerStep { BOOK, CHAPTER, VERSE }

/**
 * Seletor **Livro → Capítulo → Versículo**, sem cor nem fonte próprias (recebe [colors] e [textStyle]).
 *
 * Mostra só o que existe no arquivo ([books] vem de [BibleDatabase.availability]); a quantidade de
 * versículos é perguntada ao banco quando o capítulo é escolhido ([loadVerseCount]). Com
 * [pickVerse] = `false`, termina no capítulo ("Ler" de uma Bíblia).
 *
 * Ids de teste: `<testTagPrefix>-livro-<USFM>`, `-capitulo-<n>`, `-versiculo-<n>`, `-voltar`.
 * Moda para uma folha (`AppModalBottomSheet`) ou tela cheia — o tamanho é de quem chama.
 */
@Composable
fun BibleReferencePicker(
    books: List<BibleBookAvailability>,
    language: BibleLanguage,
    onPicked: (VerseRef) -> Unit,
    colors: BibleReferencePickerColors,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    loadVerseCount: suspend (BibleBook, Int) -> Int = { _, _ -> 0 },
    pickVerse: Boolean = true,
    initial: VerseRef? = null,
    texts: BibleReferencePickerTexts = remember(language) { BibleReferencePickerTexts.of(language) },
    contentPadding: PaddingValues = PaddingValues(0.dp),
    testTagPrefix: String = "biblia-seletor",
) {
    var step by rememberSaveable { mutableStateOf(BibleReferencePickerStep.BOOK) }
    var bookId by rememberSaveable { mutableStateOf(initial?.book?.usfm) }
    var chapter by rememberSaveable { mutableIntStateOf(initial?.chapter ?: 0) }
    var verseCount by remember { mutableIntStateOf(0) }
    val book = bookId?.let { BibleBook.fromUsfm(it) }
    val available = books.firstOrNull { it.book == book }

    LaunchedEffect(step, book, chapter) {
        if (step == BibleReferencePickerStep.VERSE && book != null && chapter > 0) {
            verseCount = loadVerseCount(book, chapter)
        }
    }

    Column(modifier) {
        if (step != BibleReferencePickerStep.BOOK && book != null) {
            val title = if (step == BibleReferencePickerStep.CHAPTER) book.name(language)
            else "${book.name(language)} $chapter"
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = texts.back) {
                        step = if (step == BibleReferencePickerStep.VERSE) BibleReferencePickerStep.CHAPTER else BibleReferencePickerStep.BOOK
                    }
                    .testTag("$testTagPrefix-voltar")
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹  $title", style = textStyle.copy(fontWeight = FontWeight.Bold), color = colors.content)
            }
        }
        when (step) {
            BibleReferencePickerStep.BOOK -> LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Testament.entries.forEach { testament ->
                    val list = books.filter { it.book.testament == testament }
                    if (list.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                if (testament == Testament.OLD) texts.oldTestament else texts.newTestament,
                                style = textStyle.copy(fontWeight = FontWeight.Bold),
                                color = colors.muted,
                                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp).semantics { heading() },
                            )
                        }
                        items(list, key = { it.book.usfm }) { b ->
                            Column {
                                Text(
                                    b.book.name(language),
                                    style = textStyle,
                                    color = if (b.book == book) colors.selectedContent else colors.content,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (b.book == book) colors.selectedBackground else Color.Transparent)
                                        .clickable(role = Role.Button) {
                                            bookId = b.book.usfm
                                            chapter = 0
                                            if (b.chapters.size == 1 && pickVerse) {
                                                chapter = b.chapters.single()
                                                step = BibleReferencePickerStep.VERSE
                                            } else if (b.chapters.size == 1) {
                                                onPicked(VerseRef(b.book, b.chapters.single()))
                                            } else {
                                                step = BibleReferencePickerStep.CHAPTER
                                            }
                                        }
                                        .testTag("$testTagPrefix-livro-${b.book.usfm}")
                                        .padding(horizontal = 8.dp, vertical = 14.dp),
                                )
                                HorizontalDivider(color = colors.divider)
                            }
                        }
                    }
                }
            }
            BibleReferencePickerStep.CHAPTER -> NumberGrid(
                numbers = available?.chapters.orEmpty(),
                selected = chapter,
                label = texts.chapter,
                colors = colors,
                textStyle = textStyle,
                contentPadding = contentPadding,
                tag = { "$testTagPrefix-capitulo-$it" },
            ) { ch ->
                chapter = ch
                val b = book ?: return@NumberGrid
                if (pickVerse) step = BibleReferencePickerStep.VERSE else onPicked(VerseRef(b, ch))
            }
            BibleReferencePickerStep.VERSE -> NumberGrid(
                numbers = (1..verseCount).toList(),
                selected = initial?.takeIf { it.book == book && it.chapter == chapter }?.verseStart ?: 0,
                label = texts.verse,
                colors = colors,
                textStyle = textStyle,
                contentPadding = contentPadding,
                tag = { "$testTagPrefix-versiculo-$it" },
            ) { v -> book?.let { onPicked(VerseRef(it, chapter, v)) } }
        }
    }
}

@Composable
private fun NumberGrid(
    numbers: List<Int>,
    selected: Int,
    label: String,
    colors: BibleReferencePickerColors,
    textStyle: TextStyle,
    contentPadding: PaddingValues,
    tag: (Int) -> String,
    onPick: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(56.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(numbers, key = { it }) { n ->
            val on = n == selected
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) colors.selectedBackground else colors.cellBackground)
                    .clickable(role = Role.Button) { onPick(n) }
                    .semantics { contentDescription = "$label $n" }
                    .testTag(tag(n)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$n",
                    style = textStyle.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    color = if (on) colors.selectedContent else colors.cellContent,
                )
            }
        }
    }
}
