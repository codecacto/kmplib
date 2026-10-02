package br.com.codecacto.kmplib.ui.theme

import androidx.compose.runtime.Composable

/**
 * No iOS não há o que aplicar por tela — e é decisão, não lacuna.
 *
 * A status bar do iOS (estilo `.default`) segue o `userInterfaceStyle` da janela. Num app Compose
 * hospedado em SwiftUI (o molde da fábrica), o `preferredStatusBarStyle` do controller do Compose
 * não é consultado; sobra `UIWindow.overrideUserInterfaceStyle` — o mesmo mecanismo do
 * `.preferredColorScheme` do SwiftUI. Só que ele muda o *trait* da janela inteira, e é desse trait
 * que o `isSystemInDarkTheme()` do Compose lê: forçar "claro" por causa de UMA tela faz o
 * `AppTheme(darkTheme = isSystemInDarkTheme())` virar claro, o pedido do tema passar a ser "claro",
 * e o app fica preso no modo forçado mesmo depois de a tela sair.
 *
 * Por isso, no iOS, quem resolve é a origem: a tela segue o tema (login e cadastro da lib usam as
 * cores do `AppTheme` por default desde a 2.241.0), e o app de tema FIXO declara
 * `UIUserInterfaceStyle` (`Light`/`Dark`) no `Info.plist`, que é a forma da Apple de sair do modo
 * escuro automático.
 */
@Composable
internal actual fun ApplySystemBarsAppearance(background: SystemBarsBackground) = Unit
