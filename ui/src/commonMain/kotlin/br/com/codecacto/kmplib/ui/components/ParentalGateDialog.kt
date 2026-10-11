package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_cancel
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_instruction
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_number_0
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_number_1
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_number_2
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_number_3
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_number_4
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_number_5
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_number_6
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_number_7
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_number_8
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_number_9
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_progress
import br.com.codecacto.kmplib.generated.resources.kmplib_parental_gate_title
import br.com.codecacto.kmplib.platform.audience.ParentalGate
import br.com.codecacto.kmplib.platform.automation.DialogTestTags
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import kotlin.random.Random

/** Ids do portão de pais para a automação (Maestro). Desde 2.259.0. */
object ParentalGateTestTags {
    /** O conteúdo do portão (dentro do `dialogo`). */
    const val CONTAINER: String = "portao-pais"

    /** O desafio por extenso ("sete, dois, nove"). */
    const val DESAFIO: String = "portao-pais-desafio"

    /** Cancelar — fecha o portão sem sair do app. */
    const val BTN_CANCELAR: String = "portao-pais-btn-cancelar"

    /**
     * Tecla do dígito [digit] (0–9): `portao-pais-digito-7`. O desafio só usa 1–9, então
     * `portao-pais-digito-0` é SEMPRE uma resposta errada — é o toque que o flow usa para provar
     * que a criança não sai do app.
     */
    fun digito(digit: Int): String = "portao-pais-digito-$digit"
}

/** Andamento da resposta ao [ParentalGateChallenge]. */
enum class ParentalGateCheck { INCOMPLETE, PASSED, FAILED }

/**
 * O desafio do portão de pais: tocar, na ordem, os dígitos escritos POR EXTENSO (2.259.0).
 *
 * É o padrão que as lojas aceitam para a categoria infantil: exige ler palavras e associá-las a
 * números — o que criança pequena não faz — sem pedir nenhum dado a ninguém.
 *
 * @property digits a sequência pedida. [random] sorteia [DEFAULT_LENGTH] dígitos DISTINTOS de 1 a 9.
 */
@Immutable
class ParentalGateChallenge(val digits: List<Int>) {
    init {
        require(digits.isNotEmpty() && digits.all { it in 0..9 }) { "Dígitos de 0 a 9, ao menos um" }
    }

    /** Errou um dígito = [ParentalGateCheck.FAILED] na hora; não há segunda chance no mesmo desafio. */
    fun check(entered: List<Int>): ParentalGateCheck = when {
        entered.size > digits.size -> ParentalGateCheck.FAILED
        entered != digits.subList(0, entered.size) -> ParentalGateCheck.FAILED
        entered.size == digits.size -> ParentalGateCheck.PASSED
        else -> ParentalGateCheck.INCOMPLETE
    }

    companion object {
        const val DEFAULT_LENGTH: Int = 3

        fun random(random: Random = Random.Default, length: Int = DEFAULT_LENGTH): ParentalGateChallenge {
            require(length in 1..9) { "De 1 a 9 dígitos" }
            return ParentalGateChallenge((1..9).shuffled(random).take(length))
        }
    }
}

/**
 * Textos do portão de pais. [numberWords] = as palavras de 0 a 9, no idioma da tela (o andamento
 * lido pelo leitor de tela, "1 de 3 números tocados", vem do recurso da lib). Default: [rememberParentalGateTexts] (pt-BR, en, es, pt-PT).
 */
@Immutable
data class ParentalGateTexts(
    val title: String,
    val instruction: String,
    val cancel: String,
    val numberWords: List<String>,
) {
    init {
        require(numberWords.size == 10) { "numberWords precisa das 10 palavras, de zero a nove" }
    }

    /** O desafio por extenso: "sete, dois, nove". */
    fun spell(challenge: ParentalGateChallenge): String =
        challenge.digits.joinToString(", ") { numberWords[it] }
}

@Composable
fun rememberParentalGateTexts(): ParentalGateTexts {
    val words = listOf(
        kmpStringResource(Res.string.kmplib_parental_gate_number_0),
        kmpStringResource(Res.string.kmplib_parental_gate_number_1),
        kmpStringResource(Res.string.kmplib_parental_gate_number_2),
        kmpStringResource(Res.string.kmplib_parental_gate_number_3),
        kmpStringResource(Res.string.kmplib_parental_gate_number_4),
        kmpStringResource(Res.string.kmplib_parental_gate_number_5),
        kmpStringResource(Res.string.kmplib_parental_gate_number_6),
        kmpStringResource(Res.string.kmplib_parental_gate_number_7),
        kmpStringResource(Res.string.kmplib_parental_gate_number_8),
        kmpStringResource(Res.string.kmplib_parental_gate_number_9),
    )
    return ParentalGateTexts(
        title = kmpStringResource(Res.string.kmplib_parental_gate_title),
        instruction = kmpStringResource(Res.string.kmplib_parental_gate_instruction),
        cancel = kmpStringResource(Res.string.kmplib_parental_gate_cancel),
        numberWords = words,
    )
}

