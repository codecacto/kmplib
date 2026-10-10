package br.com.codecacto.kmplib.ui.compare

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Contrato de dados do comparador de fotos ([PhotoCompare], `kmplib-ui`, 2.285.0) — o par mobile do
 * `PhotoCompare` da weblib (`@codecacto/weblib/compare`, ≥ 0.237.0), `GAP-VIT-K02`.
 *
 * GENÉRICO de propósito — nada de paciente, lesão ou obra. Uma **sessão** é um momento de captura
 * (consulta, avaliação, visita técnica) com N fotos; cada **foto** tem uma **vista** (frente, perfil,
 * coxa direita, fachada norte) que a casa com a foto da mesma vista em outra sessão. Comparar é
 * escolher DUAS sessões e uma vista.
 *
 * Os nomes e a regra de cada campo são **iguais aos da weblib** (e as classes são `@Serializable`),
 * para o JSON do backend servir às duas pontas sem tradução.
 */

/**
 * Uma foto de uma sessão.
 *
 * @param id identificador estável da foto.
 * @param src endereço da imagem. Pode ser uma URL pública/assinada **ou** o caminho de um endpoint
 *   autenticado — quem decide como carregar é o `photoSource` do [PhotoCompare] (ex.:
 *   `PhotoSource.authenticated(api, foto.src, accountId = …)` para foto privada).
 * @param alt texto alternativo — obrigatório. Descreve a FOTO ("Frente, em pé"); a data e o papel
 *   (antes/depois) já vêm no rótulo sobre a imagem.
 * @param view chave da VISTA: casa esta foto com a da mesma vista em outra sessão ("frente").
 *   Sem ela, casa pelo [label]; sem os dois, pela posição dentro da sessão (1ª com 1ª).
 * @param label nome da vista na tela ("Frente"). Default: o [view], ou "Foto N".
 * @param takenAt data da foto, quando difere da sessão ("AAAA-MM-DD" ou ISO com hora).
 */
@Immutable
@Serializable
data class ComparePhoto(
    val id: String,
    val src: String,
    val alt: String,
    val view: String? = null,
    val label: String? = null,
    val takenAt: String? = null,
) {
    /** Sem o [src] (pode ser URL assinada — segredo de curta duração não vai para log). */
    override fun toString(): String = "ComparePhoto(id=$id, view=$view)"
}

/**
 * Um momento de captura, com as suas fotos.
 *
 * @param id identificador estável — é o que o [ComparePair] guarda.
 * @param date **"AAAA-MM-DD"** (data civil: igual em qualquer fuso) ou ISO com hora. Esta data
 *   ORDENA a linha do tempo e decide quem é "antes": a lib nunca confia na ordem da lista.
 * @param label título curto ("Sessão 1").
 * @param description linha de apoio ("4 fotos", "78 kg · Protocolo X").
 * @param photos fotos da sessão. Sessão sem foto não entra na comparação.
 */
@Immutable
@Serializable
data class CompareSession(
    val id: String,
    val date: String,
    val label: String? = null,
    val description: String? = null,
    val photos: List<ComparePhoto> = emptyList(),
) {
    /** Sem a [description] (pode trazer peso, protocolo — dado de saúde). */
    override fun toString(): String = "CompareSession(id=$id, date=$date, photos=${photos.size})"
}

/** O par comparado, por id de sessão. [before] é sempre a MAIS ANTIGA das duas. */
@Immutable
@Serializable
data class ComparePair(val before: String, val after: String)

/**
 * Modo de comparação. [SLIDER] = controle deslizante (uma foto sobre a outra, recortada);
 * [SIDE_BY_SIDE] = lado a lado. A linha do tempo ([CompareTimeline]) não é um modo: é o seletor do
 * par, presente nos dois — igual à weblib.
 */
enum class CompareMode {
    SLIDER,
    SIDE_BY_SIDE,
    ;

    companion object {
        /** Os dois modos, na ordem do seletor (o deslizante primeiro, como no protótipo aprovado). */
        val ALL: List<CompareMode> = listOf(SLIDER, SIDE_BY_SIDE)
    }
}

/** Uma vista disponível: a união das sessões, na ordem em que aparece pela primeira vez. */
@Immutable
data class CompareView(
    /** A chave de casamento (`view` › `label` › `#N`). */
    val key: String,
    /** O nome mostrado no seletor. */
    val label: String,
)

/** Como a foto ocupa o quadro. */
enum class CompareFit {
    /**
     * A foto INTEIRA, com faixas se a proporção não bater (default) — num comparador, cortar a borda
     * pode esconder exatamente o que mudou.
     */
    CONTAIN,

    /** Preenche o quadro e corta o que sobra. */
    COVER,
}
