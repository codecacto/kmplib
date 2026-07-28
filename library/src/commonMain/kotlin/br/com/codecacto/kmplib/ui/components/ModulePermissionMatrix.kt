package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.permissions.PermissionFlagSpec
import br.com.codecacto.kmplib.permissions.PermissionLevel
import br.com.codecacto.kmplib.permissions.PermissionMatrixIssue
import br.com.codecacto.kmplib.permissions.PermissionMatrixState
import br.com.codecacto.kmplib.permissions.PermissionModuleSpec
import br.com.codecacto.kmplib.ui.theme.AppTheme
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Textos da matriz de permissão (i18n injetado pelo app; defaults pt-BR alinhados com o par web da
 * weblib — os mesmos rótulos que o Influencer já usa em produção).
 */
data class PermissionMatrixTexts(
    val levelNone: String = "Sem acesso",
    val levelView: String = "Ver",
    val levelEdit: String = "Ver e editar",
    val flagEnabled: String = "Permitido",
    val flagDisabled: String = "Sem permissão",
    val noAccessError: String =
        "Selecione ao menos um módulo com acesso (Ver ou Ver e editar).",
) {
    /** Rótulo do nível. */
    fun labelFor(level: PermissionLevel): String = when (level) {
        PermissionLevel.NONE -> levelNone
        PermissionLevel.VIEW -> levelView
        PermissionLevel.EDIT -> levelEdit
    }

    /** Mensagem de erro correspondente ao problema de validação. */
    fun messageFor(issue: PermissionMatrixIssue): String = when (issue) {
        PermissionMatrixIssue.NoAccess -> noAccessError
    }
}

/** Tom semântico do selo usado no modo somente-leitura: `EDIT` verde, `VIEW` info, `NONE` neutro. */
fun permissionLevelTone(level: PermissionLevel): StatusTone = when (level) {
    PermissionLevel.NONE -> StatusTone.NEUTRAL
    PermissionLevel.VIEW -> StatusTone.INFO
    PermissionLevel.EDIT -> StatusTone.SUCCESS
}

/** Largura máxima do seletor no modo expandido — evita a faixa de 3 opções esticar a tela toda. */
private val SegmentedMaxWidth = 320.dp

/**
 * **Matriz de permissão por módulo** — uma linha por módulo, cada uma com o seletor de nível
 * (Sem acesso / Ver / Ver e editar), mais um bloco opcional de **flags booleanas** dependentes.
 *
 * Empacota o padrão que Influencer (mobile + web) e TattooStudio precisam do mesmo jeito, com as
 * regras de normalização/validação em funções puras (pacote `permissions`) em vez de escondidas no
 * componente — foi essa ocultação que fez as duas plataformas do Influencer divergirem (o web
 * filtrava `NONE` e bloqueava salvar sem acesso; o mobile não fazia nenhum dos dois).
 *
 * **Stateless:** recebe [state] imutável e devolve o novo estado em [onStateChange]
 * (`setState { copy(perms = it) }` no MVI). A tela continua dona do "salvar" e da mensagem de erro
 * — use `state.validate()` + `texts.messageFor(issue)`.
 *
 * ```kotlin
 * val MODULOS = listOf(
 *     PermissionModuleSpec("AGENDA", stringResource(Res.string.perm_module_agenda)),
 *     PermissionModuleSpec("FINANCE", stringResource(Res.string.perm_module_finance)),
 * )
 *
 * ModulePermissionMatrix(
 *     modules = MODULOS,
 *     state = state.perms,
 *     onStateChange = { dispatch(Action.PermsChanged(it)) },
 * )
 * ```
 *
 * Responsivo (`LocalIsCompact`): no telefone o seletor vai numa linha própria em largura cheia (3
 * opções + rótulo longo não cabem lado a lado); no tablet/desktop o rótulo fica à esquerda e o
 * seletor à direita, limitado a [SegmentedMaxWidth] para não sobrar espaço morto.
 *
 * Acessibilidade: cada segmento é anunciado com o **módulo** junto ("Agenda, Ver e editar") — sem
 * isso, uma matriz de N linhas soa como N vezes "Ver" sem contexto. As flags são um alvo único de
 * `Role.Switch` cobrindo a linha inteira.
 *
 * @param modules módulos exibidos, na ordem. Chave de fio + rótulo **já resolvido** pelo app.
 * @param state estado imutável corrente.
 * @param onStateChange novo estado após uma interação. Ignorado quando [readOnly].
 * @param modifier modificador externo.
 * @param flags flags booleanas extras (ex.: "pode publicar", que exige Conteúdos = Ver e editar).
 *   Lista vazia (o caso comum) não renderiza nada — nem divisória.
 * @param readOnly `true` exibe só o nível atual como selo (tela "minhas permissões", sem edição).
 * @param enabled `false` desabilita a edição sem mudar o visual para o modo selo (ex.: salvando).
 * @param texts i18n dos rótulos fixos do componente.
 */
