package br.com.codecacto.kmplib.ui.components

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------
// A geometria do radar, separada do desenho.
//
// Existe porque o Canvas não é testável em commonMain, mas o que pode dar errado
// num radar é aritmética: o vértice que sai fora da caixa, o valor negativo que
// vira ponta para dentro, o rótulo que estoura a borda. Tudo isso se prova aqui,
// sem tela.
//
// Espelha o `DomainRadarChart` da weblib (0.118.x) — mesma decisão de recortar o
// rótulo em duas linhas e mesmo raio proporcional —, para que o app e o portal do
// cliente mostrem o MESMO desenho dos 7 domínios do ICTC.
// ---------------------------------------------------------------------------

/** Um ponto no plano do gráfico, em pixels, relativo ao canto superior esquerdo da área. */
data class PontoDoRadar(val x: Float, val y: Float)

/**
 * O ângulo de cada vértice, em radianos, começando no TOPO e girando no sentido horário.
 *
 * Começar no topo não é estética: o primeiro domínio da lista é o que a pessoa lê primeiro, e num
 * radar que começa à direita ele cai no meio da lateral, onde ninguém procura.
 */
fun angulosDosVertices(quantidade: Int): List<Double> {
    if (quantidade <= 0) return emptyList()
    val passo = 2 * PI / quantidade
    return List(quantidade) { i -> -PI / 2 + i * passo }
}

/**
 * A fração do raio que um valor ocupa — sempre entre 0 e 1.
 *
 * Valor acima do máximo **satura** em vez de estourar a caixa, e valor negativo vira 0: um domínio
 * com score inválido não pode desenhar uma ponta para dentro do centro, que lê como "o oposto".
 */
fun fracaoDoRaio(valor: Double, maximo: Double): Float {
    if (maximo <= 0.0) return 0f
    return max(0.0, min(1.0, valor / maximo)).toFloat()
}

/**
 * Os vértices do polígono de uma série.
 *
 * @param valores um por eixo, na ordem dos eixos.
 * @param maximo o topo da escala (100 para percentual, 5 para a escala Likert crua).
 * @param raio o raio útil, já descontado o espaço dos rótulos.
 */
fun verticesDaSerie(
    valores: List<Double>,
    maximo: Double,
    raio: Float,
    centroX: Float,
    centroY: Float,
): List<PontoDoRadar> {
    val angulos = angulosDosVertices(valores.size)
    return valores.mapIndexed { i, valor ->
        val distancia = raio * fracaoDoRaio(valor, maximo)
        PontoDoRadar(
            x = centroX + (distancia * cos(angulos[i])).toFloat(),
            y = centroY + (distancia * sin(angulos[i])).toFloat(),
        )
    }
}

/**
 * Os vértices de um anel da grade (o "polígono de fundo" em cada fração da escala).
 *
 * A grade do radar é poligonal, não circular: um círculo sugere uma escala contínua entre eixos que
 * não existe — entre "Autoconsciência" e "Flexibilidade" não há nada.
 */
fun verticesDaGrade(
    quantidadeDeEixos: Int,
    fracao: Float,
    raio: Float,
    centroX: Float,
    centroY: Float,
): List<PontoDoRadar> =
    angulosDosVertices(quantidadeDeEixos).map { angulo ->
        PontoDoRadar(
            x = centroX + (raio * fracao * cos(angulo)).toFloat(),
            y = centroY + (raio * fracao * sin(angulo)).toFloat(),
        )
    }

/**
 * Quebra o rótulo do vértice em no máximo duas linhas, no espaço mais próximo do meio.
 *
 * "Flexibilidade Comportamental" numa linha só é mais largo que o gráfico inteiro no celular — e o
 * que acontece então não é o texto encolher, é o rótulo sair pela borda. Quebrar no espaço do meio
 * (e não no primeiro) dá duas linhas de comprimento parecido, que é o que ocupa menos largura.
 *
 * Palavra única não se quebra: cortar "Autoconsciência" ao meio inventa uma sílaba que não existe.
 */
fun quebrarRotuloDoVertice(rotulo: String, limiteDeCaracteres: Int = 14): List<String> {
    val limpo = rotulo.trim()
    if (limpo.length <= limiteDeCaracteres || !limpo.contains(' ')) return listOf(limpo)

    val meio = limpo.length / 2
    var melhorCorte = -1
    limpo.forEachIndexed { i, c ->
        if (c == ' ') {
            val distanciaAtual = kotlin.math.abs(i - meio)
            val distanciaMelhor = if (melhorCorte < 0) Int.MAX_VALUE else kotlin.math.abs(melhorCorte - meio)
            if (distanciaAtual < distanciaMelhor) melhorCorte = i
        }
    }
    if (melhorCorte < 0) return listOf(limpo)
    return listOf(limpo.substring(0, melhorCorte), limpo.substring(melhorCorte + 1))
}

/**
 * O alinhamento horizontal do rótulo em função de onde o vértice caiu.
 *
 * O rótulo do vértice da direita cresce para a direita e sai da tela; o da esquerda faz o mesmo do
 * outro lado. Ancorá-lo pelo lado que aponta para fora é o que mantém tudo dentro da caixa.
 */
enum class AncoraDoRotulo { INICIO, CENTRO, FIM }

fun ancoraDoRotulo(anguloEmRadianos: Double): AncoraDoRotulo {
    val cosseno = cos(anguloEmRadianos)
    return when {
        cosseno > 0.25 -> AncoraDoRotulo.INICIO
        cosseno < -0.25 -> AncoraDoRotulo.FIM
        else -> AncoraDoRotulo.CENTRO
    }
}

/** Seno exposto para o desenho posicionar o rótulo acima/abaixo do vértice sem recalcular ângulo. */
fun deslocamentoVerticalDoRotulo(anguloEmRadianos: Double): Float = sin(anguloEmRadianos).toFloat()
