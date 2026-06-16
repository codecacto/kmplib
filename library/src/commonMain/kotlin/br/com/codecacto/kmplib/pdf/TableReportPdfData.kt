package br.com.codecacto.kmplib.pdf

import kotlinx.serialization.Serializable

/**
 * Modelo de entrada (genérico) para a geração de um **PDF de tabela puro** — um
 * relatório **não-financeiro** composto por: cabeçalho do documento (título +
 * subtítulo opcional), uma tabela de N colunas × M linhas (cada célula é uma
 * `String` já formatada pelo app) e, opcionalmente, um resumo textual e um rodapé.
 *
 * **Por que existe (distinção do [OsPdfData]):** o [OsPdfData]/[OsPdfGenerator] é um
 * template de **documento financeiro** (Ordem de Serviço / orçamento / recibo) —
 * tem colunas monetárias (UNIT./SUBTOTAL) e imprime SEMPRE uma caixa "TOTAL R$ ...".
 * Forçá-lo para um relatório de frequência/presença, lista de tarefas, inventário,
 * etc. produz uma caixa "TOTAL R$ 0,00" residual (layout de fatura enganoso).
 * O [TableReportPdfData] é o template de **tabela genérica, sem nenhum bloco de
 * dinheiro/total** — serve a qualquer relatório tabular do ecossistema (ex.:
 * relatório de frequência do Chamada Fácil: Aluno × Presenças × Total × Frequência%).
 *
 * Convenções de dados:
 *  - **Tudo é texto já formatado** pelo app (datas, percentuais, contagens). A lib
 *    NÃO interpreta nem soma nada — apenas desenha as células como vieram.
 *  - As células de cada [TableReportRow] são posicionais: a célula `i` cai sob a
 *    coluna `i` de [columns]. Linhas mais curtas que [columns] preenchem o resto
 *    com vazio; células excedentes são ignoradas pelo render.
 *
 * Layout lógico produzido (idêntico em todas as plataformas):
 *  - Cabeçalho do documento: logo (opcional) + nome da empresa/emissor; à direita o
 *    [title] e o [subtitle] (ex.: "Relatório de Frequência" / "Turma 3º A").
 *  - Linha de cabeçalho da tabela (negrito, fundo neutro) com os rótulos das colunas.
 *  - Linhas de dados com zebra (linhas alternadas) e strokes leves, paginadas quando
 *    estouram a altura da página.
 *  - [summary] textual opcional (sem "TOTAL R$").
 *  - [footer] textual opcional (ex.: data de emissão).
 *  - Marca d'água discreta quando [watermark] = true (tipicamente plano gratuito).
 *
 * @see TableReportPdfGenerator
 * @see generateTablePdfBytes
 * @see generateAndShareTablePdf
 */
@Serializable
data class TableReportPdfData(
    /** Empresa/emissor exibido no cabeçalho (nome obrigatório, logo/contato opcionais). */
    val company: TableReportPdfCompany,
    /** Título do documento (ex.: "Relatório de Frequência"). */
    val title: String,
    /** Subtítulo opcional sob o título (ex.: "Turma 3º A — Maio/2026"). */
    val subtitle: String? = null,
    /** Definição das colunas (rótulo + alinhamento + peso/largura relativa). */
    val columns: List<TableReportColumn>,
    /** Linhas de dados (cada linha = lista de células `String`). Pode ser vazia. */
    val rows: List<TableReportRow> = emptyList(),
    /** Texto exibido quando [rows] está vazia (ex.: "Nenhum registro no período."). */
    val emptyText: String = "Sem registros.",
    /** Resumo textual opcional exibido após a tabela (NUNCA "TOTAL R$"). Ex.: "Frequência média da turma: 92%". */
    val summary: String? = null,
    /** Texto do rodapé (ex.: "Emitido em 14/06/2026"). Opcional. */
    val footer: String? = null,
    /**
     * Quando `true`, imprime uma marca d'água discreta na página.
     * Regra de negócio do app: tipicamente `watermark = (plan == "free")`.
     */
    val watermark: Boolean = false,
    /** Texto da marca d'água (default genérico). Apps customizam, ex.: "Feito com Chamada Fácil". */
    val watermarkText: String = "Gerado com kmplib",
)

/** Empresa/emissor do documento (cabeçalho). Espelha o mecanismo de logo do [OsPdfCompany]. */
@Serializable
data class TableReportPdfCompany(
    val name: String,
    /** Telefone/contato exibido sob o nome. Opcional. */
    val phone: String? = null,
    /** E-mail/contato adicional. Opcional. */
    val email: String? = null,
    /** Endereço/linha de contato adicional. Opcional. */
    val address: String? = null,
    /**
     * Logo em PNG/JPEG (bytes). Opcional — quando ausente, o cabeçalho usa só o nome.
     * O app baixa os bytes do logo (Storage/cache) e os passa aqui.
     */
    val logoBytes: ByteArray? = null,
) {
    // equals/hashCode manuais por causa do ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TableReportPdfCompany) return false
        return name == other.name &&
            phone == other.phone &&
            email == other.email &&
            address == other.address &&
            (logoBytes?.contentEquals(other.logoBytes) ?: (other.logoBytes == null))
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (phone?.hashCode() ?: 0)
        result = 31 * result + (email?.hashCode() ?: 0)
        result = 31 * result + (address?.hashCode() ?: 0)
        result = 31 * result + (logoBytes?.contentHashCode() ?: 0)
        return result
    }
}

/** Alinhamento horizontal de uma coluna (cabeçalho e células). */
@Serializable
enum class TableReportAlign { START, CENTER, END }

/**
 * Definição de uma coluna da tabela.
 *
 * @param label rótulo exibido na linha de cabeçalho (negrito).
 * @param align alinhamento horizontal do rótulo e das células desta coluna.
 * @param weight peso/largura relativa da coluna. A largura útil da tabela é dividida
 *   na proporção dos pesos de todas as colunas (ex.: pesos `2f, 1f, 1f` → a 1ª coluna
 *   ocupa metade). Default `1f` (colunas de mesma largura). Deve ser `> 0`.
 */
@Serializable
data class TableReportColumn(
    val label: String,
    val align: TableReportAlign = TableReportAlign.START,
    val weight: Float = 1f,
)

/**
 * Uma linha de dados da tabela.
 *
 * @param cells células posicionais (a célula `i` cai sob a coluna `i`). Já formatadas
 *   pelo app (texto puro). Se houver menos células que colunas, as faltantes ficam
 *   vazias; se houver mais, as excedentes são ignoradas no render.
 */
@Serializable
data class TableReportRow(
    val cells: List<String>,
)
