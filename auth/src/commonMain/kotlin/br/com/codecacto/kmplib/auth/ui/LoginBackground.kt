package br.com.codecacto.kmplib.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Imagem de FUNDO do `LoginScreen` (2.299.0) — o login "com foto" que o design pede (App do Personal,
 * item 19), sem o app reescrever a tela e perder o padrão da fábrica (usuário ou e-mail + senha com o
 * olho, "Cadastrar-se", social embaixo, esqueci a senha, termos).
 *
 * Como fica: a imagem cobre a tela de borda a borda (por baixo das barras do sistema), um **degradê**
 * escuro por cima garante contraste para as barras e para as bordas da foto, e o formulário inteiro vai
 * num **cartão opaco** na cor de superfície do tema — o texto nunca fica direto sobre a foto, que é o que
 * o WCAG não deixa garantir (o contraste de texto sobre imagem varia ponto a ponto). Sem `background`, o
 * login é exatamente o de antes.
 *
 * A imagem é DECORATIVA: não tem descrição para o leitor de tela (a marca e os campos já dizem de quem é
 * a tela). Use foto sem texto dentro.
 *
 * @property painter a imagem. De recurso do app: [loginBackground] (`DrawableResource`).
 * @property contentScale default `Crop` = cobre a tela sem distorcer (corta as sobras).
 * @property alignment o ponto da foto que fica à vista quando ela é cortada (ex.: `TopCenter` para rosto).
 * @property scrimColor cor do degradê. Default preto: as barras do sistema ficam com ícones CLAROS.
 *   Com um degradê claro (branco), os ícones viram escuros sozinhos.
 * @property scrimTopAlpha opacidade do degradê no topo (0..1, default 0,25) — onde ficam relógio e bateria.
 * @property scrimBottomAlpha opacidade do degradê embaixo (0..1, default 0,60).
 * @property cardColor cor do cartão do formulário; `null` = a superfície do `LoginColors` (opaca).
 *   Mantenha opaca: cartão translúcido devolve o texto para cima da foto.
 * @property cardElevation sombra do cartão.
 */
@Immutable
data class LoginBackground(
    val painter: Painter,
    val contentScale: ContentScale = ContentScale.Crop,
    val alignment: Alignment = Alignment.Center,
    val scrimColor: Color = Color.Black,
    val scrimTopAlpha: Float = LoginBackgroundDefaults.SCRIM_TOP_ALPHA,
    val scrimBottomAlpha: Float = LoginBackgroundDefaults.SCRIM_BOTTOM_ALPHA,
    val cardColor: Color? = null,
    val cardElevation: Dp = LoginBackgroundDefaults.CardElevation,
)

/** Medidas padrão do login com fundo. */
object LoginBackgroundDefaults {
    const val SCRIM_TOP_ALPHA: Float = 0.25f
    const val SCRIM_BOTTOM_ALPHA: Float = 0.60f
    val CardElevation: Dp = 8.dp
    val CardCornerRadius: Dp = 24.dp

    /** Folga entre o topo da área segura e o cartão. */
    val CardTopSpacing: Dp = 32.dp

    /** `testTag` do cartão do formulário (flow Maestro / teste). */
    const val CARD_TEST_TAG: String = "login-cartao"

    /** `testTag` da imagem de fundo. */
    const val IMAGE_TEST_TAG: String = "login-fundo"
}

/** [LoginBackground] a partir de um recurso do app (`Res.drawable.login_fundo`). */
@Composable
fun loginBackground(
    image: DrawableResource,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    scrimColor: Color = Color.Black,
    scrimTopAlpha: Float = LoginBackgroundDefaults.SCRIM_TOP_ALPHA,
    scrimBottomAlpha: Float = LoginBackgroundDefaults.SCRIM_BOTTOM_ALPHA,
    cardColor: Color? = null,
    cardElevation: Dp = LoginBackgroundDefaults.CardElevation,
): LoginBackground = LoginBackground(
    painter = painterResource(image),
    contentScale = contentScale,
    alignment = alignment,
    scrimColor = scrimColor,
    scrimTopAlpha = scrimTopAlpha,
    scrimBottomAlpha = scrimBottomAlpha,
    cardColor = cardColor,
    cardElevation = cardElevation,
)

/**
 * As duas cores do degradê (topo, base), com a opacidade limitada a 0..1 — regra pura, testável.
 * Opacidade fora da faixa (ou NaN) não pode apagar o degradê nem estourar a cor.
 */
internal fun loginScrimColors(background: LoginBackground): Pair<Color, Color> {
    fun clamp(alpha: Float): Float = if (alpha.isNaN()) 0f else alpha.coerceIn(0f, 1f)
    return background.scrimColor.copy(alpha = clamp(background.scrimTopAlpha)) to
        background.scrimColor.copy(alpha = clamp(background.scrimBottomAlpha))
}

/**
 * A cor que as barras do sistema "veem" atrás de si com o fundo — a do degradê, OPACA: é dela que sai
 * ícone claro (degradê escuro) ou escuro (degradê claro). A foto por baixo é desconhecida, e o degradê é
 * justamente o que torna a escolha previsível.
 */
internal fun loginSystemBarsBackground(background: LoginBackground): Color = background.scrimColor.copy(alpha = 1f)

/** Imagem + degradê, ocupando todo o espaço do pai (vai atrás do formulário). */
@Composable
internal fun LoginBackgroundLayer(background: LoginBackground) {
    val (top, bottom) = loginScrimColors(background)
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = background.painter,
            contentDescription = null,
            contentScale = background.contentScale,
            alignment = background.alignment,
            modifier = Modifier.fillMaxSize().testTag(LoginBackgroundDefaults.IMAGE_TEST_TAG),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(top, bottom))),
        )
    }
}
