package br.com.codecacto.kmplib.familia.theme

import androidx.compose.ui.graphics.Color

/**
 * As 12 paletas da família (claro e escuro), exatamente as do `ds.css` do protótipo (11/nov/2026).
 * **Este é o ÚNICO lugar do kit com `Color(0x…)`** (regra D16): o resto lê [FamilyTheme.colors].
 * Gerado do `ds.css` — para mudar uma cor, mude o protótipo e regenere (ver `references/familia.md`).
 */
object FamilyPalettes {
    /** As paletas claro/escuro de [app]. */
    fun of(app: FamilyApp): FamilyColorScheme = when (app) {
        FamilyApp.VersiculoHoje -> VERSICULOHOJE
        FamilyApp.RosarioHoje -> ROSARIOHOJE
        FamilyApp.Decora -> DECORA
        FamilyApp.BibliaAberta -> BIBLIAABERTA
        FamilyApp.QuizDaBiblia -> QUIZDABIBLIA
        FamilyApp.HinarioDeBolso -> HINARIODEBOLSO
        FamilyApp.DevocionalDoDia -> DEVOCIONALDODIA
        FamilyApp.DiarioDeOracao -> DIARIODEORACAO
        FamilyApp.MeuDizimo -> MEUDIZIMO
        FamilyApp.Jejuar -> JEJUAR
        FamilyApp.CadernoDeSermao -> CADERNODESERMAO
        FamilyApp.AtlasBiblico -> ATLASBIBLICO
    }

