package br.com.codecacto.kmplib.testing.vm

/**
 * Os **4 caminhos** que toda ação que GRAVA prova na célula `tela` do nível mínimo de testes (2.381.0):
 * sucesso, recusa do servidor (4xx/402), conexão caindo DEPOIS do envio e duplo toque.
 *
 * Constante para não errar a grafia — é o que a medição (`Ferramentas/nivel-de-teste`) procura no nome do teste.
 */
object Caminho {
    const val SUCESSO: String = "sucesso"
    const val RECUSA: String = "recusa"
    const val CONEXAO_CAIU: String = "conexao-caiu"
    const val DUPLO_TOQUE: String = "duplo-toque"

    /** Os quatro, na ordem da regra. */
    val TODOS: List<String> = listOf(SUCESSO, RECUSA, CONEXAO_CAIU, DUPLO_TOQUE)
}

/** As células da matriz de fluxos (`docs/testes/fluxos.yaml`). */
object Celula {
    const val REGRA: String = "regra"
    const val SERVIDOR: String = "servidor"
    const val TELA: String = "tela"
    const val E2E: String = "e2e"

    val TODAS: List<String> = listOf(REGRA, SERVIDOR, TELA, E2E)
}

/**
 * A **marca de fluxo** no nome do teste — o que liga o teste à matriz.
 *
 * Duas grafias, as duas lidas pela medição:
 * - **[nome]** (`fluxo locacao_cancelar tela conexao-caiu - descrição`) — a do teste Kotlin com crase. O
 *   compilador recusa `.`, `[`, `]`, `|`, `:` e `,` no nome de função (JVM e Kotlin/Native), então o ponto do id
 *   vira `_` (o id é kebab com ponto e nunca tem `_`, logo a volta é exata);
 * - **[colchetes]** (`[locacao.cancelar|tela|conexao-caiu] descrição`) — a do nome em TEXTO (Kotest, Vitest,
 *   Playwright).
 *
 * ```kotlin
 * @Test fun `fluxo locacao_cancelar tela conexao-caiu - confere no servidor`() = runViewModelTest { … }
 * ```
 */
object MarcaDeFluxo {

    /** `fluxo <id com _> <celula>[ <caminho>][ - <descricao>]`. */
    fun nome(fluxo: String, celula: String, caminho: String? = null, descricao: String? = null): String {
        validar(fluxo, celula, caminho)
        return buildString {
            append("fluxo ").append(fluxo.replace('.', '_')).append(' ').append(celula)
            caminho?.let { append(' ').append(it) }
            descricao?.takeIf { it.isNotBlank() }?.let { append(" - ").append(it.trim()) }
        }
    }

    /** `[<id>|<celula>[|<caminho>]][ <descricao>]`. */
    fun colchetes(fluxo: String, celula: String, caminho: String? = null, descricao: String? = null): String {
        validar(fluxo, celula, caminho)
        return buildString {
            append('[').append(fluxo).append('|').append(celula)
            caminho?.let { append('|').append(it) }
            append(']')
            descricao?.takeIf { it.isNotBlank() }?.let { append(' ').append(it.trim()) }
        }
    }

    /** Lê a marca de um nome de teste (qualquer das duas grafias); `null` = o teste não marca fluxo. */
    fun ler(nomeDoTeste: String): Marca? {
        val texto = nomeDoTeste.trim()
        NOME.matchEntire(texto)?.let { m ->
            return Marca(m.groupValues[1].replace('_', '.'), m.groupValues[2], m.groupValues[3].ifEmpty { null })
        }
        COLCHETES.find(texto)?.let { m ->
            return Marca(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { null })
        }
        return null
    }

    /** A marca lida: fluxo (`locacao.cancelar`), célula e caminho (só na célula `tela`, em geral). */
    data class Marca(val fluxo: String, val celula: String, val caminho: String?)

    private fun validar(fluxo: String, celula: String, caminho: String?) {
        require(ID.matches(fluxo)) { "id de fluxo é kebab com ponto (ex.: locacao.cancelar): '$fluxo'" }
        require(celula in Celula.TODAS) { "célula desconhecida '$celula' (use ${Celula.TODAS})" }
        require(caminho == null || caminho in Caminho.TODOS) { "caminho desconhecido '$caminho' (use ${Caminho.TODOS})" }
    }

    private const val ID_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*(?:\\.[a-z0-9]+(?:-[a-z0-9]+)*)*"
    private val ID = Regex(ID_PATTERN)
    private const val CELULAS = "regra|servidor|tela|e2e"
    private const val CAMINHOS = "sucesso|recusa|conexao-caiu|duplo-toque"
    private val NOME = Regex("^fluxo ([a-z0-9_-]+) ($CELULAS)(?: ($CAMINHOS))?(?: - .*)?$")
    private val COLCHETES = Regex("\\[($ID_PATTERN)\\|($CELULAS)(?:\\|($CAMINHOS))?]")
}
