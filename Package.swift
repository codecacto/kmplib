// swift-tools-version:5.9
//
// RESÍDUO da era AdMob (antes da 2.38.0) — nenhum app nem a casca referencia este pacote, e as
// dependências iOS da lib são declaradas pelo APP (ver IOS_INTEGRATION.md §"Dependências iOS (SPM)").
// Até a 2.290.0 ele ainda declarava `GoogleMobileAds` (AdMob, que lê o IDFA): quem o adicionasse ao
// Xcode receberia o SDK de anúncio de terceiros que a fábrica não usa. A dependência saiu na
// 2.291.0; o arquivo e `Sources/KmpLibDeps/` são candidatos a apagar (comando no handoff).
import PackageDescription

let package = Package(
    name: "KmpLibDeps",
    platforms: [.iOS(.v14)],
    dependencies: [],
    targets: [
        .target(name: "KmpLibDeps", dependencies: [])
    ]
)
