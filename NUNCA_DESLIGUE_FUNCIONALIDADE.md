# NUNCA desligue uma funcionalidade para fazer compilar

**Regra do fundador, 27/ago/2026. Vale para qualquer agente e qualquer máquina — inclusive (e
especialmente) quem está no Mac corrigindo build de iOS.**

Esta biblioteca é a fundação de **dezenas de apps**. Quem mexe nela raramente tem a lista inteira do
que ela afeta na frente — e é exatamente por isso que a regra existe: a decisão de remover algo
daqui **não é local**, mesmo quando o arquivo parece isolado.

---

## A regra

Diante de um erro de compilação, de uma API que não está exposta ou de um conflito do compilador, a
entrega é a **solução correta do problema**. Nunca:

- comentar, apagar ou "simplificar" um comportamento que a lib entrega;
- deixar um parâmetro público virar **no-op** (o pior caso: compila, o app passa o valor e nada
  acontece);
- trocar um caminho oficial por um contorno que faz menos;
- resolver removendo o método que dava erro.

Se a ferramenta oficial não faz o que o produto precisa, a resposta certa é *como se resolve isto
direito* — outra API do mesmo framework, a anotação que existe para o caso, um arquivo separado, a
lib adequada. E então executar.

**"Já estava assim" e "é mais fácil" não são critério.** Um atalho aqui vira dívida multiplicada em
todos os apps.

## Se você não consegue resolver agora

Isso acontece, e é legítimo — falta de Mac, API que o binding não expõe, limitação real do
Kotlin/Native. O que **não** é legítimo é entregar o recorte em silêncio. Nesse caso:

1. **PARE e reporte ao fundador**, dizendo o que trava e qual é o plano da solução correta.
2. Não faça o commit que remove a funcionalidade sem esse aval.
3. Se, com o aval, algo ficar temporariamente desligado: registre **no CHANGELOG** e num documento
   de pendência, com o sintoma para quem usa e a hipótese de correção. O que não está escrito não
   volta.

## Por que a régua é essa

Um recorte silencioso não parece um bug. Ele compila, passa no CI, o app sobe e o defeito aparece
semanas depois, na mão do usuário, longe de quem o causou:

- um parâmetro público que virou no-op não falha — **carrega o documento sem o header e devolve 401**;
- um handler de erro removido não avisa — a tela fica **carregando para sempre** quando cai a rede;
- um observador removido não trava — o botão **diz que a lanterna está ligada** depois que o sistema
  a apagou;
- um rótulo de acessibilidade removido não quebra nada — só deixa o leitor de tela dizendo "✕".

Nenhum desses aparece para quem fez a mudança. Todos aparecem para quem usa o app.

## O que já aconteceu (o caso que originou esta regra)

Em 27/ago/2026, a correção do build iOS (commit `c2085a5`, validada em macOS) fez o build passar —
e junto **desligou quatro coisas**, sem consulta:

| Removido | O que o usuário perde |
|---|---|
| Headers do `WKWebView` | `HtmlDocumentSource.Url(headers = …)` é **API pública** e virou no-op no iOS: documento protegido carrega sem `Authorization` e devolve 401 |
| `didFailProvisionalNavigation` | Falha de rede/DNS/TLS acontece na navegação *provisional*: sem esse método, "sem internet" **nunca** vira erro e a tela carrega para sempre |
| KVO de `torchActive` | O estado da lanterna para de acompanhar o hardware — o iOS apaga o LED e o app continua dizendo "ligada" |
| `accessibilityLabel` do botão de fechar vídeo | O VoiceOver lê "✕" |

As quatro têm hipótese de correção escrita em **`docs/ios-kotlin-native-pitfalls.md`**, na seção
"O que a correção de 27/ago/2026 DESLIGOU e precisa voltar" — inclusive duas em que o próprio
documento se contradiz (os headers esbarram no import do Compose, não no `NSMutableURLRequest`; e o
conflito de overload tem anotação oficial, `@ObjCSignatureOverride`, que não é a que estava sugerida).

**Quem for corrigir: comece por lá, e devolva as quatro.**

---

Ver também: `AGENTS.md` (como trabalhar nesta lib), `CHANGELOG.md` (o que mudou e por quê) e
`BREAKING_CHANGES.md` (o que exige ação de quem consome).