@Composable
fun ModulePermissionMatrix(
    modules: List<PermissionModuleSpec>,
    state: PermissionMatrixState,
    onStateChange: (PermissionMatrixState) -> Unit,
    modifier: Modifier = Modifier,
    flags: List<PermissionFlagSpec> = emptyList(),
    readOnly: Boolean = false,
    enabled: Boolean = true,
    texts: PermissionMatrixTexts = PermissionMatrixTexts(),
) {
    if (modules.isEmpty() && flags.isEmpty()) return

    val isCompact = LocalIsCompact.current
    val levels = PermissionLevel.entries
    val levelLabels = levels.map { texts.labelFor(it) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 14.dp else 10.dp),
    ) {
        modules.forEach { module ->
            val current = state.levelOf(module)

            if (readOnly) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ModuleLabel(module, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(texts.labelFor(current), permissionLevelTone(current))
                }
                return@forEach
            }

            val descriptions = levelLabels.map { "${module.label}, $it" }
            val selector: @Composable (Modifier) -> Unit = { selectorModifier ->
                SegmentedControl(
                    options = levelLabels,
                    selectedIndex = levels.indexOf(current),
                    onOptionSelected = { index ->
                        levels.getOrNull(index)?.let { onStateChange(state.withLevel(module, it)) }
                    },
                    modifier = selectorModifier,
                    enabled = enabled,
                    optionContentDescriptions = descriptions,
                )
            }

            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ModuleLabel(module, Modifier.fillMaxWidth())
                    selector(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ModuleLabel(module, Modifier.weight(1f))
                    selector(Modifier.widthIn(max = SegmentedMaxWidth))
                }
            }
        }

        if (flags.isNotEmpty()) {
            if (modules.isNotEmpty()) HorizontalDivider()

            flags.forEach { flag ->
                val available = state.isFlagAvailable(flag)
                val checked = state.resolveFlag(flag)

                if (readOnly) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FlagLabel(flag, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(
                            text = if (checked) texts.flagEnabled else texts.flagDisabled,
                            tone = if (checked) StatusTone.SUCCESS else StatusTone.NEUTRAL,
                        )
                    }
                    return@forEach
                }

                val toggleEnabled = enabled && available
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            enabled = toggleEnabled,
                            role = Role.Switch,
                            onValueChange = { onStateChange(state.withFlag(flag, it)) },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FlagLabel(flag, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    // A linha inteira é o alvo (Role.Switch); o controle é decorativo para o
                    // leitor de tela, senão o mesmo toggle seria anunciado duas vezes.
                    AppSwitch(
                        checked = checked,
                        onCheckedChange = { onStateChange(state.withFlag(flag, it)) },
                        enabled = toggleEnabled,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleLabel(module: PermissionModuleSpec, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = module.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        module.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlagLabel(flag: PermissionFlagSpec, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = flag.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        flag.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ModulePermissionMatrixPreview() {
    AppTheme {
        ModulePermissionMatrix(
            modules = listOf(
                PermissionModuleSpec("AGENDA", "Agenda"),
                PermissionModuleSpec("FINANCE", "Financeiro"),
                PermissionModuleSpec("CLIENTS", "Clientes"),
            ),
            state = PermissionMatrixState(
                levels = mapOf("AGENDA" to PermissionLevel.EDIT, "FINANCE" to PermissionLevel.VIEW),
            ),
            onStateChange = {},
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun ModulePermissionMatrixReadOnlyPreview() {
    AppTheme {
        ModulePermissionMatrix(
            modules = listOf(
                PermissionModuleSpec("AGENDA", "Agenda"),
                PermissionModuleSpec("COMPANY", "Empresa"),
            ),
            state = PermissionMatrixState(levels = mapOf("AGENDA" to PermissionLevel.EDIT)),
            onStateChange = {},
            readOnly = true,
        )
    }
}
