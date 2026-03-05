# Monetization - Ads & Assinaturas

Sistema unificado de monetizacao da KmpLib que suporta 3 modos:

| Modo | Ads | Assinatura | Caso de uso |
|---|---|---|---|
| **AdsOnly** | Sim, sempre | Nao | App gratuito com publicidade |
| **PremiumOnly** | Nao | Sim | App pago por assinatura |
| **Freemium** | Sim (free) / Nao (premium) | Sim | Gratuito com ads, paga pra remover |

---

## Pre-requisitos

### Android

No `Application.onCreate()`:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KmpLib.init(this) // necessario para AdMob
    }
}
```

Na `MainActivity`:

```kotlin
override fun onResume() {
    super.onResume()
    KmpLib.setActivity(this) // necessario para interstitial e app open
}
```

### iOS

Adicionar o GoogleMobileAds framework ao projeto Xcode (se usar ads).

### RevenueCat

Criar conta em [revenuecat.com](https://www.revenuecat.com), configurar app Android e iOS,
e obter as API keys. Configurar os produtos e o entitlement no dashboard.

---

## Inicializacao

Chame `MonetizationManager.initialize()` **uma vez** no inicio do app (ex: `LaunchedEffect` no `App.kt`).

### Modo 1: Somente Ads

```kotlin
import br.com.codecacto.kmplib.firebase.ads.AdConfig
import br.com.codecacto.kmplib.firebase.ads.AdUnitIds
import br.com.codecacto.kmplib.monetization.MonetizationConfig
import br.com.codecacto.kmplib.monetization.MonetizationManager

MonetizationManager.initialize(
    MonetizationConfig.AdsOnly(
        ads = AdConfig(
            banner = AdUnitIds(
                android = "ca-app-pub-xxx/111",
                ios = "ca-app-pub-xxx/222"
            ),
            interstitial = AdUnitIds(
                android = "ca-app-pub-xxx/333",
                ios = "ca-app-pub-xxx/444"
            ),
            appOpen = AdUnitIds( // opcional
                android = "ca-app-pub-xxx/555",
                ios = "ca-app-pub-xxx/666"
            ),
            testMode = BuildConfig.DEBUG,
            remoteConfigKey = "admob_enabled" // chave no Firebase Remote Config
        )
    )
)
```

### Modo 2: Somente Assinatura (sem ads)

```kotlin
import br.com.codecacto.kmplib.monetization.MonetizationConfig
import br.com.codecacto.kmplib.monetization.MonetizationManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseConfig
import br.com.codecacto.kmplib.monetization.purchase.ProductConfig
import br.com.codecacto.kmplib.monetization.purchase.SubscriptionPeriod

MonetizationManager.initialize(
    MonetizationConfig.PremiumOnly(
        purchase = PurchaseConfig(
            androidApiKey = "goog_xxxxxx",
            iosApiKey = "appl_xxxxxx",
            entitlementId = "premium", // deve ser o mesmo no dashboard RevenueCat
            products = listOf(
                ProductConfig("premium_mensal", SubscriptionPeriod.MONTHLY),
                ProductConfig("premium_anual", SubscriptionPeriod.ANNUAL)
            ),
            debugMode = BuildConfig.DEBUG
        )
    )
)
```

### Modo 3: Freemium (Ads + Assinatura)

```kotlin
MonetizationManager.initialize(
    MonetizationConfig.Freemium(
        ads = AdConfig(
            banner = AdUnitIds(
                android = "ca-app-pub-xxx/111",
                ios = "ca-app-pub-xxx/222"
            ),
            interstitial = AdUnitIds(
                android = "ca-app-pub-xxx/333",
                ios = "ca-app-pub-xxx/444"
            ),
            testMode = BuildConfig.DEBUG
        ),
        purchase = PurchaseConfig(
            androidApiKey = "goog_xxxxxx",
            iosApiKey = "appl_xxxxxx",
            entitlementId = "premium",
            products = listOf(
                ProductConfig("premium_mensal", SubscriptionPeriod.MONTHLY),
                ProductConfig("premium_semestral", SubscriptionPeriod.SEMI_ANNUAL)
            ),
            debugMode = BuildConfig.DEBUG
        )
    )
)
```

Neste modo, quando o usuario assinar, **os ads somem automaticamente** sem nenhum codigo extra.

---

## Usando Ads

### Banner

Coloque em qualquer Composable. A lib decide sozinha se mostra ou nao.

```kotlin
import br.com.codecacto.kmplib.firebase.ads.BannerAd

