package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.platform.encodeBitmapToPng
import br.com.codecacto.kmplib.qr.QrCode
import br.com.codecacto.kmplib.qr.QrEncodeResult
import br.com.codecacto.kmplib.qr.QrErrorCorrection
import br.com.codecacto.kmplib.qr.encodeQr
import kotlin.math.floor

/**
 * Textos do [QrCodeView] (i18n injetável, defaults pt-BR).
 *
 * [contentDescription] existe porque um QR Code é **puramente visual**: para quem usa leitor de tela,
 * a imagem sem descrição é um retângulo silencioso. O texto default não revela o conteúdo (que pode
 * ser um payload técnico), só diz o que é.
 */
data class QrCodeTexts(
    val contentDescription: String = "Código QR",
    val tooLongMessage: String = "Conteúdo muito grande para um único QR Code",
)

/**
 * Desenha um [QrCode] já codificado.
 *
 * Este é o overload **preferido** quando o app já tem o símbolo (por exemplo, porque consultou a
 * capacidade antes com `qrCodeFits`): a codificação não se repete a cada recomposição.
 *
 * A quiet zone **já está embutida** no [qrCode] e é desenhada com a cor de fundo — o componente não
 * "aparenta" margem com padding, ele desenha a margem que o padrão exige. Reduzir isso é o caminho
 * conhecido para um QR que lê no aparelho de quem testou e falha no do usuário.
 *
 * O módulo é desenhado em **pixels inteiros** (o lado é arredondado para baixo e o desenho é
 * centralizado): meio pixel de arredondamento por módulo acumula ao longo de 100+ módulos e produz
 * linhas de espessura irregular, que é o que faz a câmera perder a grade.
 *
 * @param size lado do quadrado desenhado (o QR é sempre quadrado).
 * @param foregroundColor cor dos módulos escuros. Default: `onSurface` do tema.
 * @param backgroundColor cor do fundo **e da quiet zone**. Default: `surface` do tema.
 *   **Precisa ser claro e contrastante:** o leitor procura módulos escuros sobre fundo claro, e
 *   inverter (claro sobre escuro) faz muitos leitores não decodificarem.
 */
@Composable
fun QrCodeView(
    qrCode: QrCode,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    foregroundColor: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    texts: QrCodeTexts = QrCodeTexts(),
) {
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = texts.contentDescription },
    ) {
        Canvas(modifier = Modifier.size(size)) {
            drawQrCode(qrCode, foregroundColor, backgroundColor)
        }
    }
}

/**
 * Codifica [value] e desenha, num passo só.
 *
 * A codificação é memoizada por conteúdo/nível (`remember`), então recompor não recodifica.
 *
 * **Conteúdo que não cabe não vira exceção nem tela quebrada:** o composable chama
 * [onTooLong] (quando informado) e desenha só o fundo. Ainda assim, a decisão certa é conferir com
 * `qrCodeFits` **antes** de chegar aqui — a tela precisa oferecer o caminho alternativo (arquivo),
 * e isso é decisão de produto, não de componente.
 */
@Composable
fun QrCodeView(
    value: String,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
    foregroundColor: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    maxVersion: Int = QrCode.MAX_VERSION,
    texts: QrCodeTexts = QrCodeTexts(),
    onTooLong: ((QrEncodeResult.TooLong) -> Unit)? = null,
) {
    val result = remember(value, errorCorrection, maxVersion) {
        encodeQr(value, errorCorrection = errorCorrection, maxVersion = maxVersion)
    }

    when (result) {
        is QrEncodeResult.Success -> QrCodeView(
            qrCode = result.qrCode,
            modifier = modifier,
            size = size,
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor,
            texts = texts,
        )

        is QrEncodeResult.TooLong -> {
            remember(result) { onTooLong?.invoke(result) }
            Box(
                modifier = modifier
                    .size(size)
                    .clearAndSetSemantics { contentDescription = texts.tooLongMessage },
            ) {
                Canvas(modifier = Modifier.size(size)) {
                    drawRect(color = backgroundColor)
                }
            }
        }
    }
}

/**
 * Desenha o símbolo no [DrawScope] atual, ocupando o menor lado disponível.
 *
 * Exposto para quem compõe o próprio layout (colocar o QR dentro de um cartão desenhado, por exemplo)
 * sem passar pelo [QrCodeView].
 */
