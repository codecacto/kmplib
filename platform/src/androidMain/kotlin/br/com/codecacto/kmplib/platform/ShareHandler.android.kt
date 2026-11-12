package br.com.codecacto.kmplib.platform

import android.app.Activity
import br.com.codecacto.kmplib.platform.audience.withParentalGate
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.core.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

/**
 * Holder para o contexto do Android.
 * Deve ser inicializado no Application.onCreate() ou MainActivity.
 */
object ShareHandlerHolder {
    private var contextRef: WeakReference<Context>? = null
    private var activityRef: WeakReference<Activity>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    /**
     * A `Activity` em foco (via `kmpLibPlatformOnResume`, desde 2.195.0). Com ela o chooser abre
     * **na tarefa do app**, como a documentação do Android manda; sem ela o handler cai no
     * `applicationContext` + `FLAG_ACTIVITY_NEW_TASK`, que funciona mas abre a folha numa tarefa
     * separada (o "voltar" e a multitarefa se comportam como se fosse outro app).
     */
    internal fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    internal fun clearActivity() {
        activityRef = null
    }

    internal fun getContext(): Context? = contextRef?.get()

    internal fun currentActivity(): Activity? =
        activityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
}

class AndroidShareHandler(private val context: Context) : ShareHandler {

    companion object {
        private const val TAG = "ShareHandler"
    }

