package br.com.codecacto.kmplib.core.context

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Identidade do app DESKTOP (alvo `jvm`, 2.353.0) — o par do `AndroidAppContext`.
 *
 * Uma JVM não tem pacote, rótulo nem versão do app como o Android e o iOS têm: quem diz é o próprio
 * app, uma vez, no `main()` (`initKmpLibCore(DesktopAppInfo(...))`). Daqui saem o `BuildInfo`, a
 * pasta de dados (`appDataDirectory`), o nó das `AppPreferences` e o cofre de tokens do `kmplib-auth`.
 *
 * @property appId identificador estável do app — vira NOME DE PASTA (`%LOCALAPPDATA%\<appId>`) e nó de
 *   preferências. Só `A-Z a-z 0-9 . _ -`, 1..100 caracteres. Trocar o `appId` numa versão nova é
 *   começar do zero (sessão e preferências ficam na pasta antiga).
 * @property appName nome como o app se apresenta (Sobre, mensagens), `null` = sem nome.
 * @property versionName versão exibida (`"1.4.2"`).
 * @property buildNumber número do build.
 * @property isDebug build de desenvolvimento (o `BuildInfo.isDebug`).
 * @property dataDirectory pasta de dados explícita; `null` = o padrão do SO ([defaultAppDataDirectory]).
 */
data class DesktopAppInfo(
    val appId: String,
    val appName: String? = null,
    val versionName: String? = null,
    val buildNumber: String? = null,
    val isDebug: Boolean = false,
    val dataDirectory: Path? = null,
) {
    init {
        require(isValidDesktopAppId(appId)) {
            "appId inválido: use só letras, números, '.', '_' e '-' (1..100 caracteres)."
        }
    }
}

/** `true` se [appId] serve como nome de pasta em qualquer SO (e não escapa do diretório). */
fun isValidDesktopAppId(appId: String): Boolean =
    appId.length in 1..100 && appId != "." && appId != ".." &&
        appId.all { (it.isLetterOrDigit() && it.code < 128) || it == '.' || it == '_' || it == '-' }

/** Holder da [DesktopAppInfo] registrada por `initKmpLibCore(DesktopAppInfo)`. */
object DesktopAppContext {
    @Volatile
    private var info: DesktopAppInfo? = null

    /** Registra a identidade do app. Idempotente (a última vence). */
    fun init(appInfo: DesktopAppInfo) {
        info = appInfo
    }

    /** A identidade, ou `null` se a lib ainda não foi inicializada. */
    fun get(): DesktopAppInfo? = info

    /** A identidade, ou erro explicando o que faltou (ausência = bug de integração). */
    fun require(): DesktopAppInfo = info ?: error(
        "kmplib não foi inicializada no desktop. Chame initKmpLibCore(DesktopAppInfo(appId = \"…\")) no main().",
    )

    /** A pasta de dados do app (criada sob demanda por quem grava). Exige a lib inicializada. */
    fun appDataDirectory(): Path {
        val app = require()
        return app.dataDirectory ?: defaultAppDataDirectory(app.appId)
    }
}

/**
 * Pasta de dados LOCAL e por usuário que cada SO define para o app:
 * - **Windows:** `%LOCALAPPDATA%\<appId>` — local, NÃO itinerante (fica fora do perfil roaming do
 *   domínio, o equivalente desktop do "fora do backup" do Android/iOS);
 * - **macOS:** `~/Library/Application Support/<appId>`;
 * - **Linux:** `$XDG_DATA_HOME/<appId>` (padrão `~/.local/share/<appId>`).
 */
fun defaultAppDataDirectory(
    appId: String,
    osName: String = System.getProperty("os.name").orEmpty(),
    env: (String) -> String? = System::getenv,
    userHome: String = System.getProperty("user.home").orEmpty(),
): Path {
    require(isValidDesktopAppId(appId)) { "appId inválido" }
    val os = osName.lowercase()
    val base: Path = when {
        os.startsWith("windows") ->
            env("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
                ?: Paths.get(userHome, "AppData", "Local")
        os.startsWith("mac") || os.contains("darwin") ->
            Paths.get(userHome, "Library", "Application Support")
        else ->
            env("XDG_DATA_HOME")?.takeIf { it.isNotBlank() && Paths.get(it).isAbsolute }?.let { Paths.get(it) }
                ?: Paths.get(userHome, ".local", "share")
    }
    return base.resolve(appId)
}
