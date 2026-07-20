# ADR-001 — Gate de cota freemium offline-tolerante

- **Status:** Aceito · implementado em `monetization/quota` (`OfflineQuotaGate`, `DailyQuotaStore`,
  `QuotaRules`, `EntitlementPremiumSource`).
- **Data:** jun/2026 (documentado retroativamente jul/2026).
- **Referenciado por:** `OfflineQuotaGate.kt` (KDoc "Implementa a ADR-001 ponto a ponto"),
  `DailyQuotaStore.kt` (§1/§3/§4).

## Contexto

Quatro apps do arquétipo A (100% offline com toques online) reimplementaram, cada um à mão, o mesmo
gate de cota do modelo **freemium com limite → paywall**: `Esquecido/QuotaGate`,
`ChamadaFacil/QuotaGate`, `MundoBandeiras/QuotaGate` e `NumerosDaSorte/CotaDiariaLocal`. As cópias
divergiam em pontos de **segurança** — e errar aqui significa **dar premium de graça** ou, pior,
**bloquear um assinante pagante**.

Restrições do ecossistema que a decisão precisa respeitar:

- **Enforcement de quota é server-side** (constituição): o cliente só **lê/exibe** "X de Y" e abre o
  paywall; a fonte de verdade do saldo é a admin-api (`backlib-quota`). O 402 na ação de domínio é o
  enforcement real.
- **Premium é decidido pelo RevenueCat**, nunca por estado local (memória `premium-gate-revenuecat-direct`).
- Apps 100% offline **não têm** backend de quota — precisam degradar com segurança sem rede.
- No **cold start**, ler o snapshot do `StateFlow` do RevenueCat antes do primeiro `refresh()` trata um
  assinante Pro como Free e **consome a cota dele** (bug real: MundoBandeiras `AppModule.kt:90`).

## Decisão

Promover um **único** gate (`OfflineQuotaGate`) que codifica cinco regras de segurança inegociáveis:

1. **Premium curto-circuita** — assinante nunca consome cota. O sinal vem de `PremiumSource`
   (fachada do RevenueCat), **nunca** de estado local. Para evitar a corrida do cold start, usar
   `EntitlementPremiumSource`, que **aguarda o primeiro `refresh()`** (uma vez por processo, sob `Mutex`)
   antes de ler o flow; offline lê o cache de disco do RevenueCat (Pro segue Pro; Free jamais vira Pro).
2. **Servidor é a verdade quando existe** (`repository != null`): cada consumo faz `assertUsage`; **402
   → `Blocked`** (Paywall) e o saldo do servidor **sobrepõe** o espelho local.
3. **Fail-open LIMITADO:** falha de rede/verificação (`AssertResult.Failed`) libera **apenas enquanto a
   contagem local < `freeLimit`**. Ao atingir o teto Free offline, **bloqueia**. Falha de rede nunca
   autopromove nem libera consumo infinito.
4. **Reconcilia ao reconectar** (`reconcile()`): reenvia a contagem local (inclui o consumo offline,
   `amount=0`) e o servidor decide o saldo final.
5. **App 100% offline** (`repository = null`): o espelho local (`DailyQuotaStore`) é o gate — mesma regra
   de teto, sem `assert`/reconcile. **Burlável por construção** (aceito conscientemente — a receita real
   está no RevenueCat, não na contagem local).

Dois formatos de limite: **consumível diário** (`tryConsume()` + `DailyQuotaStore` com rollover no dia
local) e **estrutural/lifetime** (`assertStructural(currentCount)` — turmas ativas/checklists; a
contagem é do domínio, arquivar libera vaga). Regras puras isoladas em `QuotaRules` (sem I/O, testáveis).

## Alternativas descartadas

- **Gate 100% local (contar no device, sem servidor) para todos os apps:** rejeitado — apps com backend
  precisam do enforcement server-side (402); contar só no device é trivialmente burlável e diverge do saldo real.
- **Ler premium do snapshot do `StateFlow` direto:** rejeitado — é exatamente o bug da corrida do cold
  start. Daí `EntitlementPremiumSource` esperar o priming.
- **Fail-closed no offline (bloquear sem rede):** rejeitado para consumíveis — puniria o usuário legítimo
  offline dentro da cota. Adotado o fail-open **limitado ao teto Free** como meio-termo seguro.

## Consequências

- **Positivas:** um só ponto de segurança auditável (testes `OfflineQuotaGateTest`, 12 casos cobrindo a
  corrida do premium, Pro não consome cota, fail-open limitado, 402 sobrepõe, reconcile, gate 100% offline,
  estrutural). Quatro cópias divergentes viram uma. UX "X de Y" via `usageSnapshot`/`UsageMeter`.
- **Negativas / aceitas:** o gate 100% offline é burlável (documentado); o `assertUsage` degrada para
  `Failed(501)` enquanto não houver `POST /me/assert` Firebase-authed no backend — o enforcement real
  segue sendo o 402 na ação de domínio.
- **Migração:** Esquecido, ChamadaFacil, MundoBandeiras e NúmerosDaSorte devem deletar a cópia local e
  importar da lib.