    override fun shareText(text: String, title: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            launchChooser(intent, title)
        } catch (e: Exception) {
            // Loga E RELANÇA: engolir a exceção fazia o chamador (ex.: ExportService)
            // registrar "sucesso" sem nada ter sido compartilhado.
            AppLogger.e(TAG, "Erro ao compartilhar texto", e)
            throw e
        }
    }

    /**
     * Como a documentação oficial ("Send simple data to other apps") recomenda: `ACTION_SEND`
     * `text/plain` com o texto em `EXTRA_TEXT`, e o título em `EXTRA_TITLE` — é ele que aparece
     * como prévia no topo da folha do Android 10+ (o título do chooser, sozinho, o sistema não
     * mostra mais). `EXTRA_SUBJECT` é o assunto quando o destino é um app de e-mail.
     */
    override fun shareLink(url: String, message: String, title: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, composeShareText(message, url))
                if (title.isNotBlank()) {
                    putExtra(Intent.EXTRA_TITLE, title)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                }
            }
            launchChooser(intent, title)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao compartilhar link", e)
            throw e
        }
    }

    override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) {
        shareFile(imageBytes, fileName, getMimeType(fileName), title)
    }

    override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) {
        try {
            // Purga ANTES de gravar: o arquivo deste share é o mais novo do diretório, então nunca é
            // vítima da própria limpeza — e o resíduo dos shares anteriores não fica para sempre.
            clearSharedFiles()

            val cacheDir = sharedFilesDir()
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Nome sanitizado: vem do chamador (às vezes de dado do usuário) e um separador
            // escreveria fora do diretório de compartilhamento.
            val file = File(cacheDir, sanitizeSharedFileName(fileName))
            FileOutputStream(file).use { it.write(fileBytes) }

            // Obtém URI via FileProvider
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            launchChooser(intent, title)
        } catch (e: Exception) {
            // Loga E RELANÇA. Antes, o catch engolia a IllegalArgumentException do
            // FileProvider.getUriForFile (provider não declarado) e o chamador recebia
            // "sucesso" sem nada compartilhado — quebrava todo compartilhamento de
            // arquivo no Android. Agora a falha propaga para o caller refletir erro.
            // Só o TIPO do erro (2.276.0): o nome do arquivo — e a mensagem da exceção, que traz o
            // caminho (FileProvider: "Failed to find configured root that contains …/<nome>") —
            // pode conter dado de saúde ou pessoal ("laudo-joao-silva.pdf") e ia para log/GlitchTip.
            AppLogger.e(TAG, "Erro ao compartilhar arquivo: ${e::class.simpleName}")
            throw e
        }
    }

    /**
     * Apaga o resíduo dos compartilhamentos anteriores.
     *
     * **Não** existe no Android callback de "o app receptor terminou de ler a URI" — o `ACTION_SEND`
     * é assíncrono e o receptor lê depois, às vezes com o nosso processo já morto. Por isso a
     * limpeza é por **idade** (e nunca "logo depois de disparar o chooser", que quebraria o share).
     */
    override fun clearSharedFiles(olderThanMillis: Long): Int {
        val dir = sharedFilesDir()
        val arquivos = dir.listFiles() ?: return 0
        val agora = System.currentTimeMillis()
        var apagados = 0
        arquivos.forEach { file ->
            if (!file.isFile) return@forEach
            if (!shouldPurgeSharedFile(file.lastModified(), agora, olderThanMillis)) return@forEach
            if (file.delete()) {
                apagados++
            } else {
                AppLogger.w(TAG, "não foi possível apagar um arquivo compartilhado")
            }
        }
        return apagados
    }

    override fun isInstalled(target: ShareTarget, options: ShareImageOptions): Boolean = when (target) {
        ShareTarget.WHATSAPP -> whatsAppPackage() != null
        // Sem intent oficial para o Status: o WhatsApp oferece "Meu status" dentro do envio comum.
        ShareTarget.WHATSAPP_STATUS -> false
        ShareTarget.INSTAGRAM_STORIES ->
            isValidMetaAppId(options.metaAppId) && isPackageInstalled(INSTAGRAM_ANDROID_PACKAGE)
    }

    /**
     * WhatsApp: `ACTION_SEND` com `setPackage` (documentação do WhatsApp, "Android: share to WhatsApp").
     * Instagram: `com.instagram.share.ADD_TO_STORY` com a imagem como fundo e `source_application` =
     * App ID da Meta, `grantUriPermission` explícito ao pacote do Instagram (documentação da Meta,
     * "Sharing to Stories").
     */
    override fun shareImageTo(
        target: ShareTarget,
        imageBytes: ByteArray,
        fileName: String,
        options: ShareImageOptions,
    ): ShareTargetResult {
        if (!isInstalled(target, options)) return ShareTargetResult.NotAvailable
        return try {
            clearSharedFiles()
            val uri = writeSharedFile(imageBytes, fileName)
            val mime = getMimeType(fileName).takeIf { it.startsWith("image/") } ?: "image/png"
            val intent = when (target) {
                ShareTarget.WHATSAPP -> Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    setPackage(whatsAppPackage())
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ShareTarget.INSTAGRAM_STORIES -> Intent(INSTAGRAM_STORIES_ANDROID_ACTION).apply {
                    setDataAndType(uri, mime)
                    setPackage(INSTAGRAM_ANDROID_PACKAGE)
                    putExtra("source_application", options.metaAppId!!.trim())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ShareTarget.WHATSAPP_STATUS -> return ShareTargetResult.NotAvailable
            }
            val host: Context = ShareHandlerHolder.currentActivity() ?: context
            if (target == ShareTarget.INSTAGRAM_STORIES) {
                host.grantUriPermission(INSTAGRAM_ANDROID_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(context.packageManager) == null) return ShareTargetResult.NotAvailable
            if (host !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            host.startActivity(intent)
            ShareTargetResult.Opened
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro no envio direto (${target.name}): ${e::class.simpleName}")
            ShareTargetResult.Failed(e::class.simpleName ?: "erro")
        }
    }

    private fun writeSharedFile(bytes: ByteArray, fileName: String): android.net.Uri {
        val dir = sharedFilesDir()
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, sanitizeSharedFileName(fileName))
        FileOutputStream(file).use { it.write(bytes) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun whatsAppPackage(): String? = WHATSAPP_ANDROID_PACKAGES.firstOrNull { isPackageInstalled(it) }

    /** Pede ao sistema; a visibilidade dos pacotes vem do `<queries>` do manifesto da lib (Android 11+). */
    private fun isPackageInstalled(pkg: String): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0)
        }
        true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Abre o chooser do sistema a partir da `Activity` em foco, quando houver; senão, do
     * `applicationContext` com `FLAG_ACTIVITY_NEW_TASK` (obrigatório fora de uma Activity).
     */
    private fun launchChooser(intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title.ifEmpty { "Compartilhar" })
        val activity = ShareHandlerHolder.currentActivity()
        if (activity != null) {
            activity.startActivity(chooser)
        } else {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    private fun sharedFilesDir(): File = File(context.cacheDir, SHARED_FILES_DIRECTORY)

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}

actual fun getShareHandler(): ShareHandler {
    // 2.311.0: app de módulos granulares que só chama `initKmpLibCore` (o LocAki) não preenchia o
    // holder e todo compartilhamento LANÇAVA; o `Context` da aplicação vindo do core serve igual.
    val context = ShareHandlerHolder.getContext() ?: AndroidAppContext.get()
        ?: throw IllegalStateException("kmplib sem Context: chame initKmpLibCore(context) ou initKmpLibPlatform(context) no Application.onCreate()")
    // Portão de pais em app infantil (2.259.0) — fora dele, chamada direta.
    return AndroidShareHandler(context).withParentalGate()
}
