package br.com.codecacto.kmplib.astro

/**
 * As 8 fases nomeadas do ciclo lunar (vocabulário padrão de calendário lunar).
 *
 * O [glyph] é o emoji Unicode correspondente — **neutro de idioma**, serve como marcador visual em
 * lista/calendário sem depender de tradução. O **rótulo textual NÃO mora no enum**: use
 * [MoonPhaseTexts] (injetável, i18n) ou as strings do próprio app. Enum com `displayName` fixo em
 * pt-BR na fundação obrigaria todo app a exibir português — a regra do ecossistema é que o mobile
 * siga o idioma do aparelho.
 *
 * A fase nomeada é derivada da posição REAL dentro do ciclo corrente (lua nova anterior → próxima
 * lua nova, ambas calculadas por [MoonCalculator]), em 8 fatias de 1/8 **centradas** nos marcos —
 * ou seja, o dia da lua nova de verdade cai em [NEW], e não numa fatia vizinha.
 */
enum class MoonPhase(val glyph: String) {
    NEW("🌑"),
    WAXING_CRESCENT("🌒"),
    FIRST_QUARTER("🌓"),
    WAXING_GIBBOUS("🌔"),
    FULL("🌕"),
    WANING_GIBBOUS("🌖"),
    LAST_QUARTER("🌗"),
    WANING_CRESCENT("🌘");

    /**
     * `true` = crescendo (ganhando luz), `false` = minguando (perdendo luz), `null` nos picos
     * ([NEW] e [FULL], onde a lua não está claramente num sentido nem no outro).
     */
    val isWaxing: Boolean?
        get() = when (this) {
            NEW, FULL -> null
            WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS -> true
            WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT -> false
        }

    /** Agrupamento amplo (Nova / Crescente / Cheia / Minguante) — ver [MoonPhaseGroup]. */
    val group: MoonPhaseGroup
        get() = when (this) {
            NEW -> MoonPhaseGroup.NEW
            WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS -> MoonPhaseGroup.WAXING
            FULL -> MoonPhaseGroup.FULL
            WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT -> MoonPhaseGroup.WANING
        }

    /** A fase principal correspondente, quando esta é um marco; `null` nas fases intermediárias. */
    val principal: PrincipalMoonPhase?
        get() = when (this) {
            NEW -> PrincipalMoonPhase.NEW
            FIRST_QUARTER -> PrincipalMoonPhase.FIRST_QUARTER
            FULL -> PrincipalMoonPhase.FULL
            LAST_QUARTER -> PrincipalMoonPhase.LAST_QUARTER
            else -> null
        }
}

/**
 * Agrupamento amplo das 8 fases em 4 grupos — o recorte que regras de calendário lunar
 * (pesca/plantio/corte de cabelo) e destaques de UI costumam usar. Cada uma das 8 fases cai em
 * exatamente um grupo.
 */
enum class MoonPhaseGroup {
    NEW,
    WAXING,
    FULL,
    WANING,
}

/**
 * As **4 fases principais** — os únicos pontos do ciclo que têm um **instante exato** definido
 * astronomicamente (o momento em que a diferença de longitude eclíptica Lua−Sol vale 0°, 90°, 180°
 * ou 270°). São elas que [MoonCalculator.nextPhase]/[MoonCalculator.previousPhase] calculam.
 *
 * As outras quatro fases nomeadas (crescente/gibosa/minguante) são **intervalos** entre marcos, não
 * instantes — por isso não entram aqui.
 *
 * [cycleOffset] é a posição da fase dentro do ciclo (0 = nova, 0.25 = quarto crescente, …), usada
 * internamente para escolher o `k` de Meeus.
 */
enum class PrincipalMoonPhase(val cycleOffset: Double, val glyph: String) {
    /** Lua nova (elongação 0°). */
    NEW(0.0, "🌑"),

    /** Quarto crescente (elongação 90°) — "lua crescente" no uso popular pt-BR. */
    FIRST_QUARTER(0.25, "🌓"),

    /** Lua cheia (elongação 180°). */
    FULL(0.5, "🌕"),

    /** Quarto minguante (elongação 270°) — "lua minguante" no uso popular pt-BR. */
    LAST_QUARTER(0.75, "🌗");

    /** A fase nomeada (das 8) correspondente a este marco. */
    val named: MoonPhase
        get() = when (this) {
            NEW -> MoonPhase.NEW
            FIRST_QUARTER -> MoonPhase.FIRST_QUARTER
            FULL -> MoonPhase.FULL
            LAST_QUARTER -> MoonPhase.LAST_QUARTER
        }
}

/**
 * Rótulos das fases da lua, injetáveis (padrão `*Texts` da lib). Defaults em **pt-BR**; um app em
 * outro idioma passa os seus (idealmente vindos dos Compose Resources dele, que já seguem o idioma
 * do aparelho).
 */
data class MoonPhaseTexts(
    val newMoon: String = "Lua Nova",
    val waxingCrescent: String = "Lua Crescente",
    val firstQuarter: String = "Quarto Crescente",
    val waxingGibbous: String = "Crescente Gibosa",
    val fullMoon: String = "Lua Cheia",
    val waningGibbous: String = "Minguante Gibosa",
    val lastQuarter: String = "Quarto Minguante",
    val waningCrescent: String = "Lua Minguante",
) {
    /** Rótulo de uma das 8 fases nomeadas. */
    fun labelFor(phase: MoonPhase): String = when (phase) {
        MoonPhase.NEW -> newMoon
        MoonPhase.WAXING_CRESCENT -> waxingCrescent
        MoonPhase.FIRST_QUARTER -> firstQuarter
        MoonPhase.WAXING_GIBBOUS -> waxingGibbous
        MoonPhase.FULL -> fullMoon
        MoonPhase.WANING_GIBBOUS -> waningGibbous
        MoonPhase.LAST_QUARTER -> lastQuarter
        MoonPhase.WANING_CRESCENT -> waningCrescent
    }

    /** Rótulo de uma das 4 fases principais. */
    fun labelFor(phase: PrincipalMoonPhase): String = labelFor(phase.named)
}
