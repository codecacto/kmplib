import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * Trava de grafo: nenhum módulo da kmplib leva ao app um SDK que a fábrica proíbe (2.291.0).
 *
 * Crashlytics, Google Analytics for Firebase e a permissão AD_ID "a gente nunca vai ter" (fundador,
 * 11/out/2026); AdMob saiu da fábrica na 2.38.0. Até a 2.290.0 o `kmplib-firebase` declarava
 * `api(firebase-analytics)` e o `kmplib-auth` dependia dele — todo app com login recebia Analytics,
 * `play-services-measurement` e AD_ID sem pedir, e só se descobriu olhando o grafo do Meu Advogado.
 * Esta tarefa faz a lib reprovar o próprio build, em vez de um app descobrir depois.
 *
 * Confere o `releaseRuntimeClasspath` (o que vai para o APK) inteiro, transitivos inclusive.
 * Entra no `check` e antes de toda publicação. Rodar à mão: `./gradlew verificarSdksProibidos`.
 */

/** `grupo:artefato` exato ou prefixo terminado em `*`. */
val sdksProibidos: List<String> = listOf(
    // Google Analytics for Firebase (+ -ktx) e o motor de medição que ele puxa (traz AD_ID e
    // ACCESS_ADSERVICES_*). `firebase-measurement-connector` NÃO entra: é a interface vazia que o
    // Remote Config declara, sem SDK nem permissão.
    "com.google.firebase:firebase-analytics*",
    "com.google.android.gms:play-services-measurement*",
    // Crashlytics — crash vai ao GlitchTip (`kmplib-observability`).
    "com.google.firebase:firebase-crashlytics*",
    // AdMob — publicidade da fábrica é house ad do apps-api. `play-services-ads-identifier` NÃO
    // entra por prefixo: a RevenueCat o declara, e a permissão dele é removida no manifesto do
    // `kmplib-monetization`.
    "com.google.android.gms:play-services-ads",
    "com.google.android.gms:play-services-ads-lite",
    "com.google.android.gms:play-services-ads-api",
    "com.google.android.gms:play-services-ads-base",
)

val verificarSdksProibidos by tasks.registering {
    group = "verification"
    description = "Reprova se o grafo de runtime Android leva Analytics/Crashlytics/measurement/AdMob."
    val raiz: Provider<ResolvedComponentResult> = configurations.named("releaseRuntimeClasspath")
        .flatMap { it.incoming.resolutionResult.rootComponent }
    val modulo = project.path
    val padroes = sdksProibidos
    doLast {
        val achados = sortedSetOf<String>()
        val vistos = mutableSetOf<ResolvedComponentResult>()
        val fila = ArrayDeque(listOf(raiz.get()))
        while (fila.isNotEmpty()) {
            val atual = fila.removeFirst()
            if (!vistos.add(atual)) continue
            val id = atual.id
            if (id is ModuleComponentIdentifier) {
                val coord = "${id.group}:${id.module}"
                if (padroes.any { p -> if (p.endsWith("*")) coord.startsWith(p.dropLast(1)) else coord == p }) {
                    achados += "${coord}:${id.version}"
                }
            }
            atual.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { fila += it.selected }
        }
        if (achados.isNotEmpty()) {
            throw GradleException(
                "kmplib $modulo leva ao app SDK proibido na fábrica (Crashlytics, Analytics, AD_ID e " +
                    "AdMob nunca existem em app — 11/out/2026): ${achados.joinToString()}. " +
                    "Tire a dependência (ou exclua o transitivo) no build.gradle.kts do módulo."
            )
        }
    }
}

tasks.matching { it.name == "check" }.configureEach { dependsOn(verificarSdksProibidos) }
tasks.matching { it.name.startsWith("publish") }.configureEach { dependsOn(verificarSdksProibidos) }
