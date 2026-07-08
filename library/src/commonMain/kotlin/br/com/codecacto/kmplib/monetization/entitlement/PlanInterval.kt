package br.com.codecacto.kmplib.monetization.entitlement

import br.com.codecacto.kmplib.core.money.Money

/**
 * Os **únicos** intervalos de assinatura do ecossistema (constituição, §"Tipos de plano
 * padronizados"): **Mensal(1) → Semestral(6) → Anual(12)**. Não existe trimestral, nem vitalício,
 * nem semanal.
 *
 * Serve de **whitelist**: qualquer outra duração que chegue do backend/da loja (`3`, `1200`,
 * `null`, lixo) é **não-canônica** — o plano continua **visível e assinável**, mas é
 * **inelegível ao selo** de "Recomendado" e ordena **por último**.
 *
 * > Nunca converta duração desconhecida em um número grande (`Int.MAX_VALUE`) para "mandar pro
 * > fim": foi assim que a weblib deu o selo de "Melhor valor" para um plano de intervalo
 * > desconhecido. Desconhecido é `null`, e `null` não concorre.
 */
enum class PlanInterval(val durationMonths: Int) {
    Monthly(1),
    SemiAnnual(6),
    Yearly(12);

    companion object {
        /** Resolve o intervalo canônico da duração; `null` para qualquer coisa fora de 1/6/12. */
        fun fromDurationMonths(durationMonths: Int?): PlanInterval? =
            durationMonths?.let { months -> entries.firstOrNull { it.durationMonths == months } }

        /** `true` só para 1, 6 e 12. */
        fun isCanonical(durationMonths: Int?): Boolean = fromDurationMonths(durationMonths) != null
    }
}

/**
 * O plano é **pago**? Free jamais concorre ao selo de "Recomendado" (nem quando empata em duração
 * com o mensal).
 *
 * `preco` ausente/em branco **não** significa grátis: no padrão-ouro o preço vem da **loja**, e o
 * catálogo do admin-api pode nem carregá-lo. Só é grátis quando o `plano` é `free` ou quando o
 * preço declarado é **zero**.
 */
val Plan.isPaidPlan: Boolean
    get() = !isFree && (preco.isNullOrBlank() || Money.toCents(preco) > 0L)
