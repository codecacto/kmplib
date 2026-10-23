package br.com.codecacto.kmplib.testing

import br.com.codecacto.kmplib.monetization.purchase.KmpLibStoreDoubleApi
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseRepository
import br.com.codecacto.kmplib.platform.automation.AutomationMode
import br.com.codecacto.kmplib.platform.automation.AutomationModeApi
import br.com.codecacto.kmplib.platform.automation.AutomationSignal

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
 * O gancho chama a costura pública **marcada** do `kmplib-monetization`
 * (`PurchaseManager.installStoreDouble`/`clearStoreDouble`, 2.325.0), que exige
 * `@OptIn(KmpLibStoreDoubleApi::class)` em nível ERROR — a forma recomendada pelo Kotlin para API
 * que atravessa módulo sem virar contrato de app. Até a 2.324.0 isto era feito por *friend modules*
 * (`-Xfriend-paths`/`-friend-modules`, flags não estáveis do compilador), e a compilação de metadata
 * do `iosMain` ficava sem a amizade: o `publishToMavenLocal` com os alvos Apple quebrava.
 *
 * ## Android e iOS
 *
 * Há uma cópia idêntica em `iosMain` (desde 14/08/2026, para a captura do print de review). Desde a
 * 2.325.0 o corpo só chama API pública marcada, e o lugar natural é `commonMain` — consolidar as
 * duas cópias lá é a limpeza seguinte (pendente: apagar as cópias é passo do fundador).
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
     *
     * **Liga também o [AutomationMode]** (2.238.0): processo com o dublê da loja é processo de teste,
     * e a lib cala os pedidos automáticos (avaliação por contagem, atualização opcional) que abririam
     * por cima da tela no meio da suíte. O [limpar] NÃO desliga — o processo continua sendo de teste.
     */
    @OptIn(AutomationModeApi::class, KmpLibStoreDoubleApi::class)
    fun instalar(repository: PurchaseRepository) {
        AutomationMode.activate(AutomationSignal.STORE_DOUBLE)
        PurchaseManager.installStoreDouble(repository)
    }

    /**
     * Devolve o `PurchaseManager` ao estado zerado. **Chame no `@After`.**
     *
     * `PurchaseManager` é um `object`: o repositório instalado sobrevive ao fim do teste e vale para
     * o processo inteiro. Sem esta limpeza, um teste que instalou [FakePurchaseRepository.jaAssinante]
     * faz o teste seguinte abrir com assinatura ativa — e aí o vermelho aparece no teste errado,
     * numa ordem que muda a cada execução.
     */
    @OptIn(KmpLibStoreDoubleApi::class)
    fun limpar() {
        PurchaseManager.clearStoreDouble()
    }
}