fun DrawScope.drawQrCode(
    qrCode: QrCode,
    foregroundColor: Color,
    backgroundColor: Color,
) {
    drawRect(color = backgroundColor)

    val side = minOf(this.size.width, this.size.height)
    val modulePx = floor(side / qrCode.size)
    if (modulePx < 1f) {
        // Sem pixel inteiro por módulo o símbolo sai ilegível; melhor não desenhar lixo.
        return
    }
    val drawnSide = modulePx * qrCode.size
    val originX = (this.size.width - drawnSide) / 2f
    val originY = (this.size.height - drawnSide) / 2f

    for (y in 0 until qrCode.size) {
        var x = 0
        while (x < qrCode.size) {
            if (!qrCode.isDark(x, y)) {
                x++
                continue
            }
            // Agrupa módulos escuros vizinhos numa só faixa: menos operações de desenho e, sobretudo,
            // sem costura clara entre retângulos adjacentes por arredondamento de anti-aliasing.
            var runEnd = x
            while (runEnd + 1 < qrCode.size && qrCode.isDark(runEnd + 1, y)) runEnd++
            val runLength = runEnd - x + 1
            drawRect(
                color = foregroundColor,
                topLeft = Offset(originX + x * modulePx, originY + y * modulePx),
                size = Size(modulePx * runLength, modulePx),
            )
            x = runEnd + 1
        }
    }
}

/**
 * Renderiza o símbolo **off-screen para PNG** — o caminho para anexar, salvar ou compartilhar.
 *
 * Um `@Composable` não serve para isso (só existe dentro de composição e não devolve bytes), e é por
 * isso que a matriz é separada da renderização: o MESMO [QrCode] alimenta a tela e o arquivo.
 *
 * Mesmo padrão do `renderShareCardToPng`: `ImageBitmap` + `CanvasDrawScope` em `commonMain` puro, sem
 * `expect/actual` próprio — só o `encodeBitmapToPng` do módulo `platform` (Android `Bitmap.compress`,
 * iOS Skia). Não exige host de UI ativo, e **não precisa de fontes** (o QR é só retângulos).
 *
 * ```kotlin
 * val png = renderQrCodeToPng(qr, targetSizePx = 720)
 * getShareHandler().shareImage(png, "cofre.png")
 * ```
 *
 * @param targetSizePx lado desejado em pixels. O tamanho final é **ajustado para baixo** ao múltiplo
 *   inteiro do número de módulos (um QR de 29 módulos pedido em 720px sai em 696px): módulo com
 *   tamanho fracionário é a causa clássica de "o PNG não lê, mas na tela lia".
 * @return bytes do PNG.
 */
fun renderQrCodeToPng(
    qrCode: QrCode,
    targetSizePx: Int = 720,
    foregroundColor: Color = Color.Black,
    backgroundColor: Color = Color.White,
): ByteArray {
    require(targetSizePx > 0) { "targetSizePx deve ser > 0" }

    val modulePx = maxOf(1, targetSizePx / qrCode.size)
    val sizePx = modulePx * qrCode.size

    val bitmap = ImageBitmap(sizePx, sizePx)
    val canvas = ComposeCanvas(bitmap)

    CanvasDrawScope().draw(
        density = Density(density = 1f, fontScale = 1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(sizePx.toFloat(), sizePx.toFloat()),
    ) {
        drawQrCode(qrCode, foregroundColor, backgroundColor)
    }

    return encodeBitmapToPng(bitmap)
}

/**
 * Codifica e renderiza para PNG, ou devolve `null` se o conteúdo não couber.
 *
 * O `null` é deliberado: gerar imagem de "erro" para compartilhar seria pior que não gerar.
 */
fun renderQrCodeToPngOrNull(
    value: String,
    errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
    targetSizePx: Int = 720,
    maxVersion: Int = QrCode.MAX_VERSION,
    foregroundColor: Color = Color.Black,
    backgroundColor: Color = Color.White,
): ByteArray? {
    val result = encodeQr(value, errorCorrection = errorCorrection, maxVersion = maxVersion)
    val qrCode = (result as? QrEncodeResult.Success)?.qrCode ?: return null
    return renderQrCodeToPng(qrCode, targetSizePx, foregroundColor, backgroundColor)
}
