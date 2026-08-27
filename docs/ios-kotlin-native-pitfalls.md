# Armadilhas do Kotlin/Native para iOS

Este documento lista os erros comuns ao escrever codigo iOS em Kotlin/Native e como evita-los.
O codigo iOS da kmplib e escrito no servidor Linux e **precisa ser validado em macOS** antes de
considerar pronto.

---

## 1. CGRectZero.readValue() - NAO FUNCIONA

**Erro:**
```
Unresolved reference 'readValue'
```

**Codigo errado:**
```kotlin
WKWebView(frame = CGRectZero.readValue(), configuration = config)
```

**Codigo correto:**
```kotlin
import platform.CoreGraphics.CGRectMake

WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = config)
```

**Por que:** No Kotlin/Native 2.x, `CGRectZero` e um `CValue<CGRect>` e nao tem mais o metodo
`readValue()`. Use `CGRectMake()` para criar retangulos.

---

## 2. @ObjCAction - Import correto

**Erro:**
```
Unresolved reference 'ObjCAction'
```

**Codigo errado:**
```kotlin
@platform.darwin.ObjCAction
fun fechar() { }
```

**Codigo correto:**
```kotlin
import kotlinx.cinterop.ObjCAction

@ObjCAction
fun fechar() { }
```

**Por que:** A anotacao `ObjCAction` foi movida para `kotlinx.cinterop` no Kotlin/Native 2.x.

---

## 3. accessibilityLabel - NAO DISPONIVEL em UIButton

**Erro:**
```
Unresolved reference 'accessibilityLabel'
Unresolved reference 'setAccessibilityLabel'
```

**Problema:** A propriedade `accessibilityLabel` de `UIAccessibilityProtocol` NAO e exposta
automaticamente em `UIButton` no Kotlin/Native 2.x. Tanto a propriedade quanto o setter falham.

**Solucao atual:** Remover a linha de accessibilityLabel. O VoiceOver le o titulo do botao.
```kotlin
// NOTA: accessibilityLabel nao exposto no K/N 2.x para UIButton.
// O simbolo X e visualmente claro; VoiceOver le o titulo.
```

**Por que:** UIButton nao expoe diretamente os metodos de UIAccessibilityProtocol no binding K/N.

---

## 4. Conflito de setValue do Compose

**Erro:**
```
None of the following candidates is applicable:
fun MutableState.setValue...
```

**Codigo errado (quando ha `import androidx.compose.runtime.setValue`):**
```kotlin
request.setValue(valor, forHTTPHeaderField = nome)
```

**Solucao 1 - Usar alias:**
```kotlin
import androidx.compose.runtime.setValue as composeSetValue
```

**Solucao 2 - Usar bloco apply:**
```kotlin
NSMutableURLRequest(uRL = url).apply {
    headers.forEach { (nome, valor) ->
        this.setValue(valor, forHTTPHeaderField = nome)
    }
}
```

**Por que:** O import `setValue` do Compose conflita com o metodo `setValue` do `NSMutableURLRequest`.

---

## 5. Conflito de overloads em WKNavigationDelegate

**Erro:**
```
Conflicting overloads:
fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError)
fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError)
```

**Codigo correto:**
```kotlin
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

import kotlin.native.HidesFromObjC

// Implementar apenas UM dos metodos, ou usar @HidesFromObjC
@HidesFromObjC
override fun webView(
    webView: WKWebView,
    didFailNavigation: WKNavigation?,
    withError: NSError,
) { }
```

**Alternativa - Implementar de forma diferente:**
Use nomes de funcao diferentes ou implemente um unico handler generico.

**Por que:** Em Obj-C, esses metodos tem assinaturas diferentes por causa do nome do parametro
(`didFailNavigation:` vs `didFailProvisionalNavigation:`), mas em Kotlin parecem identicos.

---

## 6. WKNavigationType - Acesso a constantes

**Erro:**
```
Unresolved reference 'WKNavigationTypeOther'
```

**Codigo errado:**
```kotlin
decidePolicyForNavigationAction.navigationType == WKNavigationType.WKNavigationTypeOther
```

**Codigo correto:**
```kotlin
import platform.WebKit.WKNavigationTypeOther

decidePolicyForNavigationAction.navigationType == WKNavigationTypeOther
```

**Por que:** Constantes de enum em Obj-C sao importadas como valores top-level, nao como membros
de uma classe.

---

## 7. AVAudioSession - Constantes de permissao

**Erro:**
```
Unresolved reference 'AVAudioSessionRecordPermissionGranted'
```

**Codigo correto:**
```kotlin
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
// ou verificar com:
import platform.AVFAudio.AVAudioSession
AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted
```

