package br.com.codecacto.kmplib.monetization.purchase

actual val PurchaseConfig.platformApiKey: String
    get() = androidApiKey
