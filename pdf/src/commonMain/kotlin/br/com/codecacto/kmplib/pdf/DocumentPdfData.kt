package br.com.codecacto.kmplib.pdf

import kotlinx.serialization.Serializable

/**
 * Modelo de entrada (genérico) para a geração de um **PDF de documento estruturado** —
 * um relatório/contrato composto por: **cabeçalho** (empresa/emissor + título/subtítulo +
 * bloco de "metadados" chave→valor), uma sequência de **seções** (cada uma sendo um bloco
 * de pares chave→valor, uma tabela de N×M, cards de destaque, um parágrafo de texto livre
 * ou uma linha de total destacada) e um **rodapé** textual opcional.
 *
 * ## Por que existe (distinção dos demais geradores)
 *  - [OsPdfData]/[OsPdfGenerator] — documento **financeiro fixo** (OS/orçamento/recibo) com
 *    colunas monetárias e caixa "TOTAL R$" obrigatória.
 *  - [TableReportPdfData]/[TableReportPdfGenerator] — **uma tabela** pura, sem seções.
 *  - [DocumentPdfData] (este) — **documento multi-seção genérico**: o que a web do
 *    Influencer produz com `@react-pdf` (contrato de parceria, relatório mensal do cliente)
 *    e o que qualquer app full-stack precisa para espelhar um PDF estruturado no mobile.
 *    Combina blocos chave→valor, tabelas e totais numa mesma página, com paginação.
 *
 * ## Convenções de dados
 *  - **Tudo é texto já formatado** pelo app (datas, percentuais, moeda em "R$ ..."). A lib
 *    NÃO interpreta nem soma nada — apenas desenha as células/valores como vieram.
 *  - O dinheiro deve chegar **já formatado** (string) — distinto do [OsPdfData] que formata
 *    via [OsPdfFormat]; aqui o documento é genérico e não conhece moeda.
 *
 * ## Casos de uso imediatos (Influencer)
 *  - **PDF do contrato de parceria**: header (org + cliente em bloco chave→valor) + seção
 *    "Termos" (tabela plano/vigência/valor) + total mensal destacado + rodapé "emitido em".
 *  - **PDF do relatório mensal do cliente**: header (cliente + competência) + seção "Entregas"
 *    (cards) + seção "Conteúdos postados" (tabela) + seção "Financeiro" (chave→valor) + total.
 *
 * @see DocumentPdfGenerator
 * @see generateDocumentPdfBytes
 * @see generateAndShareDocumentPdf
 */
@Serializable
data class DocumentPdfData(
    /** Empresa/emissor exibido no cabeçalho (nome obrigatório; logo/contato opcionais). */
    val company: DocumentPdfCompany,
    /** Título do documento (ex.: "Contrato de Parceria", "Relatório mensal"). */
    val title: String,
    /** Subtítulo opcional sob o título (ex.: "Junho/2026", "Campanha"). */
    val subtitle: String? = null,
    /**
     * Bloco de metadados do cabeçalho (chave→valor), desenhado abaixo do título.
     * Ex.: ["Cliente" → "Ana", "Vigência" → "01/06 a 30/06", "Status" → "Ativo"].
     */
    val headerInfo: List<DocumentInfoRow> = emptyList(),
    /** Seções do corpo, na ordem de renderização. */
    val sections: List<DocumentSection> = emptyList(),
    /** Texto do rodapé (ex.: "Emitido em 14/06/2026"). Opcional. */
    val footer: String? = null,
    /** Quando `true`, imprime uma marca d'água discreta -45° (tipicamente plano free/rascunho). */
    val watermark: Boolean = false,
    /** Texto da marca d'água. Apps customizam (ex.: "RASCUNHO"). */
    val watermarkText: String = "Gerado com kmplib",
)

/** Empresa/emissor do documento (cabeçalho). Espelha o mecanismo de logo do [OsPdfCompany]. */
@Serializable
data class DocumentPdfCompany(
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    /** Logo em PNG/JPEG (bytes). Opcional — quando ausente, o cabeçalho usa só o nome. */
    val logoBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocumentPdfCompany) return false
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

/** Um par chave→valor (linha de bloco de metadados / financeiro). */
@Serializable
data class DocumentInfoRow(
    val label: String,
    val value: String,
)

/** Alinhamento horizontal de uma coluna de tabela do documento. */
@Serializable
enum class DocumentAlign { START, CENTER, END }

/** Definição de uma coluna de tabela do documento. */
@Serializable
data class DocumentColumn(
    val label: String,
    val align: DocumentAlign = DocumentAlign.START,
    /** Peso/largura relativa (a largura útil é dividida na proporção dos pesos). `> 0`. */
    val weight: Float = 1f,
)

/** Uma linha de dados de uma tabela do documento (células posicionais, texto puro). */
@Serializable
data class DocumentRow(
    val cells: List<String>,
)

/** Um card de destaque (rótulo + valor grande) usado na seção [DocumentSection.Cards]. */
@Serializable
data class DocumentCard(
    val label: String,
    val value: String,
)

/**
 * Uma seção do corpo do documento. Cada variante renderiza um tipo de bloco distinto;
 * todas começam com um título de seção opcional ([title]).
 */
@Serializable
sealed interface DocumentSection {
    /** Título da seção (negrito, sobre o bloco). Opcional. */
    val title: String?

    /** Bloco de pares chave→valor (ex.: dados do cliente, financeiro). */
    @Serializable
    data class Info(
        override val title: String? = null,
        val rows: List<DocumentInfoRow>,
        /** Texto exibido quando [rows] está vazio. */
        val emptyText: String = "Sem informações.",
    ) : DocumentSection

    /** Tabela de N colunas × M linhas, com paginação e zebra. */
    @Serializable
    data class Table(
        override val title: String? = null,
        val columns: List<DocumentColumn>,
        val rows: List<DocumentRow> = emptyList(),
        val emptyText: String = "Sem registros.",
    ) : DocumentSection

    /** Linha de cards de destaque (ex.: "Stories 8/10", "Reels 3/3"). */
    @Serializable
    data class Cards(
        override val title: String? = null,
        val cards: List<DocumentCard>,
        val emptyText: String = "Sem indicadores.",
    ) : DocumentSection

    /** Parágrafo de texto livre (com quebra de linha automática). */
    @Serializable
    data class Paragraph(
        override val title: String? = null,
        val text: String,
    ) : DocumentSection

    /**
     * Linha de **total destacada** (rótulo à esquerda, valor grande à direita, em caixa).
     * O valor chega já formatado (ex.: "R$ 1.500,00") — a lib não conhece moeda.
     */
    @Serializable
    data class Total(
        override val title: String? = null,
        val label: String,
        val value: String,
    ) : DocumentSection
}
