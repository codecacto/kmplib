package br.com.codecacto.kmplib.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.util.AppLogger
import java.net.URLEncoder

/**
 * Ponto de entrada HISTÓRICO do holder de contexto — hoje só delega para
 * [AndroidAppContext], que mora em `core`.
 *
 * O holder saiu daqui porque `core` precisava dele (preferências, rede, `BuildInfo`) e não pode
 * depender de `platform`: era o ciclo que travava a modularização da lib. Continua público para
 * não quebrar quem já chama `UrlLauncherHolder.init(context)` no `Application.onCreate()`.
 */
@Deprecated(
    message = "Use initKmpLib(context), que já inicializa o AndroidAppContext.",
    replaceWith = ReplaceWith(
        "AndroidAppContext.init(context)",
        "br.com.codecacto.kmplib.core.context.AndroidAppContext"
    )
)
object UrlLauncherHolder {
    fun init(context: Context) = AndroidAppContext.init(context)
}

class AndroidUrlLauncher(private val context: Context) : UrlLauncher {

    companion object {
        private const val TAG = "UrlLauncher"
    }

    override fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir URL: $url", e)
        }
    }

    override fun openEmail(to: String, subject: String, body: String) {
        try {
            val uri = buildString {
                append("mailto:")
                append(Uri.encode(to))
                val params = mutableListOf<String>()
                if (subject.isNotEmpty()) {
                    params.add("subject=${Uri.encode(subject)}")
                }
                if (body.isNotEmpty()) {
                    params.add("body=${Uri.encode(body)}")
                }
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }
            }

            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir email", e)
        }
    }

    override fun openPhone(phoneNumber: String) {
        try {
            val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' }
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir telefone: $phoneNumber", e)
        }
    }

    override fun openWhatsApp(phone: String, message: String) {
        try {
            val cleanPhone = phone.filter { it.isDigit() }
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage"

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir WhatsApp", e)
        }
    }

    override fun openStorePage(androidPackage: String?, iosAppId: String?) {
        try {
            val packageName = androidPackage ?: context.packageName
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Se não tiver Play Store, abre no navegador
                openUrl("https://play.google.com/store/apps/details?id=$packageName")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir loja", e)
        }
    }

    override fun openMap(query: String) {
        try {
            val encodedQuery = Uri.encode(query)
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=$encodedQuery")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Se não tiver app de mapas, abre no Google Maps web
                openUrl("https://www.google.com/maps/search/?api=1&query=$encodedQuery")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir mapa", e)
        }
    }

    override fun openSubscriptionManagement() {
        val packageName = context.packageName
        openUrl("https://play.google.com/store/account/subscriptions?package=$packageName")
    }

    override fun openAppSettings() {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao abrir as Configurações do app", e)
        }
    }
}

actual fun getUrlLauncher(): UrlLauncher {
    val context = AndroidAppContext.get()
        ?: throw IllegalStateException("kmplib não foi inicializada. Chame initKmpLib(context) no Application.onCreate()")
    return AndroidUrlLauncher(context)
}
