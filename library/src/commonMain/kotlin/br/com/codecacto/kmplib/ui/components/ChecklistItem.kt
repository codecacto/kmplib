package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Textos do [ChecklistItem] (i18n; defaults pt-BR).
 *
 * [checkedState] e [uncheckedState] são a **descrição de estado anunciada pelo leitor de tela**
 * (`stateDescription`), substituindo o genérico "marcado/não marcado" do Material por algo do
 * domínio: `ChecklistItemTexts(checkedState = "Embarcado", uncheckedState = "Aguardando")` faz o
 * TalkBack/VoiceOver ler *"Ana Beatriz, Rua das Flores 123, Embarcado"*.
 */
data class ChecklistItemTexts(
    val checkedState: String = "Marcado",
    val uncheckedState: String = "Não marcado",
)

/** Valores padrão do [ChecklistItem] (alvo de toque, ícones de estado). */
object ChecklistItemDefaults {
    /**
     * Altura mínima do item — **64dp**, acima dos 48dp mínimos do Material, porque este componente
     * nasceu para uso "de campo" (motorista no trânsito, profissional de luva): o alvo é o item
     * INTEIRO e precisa ser acertado sem mirar.
     */
    val MinHeight: Dp = 64.dp

    /** Ícone do estado marcado (círculo preenchido com "✓"). */
    val CheckedIcon: ImageVector = Icons.Filled.CheckCircle

    /** Ícone do estado não marcado (círculo vazado). */
    val UncheckedIcon: ImageVector = Icons.Outlined.RadioButtonUnchecked

    /** Opacidade do fundo tingido pelo tom (fundo "suave" que não compete com o texto). */
    const val ToneContainerAlpha: Float = 0.14f

    /** Opacidade da borda tingida pelo tom. */
    const val ToneBorderAlpha: Float = 0.45f

    /** Opacidade do conteúdo quando o item está desabilitado. */
    const val DisabledContentAlpha: Float = 0.45f
}

/**
 * Tom **efetivo** do item conforme o estado. Lógica pura (sem Compose) — testável.
 */
fun checklistItemTone(
    checked: Boolean,
    checkedTone: StatusTone,
    uncheckedTone: StatusTone,
): StatusTone = if (checked) checkedTone else uncheckedTone

/**
 * `true` quando o tom deve **tingir o fundo/borda** do item. `NEUTRAL` significa deliberadamente
 * "sem tom" — fundo de superfície comum e borda de contorno, para que só os itens que **significam
 * algo** (marcado, pendente crítico) chamem a atenção numa lista longa. Lógica pura — testável.
 */
fun checklistItemUsesToneContainer(tone: StatusTone): Boolean = tone != StatusTone.NEUTRAL

/**
 * Descrição de estado anunciada pelo leitor de tela. Lógica pura — testável.
 */
fun checklistItemStateDescription(checked: Boolean, texts: ChecklistItemTexts): String =
    if (checked) texts.checkedState else texts.uncheckedState

