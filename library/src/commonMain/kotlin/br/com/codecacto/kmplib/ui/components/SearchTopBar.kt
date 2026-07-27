package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Textos do [SearchTopBar] (i18n; defaults pt-BR).
 */
data class SearchTopBarTexts(
    val searchPlaceholder: String = "Buscar",
    val openSearchDescription: String = "Buscar",
    val closeSearchDescription: String = "Fechar busca",
    val clearQueryDescription: String = "Limpar busca",
    val filterDescription: String = "Filtros",
)

/**
 * Estado hoisted do [SearchTopBar]: se a busca está aberta e o texto digitado.
 *
 * Fica no `remember` da tela (não no ViewModel) — é estado de **UI**; o `query` é propagado para o
 * ViewModel via `onQueryChange`.
 */
@Stable
class SearchTopBarState(initialQuery: String = "", initialActive: Boolean = false) {
    /** `true` quando o campo de busca substituiu o título na barra. */
    var active: Boolean by mutableStateOf(initialActive)
        private set

    /** Texto corrente da busca. */
    var query: String by mutableStateOf(initialQuery)
        private set

    /** Abre a busca (revela o campo). Idempotente. */
    fun open() {
        active = true
    }

    /** Fecha a busca e **limpa** a consulta (o app deve reagir ao `query` vazio recarregando a lista). */
    fun close() {
        active = false
        query = ""
    }

    /** Alterna aberto/fechado. Fechar limpa a consulta. */
    fun toggle() {
        if (active) close() else open()
    }

    /** Atualiza o texto (chamado pelo campo). */
    fun onQueryChange(value: String) {
        query = value
    }

    /** Limpa só o texto, mantendo a busca aberta (o "x" dentro do campo). */
    fun clearQuery() {
        query = ""
    }
}

/** Lembra um [SearchTopBarState] ao longo das recomposições. */
@Composable
fun rememberSearchTopBarState(
    initialQuery: String = "",
    initialActive: Boolean = false,
): SearchTopBarState = remember { SearchTopBarState(initialQuery, initialActive) }

/**
 * Especificação do **ícone de filtros** da top bar (funil + badge quando há filtro ativo).
 *
 * @param activeCount quantos filtros estão ativos (`0` = sem badge).
 * @param onClick abre a folha/diálogo de filtros (hospedado pela tela).
 */
data class FilterAction(
    val activeCount: Int,
    val onClick: () -> Unit,
    val icon: ImageVector = Icons.Default.FilterList,
)

/**
 * Rótulo do badge de filtros ativos. `<= 0` ⇒ `null` (sem badge); `> 9` ⇒ `"9+"` (não estoura o
 * círculo). Função pura, testável.
 */
fun filterBadgeLabel(activeCount: Int): String? = when {
    activeCount <= 0 -> null
    activeCount > 9 -> "9+"
    else -> activeCount.toString()
}

/**
 * **Busca no top bar** — padrão inegociável do ecossistema (memória `search-topbar-android`): no
 * mobile a busca é um **ícone de lupa na barra superior** que revela o campo em cima do título;
 * **nunca** um `TextField` inline logo abaixo da barra (rouba altura útil e some ao rolar).
 *
 * Complementa o padrão irmão (memória `filters-topbar-icon`): filtros que ocupam espaço viram um
 * **ícone de funil** na mesma barra, com **badge** quando há filtro ativo — ver [FilterAction] e
 * [FilterIconButton].
 *
 * Promovido porque quase todo app da onda improvisava um `AppTextField` solto abaixo da barra
 * (MinhaAgenda, Exiba, NúmerosDaSorte, MinhaDespensa).
 *
 * Comportamento: fechado mostra `title` + lupa (+ funil + `actions`); aberto substitui o título pelo
 * campo, foca automaticamente, oferece "x" para limpar e voltar/fechar para sair (limpando a busca).
 *
 * ```kotlin
 * val search = rememberSearchTopBarState()
 * Scaffold(topBar = {
 *     SearchTopBar(
 *         title = "Clientes",
 *         state = search,
 *         onQueryChange = { vm.dispatch(Action.Search(it)) },
 *         navigationType = NavigationType.MENU,
 *         onNavigationClick = { drawer.open() },
 *         filter = FilterAction(activeCount = state.filtrosAtivos) { vm.dispatch(Action.OpenFilters) },
 *     )
 * }) { ... }
 * ```
 *
 * @param onQueryChange espelha o texto para o ViewModel (aplique debounce lá, não aqui).
 * @param onSearchClosed opcional; disparado ao fechar (o `onQueryChange("")` já é emitido).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    title: String,
    state: SearchTopBarState,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    navigationType: NavigationType = NavigationType.NONE,
    onNavigationClick: () -> Unit = {},
    filter: FilterAction? = null,
    actions: @Composable RowScope.() -> Unit = {},
    texts: SearchTopBarTexts = SearchTopBarTexts(),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onSearchClosed: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.active) {
        if (state.active) runCatching { focusRequester.requestFocus() }
    }

    TopAppBar(
        modifier = modifier,
        title = {
            if (state.active) {
                TextField(
                    value = state.query,
                    onValueChange = {
                        state.onQueryChange(it)
                        onQueryChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    placeholder = { Text(texts.searchPlaceholder) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = {
                                state.clearQuery()
                                onQueryChange("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = texts.clearQueryDescription)
                            }
                        }
                    },
                    // Busca: sem autocorreção — trocar "Hygor" por "Higor" no meio da digitação some com o resultado.
                    keyboardOptions = appKeyboardOptions(imeAction = ImeAction.Search, autoCorrect = false),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            } else {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            when {
                state.active -> IconButton(onClick = {
                    state.close()
                    onQueryChange("")
                    onSearchClosed()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = texts.closeSearchDescription,
                    )
                }

                navigationType == NavigationType.BACK -> IconButton(onClick = onNavigationClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }

                navigationType == NavigationType.MENU -> IconButton(onClick = onNavigationClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }

                else -> Unit
            }
        },
        actions = {
            if (!state.active) {
                IconButton(onClick = { state.open() }) {
                    Icon(Icons.Default.Search, contentDescription = texts.openSearchDescription)
                }
                filter?.let { FilterIconButton(it, texts.filterDescription) }
                actions()
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = backgroundColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
            actionIconContentColor = contentColor,
        ),
    )
}

/**
 * **Ícone de funil** para filtros na top bar, com badge de contagem quando há filtro ativo (memória
 * `filters-topbar-icon`). Use solto (em qualquer `AppTopBar`) ou via [SearchTopBar]`(filter = ...)`.
 *
 * Cores por token do tema; badge usa `error` (o mesmo sinal de "atenção" do resto da lib).
 */
@Composable
fun FilterIconButton(
    filter: FilterAction,
    contentDescription: String = "Filtros",
    modifier: Modifier = Modifier,
) {
    val badge = filterBadgeLabel(filter.activeCount)
    Box(modifier = modifier) {
        IconButton(onClick = filter.onClick) {
            Icon(filter.icon, contentDescription = contentDescription)
        }
        if (badge != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp),
            ) {
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}
