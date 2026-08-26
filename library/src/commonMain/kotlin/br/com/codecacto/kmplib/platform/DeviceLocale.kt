package br.com.codecacto.kmplib.platform

/**
 * **A região do aparelho** — o país que o usuário configurou no sistema.
 *
 * Existe para o app poder mostrar **conteúdo regional certo**: limites normativos, unidades,
 * moeda, formato de documento. Traduzir a interface não resolve isso — um decibelímetro em inglês
 * exibindo a NR-15 brasileira continua mostrando uma norma que não vale para quem está lendo.
 *
 * ## Região é diferente de idioma, e a confusão custa caro
 *
 * O idioma responde "em que língua escrever"; a região responde "que regras valem aqui". Um
 * brasileiro morando em Portugal costuma ter o aparelho em português **com região PT**; um
 * americano que estuda espanhol pode ter idioma `es` **com região US**. Escolher norma pelo idioma
 * erra os dois casos. Por isso este arquivo devolve a **região**, e a tradução continua sendo do
 * `compose-resources`.
 *
 * ## O que ele NÃO é
 *
 * Não é geolocalização: é a configuração do sistema, sem permissão nenhuma e sem rede. Alguém pode
 * estar viajando com o aparelho configurado no país de origem — e, para escolher a tabela de
 * referência de um app, é justamente o comportamento desejável (a pessoa entende as normas do lugar
 * de onde ela é). Quem precisa de posição real usa `location`, que pede permissão e é outro
 * assunto.
 */

/**
 * Código cru da região, como a plataforma o entrega. Pode vir vazio, em minúsculas ou ausente.
 *
 * **Não use direto** — use [deviceRegion], que normaliza e valida.
 */
expect fun platformRegionCode(): String?

/**
 * Região do aparelho em **ISO 3166-1 alfa-2 maiúsculo** (`"BR"`, `"US"`, `"PT"`), ou `null` quando
 * a plataforma não sabe dizer.
 *
 * `null` é um caso real, não teórico: emulador recém-criado, aparelho com região não definida, ou
 * um valor que não é um código de país (o Android já devolveu `"419"`, do "espanhol da América
 * Latina", nessa posição). Trate-o como "não sei" e caia no conteúdo internacional — **nunca**
 * assuma um país de default, que é como um app brasileiro passa a mostrar a CLT para um americano.
 */
fun deviceRegion(): String? = normalizeRegion(platformRegionCode())

/**
 * Normaliza o código cru: apara espaços, sobe para maiúsculas e aceita **apenas** duas letras.
 *
 * Separado do acesso à plataforma de propósito — é a parte que tem regra, e é a que os testes
 * cobrem sem precisar de aparelho.
 */
internal fun normalizeRegion(raw: String?): String? {
    val limpo = raw?.trim()?.uppercase() ?: return null
    if (limpo.length != 2) return null
    if (!limpo.all { it in 'A'..'Z' }) return null
    return limpo
}