**Por que:** Constantes de permissao estao em pacotes especificos (`AVFAudio`).

---

## 8. AVCaptureDevice - lockForConfiguration

**Erro:**
```
Unresolved reference 'lockForConfiguration'
```

**Codigo correto:**
```kotlin
// O metodo retorna um Boolean e lanca excecao
val locked = device.lockForConfiguration(null)
if (locked) {
    // fazer configuracao
    device.unlockForConfiguration()
}
```

**Por que:** Em Kotlin/Native, metodos que lancam excecao em Obj-C precisam do parametro de erro.

---

## 9. AVAudioSession.inputAvailable - NAO EXPOSTO

**Erro:**
```
Unresolved reference 'inputAvailable'
Unresolved reference 'isInputAvailable'
Unresolved reference 'availableInputs'
Unresolved reference 'currentRoute'
```

**Problema:** A propriedade `inputAvailable` do AVAudioSession NAO e exposta no K/N 2.x.
Alternativas como `availableInputs` e `currentRoute` tambem falham.

**Solucao atual:** Remover a verificacao, deixar o start() falhar se nao houver entrada.
```kotlin
override val isAvailable: Boolean
    // NOTA: inputAvailable nao exposto no K/N 2.x. A verificacao real
    // acontece em start() quando tentamos configurar a sessao.
    get() = !released && hasRecordPermission()
```

---

## 10. AVAudioSessionInterruptionType - Constantes top-level

**Erro:**
```
Unresolved reference 'AVAudioSessionInterruptionTypeBegan'
```

**Codigo errado:**
```kotlin
AVAudioSessionInterruptionType.AVAudioSessionInterruptionTypeBegan.value
```

**Codigo correto:**
```kotlin
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeEnded

when (type) {
    AVAudioSessionInterruptionTypeBegan -> { /* ... */ }
    AVAudioSessionInterruptionTypeEnded -> { /* ... */ }
}
```

**Por que:** Constantes de interrupcao sao top-level ULong, nao membros de enum.

---

## 11. NSMutableURLRequest headers - Conflito setValue

**Erro:**
```
None of the following candidates is applicable:
fun MutableState.setValue...
Unresolved reference 'setAllHTTPHeaderFields'
Unresolved reference 'allHTTPHeaderFields'
```

**Problema:** Tanto `setValue(forHTTPHeaderField:)` quanto `allHTTPHeaderFields` conflitam
ou nao estao expostos quando ha imports do Compose.

**Solucao atual:** Usar NSURLRequest simples (sem headers customizados).
```kotlin
// Headers via NSMutableURLRequest nao funcionam bem em K/N 2.x
// devido a conflitos de namespace. Use NSURLRequest se possivel.
webView.loadRequest(platform.Foundation.NSURLRequest(uRL = url))
```

---

## 12. KVO observeValueForKeyPath - NAO FUNCIONA

**Erro:**
```
'observeValueForKeyPath' overrides nothing
```

**Problema:** O metodo KVO `observeValueForKeyPath` de NSObject NAO pode ser sobrescrito
diretamente em K/N 2.x. O metodo nao e exposto como `open`.

**Solucao atual:** Evitar KVO; usar polling ou ler o estado diretamente quando necessario.
```kotlin
// SEM KVO - ler estado diretamente
override fun refresh() {
    machine.onHardwareTorchChanged(device.isTorchActive())
}
```

**Por que:** K/N 2.x mudou como NSObject expoe metodos override. KVO baseado em delegate
nao funciona mais.

---

## Checklist antes de commitar codigo iOS

- [ ] Arquivo compila em macOS (`./gradlew :kmplib:compileKotlinIosSimulatorArm64`)
- [ ] Nao usa `CGRectZero.readValue()` - usar `CGRectMake()`
- [ ] `@ObjCAction` importado de `kotlinx.cinterop`
- [ ] Nao usa accessibilityLabel em UIButton (nao exposto)
- [ ] Sem conflito de `setValue` com Compose
- [ ] Constantes de enum/permissao importadas como top-level
- [ ] Metodos que lancam excecao recebem parametro de erro
- [ ] Nao usa AVAudioSession.inputAvailable (nao exposto)
- [ ] Nao usa KVO observeValueForKeyPath (nao funciona)
- [ ] NSMutableURLRequest headers via alternativa (allHTTPHeaderFields conflita)

---

## Como validar em macOS

No Mac (esta maquina):
```bash
cd /Users/weback/CodeCacto/Lib/kmplib
./gradlew :kmplib:compileKotlinIosSimulatorArm64
```

Se houver erros, corrija e commit. O servidor Linux pode continuar desenvolvendo Android
enquanto o iOS e validado aqui.

---

*Atualizado em 27/ago/2026*
