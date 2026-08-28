# Migração dos apps para os módulos da kmplib

**Estado em 28/ago/2026.** A kmplib 2.163.0 é publicada em 21 artefatos. Este documento diz quem
já migrou, quem falta, e o que fazer em cada caso.

## O que "migrar" quer dizer

Quatro mudanças no app, todas no mesmo commit:

1. **Declarar os módulos** que as telas de fato abrem, em vez de `api(libs.kmplib)`.
2. **Restringir o `export()`** do `binaries.framework` aos módulos cujos tipos o **Swift nomeia** —
   quase sempre nenhum, ou um (`ApplePushBridge` → `kmplib-push`, `GoogleSignInBridge` →
   `kmplib-auth`). É esta a mudança que resolve o OOM do link Release, não a lista de módulos:
   `export()` transforma cada símbolo público em **raiz do dead code elimination**, e nada abaixo
   de uma raiz pode ser eliminado.
3. **`kotlin.native.jvmArgs=-Xmx6g`** no `gradle.properties` — `org.gradle.jvmargs` não alcança o
   Kotlin/Native, que compila em processo próprio.
4. **Init por módulo**: `initKmpLibCore/Platform/Auth/Sync/Media` no lugar de `KmpLib.init`, e
   `kmpLibPlatformOnResume/OnPause` + `kmpLibAuthOnResume/OnPause` no lugar de
   `setActivity`/`clearActivity`.

## Estado

| | |
|---|---:|
| Apps migrados e com compilação Android verde | **67** |
| Apps que ainda não compilam (motivo próprio, ver abaixo) | **6** |
| Apps que ainda usam o umbrella | 0 |

O umbrella `br.com.codecacto:kmplib` **continua existindo e continua funcionando**. Ele não foi
removido e não há prazo para removê-lo: é o que permitiu migrar os apps um a um sem que nenhum
parasse de compilar no meio do caminho.

## Os 6 que faltam — e por que não é da migração

Estes apps **nunca compilaram neste servidor**. Antes da guarda de host, o Gradle tentava resolver
os artefatos iOS de todas as dependências e morria em `KMP Dependencies Resolution Failure`
**antes** de chamar o compilador Kotlin. A migração pôs a guarda, o Android passou a compilar pela
primeira vez — e o que já estava quebrado apareceu. Nenhum destes erros é da kmplib:

| App | O que o compilador diz |
|---|---|
| `ABC Divertido` | AbcDivertidoApplication.kt:5:46 Unresolved reference 'appModules'. AbcDivertidoApplication |
| `Divide Ai` | SplashScreen.kt:22:60 Unresolved reference 'Login'. SplashScreen.kt:24:27 Unresolved refer |
| `Meu Controle` | MoreScreen.kt:11:60 Unresolved reference 'ChevronRight'. MoreScreen.kt:75:44 Unresolved re |
| `Minha Ficha` | BibliotecaContract.kt:26:95 Unresolved reference 'label'. ExercisePickerContract.kt:22:95 |
| `Sinaleiro` | QuizSetupViewModel.kt:18:64 Argument type mismatch: actual type is 'String?', but 'String' wa |
| `Vou Ganhar` | ReminderEntry.kt:22:56 Argument type mismatch: actual type is 'Long', but 'Int' was expected. |

São erros de código do próprio app (uma rota que não existe, um ícone com nome errado, um tipo
trocado, um símbolo do módulo Koin). Cada um é meia hora de trabalho, mas é trabalho de produto,
não de plataforma — por isso ficaram de fora deste lote.

## O que se aprende ao migrar o próximo

**A lista de módulos não sai só dos `import`.** 34 arquivos mudaram de módulo **sem mudar de
pacote** (`PaywallScreen` continua em `br.com.codecacto.kmplib.ui.screens.paywall` e mora no
`kmplib-monetization`), de propósito, para não quebrar os imports dos apps. E um `typealias X =
br.com.codecacto.kmplib.push.X` usa um módulo sem nenhuma linha de import — foi assim que o
Influencer usava o `kmplib-push` sem aparecer em nenhum levantamento.

**Dois ajustes de bump aparecem em quase todo app** que estava na 2.139.0:
`kotlinx.datetime.Clock` virou `kotlin.time.Clock` (datetime 0.7 moveu para a stdlib), e o `when`
sobre `LoginEffect` precisa do ramo `ToForgotPassword` (2.15x). O segundo é sempre um no-op para
quem usa o diálogo de recuperação da própria lib.

**Projeto em produção com cliente vai na `dev`.** Meu Barbeiro, LocaSys e Meu Advogado foram
commitados lá; o merge para `main` e o deploy são decisão do fundador.
