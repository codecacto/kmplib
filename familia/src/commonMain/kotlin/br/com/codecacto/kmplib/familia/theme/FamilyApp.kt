package br.com.codecacto.kmplib.familia.theme

import androidx.compose.ui.graphics.vector.ImageVector
import br.com.codecacto.kmplib.familia.icons.FamilyAppIconArt

/**
 * Os 12 apps da família cristã CodeCacto. [code] é o prefixo do protótipo (`vh`, `rh`…), [slug] o do
 * projeto/repo/loja, [displayName] o nome de nascença (pt-BR) — o nome por idioma na loja mora no
 * Nexus; o app passa o dele para as telas quando for outro.
 *
 * Desde a 2.358.0 é uma [FamilyAppIdentity] — app de FORA da família declara a sua com
 * `FamilyAppIdentity(slug, displayName, icon, scheme)` e usa o mesmo kit.
 */
enum class FamilyApp(
    override val code: String,
    override val slug: String,
    override val displayName: String,
) : FamilyAppIdentity {
    VersiculoHoje("vh", "versiculo-hoje", "Versículo Hoje"),
    RosarioHoje("rh", "rosario-hoje", "Rosário Hoje"),
    Decora("dc", "decora", "Decora"),
    BibliaAberta("ba", "biblia-aberta", "Bíblia Aberta"),
    QuizDaBiblia("qz", "quiz-da-biblia", "Quiz da Bíblia"),
    HinarioDeBolso("hb", "hinario-de-bolso", "Hinário de Bolso"),
    DevocionalDoDia("dv", "devocional-do-dia", "Devocional do Dia"),
    DiarioDeOracao("do", "diario-de-oracao", "Diário de Oração"),
    MeuDizimo("dz", "meu-dizimo", "Meu Dízimo"),
    Jejuar("jj", "jejuar", "Jejuar"),
    CadernoDeSermao("cs", "caderno-de-sermao", "Caderno de Sermão"),
    AtlasBiblico("at", "atlas-biblico", "Atlas Bíblico");

    /** O ícone do app (arte do protótipo, 100×100). Recorte em squircle: `FamilyAppIcon`. */
    override val icon: ImageVector
        get() = when (this) {
            VersiculoHoje -> FamilyAppIconArt.VersiculoHoje
            RosarioHoje -> FamilyAppIconArt.RosarioHoje
            Decora -> FamilyAppIconArt.Decora
            BibliaAberta -> FamilyAppIconArt.BibliaAberta
            QuizDaBiblia -> FamilyAppIconArt.QuizDaBiblia
            HinarioDeBolso -> FamilyAppIconArt.HinarioDeBolso
            DevocionalDoDia -> FamilyAppIconArt.DevocionalDoDia
            DiarioDeOracao -> FamilyAppIconArt.DiarioDeOracao
            MeuDizimo -> FamilyAppIconArt.MeuDizimo
            Jejuar -> FamilyAppIconArt.Jejuar
            CadernoDeSermao -> FamilyAppIconArt.CadernoDeSermao
            AtlasBiblico -> FamilyAppIconArt.AtlasBiblico
        }

    /** A paleta do protótipo deste app ([FamilyPalettes.of]). */
    override val scheme: FamilyColorScheme
        get() = FamilyPalettes.of(this)

    companion object {
        /** O app do [slug] (`"versiculo-hoje"`), ou `null`. */
        fun fromSlug(slug: String): FamilyApp? = entries.firstOrNull { it.slug == slug.trim().lowercase() }

        /**
         * O app da família a que se refere um item do `/public/developer` (nome do Nexus), comparando
         * sem acento e sem caixa. `null` = não é da família.
         */
        fun matchName(name: String): FamilyApp? {
            val n = br.com.codecacto.kmplib.core.text.foldForSearch(name)
            return entries.firstOrNull { br.com.codecacto.kmplib.core.text.foldForSearch(it.displayName) == n }
        }
    }
}

/** O ícone da CodeCacto na mesma arte (o "Desenvolvido por" usa o logo oficial da `DeveloperScreen`). */
val CodeCactoFamilyIcon: ImageVector get() = FamilyAppIconArt.CodeCacto
