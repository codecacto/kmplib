// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "KmpLibDeps",
    platforms: [.iOS(.v14)],
    dependencies: [
        .package(url: "https://github.com/googleads/swift-package-manager-google-mobile-ads.git", from: "12.2.0")
    ],
    targets: [
        .target(
            name: "KmpLibDeps",
            dependencies: [
                .product(name: "GoogleMobileAds", package: "swift-package-manager-google-mobile-ads")
            ]
        )
    ]
)
