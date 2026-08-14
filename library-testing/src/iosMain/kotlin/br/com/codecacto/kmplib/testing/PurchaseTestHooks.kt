package br.com.codecacto.kmplib.testing

import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseRepository

/**
 * **Ponto de injeção da loja em teste instrumentado.** Troca a implementação de compra que o app
 * usa, sem tocar em Google Play, App Store ou RevenueCat.
 *
 * ```kotlin
 * @Before fun antes() {
 *     PurchaseTestHooks.instalar(FakePurchaseRepository.compraQueDaCerto())
 * }
 *
 * @After fun depois() {
 *     PurchaseTestHooks.limpar()   // estado global VAZA entre testes — ver abaixo
 * }
 * ```
 *
 * ## O problema que isto resolve
 *
 * O app não recebe o repositório de compra por injeção: ele lê `PurchaseManager.repository`, um
 * `object` da kmplib cujo campo é privado e só escrito pelo `initialize`, que configura o SDK
 * nativo. Sem um ponto de injeção **na lib**, nenhum teste instrumentado de nenhum app do portfólio
 * conseguia simular uma compra — e era por isso que o `FakePurchaseRepository` do Super 8 existia
 * sem ser referenciado em lugar nenhum, e que a suíte `pagamento-e2e` só chegava a "o paywall
 * abriu", a metade menos interessante do fluxo.
 *
 * ## Por que ele mora em `kmplib-testing`, e não na kmplib
 *
 * Este gancho troca **a implementação que decide se alguém é assinante**. Publicado dentro da
 * kmplib, seria um caminho para injetar "é premium para todo mundo" alcançável em build de release,
 * por qualquer código do app ou por uma dependência dele — gancho de teste dentro do caminho do
 * dinheiro.
 *
 * Como `br.com.codecacto:kmplib-testing` é um artefato separado, declarado apenas em
 * `androidInstrumentedTestImplementation`, ele **não existe no APK/AAB de release**. Isso é
 * verificável, e vale conferir de vez em quando:
 *
 * ```bash
 * unzip -p app-release.aab "base/dex/classes.dex" | strings | grep -c PurchaseTestHooks  # 0
 * ```
 *
 * A visibilidade do `PurchaseManager.initializeWith` **não foi afrouxada** para isto funcionar: ele
 * segue `internal` na kmplib, e este módulo o alcança por *friend modules* (`-Xfriend-paths`, o
 * mecanismo oficial do compilador Kotlin — ver o `build.gradle.kts` daqui). A API publicada da
 * kmplib é idêntica à de antes.
 *
 * ## Android e iOS (iOS desde 14/08/2026)
 *
 * O gancho nasceu só em `androidMain`, porque era de lá que saíam os testes instrumentados. O iOS
 * entrou quando a captura do print de review passou a compilar o app com o dublê ligado: sem o
 * gancho no alvo Apple, `LojaDeDemonstracao.kt` não resolvia `PurchaseTestHooks`.
 *
 * O arquivo é o MESMO nos dois source sets, de propósito — e não `expect/actual`, porque não há nada
 * de específico de plataforma aqui: o corpo chama o `internal` da `:kmplib`, e o que muda por
 * plataforma é só a AMIZADE do compilador (ver `-Xfriend-paths`/`-Xfriend-modules` no
 * `build.gradle.kts`). Subir para `commonMain` obrigaria a compilação de METADATA a ter amizade
 * também, o que ampliaria a superfície do ajuste sem ganho.
 */
object PurchaseTestHooks {

    /**
     * Instala [repository] como a loja do app, imediatamente.
     *
     * Chame **antes** de a tela sob teste ser composta — um `PaywallViewModel` que já leu
     * `PurchaseManager.repository` guarda a referência antiga e continuaria falando com a loja de
     * verdade. Em `TestApplication.onCreate()` ou no `@Before`, antes do `setContent`.
     *
     * Não passa pelo `PurchaseInitializer` (que configura o SDK nativo), então não precisa de chave
     * de API, de rede nem de Play Services.
     */
    fun instalar(repository: PurchaseRepository) {
        PurchaseManager.initializeWith(repository)
    }

    /**
     * Devolve o `PurchaseManager` ao estado zerado. **Chame no `@After`.**
     *
     * `PurchaseManager` é um `object`: o repositório instalado sobrevive ao fim do teste e vale para
     * o processo inteiro. Sem esta limpeza, um teste que instalou [FakePurchaseRepository.jaAssinante]
     * faz o teste seguinte abrir com assinatura ativa — e aí o vermelho aparece no teste errado,
     * numa ordem que muda a cada execução.
     */
    fun limpar() {
        PurchaseManager.reset()
    }
}
