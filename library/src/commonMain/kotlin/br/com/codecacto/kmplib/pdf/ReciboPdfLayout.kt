package br.com.codecacto.kmplib.pdf

/**
 * Helpers **compartilhados** (commonMain) do renderer de PDF do recibo, usados por
 * Android (`PdfDocument`) e iOS (`UIGraphicsPDFRenderer`) para garantir **paridade
 * visual** com o renderer da weblib (`@react-pdf/renderer`) conforme
 * `ReciboFacil/docs/design/recibo-layout-spec.md`.
 *
 * Centraliza duas regras antes divergentes entre as plataformas:
 *
 * 1. **Composição da frase do corpo (§5) com trechos em negrito inline.** Ambos os
 *    renderers consomem [reciboBodySegments] em vez de montar a string localmente,
 *    eliminando a divergência (Android desenhava tudo em regular).
 *
 * 2. **Conversão de baseline / coordenadas de texto.** A spec dá todas as posições de
 *    texto como **baseline** (origem topo-esquerda, y→baixo). As APIs nativas divergem:
 *    - Android `Canvas.drawText(x, y)` → `y` é a **baseline** (igual à spec).
 *    - iOS `NSString.drawAtPoint(x, y)` → `y` é o **topo** da caixa de texto.
 *    [textTopFromBaseline] converte a baseline da spec para o topo que o iOS precisa,
 *    dada a métrica de ascent da fonte, mantendo o texto visualmente alinhado.
 */

/** Constante de conversão milímetro → ponto PDF (1 mm = 2.83465 pt; 1 pt = 1/72 in). */
const val MM_TO_PT: Double = 2.83465

/** Converte milímetros para pontos PDF. */
fun mmToPt(value: Double): Double = value * MM_TO_PT

/**
 * Um trecho de texto com peso (regular/bold) para composição rica inline.
 *
 * @property text conteúdo textual do trecho (já pronto, sem formatação adicional).
 * @property bold `true` para peso 700 (negrito); `false` para regular.
 */
data class ReciboTextSegment(val text: String, val bold: Boolean)

/**
 * Monta a frase do corpo do recibo (§5) como uma lista de [ReciboTextSegment], com os
 * trechos variáveis (`{pagador}`, `{valor}`, `{valor por extenso}`, `{descrição}`) em
 * **negrito** — espelhando EXATAMENTE a composição da weblib (`ReciboPdfDocument.tsx`):
 *
 * > Recebi(emos) de **{pagador}** a quantia de **{valor}** (**{valor por extenso}**)
 * > referente a **{descrição}**.
 *
 * - "Recebi" para pessoa física, "Recebemos" para pessoa jurídica (§5, item "Recebi(emos)").
 * - Os parênteses ao redor do valor por extenso são **regular** (só o conteúdo é bold),
 *   idêntico à weblib (`(` e `)` ficam fora do `<Text style={bold}>`).
 *
 * Os renderers concatenam os segmentos preservando o peso de cada um, fazem wrap por
 * palavra respeitando a largura útil e desenham cada palavra com a fonte (regular/bold)
 * do seu segmento de origem.
 */
fun reciboBodySegments(data: ReciboPdfData): List<ReciboTextSegment> {
    val recebi = if (data.emitentePessoaJuridica) "Recebemos" else "Recebi"
    return listOf(
        ReciboTextSegment("$recebi de ", bold = false),
        ReciboTextSegment(data.pagador.nome, bold = true),
        ReciboTextSegment(" a quantia de ", bold = false),
        ReciboTextSegment(data.valorFormatado, bold = true),
        ReciboTextSegment(" (", bold = false),
        ReciboTextSegment(data.valorPorExtenso, bold = true),
        ReciboTextSegment(") referente a ", bold = false),
        ReciboTextSegment(data.descricao, bold = true),
        ReciboTextSegment(".", bold = false),
    )
}

/**
 * Uma palavra a desenhar, originada de um [ReciboTextSegment]. Carrega o peso (bold) do
 * segmento e a flag [spaceBefore] (se deve ser precedida por um espaço ao concatenar na
 * mesma linha). Permite que o renderer faça wrap por palavra mantendo o negrito por trecho.
 */
data class ReciboWord(val text: String, val bold: Boolean, val spaceBefore: Boolean)

/**
 * Achata os [segments] em palavras individuais ([ReciboWord]), preservando o peso (bold)
 * de cada caractere e respeitando os espaços **reais** entre as palavras. Usado pelos
 * renderers para layout de texto rico (negrito inline) com quebra de linha por palavra.
 *
 * Implementação por varredura de caracteres (e não por `split` segmento a segmento) para
 * que pontuação colada (ex.: `(cento`, `reais)`, `violão.`) **não** ganhe espaço espúrio —
 * o que quebraria a paridade visual. Um espaço inicia uma nova palavra com `spaceBefore=true`;
 * a transição de peso no meio de uma palavra grudada (ex.: `(` regular + `cento` bold) cria
 * uma nova [ReciboWord] **sem** espaço antes, mantendo as duas coladas ao desenhar.
 */
fun reciboBodyWords(segments: List<ReciboTextSegment>): List<ReciboWord> {
    val words = mutableListOf<ReciboWord>()
    val current = StringBuilder()
    var currentBold = false
    var spaceBefore = false
    var hasContent = false

    fun flush() {
        if (current.isNotEmpty()) {
            words.add(ReciboWord(current.toString(), currentBold, spaceBefore))
            current.clear()
            spaceBefore = false
        }
    }

    for (segment in segments) {
        for (ch in segment.text) {
            if (ch == ' ') {
                flush()
                // O próximo caractere não-espaço inicia uma palavra precedida de espaço,
                // exceto se for a primeira palavra absoluta da frase.
                spaceBefore = hasContent
                continue
            }
            // Mudança de peso dentro de uma palavra colada → quebra em sub-palavra sem espaço.
            if (current.isNotEmpty() && segment.bold != currentBold) {
                words.add(ReciboWord(current.toString(), currentBold, spaceBefore))
                current.clear()
                spaceBefore = false
            }
            currentBold = segment.bold
            current.append(ch)
            hasContent = true
        }
    }
    flush()
    return words
}

/**
 * Converte uma coordenada de **baseline** (como a spec define todas as posições de texto)
 * para a coordenada de **topo** da caixa de texto que algumas APIs nativas exigem
 * (ex.: iOS `NSString.drawAtPoint`, cujo `y` é o topo).
 *
 * `topo = baseline - ascent`, onde [ascent] é a distância (positiva) do topo da caixa de
 * glifo até a baseline da fonte no tamanho usado. Centralizar essa conversão num único
 * ponto elimina a divergência de offsets *ad hoc* (ex.: "RECIBO" desenhado em y=24mm no
 * iOS vs baseline 30mm no Android) que quebrava a paridade.
 *
 * @param baselineY a coordenada de baseline da spec (em pt).
 * @param ascent o ascent da fonte no tamanho do texto (em pt, valor positivo).
 */
fun textTopFromBaseline(baselineY: Double, ascent: Double): Double = baselineY - ascent
