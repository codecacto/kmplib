package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.platform.audience.withParentalGate
import br.com.codecacto.kmplib.core.util.AppLogger
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val TAG = "UrlLauncher"

/**
 * Desktop (2.353.0): `java.awt.Desktop` — o navegador e o cliente de e-mail PADRÃO do SO.
 *
 * - WhatsApp e mapa abrem na web (`api.whatsapp.com`, Google Maps — o único mapa da fábrica);
 * - loja: o PC não tem loja do app, então abre a página WEB da Play (ou da App Store, se só houver o
 *   id da Apple); sem nenhum dos dois, nada abre e o log diz por quê;
 * - telefone: `tel:` vai para o app que o SO associou (Teams, Telefone Vinculado…), quando houver;
 * - `openAppSettings`/`openSubscriptionManagement`: não existem no desktop — só aviso no log.
 */
internal class DesktopUrlLauncher : UrlLauncher {

    private fun browse(uri: String): Boolean = try {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        if (desktop == null || !desktop.isSupported(Desktop.Action.BROWSE)) {
            AppLogger.w(TAG, "o SO não oferece navegador padrão para ${urlForLog(uri)}")
            false
        } else {
            desktop.browse(URI(uri))
            true
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "Erro ao abrir ${urlForLog(uri)}: ${e::class.simpleName}")
        false
    }

    private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20")

    override fun openUrl(url: String) {
        browse(url)
    }

    override fun openEmail(to: String, subject: String, body: String) {
        val params = buildList {
            if (subject.isNotEmpty()) add("subject=${enc(subject)}")
            if (body.isNotEmpty()) add("body=${enc(body)}")
        }
        val uri = "mailto:" + enc(to) + if (params.isEmpty()) "" else "?" + params.joinToString("&")
        try {
            val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
            if (desktop != null && desktop.isSupported(Desktop.Action.MAIL)) desktop.mail(URI(uri)) else browse(uri)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir email: ${e::class.simpleName}")
        }
    }

    override fun openPhone(phoneNumber: String) {
        browse("tel:" + phoneNumber.filter { it.isDigit() || it == '+' })
    }

    override fun openWhatsApp(phone: String, message: String) {
        browse("https://api.whatsapp.com/send?phone=${phone.filter { it.isDigit() }}&text=${enc(message)}")
    }

    override fun openStorePage(androidPackage: String?, iosAppId: String?) {
        when {
            !androidPackage.isNullOrBlank() -> browse("https://play.google.com/store/apps/details?id=${enc(androidPackage)}")
            !iosAppId.isNullOrBlank() -> browse("https://apps.apple.com/app/id${enc(iosAppId)}")
            else -> AppLogger.w(TAG, "openStorePage no desktop exige androidPackage ou iosAppId")
        }
    }

    override fun openMap(query: String) {
        browse("https://www.google.com/maps/search/?api=1&query=${enc(query)}")
    }

    override fun openSubscriptionManagement() {
        AppLogger.w(TAG, "openSubscriptionManagement não existe no desktop (sem loja de app)")
    }

    override fun openAppSettings() {
        AppLogger.w(TAG, "openAppSettings não existe no desktop")
    }
}

actual fun getUrlLauncher(): UrlLauncher = DesktopUrlLauncher().withParentalGate()
