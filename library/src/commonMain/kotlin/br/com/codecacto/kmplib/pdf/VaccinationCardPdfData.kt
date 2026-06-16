package br.com.codecacto.kmplib.pdf

import kotlinx.serialization.Serializable

/**
 * Modelo de entrada (genérico) para a geração de uma **carteira/cronograma de vacinação** em PDF.
 *
 * **Não acoplado a domínio de vacinas humanas:** serve igualmente a carteira humana (infantil/
 * adulto), carteira pet e o **comprovante/relatório do Rebanho** (com aviso "documento
 * organizacional, não-oficial" via [notice]). O consumidor (MinhasVacinas, ...) monta este
 * [VaccinationCardPdfData] a partir do seu próprio domínio e entrega ao
 * [VaccinationCardPdfGenerator]. A lib NÃO decide regras de negócio (plano, status, validade).
 *
 * Convenções de dados (iguais às do [WorkReportPdfData]/[OsPdfData]):
 *  - Datas/textos livres ficam a cargo do app (já formatados, p.ex. "12/03/2022", "2 meses").
 *  - Logo opcional em bytes (PNG/JPEG); a lib não baixa URLs. Recomenda-se comprimir antes via
 *    [br.com.codecacto.kmplib.platform.ImageCompressor].
 *  - [watermark] segue a regra de plano do app (Free = `true`); o render desenha a marca d'água
 *    quando `true`. A lib NÃO decide o plano.
 *
 * Layout lógico produzido (paridade Android/iOS):
 *  - **Cabeçalho:** logo opcional + [title] (ex.: "Carteira de Vacinação") + nome/identificação do
 *    titular ([holder]) à esquerda; emissão + subtítulo à direita.
 *  - **Bloco do titular:** linhas de identificação ([VaccinationHolder.lines]).
 *  - **Tabela de itens:** colunas dinâmicas ([columns]) × linhas ([items]); cada célula é texto já
 *    formatado pelo app (vacina, dose, data prevista/aplicada, status — sem interpretação na lib).
 *  - **Aviso ([notice]):** caixa de aviso destacada (ex.: "Documento organizacional, não-oficial")
 *    — usada pelo comprovante do Rebanho. Opcional.
 *  - **Rodapé:** data de geração ([generatedAtLabel]) + [footer] opcional.
 *  - **Marca d'água** -45° quando [watermark] = `true`.
 *
 * @see VaccinationCardPdfGenerator
 * @see generateVaccinationCardPdfBytes
 * @see generateAndShareVaccinationCardPdf
 */
@Serializable
data class VaccinationCardPdfData(
    /** Titular da carteira (pessoa, pet ou animal/lote). */
    val holder: VaccinationHolder,
    /** Título do documento no cabeçalho (ex.: "Carteira de Vacinação"). */
    val title: String = "Carteira de Vacinação",
    /** Subtítulo opcional sob o título à direita (ex.: nome do app/flavor). */
    val subtitle: String? = null,
    /** Logo da marca/organização (PNG/JPEG). Opcional — geralmente só Pro (o app decide). */
    val logoBytes: ByteArray? = null,
    /**
     * Cabeçalhos das colunas da tabela de itens, em ordem (ex.: ["Vacina", "Dose", "Prevista",
     * "Aplicada", "Status"]). O número de colunas é livre; cada [VaccinationItem.cells] deve ter o
     * mesmo tamanho desta lista (o render preenche faltantes com vazio e ignora excedentes).
     */
    val columns: List<VaccinationColumn> = emptyList(),
    /** Linhas da tabela (doses/registros). Pode ser vazia → render mostra [emptyText]. */
    val items: List<VaccinationItem> = emptyList(),
    /** Texto exibido quando [items] está vazia. */
    val emptyText: String = "Nenhum registro.",
    /** Data/hora de geração, já formatada (ex.: "Gerado em 14/06/2026"). */
    val generatedAtLabel: String,
    /**
     * Caixa de aviso destacada no documento (ex.: comprovante do Rebanho:
     * "Documento organizacional de controle interno. Não substitui o atestado oficial de
     * vacinação."). Opcional — quando `null`, nenhuma caixa de aviso é desenhada.
     */
    val notice: String? = null,
    /** Rodapé textual opcional (ex.: contato/observação). */
    val footer: String? = null,
    /** `true` para desenhar a marca d'água (regra de plano Free = true, decidida no app). */
    val watermark: Boolean = false,
    /** Texto da marca d'água. Usado quando [watermark] = true. */
    val watermarkText: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaccinationCardPdfData) return false
        return holder == other.holder &&
            title == other.title &&
            subtitle == other.subtitle &&
            (logoBytes?.contentEquals(other.logoBytes) ?: (other.logoBytes == null)) &&
            columns == other.columns &&
            items == other.items &&
            emptyText == other.emptyText &&
            generatedAtLabel == other.generatedAtLabel &&
            notice == other.notice &&
            footer == other.footer &&
            watermark == other.watermark &&
            watermarkText == other.watermarkText
    }

    override fun hashCode(): Int {
        var result = holder.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (subtitle?.hashCode() ?: 0)
        result = 31 * result + (logoBytes?.contentHashCode() ?: 0)
        result = 31 * result + columns.hashCode()
        result = 31 * result + items.hashCode()
        result = 31 * result + emptyText.hashCode()
        result = 31 * result + generatedAtLabel.hashCode()
        result = 31 * result + (notice?.hashCode() ?: 0)
        result = 31 * result + (footer?.hashCode() ?: 0)
        result = 31 * result + watermark.hashCode()
        result = 31 * result + (watermarkText?.hashCode() ?: 0)
        return result
    }
}

