package br.com.codecacto.kmplib.platform.brand

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * O **ícone do próprio app**, como o sistema o mostra — o do launcher no Android
 * (`android:icon`, adaptativo incluído) e o `AppIcon` do bundle no iOS.
 *
 * Serve para a marca aparecer em tela da lib sem o app ter de empacotar a mesma arte duas vezes
 * (uma no launcher, outra em `composeResources`): é o que a `LoginScreen` usa no cabeçalho quando o
 * app não passa `logo` (kmplib 2.241.0). Cada flavor tem o seu ícone, então sai certo também nos
 * apps de N flavors — o mesmo raciocínio de `BuildInfo.appName`.
 *
 * `null` quando a plataforma não entrega o ícone (bundle sem `CFBundleIcons`, falha ao decodificar):
 * quem chama desenha só o nome. Nunca lança.
 *
 * No iOS a imagem é quadrada e opaca (o sistema é que arredonda no launcher) — arredonde ao
 * desenhar (`AppBrandHeader` da `kmplib-ui` já faz).
 */
@Composable
expect fun rememberAppIconPainter(): Painter?

/**
 * Regra pura do iOS: de qual arquivo do bundle sai o ícone, em ordem de tentativa.
 *
 * `CFBundleIconFiles` lista os arquivos soltos que o Xcode copia para a raiz do bundle
 * (`AppIcon60x60`, `AppIcon76x76`…), do menor para o maior — por isso de trás para frente.
 * `CFBundleIconName` (o nome do conjunto no catálogo de assets) entra por último: `UIImage(named:)`
 * nem sempre resolve um *app icon set*, e quando resolve é a melhor resolução.
 */
internal fun appIconCandidates(iconFiles: List<String>, iconName: String?): List<String> =
    (listOfNotNull(iconName?.trim()) + iconFiles.map { it.trim() }.reversed())
        .filter { it.isNotEmpty() }
        .distinct()
