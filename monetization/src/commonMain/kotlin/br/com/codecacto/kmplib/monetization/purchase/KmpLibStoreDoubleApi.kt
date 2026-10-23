package br.com.codecacto.kmplib.monetization.purchase

/**
 * Marca a **costura da loja de teste** do `kmplib-monetization` (2.325.0): o ponto em que o
 * artefato `kmplib-testing` (`PurchaseTestHooks.instalar`/`limpar`) troca o repositório de compra
 * do processo por um dublê.
 *
 * ## Por que existe
 *
 * `internal` do Kotlin não atravessa módulo publicado. Até a 2.324.0 o `kmplib-testing` alcançava o
 * `internal fun initializeWith` por *friend modules* (`-Xfriend-paths`/`-friend-modules`), flags
 * NÃO estáveis do compilador, passadas à mão por tarefa. Cada compilação nova precisava da amizade
 * — e a de metadata do `iosMain` (`compileIosMainKotlinMetadata`) nunca a teve: o
 * `publishToMavenLocal` com os alvos Apple quebrava com "it is internal in PurchaseManager".
 *
 * A forma recomendada pelo Kotlin para "público só para quem declara que sabe o que está fazendo" é
 * esta: `@RequiresOptIn` (a mesma de `@InternalCoroutinesApi`, `KmpLibCoreInternalApi` e
 * `AutomationModeApi`). Nível **ERROR**: sem `@OptIn(KmpLibStoreDoubleApi::class)` escrito à mão,
 * não compila.
 *
 * ## Não é API de app
 *
 * O que está atrás desta marca troca **a implementação que decide se alguém é assinante**. Em app,
 * use o `kmplib-testing` (`PurchaseTestHooks`), declarado só em configuração de teste/QA e travado
 * pela variante pelo plugin `br.com.codecacto.kmplib.store-double`. Chamar isto de código de
 * produção é defeito de revisão — o opt-in existe para que a chamada fique visível no diff.
 */
@RequiresOptIn(
    message = "Costura da loja de teste da kmplib (kmplib-testing / PurchaseTestHooks). " +
        "Troca o repositório que decide se alguém é assinante: nunca use em código de produção.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class KmpLibStoreDoubleApi
