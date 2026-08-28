/**
 * Camada opcional sobre [kmplib.module] para os módulos que desenham tela.
 *
 * Fica separado porque o compilador do Compose custa tempo de build em todo módulo onde é
 * aplicado, e metade da lib (`validation`, `brdata`, `qr`, `pix`, `core`) não tem um `@Composable`
 * sequer. Aplicar Compose neles seria pagar o preço sem usar nada.
 */
plugins {
    id("kmplib.module")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