/**
 * Titular da carteira — genérico (pessoa, pet ou animal/lote). O nome é destacado; [lines] são
 * pares rótulo/valor já formatados pelo app (ex.: "Nascimento" / "12/03/2022"; "Brinco" / "BR-014";
 * "Lote" / "Pasto 3").
 *
 * @param name nome/identificação principal do titular (ex.: "João", "Rex", "Lote Pasto 3").
 * @param lines linhas de identificação adicionais (rótulo → valor), já formatadas. Opcional.
 */
@Serializable
data class VaccinationHolder(
    val name: String,
    val lines: List<VaccinationHolderLine> = emptyList(),
)

/** Uma linha rótulo/valor do bloco de identificação do titular. */
@Serializable
data class VaccinationHolderLine(
    /** Rótulo do campo (ex.: "Nascimento", "Espécie", "Brinco"). */
    val label: String,
    /** Valor já formatado (ex.: "12/03/2022", "Canino", "BR-014"). */
    val value: String,
)

/** Alinhamento de uma coluna da tabela de itens (reusa a semântica de [TableReportAlign]). */
@Serializable
enum class VaccinationAlign { START, CENTER, END }

/**
 * Definição de uma coluna da tabela de itens da carteira.
 *
 * @param label cabeçalho da coluna (ex.: "Vacina", "Dose", "Aplicada", "Status").
 * @param align alinhamento do texto da coluna (default [VaccinationAlign.START]).
 * @param weight largura relativa da coluna (peso); default `1f`. Ex.: a coluna "Vacina" pode ter
 *   peso maior que "Dose".
 */
@Serializable
data class VaccinationColumn(
    val label: String,
    val align: VaccinationAlign = VaccinationAlign.START,
    val weight: Float = 1f,
)

/**
 * Uma linha da tabela de itens (uma dose/registro). As células são posicionais e correspondem, em
 * ordem, às [VaccinationCardPdfData.columns]; cada texto já vem formatado pelo app — a lib não
 * interpreta nem soma nada.
 *
 * @param cells valores das células, na ordem das colunas.
 * @param statusColorArgb cor opcional (ARGB) do indicador de status desta linha. Quando informada,
 *   o render desenha um pequeno ponto colorido antes da primeira célula (paridade com o badge de
 *   status da timeline). O app deriva o ARGB de um token do `AppTheme` (NUNCA hardcode no app) —
 *   a lib só pinta. `null` = sem indicador.
 * @param muted quando `true`, a linha é desenhada esmaecida (ex.: "Legado/não-agendável").
 */
@Serializable
data class VaccinationItem(
    val cells: List<String>,
    val statusColorArgb: Int? = null,
    val muted: Boolean = false,
)