    private val VERSICULOHOJE = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFFFF8EC), sf = Color(0xFFFFFFFF), sf2 = Color(0xFFFBEFD9), fg = Color(0xFF2A1F12), mu = Color(0xFF6E5A40), ln = Color(0xFFEADBC0), ac = Color(0xFFE8960F), aci = Color(0xFF2A1F12), acs = Color(0xFFFCE7C0), acd = Color(0xFF8A5300), ac2 = Color(0xFFC7731A)),
        dark = FamilyPalette(bg = Color(0xFF1A1510), sf = Color(0xFF251E16), sf2 = Color(0xFF2E251B), fg = Color(0xFFF7EBDA), mu = Color(0xFFC2AE92), ln = Color(0xFF3B3024), ac = Color(0xFFF2A93B), aci = Color(0xFF2A1F12), acs = Color(0xFF3B2C15), acd = Color(0xFFF5B654), ac2 = Color(0xFFF2A93B)),
    )

    private val ROSARIOHOJE = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFF5F6FB), sf = Color(0xFFFFFFFF), sf2 = Color(0xFFE9ECF6), fg = Color(0xFF141B33), mu = Color(0xFF4F5878), ln = Color(0xFFD8DDEC), ac = Color(0xFF1E3A8A), aci = Color(0xFFFFFFFF), acs = Color(0xFFE3E8F7), acd = Color(0xFF1E3A8A), ac2 = Color(0xFFB08A3E)),
        dark = FamilyPalette(bg = Color(0xFF0D1326), sf = Color(0xFF151D36), sf2 = Color(0xFF1C2542), fg = Color(0xFFEEF0F8), mu = Color(0xFFA9B1CC), ln = Color(0xFF27314F), ac = Color(0xFFD4B06A), aci = Color(0xFF1A1406), acs = Color(0xFF262C44), acd = Color(0xFFE2C27F), ac2 = Color(0xFFD4B06A)),
    )

    private val DECORA = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFF3F8F6), sf = Color(0xFFFFFFFF), sf2 = Color(0xFFE4F1EC), fg = Color(0xFF1F2427), mu = Color(0xFF4D5A5F), ln = Color(0xFFD3E3DD), ac = Color(0xFF3FCF9C), aci = Color(0xFF0E2A20), acs = Color(0xFFD5F3E7), acd = Color(0xFF13785A), ac2 = Color(0xFF2B2F33)),
        dark = FamilyPalette(bg = Color(0xFF15191B), sf = Color(0xFF1E2326), sf2 = Color(0xFF252B2F), fg = Color(0xFFE9F1EE), mu = Color(0xFFA3B1AC), ln = Color(0xFF313A3E), ac = Color(0xFF4FD9A8), aci = Color(0xFF0E2A20), acs = Color(0xFF1F3A31), acd = Color(0xFF6BE3B8), ac2 = Color(0xFFE9F1EE)),
    )

    private val BIBLIAABERTA = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFF8F1E6), sf = Color(0xFFFFFBF4), sf2 = Color(0xFFF0E5D3), fg = Color(0xFF2B1A16), mu = Color(0xFF6A544B), ln = Color(0xFFE3D3BC), ac = Color(0xFF6E1F2E), aci = Color(0xFFFFFFFF), acs = Color(0xFFF1DCDC), acd = Color(0xFF7A2333), ac2 = Color(0xFFB08A3E)),
        dark = FamilyPalette(bg = Color(0xFF16100F), sf = Color(0xFF211817), sf2 = Color(0xFF2A1F1D), fg = Color(0xFFF3E6DA), mu = Color(0xFFBFA99B), ln = Color(0xFF3A2B28), ac = Color(0xFFD98A96), aci = Color(0xFF2B0D13), acs = Color(0xFF3A2226), acd = Color(0xFFE9A2AC), ac2 = Color(0xFFD4B06A)),
    )

    private val QUIZDABIBLIA = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFFFF5EC), sf = Color(0xFFFFFFFF), sf2 = Color(0xFFFFE7D3), fg = Color(0xFF22163D), mu = Color(0xFF5B4D73), ln = Color(0xFFF1D9C6), ac = Color(0xFF5B2BB5), aci = Color(0xFFFFFFFF), acs = Color(0xFFECE3FA), acd = Color(0xFF5B2BB5), ac2 = Color(0xFFFF7A1A)),
        dark = FamilyPalette(bg = Color(0xFF170F26), sf = Color(0xFF221838), sf2 = Color(0xFF2B1F45), fg = Color(0xFFF6F0FF), mu = Color(0xFFB9ACD3), ln = Color(0xFF372A55), ac = Color(0xFFFF8A33), aci = Color(0xFF22163D), acs = Color(0xFF3A2650), acd = Color(0xFFFFA45C), ac2 = Color(0xFFA98BF0)),
    )

    private val HINARIODEBOLSO = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFFAF6EC), sf = Color(0xFFFFFDF7), sf2 = Color(0xFFEFE9D8), fg = Color(0xFF15241D), mu = Color(0xFF4E5D55), ln = Color(0xFFDFD8C3), ac = Color(0xFF1F5A43), aci = Color(0xFFFFFFFF), acs = Color(0xFFDCEAE2), acd = Color(0xFF1F5A43), ac2 = Color(0xFFB08A3E)),
        dark = FamilyPalette(bg = Color(0xFF0F1714), sf = Color(0xFF16211C), sf2 = Color(0xFF1D2A24), fg = Color(0xFFECF1EA), mu = Color(0xFFA8B8AE), ln = Color(0xFF2A3A33), ac = Color(0xFF7CCBA5), aci = Color(0xFF0C2219), acs = Color(0xFF1E3A2E), acd = Color(0xFF8FD8B5), ac2 = Color(0xFFD4B06A)),
    )

    private val DEVOCIONALDODIA = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFFFF4EE), sf = Color(0xFFFFFFFF), sf2 = Color(0xFFFCE3D6), fg = Color(0xFF1F2A4D), mu = Color(0xFF535C7A), ln = Color(0xFFF1D8CB), ac = Color(0xFF1F2A4D), aci = Color(0xFFFFFFFF), acs = Color(0xFFFCE1D3), acd = Color(0xFF1F2A4D), ac2 = Color(0xFFF7B89A)),
        dark = FamilyPalette(bg = Color(0xFF121830), sf = Color(0xFF1A2140), sf2 = Color(0xFF222A4C), fg = Color(0xFFFBEDE6), mu = Color(0xFFB8BAD0), ln = Color(0xFF2D3558), ac = Color(0xFFF7B89A), aci = Color(0xFF1F2A4D), acs = Color(0xFF3A3350), acd = Color(0xFFF9C6AD), ac2 = Color(0xFFF7B89A)),
    )

    private val DIARIODEORACAO = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFF6F4FC), sf = Color(0xFFFFFFFF), sf2 = Color(0xFFECE8F8), fg = Color(0xFF24222E), mu = Color(0xFF575468), ln = Color(0xFFDEDAEE), ac = Color(0xFF6A58C7), aci = Color(0xFFFFFFFF), acs = Color(0xFFE7E2FA), acd = Color(0xFF5A48B5), ac2 = Color(0xFFA99BE0)),
        dark = FamilyPalette(bg = Color(0xFF16151C), sf = Color(0xFF1F1D28), sf2 = Color(0xFF272433), fg = Color(0xFFEEECF6), mu = Color(0xFFB2AEC6), ln = Color(0xFF322F40), ac = Color(0xFFB7A9F0), aci = Color(0xFF201A3A), acs = Color(0xFF2E2A45), acd = Color(0xFFC4B8F5), ac2 = Color(0xFFB7A9F0)),
    )

    private val MEUDIZIMO = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFF7F1E4), sf = Color(0xFFFFFCF5), sf2 = Color(0xFFEDE3CD), fg = Color(0xFF262A14), mu = Color(0xFF5A5D44), ln = Color(0xFFE0D5BB), ac = Color(0xFF5B6820), aci = Color(0xFFFFFFFF), acs = Color(0xFFE6E8CC), acd = Color(0xFF4F5A1B), ac2 = Color(0xFFB8892F)),
        dark = FamilyPalette(bg = Color(0xFF14150E), sf = Color(0xFF1D1F15), sf2 = Color(0xFF25281B), fg = Color(0xFFF1EEDF), mu = Color(0xFFB4B29A), ln = Color(0xFF33362A), ac = Color(0xFFB9C76A), aci = Color(0xFF22260C), acs = Color(0xFF2F3420), acd = Color(0xFFC7D47E), ac2 = Color(0xFFE0B65A)),
    )

    private val JEJUAR = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFFBF3EE), sf = Color(0xFFFFFFFF), sf2 = Color(0xFFF4E2D8), fg = Color(0xFF2C1B14), mu = Color(0xFF654E44), ln = Color(0xFFEBD5C8), ac = Color(0xFFB44D27), aci = Color(0xFFFFFFFF), acs = Color(0xFFF7DDD0), acd = Color(0xFFA6461F), ac2 = Color(0xFF3F7D34)),
        dark = FamilyPalette(bg = Color(0xFF17110E), sf = Color(0xFF221915), sf2 = Color(0xFF2B201B), fg = Color(0xFFF7EAE2), mu = Color(0xFFC1ABA0), ln = Color(0xFF3A2C25), ac = Color(0xFFEB8A62), aci = Color(0xFF2C1408), acs = Color(0xFF3A261C), acd = Color(0xFFF09E7B), ac2 = Color(0xFF86C474)),
    )

    private val CADERNODESERMAO = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFF7F7F3), sf = Color(0xFFFFFFFF), sf2 = Color(0xFFECEEF0), fg = Color(0xFF17223A), mu = Color(0xFF4C566C), ln = Color(0xFFDADDE3), ac = Color(0xFF1F3A5F), aci = Color(0xFFFFFFFF), acs = Color(0xFFE0E8F3), acd = Color(0xFF1F3A5F), ac2 = Color(0xFFC0392B)),
        dark = FamilyPalette(bg = Color(0xFF0F141D), sf = Color(0xFF161D2A), sf2 = Color(0xFF1D2535), fg = Color(0xFFE9EEF6), mu = Color(0xFFA6B0C2), ln = Color(0xFF2A3345), ac = Color(0xFF8DB4E8), aci = Color(0xFF0E1C30), acs = Color(0xFF1F2C42), acd = Color(0xFF9DC0EE), ac2 = Color(0xFFF08C7E)),
    )

    private val ATLASBIBLICO = FamilyColorScheme(
        light = FamilyPalette(bg = Color(0xFFF8F3EA), sf = Color(0xFFFFFFFF), sf2 = Color(0xFFEFE4D1), fg = Color(0xFF1B2530), mu = Color(0xFF4F5966), ln = Color(0xFFE1D5C1), ac = Color(0xFF1D6FA3), aci = Color(0xFFFFFFFF), acs = Color(0xFFDCEBF5), acd = Color(0xFF17608E), ac2 = Color(0xFFC98A2B)),
        dark = FamilyPalette(bg = Color(0xFF10161C), sf = Color(0xFF172029), sf2 = Color(0xFF1E2933), fg = Color(0xFFEEF0EC), mu = Color(0xFFA9B3BC), ln = Color(0xFF2A3743), ac = Color(0xFF6FB6E3), aci = Color(0xFF0A2233), acs = Color(0xFF1C3243), acd = Color(0xFF85C3EA), ac2 = Color(0xFFE0A64A)),
    )

}

