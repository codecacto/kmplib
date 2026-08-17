package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact

// ---------------------------------------------------------------------------
// Radar (teia) para Compose Multiplatform — commonMain puro, sem lib de gráficos.
//
// Par mobile do `DomainRadarChart` da weblib (0.118.x): o app e o portal do cliente
// mostram o MESMO desenho dos 7 domínios do ICTC, e uma reavaliação (T0 × T1) é
// uma segunda série sobre a mesma teia.
//
// A geometria mora em `RadarChartGeometry.kt`, provada em teste sem tela — porque o
// que dá errado num radar é aritmética, não pincel.
// ---------------------------------------------------------------------------

/**
 * Uma série do radar — um polígono sobre a teia.
 *
 * @param nome o que a legenda diz ("Primeira avaliação", "Reavaliação").
 * @param valores um por eixo, na MESMA ordem dos eixos.
 * @param cor quando `null`, sai a cor da série pela ordem (primária, depois terciária).
 */
data class RadarSeries(
    val nome: String,
    val valores: List<Double>,
    val cor: Color? = null,
)

/**
 * Gráfico de radar com 3+ eixos e 1 ou 2 séries.
 *
 * ## A cor é da SÉRIE, nunca do eixo
 *
 * É o erro clássico do radar: pintar cada vértice de uma cor "porque cada domínio tem a sua". O
 * polígono é um objeto só — sete cores nele não codificam nada e destroem a comparação entre duas
 * avaliações, que é justamente o que o desenho existe para mostrar.
 *
 * ## Duas séries é o teto, de propósito
 *
 * Três polígonos sobrepostos numa teia de sete pontas não se distinguem em tela de celular. Um
 * histórico maior que isso é lista, não radar.
 *
 * ```kotlin
 * RadarChart(
 *     eixos = listOf("Regulação Emocional", "Reatividade à Crítica", …),
 *     series = listOf(
 *         RadarSeries("Primeira avaliação", listOf(62.0, 44.0, …)),
 *         RadarSeries("Reavaliação", listOf(72.0, 58.0, …)),
 *     ),
 *     maximo = 100.0,
 * )
 * ```
 *
 * @param eixos rótulos dos vértices (mínimo 3 — com 2 não há área, e o desenho vira uma linha).
 * @param series 1 ou 2 séries; a partir da terceira, as demais são ignoradas.
 * @param maximo topo da escala. 100 para percentual, 5 para a escala Likert crua.
 * @param emptyMessage exibido quando não há eixos suficientes ou nenhuma série.
 */