@Composable
fun HomeScreen() {
    Column {
        // ... conteudo ...
        BannerAd(modifier = Modifier.fillMaxWidth())
    }
}
```

- **AdsOnly**: mostra o banner (respeita Remote Config)
- **PremiumOnly**: renderiza um `Spacer` vazio
- **Freemium**: mostra se free, esconde se premium

### Interstitial

```kotlin
import br.com.codecacto.kmplib.firebase.ads.AdManager

// Carregar (chamar antecipadamente, ex: ao abrir uma tela)
AdManager.interstitial?.load()

// Mostrar (ex: ao concluir uma acao)
AdManager.interstitial?.show {
    // onDismissed - continuar o fluxo do app
    navigateToNextScreen()
}
```

Se o usuario for premium ou ads estiverem desabilitados, `show()` chama `onDismissed()` imediatamente.

### App Open

```kotlin
import br.com.codecacto.kmplib.firebase.ads.AdManager

// Carregar
AdManager.appOpen?.load()

// Mostrar ao voltar pro app
AdManager.appOpen?.show {
    // onDismissed
}
```

Mesmo comportamento: se premium, chama `onDismissed()` direto.

---

## Usando Assinaturas

Disponivel nos modos **PremiumOnly** e **Freemium**.

### Verificar se e premium

```kotlin
import br.com.codecacto.kmplib.monetization.MonetizationManager

// Como Flow (reativo, para Compose)
val isPremium by MonetizationManager.isPremium.collectAsState()

if (isPremium) {
    // conteudo exclusivo
}

// Como suspend (para ViewModels)
val premium = PurchaseManager.repository?.isPremium() ?: false
```

### Buscar produtos disponiveis

```kotlin
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager

val result = PurchaseManager.repository?.getProducts()
result?.onSuccess { products ->
    // products: List<PurchaseProduct>
    // Cada PurchaseProduct tem: id, title, description, price, currencyCode, subscriptionPeriod
}
```

### Comprar

```kotlin
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.PurchaseResult

val result = PurchaseManager.repository?.purchase("premium_mensal")
when (result) {
    is PurchaseResult.Success -> {
        // Assinatura ativada! Ads ja sumiram automaticamente no Freemium.
    }
    is PurchaseResult.Error -> {
        // result.message, result.code
    }
    is PurchaseResult.Cancelled -> {
        // Usuario cancelou
    }
    null -> {
        // PurchaseManager nao inicializado (modo AdsOnly?)
    }
}
```

### Restaurar compras

```kotlin
import br.com.codecacto.kmplib.monetization.purchase.PurchaseManager
import br.com.codecacto.kmplib.monetization.purchase.RestoreResult

val result = PurchaseManager.repository?.restorePurchases()
when (result) {
    is RestoreResult.Success -> { /* assinatura restaurada */ }
    is RestoreResult.Error -> { /* result.message */ }
    is RestoreResult.NoPurchasesToRestore -> { /* nada para restaurar */ }
    null -> { /* PurchaseManager nao inicializado */ }
}
```

### Sincronizar estado

Chame ao abrir o app ou ao voltar do background:

```kotlin
PurchaseManager.repository?.syncSubscriptionState()
```

---

## Observando estados (Compose)

```kotlin
import br.com.codecacto.kmplib.monetization.MonetizationManager

