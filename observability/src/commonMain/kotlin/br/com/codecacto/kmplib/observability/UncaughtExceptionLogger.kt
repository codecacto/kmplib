package br.com.codecacto.kmplib.observability

/**
 * Exceção de Kotlin **não tratada** escrita no **log do aparelho** antes de o app cair (2.290.0,
 * GAP-IOS-UNCAUGHT-LOG-01).
 *
 * ## Por que existe
 *
 * No **iOS**, uma exceção de Kotlin que escapa termina o processo por `terminateWithUnhandledException`,
 * e o relatório do sistema (`.ips`) traz **só a pilha do C++** — nem o tipo, nem a mensagem. A saída
 * padrão do Kotlin/Native vai para o `stderr`, que no aparelho e no simulador fora do Xcode não chega
 * ao log unificado. Sem DSN (build de QA, debug local) o GlitchTip também não recebe nada, e a rodada
 * vermelha do runner de QA só tinha "o app fechou" (Vitalis, 10/out/2026).
 *
 * O gancho oficial do Kotlin/Native para isso é `kotlin.native.setUnhandledExceptionHook`. Esta
 * função instala um gancho que escreve no `NSLog` e **encadeia** o que já existia — e o
 * `sentry-kotlin-multiplatform`, ao inicializar, encadeia o dele por cima deste: a exceção vai ao log
 * do aparelho **e** ao [CrashReporter], na ordem que for, e o processo termina como antes.
 *
 * **Já vem ligado:** [CrashReporter.init] (o `createCrashReporter()`) instala o gancho sozinho —
 * inclusive com DSN em branco. Chamar esta função só é preciso para **ligar a pilha** no build de
 * depuração, ou para cobrir o que roda antes do `init`.
 *
 * ## O que vai ao log
 *
 * - **Sempre:** `Uncaught Kotlin exception:` + o TIPO de cada exceção da cadeia de causas.
 * - **Binário de depuração:** também a **mensagem** de cada uma. Em binário de release a mensagem fica
 *   de fora — ela pode carregar dado de usuário, e o log do aparelho é legível por quem tem o aparelho
 *   (o GlitchTip recebe a exceção inteira de qualquer forma).
 * - **[includeStackTrace] = `true`, só em binário de depuração:** a pilha, linha a linha.
 *   ⚠️ **Exige `binaryOption("sourceInfoType", "libbacktrace")` no framework de DEBUG** (a
 *   `casca-mobile` já traz). Sem ele, montar a pilha passa pelo CoreSymbolication, que no simulador
 *   prendeu o app por **minutos** antes de cair. Por isso o default é `false`.
 *
 * O texto vai ao `NSLog` como o PRÓPRIO formato, com `%` escapado: passar uma `String` do Kotlin como
 * argumento variádico (`NSLog("%@", texto)`) derruba o app com `EXC_BAD_ACCESS` no K/N.
 *
 * **Android:** não faz nada e devolve `false`. Lá o handler padrão do sistema já escreve
 * `FATAL EXCEPTION` com tipo, mensagem e pilha completos no logcat, e o Sentry Android encadeia o dele.
 *
 * Uso (casca, iOS, antes do `CrashReporter.init`):
 * ```kotlin
 * installUncaughtExceptionLogger(includeStackTrace = Platform.isDebugBinary)
 * ```
 *
 * @param includeStackTrace escreve a pilha (só tem efeito em binário de depuração) — ver acima.
 * @return `true` se o gancho está instalado nesta plataforma.
 */
expect fun installUncaughtExceptionLogger(includeStackTrace: Boolean = false): Boolean

/**
 * Instala o gancho sem mexer na configuração já escolhida pelo app — é o que o [CrashReporter.init]
 * chama, para não desligar a pilha que o app ligou antes.
 */
internal expect fun ensureUncaughtExceptionLoggerInstalled()

/** Prefixo da primeira linha — o mesmo texto que o Kotlin/Native escreve no `stderr`, para quem já procura por ele. */
internal const val UNCAUGHT_PREFIX: String = "Uncaught Kotlin exception: "

/** Separador entre uma exceção e a sua causa na primeira linha. */
internal const val CAUSE_SEPARATOR: String = " <- causa: "

/**
 * As linhas que vão ao log, puras para teste: a cadeia de causas (tipo, e a mensagem quando
 * [includeMessage]) e, se houver, a [stackTrace] partida em linhas.
 *
 * Teto de [maxCauses] causas (cadeia circular não prende), [maxStackLines] linhas de pilha e
 * [maxLineLength] caracteres por linha — o log unificado corta mensagem longa demais, e uma linha só
 * com 6.000 caracteres chegava truncada no meio.
 */
internal fun uncaughtExceptionLogLines(
    throwable: Throwable,
    includeMessage: Boolean,
    stackTrace: String? = null,
    maxCauses: Int = 5,
    maxStackLines: Int = 80,
    maxLineLength: Int = 900,
): List<String> {
    val cadeia = generateSequence(throwable) { atual -> atual.cause?.takeIf { it !== atual } }
        .take(maxCauses)
        .joinToString(CAUSE_SEPARATOR) { describeThrowable(it, includeMessage) }
    val linhas = mutableListOf((UNCAUGHT_PREFIX + cadeia).take(maxLineLength))
    if (stackTrace != null) {
        stackTrace.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
            .take(maxStackLines)
            .forEach { linhas += it.take(maxLineLength) }
    }
    return linhas
}

/** `tipo` ou `tipo: mensagem` — nunca a mensagem quando [includeMessage] é `false`. */
internal fun describeThrowable(throwable: Throwable, includeMessage: Boolean): String {
    val tipo = throwable::class.qualifiedName ?: throwable::class.simpleName ?: "Throwable"
    val mensagem = throwable.message
    return if (includeMessage && !mensagem.isNullOrEmpty()) "$tipo: $mensagem" else tipo
}

/** O texto como FORMATO do `NSLog`: `%` vira `%%`, e nenhum especificador sobra para ler argumento que não existe. */
internal fun escapeLogFormat(text: String): String = text.replace("%", "%%")
