package br.com.codecacto.kmplib.platform.battery

import br.com.codecacto.kmplib.core.locale.FactoryLocales

/**
 * Textos do `BatteryOnboardingContent` (2.308.0). Default: [forLanguage] com o idioma da tela
 * (`appLanguageTag()`, que segue `KmpLibLocales.configure`) — os 4 idiomas da fábrica.
 *
 * Moram em Kotlin, e não em `composeResources`, porque o `kmplib-platform` não carrega recursos (quem
 * os carrega é o `kmplib-ui`, que depende dele — o contrário criaria ciclo). A escolha do idioma é a
 * MESMA do `compose-resources` ([FactoryLocales.match]).
 *
 * Para trocar uma frase: `BatteryOnboardingTexts.forLanguage(tag, appName).copy(title = "…")`.
 *
 * @property steps passos por fabricante, já com o nome do app.
 */
data class BatteryOnboardingTexts(
    val title: String,
    val intro: String,
    val vendorHeading: (BatteryVendor) -> String,
    val steps: (BatteryVendor) -> List<String>,
    val requestExemption: String,
    val exemptionGranted: String,
    val openAppSettings: String,
    val dismiss: String,
) {
    companion object {
        /**
         * Os textos no idioma [languageTag] (casado pelo [FactoryLocales.match]), citando [appName]
         * nos passos. Sem nome, "este app".
         */
        fun forLanguage(languageTag: String?, appName: String?): BatteryOnboardingTexts {
            val nome = appName?.trim()?.takeIf { it.isNotEmpty() }
            return when (FactoryLocales.match(languageTag)) {
                FactoryLocales.EN -> english(nome ?: "this app")
                FactoryLocales.ES -> spanish(nome ?: "esta app")
                FactoryLocales.PT_PT -> portuguesePt(nome ?: "esta app")
                else -> portugueseBr(nome ?: "este app")
            }
        }

        private fun portugueseBr(app: String) = BatteryOnboardingTexts(
            title = "Mantenha o app funcionando em segundo plano",
            intro = "O controle de bateria deste aparelho pode fechar o $app com a tela apagada. " +
                "Siga os passos abaixo uma vez para não perder registros.",
            vendorHeading = { v ->
                when (v) {
                    BatteryVendor.XIAOMI -> "Xiaomi, Redmi e POCO (HyperOS/MIUI)"
                    BatteryVendor.SAMSUNG -> "Samsung (One UI)"
                    BatteryVendor.MOTOROLA -> "Motorola"
                    BatteryVendor.HUAWEI -> "Huawei e Honor"
                    BatteryVendor.OTHER -> "Android"
                }
            },
            steps = { v ->
                when (v) {
                    BatteryVendor.XIAOMI -> listOf(
                        "Toque em \"Permitir em segundo plano\" abaixo e confirme.",
                        "Em Configurações > Apps > Gerenciar apps > $app, ative \"Inicialização automática\".",
                        "Na mesma tela, em \"Economia de bateria\", escolha \"Sem restrições\".",
                        "Na tela de apps recentes, segure o $app e toque no cadeado para fixá-lo.",
                    )
                    BatteryVendor.SAMSUNG -> listOf(
                        "Toque em \"Permitir em segundo plano\" abaixo e confirme.",
                        "Em Configurações > Apps > $app > Bateria, escolha \"Sem restrições\".",
                        "Em Configurações > Cuidados com o dispositivo > Bateria > Limites de uso em segundo plano, " +
                            "tire o $app de \"Apps em suspensão\" e \"Apps em suspensão profunda\".",
                    )
                    BatteryVendor.MOTOROLA -> listOf(
                        "Toque em \"Permitir em segundo plano\" abaixo e confirme.",
                        "Em Configurações > Apps > $app > Uso da bateria do app, escolha \"Sem restrições\".",
                    )
                    BatteryVendor.HUAWEI -> listOf(
                        "Toque em \"Permitir em segundo plano\" abaixo e confirme.",
                        "Em Configurações > Bateria > Inicialização de apps, desative \"Gerenciar automaticamente\" para o $app.",
                        "Na janela que abrir, ative \"Inicialização automática\", \"Inicialização secundária\" e \"Executar em segundo plano\".",
                    )
                    BatteryVendor.OTHER -> listOf(
                        "Toque em \"Permitir em segundo plano\" abaixo e confirme.",
                        "Em Configurações > Apps > $app > Bateria, escolha \"Sem restrições\".",
                    )
                }
            },
            requestExemption = "Permitir em segundo plano",
            exemptionGranted = "Uso em segundo plano permitido",
            openAppSettings = "Abrir configurações do app",
            dismiss = "Concluir",
        )

        private fun portuguesePt(app: String) = BatteryOnboardingTexts(
            title = "Mantenha a app a funcionar em segundo plano",
            intro = "O controlo de bateria deste aparelho pode fechar a $app com o ecrã desligado. " +
                "Siga os passos abaixo uma vez para não perder registos.",
            vendorHeading = { v ->
                when (v) {
                    BatteryVendor.XIAOMI -> "Xiaomi, Redmi e POCO (HyperOS/MIUI)"
                    BatteryVendor.SAMSUNG -> "Samsung (One UI)"
                    BatteryVendor.MOTOROLA -> "Motorola"
                    BatteryVendor.HUAWEI -> "Huawei e Honor"
                    BatteryVendor.OTHER -> "Android"
                }
            },
            steps = { v ->
                when (v) {
                    BatteryVendor.XIAOMI -> listOf(
                        "Toque em \"Permitir em segundo plano\" abaixo e confirme.",
                        "Em Definições > Apps > Gerir apps > $app, ative \"Arranque automático\".",
                        "No mesmo ecrã, em \"Poupança de bateria\", escolha \"Sem restrições\".",
                        "No ecrã de apps recentes, mantenha premida a $app e toque no cadeado para a fixar.",
                    )
                    BatteryVendor.SAMSUNG -> listOf(
                        "Toque em \"Permitir em segundo plano\" abaixo e confirme.",
                        "Em Definições > Aplicações > $app > Bateria, escolha \"Sem restrições\".",
                        "Em Definições > Manutenção do dispositivo > Bateria > Limites de utilização em segundo plano, " +
                            "retire a $app de \"Aplicações em suspensão\" e \"Aplicações em suspensão profunda\".",
                    )
                    BatteryVendor.MOTOROLA -> listOf(
                        "Toque em \"Permitir em segundo plano\" abaixo e confirme.",
                        "Em Definições > Apps > $app > Utilização da bateria da app, escolha \"Sem restrições\".",
                    )
                    BatteryVendor.HUAWEI -> listOf(
                        "Toque em \"Permitir em segundo plano\" abaixo e confirme.",
                        "Em Definições > Bateria > Arranque de apps, desative \"Gerir automaticamente\" para a $app.",
                        "Na janela que abrir, ative \"Arranque automático\", \"Arranque secundário\" e \"Executar em segundo plano\".",
                    )
                    BatteryVendor.OTHER -> listOf(
                        "Toque em \"Permitir em segundo plano\" abaixo e confirme.",
                        "Em Definições > Apps > $app > Bateria, escolha \"Sem restrições\".",
                    )
                }
            },
            requestExemption = "Permitir em segundo plano",
            exemptionGranted = "Utilização em segundo plano permitida",
            openAppSettings = "Abrir definições da app",
            dismiss = "Concluir",
        )

        private fun english(app: String) = BatteryOnboardingTexts(
            title = "Keep the app running in the background",
            intro = "This device's battery manager may close $app while the screen is off. " +
                "Follow the steps below once so no records are lost.",
            vendorHeading = { v ->
                when (v) {
                    BatteryVendor.XIAOMI -> "Xiaomi, Redmi and POCO (HyperOS/MIUI)"
                    BatteryVendor.SAMSUNG -> "Samsung (One UI)"
                    BatteryVendor.MOTOROLA -> "Motorola"
                    BatteryVendor.HUAWEI -> "Huawei and Honor"
                    BatteryVendor.OTHER -> "Android"
                }
            },
            steps = { v ->
                when (v) {
                    BatteryVendor.XIAOMI -> listOf(
                        "Tap \"Allow in background\" below and confirm.",
                        "In Settings > Apps > Manage apps > $app, turn on \"Autostart\".",
                        "On the same screen, under \"Battery saver\", choose \"No restrictions\".",
                        "In the recent apps screen, long-press $app and tap the lock to pin it.",
                    )
                    BatteryVendor.SAMSUNG -> listOf(
                        "Tap \"Allow in background\" below and confirm.",
                        "In Settings > Apps > $app > Battery, choose \"Unrestricted\".",
                        "In Settings > Device care > Battery > Background usage limits, " +
                            "remove $app from \"Sleeping apps\" and \"Deep sleeping apps\".",
                    )
                    BatteryVendor.MOTOROLA -> listOf(
                        "Tap \"Allow in background\" below and confirm.",
                        "In Settings > Apps > $app > App battery usage, choose \"Unrestricted\".",
                    )
                    BatteryVendor.HUAWEI -> listOf(
                        "Tap \"Allow in background\" below and confirm.",
                        "In Settings > Battery > App launch, turn off \"Manage automatically\" for $app.",
                        "In the window that opens, turn on \"Auto-launch\", \"Secondary launch\" and \"Run in background\".",
                    )
                    BatteryVendor.OTHER -> listOf(
                        "Tap \"Allow in background\" below and confirm.",
                        "In Settings > Apps > $app > Battery, choose \"Unrestricted\".",
                    )
                }
            },
            requestExemption = "Allow in background",
            exemptionGranted = "Background use allowed",
            openAppSettings = "Open app settings",
            dismiss = "Done",
        )

        private fun spanish(app: String) = BatteryOnboardingTexts(
            title = "Mantén la app funcionando en segundo plano",
            intro = "El control de batería de este dispositivo puede cerrar $app con la pantalla apagada. " +
                "Sigue estos pasos una vez para no perder registros.",
            vendorHeading = { v ->
                when (v) {
                    BatteryVendor.XIAOMI -> "Xiaomi, Redmi y POCO (HyperOS/MIUI)"
                    BatteryVendor.SAMSUNG -> "Samsung (One UI)"
                    BatteryVendor.MOTOROLA -> "Motorola"
                    BatteryVendor.HUAWEI -> "Huawei y Honor"
                    BatteryVendor.OTHER -> "Android"
                }
            },
            steps = { v ->
                when (v) {
                    BatteryVendor.XIAOMI -> listOf(
                        "Toca \"Permitir en segundo plano\" abajo y confirma.",
                        "En Ajustes > Aplicaciones > Administrar aplicaciones > $app, activa \"Inicio automático\".",
                        "En la misma pantalla, en \"Ahorro de batería\", elige \"Sin restricciones\".",
                        "En la pantalla de apps recientes, mantén pulsada $app y toca el candado para fijarla.",
                    )
                    BatteryVendor.SAMSUNG -> listOf(
                        "Toca \"Permitir en segundo plano\" abajo y confirma.",
                        "En Ajustes > Aplicaciones > $app > Batería, elige \"Sin restricciones\".",
                        "En Ajustes > Mantenimiento del dispositivo > Batería > Límites de uso en segundo plano, " +
                            "quita $app de \"Aplicaciones en suspensión\" y \"Aplicaciones en suspensión profunda\".",
                    )
                    BatteryVendor.MOTOROLA -> listOf(
                        "Toca \"Permitir en segundo plano\" abajo y confirma.",
                        "En Ajustes > Aplicaciones > $app > Uso de batería de la app, elige \"Sin restricciones\".",
                    )
                    BatteryVendor.HUAWEI -> listOf(
                        "Toca \"Permitir en segundo plano\" abajo y confirma.",
                        "En Ajustes > Batería > Inicio de aplicaciones, desactiva \"Administrar automáticamente\" para $app.",
                        "En la ventana que se abre, activa \"Inicio automático\", \"Inicio secundario\" y \"Ejecutar en segundo plano\".",
                    )
                    BatteryVendor.OTHER -> listOf(
                        "Toca \"Permitir en segundo plano\" abajo y confirma.",
                        "En Ajustes > Aplicaciones > $app > Batería, elige \"Sin restricciones\".",
                    )
                }
            },
            requestExemption = "Permitir en segundo plano",
            exemptionGranted = "Uso en segundo plano permitido",
            openAppSettings = "Abrir ajustes de la app",
            dismiss = "Listo",
        )
    }
}