/**
 * **Portão de pais** — o diálogo (2.259.0). Desafio por extenso + teclado de 0 a 9.
 *
 * Errar um dígito fecha o portão com `onResult(false)` (a ação não acontece e a criança continua
 * no app); acertar a sequência inteira devolve `onResult(true)`. Cancelar, voltar ou tocar fora =
 * `false`. Nada é coletado nem guardado.
 *
 * **Quase nunca se chama direto**: em app infantil (`KmpLibAudience.configure(AppAudience.KIDS)`),
 * o [ParentalGateHost] — que o `AppTheme` já instala — mostra este diálogo sempre que uma saída da
 * lib (ou um `ParentalGate.guard { }` do app) pede passagem. Use direto só para desenhar o portão
 * dentro de um fluxo próprio.
 *
 * Ids: [ParentalGateTestTags] + `dialogo` ([DialogTestTags.CONTAINER]).
 */
@Composable
fun ParentalGateDialog(
    onResult: (passed: Boolean) -> Unit,
    challenge: ParentalGateChallenge = remember { ParentalGateChallenge.random() },
    texts: ParentalGateTexts = rememberParentalGateTexts(),
) {
    var entered by remember(challenge) { mutableStateOf(emptyList<Int>()) }
    var finished by remember(challenge) { mutableStateOf(false) }

    fun tap(digit: Int) {
        if (finished) return
        val next = entered + digit
        entered = next
        when (challenge.check(next)) {
            ParentalGateCheck.PASSED -> { finished = true; onResult(true) }
            ParentalGateCheck.FAILED -> { finished = true; onResult(false) }
            ParentalGateCheck.INCOMPLETE -> Unit
        }
    }

    AppDialog(
        onDismissRequest = { if (!finished) { finished = true; onResult(false) } },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .testTag(DialogTestTags.CONTAINER),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .testTag(ParentalGateTestTags.CONTAINER)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = texts.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag(DialogTestTags.TITULO)
                        .semantics { heading() },
                )
                Text(
                    text = texts.instruction,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(DialogTestTags.MENSAGEM),
                )
                Text(
                    text = texts.spell(challenge),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(ParentalGateTestTags.DESAFIO),
                )
                ProgressDots(
                    entered = entered.size,
                    total = challenge.digits.size,
                    description = kmpStringResource(
                        Res.string.kmplib_parental_gate_progress,
                        entered.size,
                        challenge.digits.size,
                    ),
                )
                Keypad(onDigit = ::tap)
                TextButton(
                    onClick = { if (!finished) { finished = true; onResult(false) } },
                    modifier = Modifier.testTag(ParentalGateTestTags.BTN_CANCELAR),
                ) { Text(texts.cancel) }
            }
        }
    }
}

@Composable
private fun ProgressDots(entered: Int, total: Int, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        repeat(total) { i ->
            val filled = i < entered
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .then(
                        if (filled) {
                            Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        },
                    ),
            )
        }
    }
}

private val KEYPAD_ROWS = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(0))

@Composable
private fun Keypad(onDigit: (Int) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KEYPAD_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    FilledTonalButton(
                        onClick = { onDigit(digit) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .sizeIn(minWidth = 64.dp, minHeight = 56.dp)
                            .testTag(ParentalGateTestTags.digito(digit)),
                    ) {
                        Text(digit.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

/**
 * Desenha o portão de pais quando alguém pede passagem ([ParentalGate.pending]) — 2.259.0.
 *
 * O `AppTheme` da kmplib já o instala na raiz; app com tema próprio chama `ParentalGateHost()` uma
 * vez, junto da raiz. Com mais de um host na árvore, só o primeiro desenha. Fora do modo infantil
 * nunca há pedido e ele não desenha nada.
 */
@Composable
fun ParentalGateHost() {
    val token = remember { Any() }
    DisposableEffect(token) {
        ParentalGate.attachHost(token)
        onDispose { ParentalGate.detachHost(token) }
    }
    val primary by ParentalGate.primaryHost.collectAsState()
    val pending by ParentalGate.pending.collectAsState()
    val request = pending
    if (primary === token && request != null) {
        key(request.id) {
            ParentalGateDialog(onResult = { passed -> ParentalGate.resolve(request, passed) })
        }
    }
}

/**
 * [UriHandler] que passa cada link pelo [ParentalGate] — o `AppTheme` o provê em
 * `LocalUriHandler`, para link em texto (`LinkAnnotation`) também respeitar o modo infantil.
 */
internal class ParentalGatedUriHandler(private val delegate: UriHandler) : UriHandler {
    override fun openUri(uri: String) {
        if (!ParentalGate.isRequired) {
            delegate.openUri(uri)
            return
        }
        ParentalGate.guard {
            try {
                delegate.openUri(uri)
            } catch (e: Exception) {
                AppLogger.e("ParentalGate", "Falha ao abrir link depois do portão de pais", e)
            }
        }
    }
}
