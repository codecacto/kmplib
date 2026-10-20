package br.com.codecacto.kmplib.platform.battery

/**
 * # Onboarding de bateria (2.308.0)
 *
 * App que precisa rodar em segundo plano — ronda com localização, alarme de início de turno — é morto
 * pelo gerenciador de bateria de vários fabricantes Android **mesmo com foreground service**: Xiaomi
 * (MIUI/HyperOS), Samsung (One UI, "apps em suspensão"), Huawei/Honor (EMUI/MagicOS) e, em menor grau,
 * Motorola. O sistema não avisa ninguém; o vigia só descobre que a ronda parou de registrar depois.
 *
 * A saída oficial do Android é a isenção de otimização de bateria
 * (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — e, nos fabricantes acima, um ajuste a mais no menu
 * próprio deles, que não tem API pública e por isso é **explicado em texto**, nunca aberto por intent
 * privada (os componentes internos de "autostart" mudam a cada versão do MIUI/EMUI e quebram calados).
 *
 * - [needsBatteryOnboarding]: `true` só em fabricante conhecido **e** app ainda não isento.
 * - `BatteryOnboardingContent(onDismiss)`: as instruções do fabricante + o botão da isenção.
 * - iOS: não existe restrição de fabricante — [needsBatteryOnboarding] é sempre `false` e o conteúdo
 *   não desenha nada.
 */
enum class BatteryVendor {
    /** Xiaomi, Redmi e POCO — MIUI / HyperOS. */
    XIAOMI,

    /** Samsung — One UI. */
    SAMSUNG,

    /** Motorola — My UX. */
    MOTOROLA,

    /** Huawei e Honor — EMUI / MagicOS. */
    HUAWEI,

    /** Qualquer outro Android (instruções genéricas; não dispara o onboarding sozinho). */
    OTHER,
    ;

    /** Fabricante com gerenciador de bateria agressivo, que justifica mostrar o onboarding. */
    val isAggressive: Boolean get() = this != OTHER
}

/**
 * O fabricante a partir de `Build.MANUFACTURER` e `Build.BRAND`. Pura, para teste.
 *
 * Confere os dois porque a submarca nem sempre aparece no fabricante: Redmi e POCO saem como
 * `Xiaomi` no `MANUFACTURER` na maioria dos aparelhos, mas há firmware com `BRAND = Redmi` e
 * `MANUFACTURER` diferente; Honor virou empresa própria e reporta `HONOR`.
 */
fun batteryVendorOf(manufacturer: String?, brand: String?): BatteryVendor {
    val nomes = listOfNotNull(manufacturer, brand).map { it.trim().lowercase() }
    return when {
        nomes.any { it == "xiaomi" || it == "redmi" || it == "poco" } -> BatteryVendor.XIAOMI
        nomes.any { it == "samsung" } -> BatteryVendor.SAMSUNG
        nomes.any { it == "motorola" || it == "moto" } -> BatteryVendor.MOTOROLA
        nomes.any { it == "huawei" || it == "honor" } -> BatteryVendor.HUAWEI
        else -> BatteryVendor.OTHER
    }
}

/**
 * A regra do [needsBatteryOnboarding], pura: fabricante agressivo **e** app ainda não isento.
 * [isIgnoringOptimizations] `null` = a plataforma não informa (iOS) → nunca precisa.
 */
fun batteryOnboardingNeeded(vendor: BatteryVendor?, isIgnoringOptimizations: Boolean?): Boolean =
    vendor != null && vendor.isAggressive && isIgnoringOptimizations == false

/**
 * O aparelho precisa das instruções de bateria? `true` só no Android, em Xiaomi/Redmi/POCO, Samsung,
 * Motorola ou Huawei/Honor, **e** com o app ainda sujeito à otimização de bateria
 * (`PowerManager.isIgnoringBatteryOptimizations(packageName) == false`). iOS: sempre `false`.
 *
 * Barato (sem IO): pode ser chamado no `ON_RESUME` para esconder o aviso assim que a pessoa liberar.
 * Android exige `initKmpLibPlatform(context)` antes; sem ele devolve `false`.
 */
expect fun needsBatteryOnboarding(): Boolean

/** O fabricante deste aparelho, ou `null` onde o conceito não existe (iOS). */
expect fun deviceBatteryVendor(): BatteryVendor?

/**
 * O app já está isento da otimização de bateria? `null` onde não se aplica (iOS) ou antes do
 * `initKmpLibPlatform`.
 */
expect fun isIgnoringBatteryOptimizations(): Boolean?

/**
 * Abre o pedido de isenção de otimização de bateria (Android).
 *
 * - Com `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` declarado **no manifesto do APP**: o diálogo do
 *   sistema "Permitir que o app sempre rode em segundo plano?" (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
 * - Sem a permissão: a lista de otimização de bateria (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`),
 *   onde a pessoa escolhe o app à mão. Último recurso: a tela de detalhes do app.
 *
 * ⚠️ A lib **não** declara `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: é permissão de uso restrito na Play
 * (o app precisa se enquadrar — ronda, alarme, rastreamento contínuo) e impô-la a todo app do umbrella
 * transferiria um risco de revisão de loja a quem não pediu. Quem se enquadra declara no próprio
 * `AndroidManifest.xml`.
 *
 * @return `true` se alguma tela abriu. iOS: `false` (não há o que abrir).
 */
expect fun openBatteryOptimizationRequest(): Boolean

/**
 * Abre a tela de detalhes do app no sistema — onde Samsung, Xiaomi e Motorola põem o "Bateria → Sem
 * restrições". iOS: a tela do app em Ajustes.
 */
expect fun openAppBatterySettings(): Boolean
