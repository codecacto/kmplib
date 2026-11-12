package br.com.codecacto.kmplib.familia.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Os 11 tokens de cor de um app da família (nomes do `ds.css`):
 *
 * | token | papel |
 * |---|---|
 * | [bg] | fundo da tela |
 * | [sf] | superfície (cartão, lista, abas) |
 * | [sf2] | superfície 2 (faixa, banner, chip de dia, segmentado) |
 * | [fg] | texto |
 * | [mu] | texto secundário |
 * | [ln] | linha/borda |
 * | [ac] | cor de ação (botão, interruptor ligado) |
 * | [aci] | texto sobre [ac] |
 * | [acs] | ação suave (fundo de selo, aba ativa, cartão `tint`) |
 * | [acd] | ação escura (texto de destaque, ícone da aba ativa) |
 * | [ac2] | cor secundária da marca |
 */
@Immutable
data class FamilyPalette(
    val bg: Color,
    val sf: Color,
    val sf2: Color,
    val fg: Color,
    val mu: Color,
    val ln: Color,
    val ac: Color,
    val aci: Color,
    val acs: Color,
    val acd: Color,
    val ac2: Color,
)

/** O par claro/escuro de um app. */
@Immutable
data class FamilyColorScheme(val light: FamilyPalette, val dark: FamilyPalette) {
    /** A paleta do tema pedido. */
    fun forTheme(dark: Boolean): FamilyPalette = if (dark) this.dark else light
}

/**
 * Um **token próprio do app** (D16): cor fora dos 11 tokens — cartão herói do Rosário, níveis do
 * Decora, eras do Atlas — sempre com o par claro/escuro, declarado no arquivo de paleta DO APP.
 * Lido com [resolve] (segue o tema em vigor).
 */
@Immutable
data class FamilyColorPair(val light: Color, val dark: Color) {
    /** A cor do tema em vigor no [FamilyTheme]. */
    @Composable
    @ReadOnlyComposable
    fun resolve(): Color = if (LocalFamilyDark.current) dark else light
}
