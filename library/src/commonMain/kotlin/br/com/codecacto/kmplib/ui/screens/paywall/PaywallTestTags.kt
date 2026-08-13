package br.com.codecacto.kmplib.ui.screens.paywall

/**
 * **Ids canônicos do paywall para automação de UI** — os mesmos nomes do conjunto declarado no
 * briefing de QA (`AUTOMACAO-QA-BRIEFING-RUNNER.md`), e os mesmos que a `PricingTable` da weblib
 * emite no lado web. App e portal do mesmo produto passam a se automatizar com o **mesmo vocabulário**.
 *
 * Ficam aqui, e não em cada app, porque quem renderiza o paywall é a lib: um id plantado no app não
 * alcançaria o card nem o CTA. E ficam **públicos** de propósito — é o teste do app que os consome,
 * e teste que redigita a string do id quebra em silêncio no dia em que a lib mudar de nome.
 *
 * Para o Maestro/Appium enxergarem estes ids como `resource-id`, a raiz da hierarquia precisa
 * declarar `testTagsAsResourceId` — o que o `AppTheme` faz desde a 2.107.0, sem o app configurar nada.
 */
object PaywallTestTags {

    /** Card de um plano. Sufixo: [planSuffix]. */
    fun plano(plan: PaywallPlan): String = "paywall-plano-${planSuffix(plan)}"

    /**
     * CTA de assinar de um plano. **O sufixo não é opcional:** há um CTA por plano na tela, e um id
     * único para todos faria o teste tocar no primeiro e **comprar o plano errado** — passando verde,
     * porque "virou premium" fica verde nos dois casos. Mesma armadilha resolvida na weblib 0.106.0.
     */
    fun botaoAssinar(plan: PaywallPlan): String = "paywall-btn-assinar-${planSuffix(plan)}"

    /**
     * Os ids dos **três planos do padrão da fábrica**, prontos.
     *
     * Existem porque quem consome estes ids é um teste, e do lado do teste não há um [PaywallPlan]
     * para passar a [plano]/[botaoAssinar] — o teste não constrói o estado da tela, ele lê a tela.
     * Sem a constante, a alternativa real é redigitar `"paywall-btn-assinar-mensal"` no `@Test`, que
     * é justamente o acoplamento por string que este objeto existe para evitar.
     */
    const val PLANO_MENSAL: String = "paywall-plano-mensal"
    const val PLANO_SEMESTRAL: String = "paywall-plano-semestral"
    const val PLANO_ANUAL: String = "paywall-plano-anual"
    const val BOTAO_ASSINAR_MENSAL: String = "paywall-btn-assinar-mensal"
    const val BOTAO_ASSINAR_SEMESTRAL: String = "paywall-btn-assinar-semestral"
    const val BOTAO_ASSINAR_ANUAL: String = "paywall-btn-assinar-anual"

    /** Bloco "assinatura ativa" (só aparece para quem já assina). */
    const val ASSINATURA_ATIVA: String = "paywall-assinatura-ativa"

    /** Botão "Gerenciar assinatura" dentro do bloco de assinatura ativa. */
    const val BOTAO_GERENCIAR: String = "paywall-btn-gerenciar-assinatura"

    /** Botão "Restaurar compras". */
    const val BOTAO_RESTAURAR: String = "paywall-btn-restaurar"

    /** Mensagem de "nenhum plano disponível" — o paywall vazio, que a suíte precisa distinguir. */
    const val SEM_PLANOS: String = "paywall-sem-planos"

    /** Card de erro do paywall. */
    const val ERRO: String = "paywall-erro"

    /**
     * Sufixo estável de um plano: **`mensal` / `semestral` / `anual`** pela duração canônica.
     *
     * Deriva da duração, e não do [PaywallPlan.id], porque o `id` é o `packageId` da loja
     * (`$rc_monthly`, ou um id interno que muda por projeto) — id de teste que carrega `$` e varia de
     * app para app não é vocabulário comum, é gambiarra por produto.
     *
     * Plano de duração não-canônica (um `lifetime`, um trimestral residual, `null`) cai no próprio
     * `id`, **sanitizado**: a verdade honesta em vez de um sufixo inventado que colidiria com outro
     * plano na mesma tela.
     */
    fun planSuffix(plan: PaywallPlan): String = when (plan.durationMonths) {
        1 -> "mensal"
        6 -> "semestral"
        12 -> "anual"
        else -> plan.id.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .ifEmpty { "desconhecido" }
    }
}