/**
 * **Item de checklist / check-in em que o ITEM INTEIRO é o alvo de toque** — 1 toque marca, o 2º
 * desfaz, **sem diálogo** no caminho feliz.
 *
 * É o componente de execução de listas do ecossistema: chamada/presença, check-in de evento,
 * conferência de inventário, embarque/desembarque de passageiros, checklist de vistoria. Nasceu do
 * RNF "1-2 toques, sem digitação, atenção dividida" (app do motorista) mas é **domínio-agnóstico**:
 * recebe título, subtítulo e tons, nunca conceitos de um app.
 *
 * ### Por que não um `Checkbox`
 * Um checkbox de 20dp obriga a mirar. Aqui o alvo é o **card inteiro** (mínimo
 * [ChecklistItemDefaults.MinHeight] = 64dp) e o estado é comunicado **redundantemente** por
 * ícone **e** por tom de fundo/borda — nunca só por cor (WCAG 1.4.1).
 *
 * ### Acessibilidade (padrão-ouro)
 * - [Modifier.toggleable] com [Role.Checkbox]: um único nó semântico para o item todo (o leitor de
 *   tela anuncia título + subtítulo + estado numa tacada, e a ação de alternar fica no item).
 * - `stateDescription` do domínio via [ChecklistItemTexts] (ex.: "Embarcado" / "Aguardando"), em vez
 *   do genérico "marcado".
 * - Retorno **háptico** no toque ([hapticFeedback]): confirma o acerto sem exigir olhar a tela.
 *
 * ### Tons
 * Tudo vem de tokens (`AppTheme`/[statusToneColor]) — zero cor hardcoded. `NEUTRAL` = sem tingimento
 * (ver [checklistItemUsesToneContainer]); qualquer outro tom tinge fundo e borda. Isso cobre os dois
 * usos reais sem parâmetro extra: `aguardando ⇄ embarcado` (`NEUTRAL` ⇄ `SUCCESS`) e "pendência
 * crítica" (`uncheckedTone = DANGER`, item vermelho enquanto não for resolvido).
 *
 * ```kotlin
 * ChecklistItem(
 *     title = passageiro.nome,
 *     subtitle = passageiro.ponto,
 *     checked = passageiro.embarcado,
 *     onCheckedChange = { vm.dispatch(Action.AlternarEmbarque(passageiro.id)) },
 *     texts = ChecklistItemTexts(checkedState = "Embarcado", uncheckedState = "Aguardando"),
 * )
 * ```
 *
 * @param title texto principal (ex.: nome da pessoa, item do checklist).
 * @param checked estado atual (o componente é **stateless**: quem decide é o State do ViewModel).
 * @param onCheckedChange novo estado após o toque. Marcar **não** é ação destrutiva: não peça
 *   confirmação aqui (o desfazer é o próprio 2º toque).
 * @param subtitle linha secundária opcional (ex.: endereço/ponto, código, observação).
 * @param checkedTone tom quando marcado (default [StatusTone.SUCCESS]).
 * @param uncheckedTone tom quando não marcado (default [StatusTone.NEUTRAL] = sem tingimento).
 * @param enabled quando `false`, o item não alterna (lista somente-leitura, ex.: detalhe histórico).
 * @param minHeight altura mínima do alvo de toque.
 * @param showStateIcon exibe o ícone de estado à esquerda. Desligue só se o [trailing] já comunicar
 *   o estado — o estado NUNCA pode ficar apenas na cor.
 * @param checkedIcon/[uncheckedIcon] ícones de estado.
 * @param hapticFeedback retorno tátil ao alternar (default `true`).
 * @param contentDescription descrição adicional anunciada pelo leitor de tela junto ao conteúdo;
 *   use quando o texto visível não for autoexplicativo.
 * @param texts textos i18n (descrições de estado).
 * @param leading slot opcional **antes** dos textos (ex.: [Avatar], foto, ícone da categoria).
 * @param trailing slot opcional **depois** dos textos (ex.: horário, [StatusBadge], contador).
 */
@Composable
fun ChecklistItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    checkedTone: StatusTone = StatusTone.SUCCESS,
    uncheckedTone: StatusTone = StatusTone.NEUTRAL,
    enabled: Boolean = true,
    minHeight: Dp = ChecklistItemDefaults.MinHeight,
    showStateIcon: Boolean = true,
    checkedIcon: ImageVector = ChecklistItemDefaults.CheckedIcon,
    uncheckedIcon: ImageVector = ChecklistItemDefaults.UncheckedIcon,
    hapticFeedback: Boolean = true,
    contentDescription: String? = null,
    texts: ChecklistItemTexts = ChecklistItemTexts(),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tone = checklistItemTone(checked, checkedTone, uncheckedTone)
    val toneColor = statusToneColor(tone)
    val tinted = checklistItemUsesToneContainer(tone)

    val container: Color = if (tinted) {
        toneColor.copy(alpha = ChecklistItemDefaults.ToneContainerAlpha)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border: Color = if (tinted) {
        toneColor.copy(alpha = ChecklistItemDefaults.ToneBorderAlpha)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val haptic = LocalHapticFeedback.current
    val stateDesc = checklistItemStateDescription(checked, texts)
    val extraDescription = contentDescription
    val contentAlpha = if (enabled) 1f else ChecklistItemDefaults.DisabledContentAlpha

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { novo ->
                        if (hapticFeedback) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onCheckedChange(novo)
                    },
                )
                .semantics {
                    stateDescription = stateDesc
                    if (extraDescription != null) this.contentDescription = extraDescription
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showStateIcon) {
                Icon(
                    imageVector = if (checked) checkedIcon else uncheckedIcon,
                    contentDescription = null, // estado vai em stateDescription (nó único)
                    tint = toneColor,
                    modifier = Modifier.size(28.dp),
                )
            }

            leading?.invoke()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            trailing?.invoke()
        }
    }
}