/**
 * Cores fixas iguais nos 12 apps (fora dos 11 tokens), do `ds.css`: os 5 marca-textos (Bíblia/Decora),
 * o véu das camadas, o fundo do anúncio em tela cheia e os círculos da folha de compartilhar.
 */
object FamilyFixedColors {
    /** Marca-texto amarelo (`.hl1`). */
    val highlightYellow: Color = Color(0x61FFD600)
    /** Marca-texto verde (`.hl2`). */
    val highlightGreen: Color = Color(0x6178D278)
    /** Marca-texto azul (`.hl3`). */
    val highlightBlue: Color = Color(0x616EAAFF)
    /** Marca-texto rosa (`.hl4`). */
    val highlightPink: Color = Color(0x61FF8CB4)
    /** Marca-texto laranja (`.hl5`). */
    val highlightOrange: Color = Color(0x61FFA046)
    /** Os 5 marca-textos na ordem do protótipo. */
    val highlights: List<Color> get() = listOf(highlightYellow, highlightGreen, highlightBlue, highlightPink, highlightOrange)
    /** Véu das folhas e diálogos (`rgba(10,8,5,.52)`). */
    val scrim: Color = Color(0x850A0805)
    /** Fundo do anúncio em tela cheia (`#111`). */
    val interstitialBackground: Color = Color(0xFF111111)
    /** Texto sobre o fundo do anúncio em tela cheia. */
    val interstitialContent: Color = Color(0xFFFFFFFF)
    /** Texto secundário do anúncio em tela cheia (`#cfcfcf`). */
    val interstitialMuted: Color = Color(0xFFCFCFCF)
    /** Círculo do WhatsApp na folha de compartilhar. */
    val shareWhatsApp: Color = Color(0xFF1F8F4E)
    /** Círculo do Status. */
    val shareStatus: Color = Color(0xFF2B6B4A)
    /** Círculo do Instagram. */
    val shareInstagram: Color = Color(0xFFB23A6E)
    /** Texto/ícone sobre os três círculos coloridos. */
    val shareOnColor: Color = Color(0xFFFFFFFF)
    /** A bolinha do interruptor (branca nos dois temas, como no protótipo). */
    val switchThumb: Color = Color(0xFFFFFFFF)
}