@Composable
fun RadarChart(
    eixos: List<String>,
    series: List<RadarSeries>,
    modifier: Modifier = Modifier,
    maximo: Double = 100.0,
    aneisDaGrade: Int = 4,
    mostrarLegenda: Boolean = true,
    emptyMessage: String = "Sem dados para exibir.",
) {
    val seriesValidas = series.filter { it.valores.size == eixos.size }.take(2)
    if (eixos.size < 3 || seriesValidas.isEmpty()) {
        RadarVazio(emptyMessage, modifier)
        return
    }

    val compacto = LocalIsCompact.current
    val medidor = rememberTextMeasurer()

    val corGrade = MaterialTheme.colorScheme.outlineVariant
    val corRotulo = MaterialTheme.colorScheme.onSurfaceVariant
    val paleta = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
    )
    val cores = seriesValidas.mapIndexed { i, s -> s.cor ?: paleta[i % paleta.size] }

    val estiloDoRotulo = TextStyle(
        fontSize = if (compacto) 9.sp else 11.sp,
        color = corRotulo,
    )

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                // Um leitor de tela não lê um Canvas. A tabela do resultado continua sendo a fonte
                // acessível; aqui vai o resumo que evita o "imagem sem descrição".
                .semantics {
                    contentDescription = seriesValidas.joinToString(" · ") { serie ->
                        serie.nome + ": " + eixos.zip(serie.valores)
                            .joinToString(", ") { (eixo, valor) -> "$eixo ${valor.toInt()}" }
                    }
                },
        ) {
            // O raio útil desconta a faixa dos rótulos. 62% (76% no compacto, onde o rótulo é menor)
            // é a mesma proporção do par web — foi medida lá para o vértice não sair pela borda.
            val proporcao = if (compacto) 0.76f else 0.62f
            val raio = (size.minDimension / 2f) * proporcao
            val centroX = size.width / 2f
            val centroY = size.height / 2f

            desenharGrade(eixos.size, aneisDaGrade, raio, centroX, centroY, corGrade)

            seriesValidas.forEachIndexed { i, serie ->
                desenharSerie(serie.valores, maximo, raio, centroX, centroY, cores[i])
            }

            desenharRotulos(eixos, raio, centroX, centroY, medidor, estiloDoRotulo, compacto)
        }

        // A legenda é obrigatória com 2 séries: sem ela, "qual polígono é o de antes?" só se
        // responde por cor, e cor sozinha não é identidade.
        if (mostrarLegenda && seriesValidas.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                seriesValidas.forEachIndexed { i, serie ->
                    if (i > 0) Box(Modifier.size(width = 16.dp, height = 1.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(cores[i]),
                        )
                        Text(
                            text = serie.nome,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.desenharGrade(
    quantidadeDeEixos: Int,
    aneis: Int,
    raio: Float,
    centroX: Float,
    centroY: Float,
    cor: Color,
) {
    for (anel in 1..aneis) {
        val fracao = anel.toFloat() / aneis
        val pontos = verticesDaGrade(quantidadeDeEixos, fracao, raio, centroX, centroY)
        drawPath(caminhoFechado(pontos), color = cor, style = Stroke(width = 1f))
    }
    // Os raios que ligam o centro a cada vértice — é o que deixa claro que os eixos são discretos.
    verticesDaGrade(quantidadeDeEixos, 1f, raio, centroX, centroY).forEach { ponto ->
        drawLine(
            color = cor,
            start = androidx.compose.ui.geometry.Offset(centroX, centroY),
            end = androidx.compose.ui.geometry.Offset(ponto.x, ponto.y),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.desenharSerie(
    valores: List<Double>,
    maximo: Double,
    raio: Float,
    centroX: Float,
    centroY: Float,
    cor: Color,
) {
    val pontos = verticesDaSerie(valores, maximo, raio, centroX, centroY)
    val caminho = caminhoFechado(pontos)
    // Preenchimento translúcido para que duas séries sobrepostas continuem legíveis: opaco, a de
    // cima apaga a de baixo e a comparação — o motivo do desenho — se perde.
    drawPath(caminho, color = cor.copy(alpha = 0.22f))
    drawPath(caminho, color = cor, style = Stroke(width = 2f))
    pontos.forEach { ponto ->
        drawCircle(cor, radius = 3.5f, center = androidx.compose.ui.geometry.Offset(ponto.x, ponto.y))
    }
}

private fun DrawScope.desenharRotulos(
    eixos: List<String>,
    raio: Float,
    centroX: Float,
    centroY: Float,
    medidor: TextMeasurer,
    estilo: TextStyle,
    compacto: Boolean,
) {
    val angulos = angulosDosVertices(eixos.size)
    val folga = if (compacto) 10f else 16f
    eixos.forEachIndexed { i, rotulo ->
        val angulo = angulos[i]
        val linhas = quebrarRotuloDoVertice(rotulo, if (compacto) 12 else 16)
        val texto = linhas.joinToString("\n")
        val medida = medidor.measure(texto, estilo)

        val x = centroX + ((raio + folga) * kotlin.math.cos(angulo)).toFloat()
        val y = centroY + ((raio + folga) * kotlin.math.sin(angulo)).toFloat()

        // Ancorar pelo lado que aponta para fora é o que mantém o rótulo dentro da caixa: o do
        // vértice da direita cresce para a direita e sairia da tela.
        val deslocamentoX = when (ancoraDoRotulo(angulo)) {
            AncoraDoRotulo.INICIO -> 0f
            AncoraDoRotulo.CENTRO -> -medida.size.width / 2f
            AncoraDoRotulo.FIM -> -medida.size.width.toFloat()
        }
        val deslocamentoY = -medida.size.height / 2f +
            (deslocamentoVerticalDoRotulo(angulo) * medida.size.height / 3f)

        drawText(
            textLayoutResult = medida,
            topLeft = androidx.compose.ui.geometry.Offset(x + deslocamentoX, y + deslocamentoY),
        )
    }
}

private fun caminhoFechado(pontos: List<PontoDoRadar>): Path {
    val caminho = Path()
    pontos.forEachIndexed { i, ponto ->
        if (i == 0) caminho.moveTo(ponto.x, ponto.y) else caminho.lineTo(ponto.x, ponto.y)
    }
    caminho.close()
    return caminho
}

@Composable
private fun RadarVazio(mensagem: String, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = mensagem,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
