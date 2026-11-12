package br.com.codecacto.kmplib.familia.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A identidade de um app que usa o kit visual (2.358.0): nome, slug, ícone e paleta. É o que o
 * [FamilyTheme] e as telas comuns leem — topo, boas-vindas, Sobre.
 *
 * O kit serve à **família visual CodeCacto** em geral, não só aos 12 apps cristãos: o nome do artefato
 * (`kmplib-familia`) ficou por compatibilidade. Os 12 cristãos são o [FamilyApp] (que implementa esta
 * interface); um app de FORA da família declara a própria identidade com a fábrica
 * [FamilyAppIdentity] e a sua paleta (11 tokens × claro/escuro, como no protótipo):
 *
 * ```kotlin
 * // Um lugar só do app com Color(0x…) — o mesmo papel do FamilyPalettes.
 * val LocaMotoristaIdentity = FamilyAppIdentity(
 *     slug = "loca-motorista",
 *     displayName = "Loca Motorista",
 *     icon = LocaMotoristaIconArt,            // ImageVector 100×100 do protótipo
 *     scheme = FamilyColorScheme(light = FamilyPalette(…), dark = FamilyPalette(…)),
 * )
 *
 * FamilyTheme(LocaMotoristaIdentity, darkTheme = mode.isDarkTheme(), textScale = scale) { … }
 * ```
 *
 * O que muda para app de fora (nada é configurado à mão): "Outros apps" não mostra os 12 cristãos
 * ([familyOtherAppsCatalogFor] = vazio — passe `onOtherApps = null` no "Mais" ou o seu próprio catálogo
 * a `FamilyOtherAppsScreen(onBack, catalog = …)`), e [FamilyTheme.app] não existe (use
 * [FamilyTheme.identity]). "Sobre a tradução" é opcional e só aparece se o app passar
 * `onAboutTranslation`.
 */
@Stable
interface FamilyAppIdentity {
    /** Prefixo curto do protótipo (`vh`, `rh`…); no app de fora, default = o [slug]. */
    val code: String

    /** Slug do projeto/repo/loja (`"versiculo-hoje"`). */
    val slug: String

    /** Nome de nascença (pt-BR); as telas recebem o nome por idioma quando for outro. */
    val displayName: String

    /** Ícone do app (arte 100×100). Recorte em squircle: `FamilyAppIcon(identity.icon, …)`. */
    val icon: ImageVector

    /** A paleta claro/escuro (11 tokens). */
    val scheme: FamilyColorScheme
}

/**
 * Identidade de um app de fora da família cristã, com a própria paleta e o próprio ícone.
 * Igualdade por valor (duas declarações iguais são a mesma identidade).
 *
 * @param code prefixo curto (default = [slug]).
 */
fun FamilyAppIdentity(
    slug: String,
    displayName: String,
    icon: ImageVector,
    scheme: FamilyColorScheme,
    code: String = slug,
): FamilyAppIdentity {
    require(slug.isNotBlank()) { "slug vazio" }
    require(displayName.isNotBlank()) { "displayName vazio" }
    return CustomFamilyAppIdentity(code = code, slug = slug, displayName = displayName, icon = icon, scheme = scheme)
}

@Immutable
private data class CustomFamilyAppIdentity(
    override val code: String,
    override val slug: String,
    override val displayName: String,
    override val icon: ImageVector,
    override val scheme: FamilyColorScheme,
) : FamilyAppIdentity {
    override fun toString(): String = "FamilyAppIdentity($slug)"
}

/** O app da família cristã, ou `null` quando é uma identidade de fora. */
val FamilyAppIdentity.familyApp: FamilyApp? get() = this as? FamilyApp

/**
 * O catálogo de "Outros apps" que o kit oferece por padrão a [identity]: os 12 [FamilyApp] para um app
 * da família cristã; **vazio** para app de fora (que passa o próprio catálogo, se tiver). Puro.
 */
fun familyOtherAppsCatalogFor(identity: FamilyAppIdentity): List<FamilyAppIdentity> =
    if (identity is FamilyApp) FamilyApp.entries else emptyList()

/** A paleta em vigor de [identity] no tema [darkTheme]. Pura (é a conta do [FamilyTheme]). */
fun familyPaletteFor(identity: FamilyAppIdentity, darkTheme: Boolean): FamilyPalette = identity.scheme.forTheme(darkTheme)
