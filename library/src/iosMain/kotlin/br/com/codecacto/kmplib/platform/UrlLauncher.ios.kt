package br.com.codecacto.kmplib.platform

import platform.Foundation.NSURL
import platform.Foundation.NSString
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.NSCharacterSet
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.UIKit.UIApplication
import br.com.codecacto.kmplib.core.util.AppLogger

class IosUrlLauncher : UrlLauncher {

    companion object {
        private const val TAG = "UrlLauncher"
    }

    override fun openUrl(url: String) {
        try {
            val nsUrl = NSURL.URLWithString(url) ?: return
            UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir URL: $url", e)
        }
    }

    override fun openEmail(to: String, subject: String, body: String) {
        try {
            val encodedSubject = encodeUrlComponent(subject)
            val encodedBody = encodeUrlComponent(body)

            val url = buildString {
                append("mailto:")
                append(to)
                val params = mutableListOf<String>()
                if (subject.isNotEmpty()) {
                    params.add("subject=$encodedSubject")
                }
                if (body.isNotEmpty()) {
                    params.add("body=$encodedBody")
                }
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }
            }

            openUrl(url)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir email", e)
        }
    }

    override fun openPhone(phoneNumber: String) {
        try {
            val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
            openUrl("tel:$cleanNumber")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir telefone: $phoneNumber", e)
        }
    }

    override fun openWhatsApp(phone: String, message: String) {
        try {
            val cleanPhone = phone.filter { it.isDigit() }
            val encodedMessage = encodeUrlComponent(message)
            openUrl("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir WhatsApp", e)
        }
    }

    override fun openStorePage(androidPackage: String?, iosAppId: String?) {
        try {
            if (iosAppId != null) {
                openUrl("itms-apps://itunes.apple.com/app/id$iosAppId")
            } else {
                AppLogger.w(TAG, "iosAppId não fornecido para openStorePage(). Forneça o ID do app na App Store.")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir loja", e)
        }
    }

    override fun openMap(query: String) {
        try {
            val encodedQuery = encodeUrlComponent(query)
            // Tenta abrir no Apple Maps
            val mapsUrl = "maps://?q=$encodedQuery"
            val nsUrl = NSURL.URLWithString(mapsUrl)

            if (nsUrl != null && UIApplication.sharedApplication.canOpenURL(nsUrl)) {
                UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
            } else {
                // Fallback para Google Maps web
                openUrl("https://www.google.com/maps/search/?api=1&query=$encodedQuery")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir mapa", e)
        }
    }

    override fun openSubscriptionManagement() {
        openUrl("https://apps.apple.com/account/subscriptions")
    }

    private fun encodeUrlComponent(value: String): String {
        return (value as NSString).stringByAddingPercentEncodingWithAllowedCharacters(
            NSCharacterSet.URLQueryAllowedCharacterSet
        ) ?: value
    }
}

actual fun getUrlLauncher(): UrlLauncher = IosUrlLauncher()