@Composable
fun MyScreen() {
    val shouldShowAds by MonetizationManager.shouldShowAds.collectAsState()
    val isPremium by MonetizationManager.isPremium.collectAsState()

    if (isPremium) {
        PremiumBadge()
    }

    // BannerAd ja faz isso internamente, mas voce pode usar
    // shouldShowAds para logica customizada
    if (shouldShowAds) {
        // mostrar algo especifico para free users
    }
}
```

---

## Propriedades uteis

| Propriedade | Tipo | Descricao |
|---|---|---|
| `MonetizationManager.shouldShowAds` | `StateFlow<Boolean>` | Se ads devem ser exibidos |
| `MonetizationManager.isPremium` | `StateFlow<Boolean>` | Se usuario e premium |
| `MonetizationManager.hasAds` | `Boolean` | Se o modo inclui ads |
| `MonetizationManager.hasPurchase` | `Boolean` | Se o modo inclui assinatura |
| `MonetizationManager.config` | `MonetizationConfig?` | Configuracao atual |
| `PurchaseManager.repository` | `PurchaseRepository?` | Repositorio de compras (null se AdsOnly) |

---

## Comportamento automatico por modo

| Componente | AdsOnly | PremiumOnly | Freemium (free) | Freemium (premium) |
|---|---|---|---|---|
| `BannerAd()` | Mostra | Spacer vazio | Mostra | Spacer vazio |
| `interstitial.show()` | Mostra | onDismissed() | Mostra | onDismissed() |
| `appOpen.show()` | Mostra | onDismissed() | Mostra | onDismissed() |
| `isPremium` | false | true/false | false | true |
| `shouldShowAds` | true* | false | true* | false |

\* Respeita tambem o Firebase Remote Config (`admob_enabled`)

---

## Exemplo completo: App Freemium

```kotlin
// App.kt
@Composable
fun App() {
    LaunchedEffect(Unit) {
        MonetizationManager.initialize(
            MonetizationConfig.Freemium(
                ads = AdConfig(
                    banner = AdUnitIds("ca-app-pub-xxx/111", "ca-app-pub-xxx/222"),
                    interstitial = AdUnitIds("ca-app-pub-xxx/333", "ca-app-pub-xxx/444"),
                    testMode = true
                ),
                purchase = PurchaseConfig(
                    androidApiKey = "goog_xxxxxx",
                    iosApiKey = "appl_xxxxxx",
                    entitlementId = "premium",
                    products = listOf(
                        ProductConfig("premium_mensal", SubscriptionPeriod.MONTHLY),
                        ProductConfig("premium_semestral", SubscriptionPeriod.SEMI_ANNUAL)
                    ),
                    debugMode = true
                )
            )
        )

        // Carregar interstitial antecipadamente
        AdManager.interstitial?.load()
    }

    AppTheme {
        AppContent()
    }
}

// HomeScreen.kt
@Composable
fun HomeScreen(onNavigateToPremium: () -> Unit) {
    val isPremium by MonetizationManager.isPremium.collectAsState()

    Column {
        if (!isPremium) {
            TextButton(onClick = onNavigateToPremium) {
                Text("Assine Premium")
            }
        }

        // ... conteudo ...

        BannerAd(modifier = Modifier.fillMaxWidth())
    }
}

// PremiumScreen.kt (UI customizada do app)
@Composable
fun PremiumScreen() {
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<PurchaseProduct>>(emptyList()) }

    LaunchedEffect(Unit) {
        PurchaseManager.repository?.getProducts()?.onSuccess { products = it }
    }

    Column {
        Text("Seja Premium!")
        products.forEach { product ->
            Button(onClick = {
                scope.launch {
                    val result = PurchaseManager.repository?.purchase(product.id)
                    if (result is PurchaseResult.Success) {
                        // Ads ja sumiram automaticamente!
                    }
                }
            }) {
                Text("${product.title} - ${product.price}")
            }
        }

        TextButton(onClick = {
            scope.launch { PurchaseManager.repository?.restorePurchases() }
        }) {
            Text("Restaurar compras")
        }
    }
}
```

---

## Erros de compra

| Codigo | Descricao |
|---|---|
| `NETWORK_ERROR` | Sem conexao com a internet |
| `STORE_ERROR` | Erro na loja (Google Play / App Store) |
| `PRODUCT_NOT_FOUND` | Produto nao encontrado (ID errado?) |
| `PAYMENT_PENDING` | Pagamento pendente |
| `PAYMENT_DECLINED` | Pagamento recusado |
| `ALREADY_OWNED` | Usuario ja possui este produto |
| `UNKNOWN` | Erro desconhecido |
