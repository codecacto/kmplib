## Handoff — lib-mobile → dev-mobile / cto

**Demanda:** Executar os itens da kmplib da auditoria de plataforma (`docs/analises/2026-07-19-...md`).
**Status:** concluído (código + testes verdes); dívida iOS pendente de validação em **macOS**.
**Versão nova:** `br.com.codecacto:kmplib:2.77.0 → 2.78.0` (sem breaking).

### O que foi feito
- **Higiene (P1-11):** `git rm --cached` de `teste.txt`, `test-output.log`, `cities_generated.txt`,
  `municipios.json`, `generate_cities.ps1`, `generate_kotlin.ps1`, `docs/firebase/google-services.json`,
  `docs/firebase/GoogleService-Info.plist` (mantidos em disco).
- **Docs (P1-1, P2-16, P2-1):** README `2.2.0→2.78.0` + nota apontando o catálogo; `BREAKING_CHANGES.md`
  com tabela-resumo 2.x; 7 `.md` de planejamento morto → `docs/legacy/`; KDoc do mapa iOS CocoaPods→SPM
  (`googlemaps/ios-maps-sdk`) + `IOS_INTEGRATION.md`; **`docs/adr/`** (ADR-001 quota, ADR-0003 PDF iOS).
- **`OnboardingPager` (P1-5):** novo `ui/components/OnboardingPager` config-driven (a maior duplicação —
  17 apps). Lógica pura testável + `OnboardingPagerTest` (8).
- **`CrashReporter.initFromBuildConfig` (P2-2):** helper aditivo que mata o boilerplate de ~37 apps;
  derivação pura + `CrashReporterBuildConfigTest` (6).
- **firebase/auth (P2-11):** `FakeAuthRepository` + `AuthTest` (7).
- **PDF iOS (P1-9):** renderer multi-página `renderIosPdfPaged`/`IosPageFlow` + primitivas no
  `IosPdfCanvas`; os 7 geradores stub → reais (Document/Table/Finance/Hours/Vaccination/Work/Inspection).
- **Câmera/OCR iOS (P1-10):** `PlateOcrAnalyzer.ios` (Apple Vision) + `CameraView.ios` (AVFoundation +
  Vision + JPEG do frame).

### Decisões tomadas
- **Flags `PlatformCapabilities.pdfGeneration`/`cameraCapture` mantidos `false`** apesar do código estar
  completo: o build Kotlin/Native iOS não roda em Linux, então o código iOS **não foi compilado**. Flipar
  para `true` sem validar violaria o princípio "não vender o que não se prova". O flip é o passo final em
  macOS. (Divergência consciente da instrução literal "virar para true" — justificada.)
- Nenhum gerador de PDF ficou como stub: todos os 7 foram portados espelhando o Android.
- Versão nova como **minor** (2.78.0): tudo aditivo/retrocompatível, zero breaking.

### Arquivos tocados (principais)
- `library/build.gradle.kts` — version 2.78.0.
- `library/src/commonMain/.../ui/components/OnboardingPager.kt` (novo).
- `library/src/commonMain/.../observability/CrashReporterBuildConfig.kt` (novo).
- `library/src/commonTest/.../{ui/components/OnboardingPagerTest, observability/CrashReporterBuildConfigTest,
  firebase/auth/FakeAuthRepository, firebase/auth/AuthTest}.kt` (novos).
- `library/src/iosMain/.../pdf/IosPdfRenderer.ios.kt` (multi-página + primitivas) e os 7
  `*PdfGenerator.ios.kt` (stub → real).
- `library/src/iosMain/.../camera/{CameraView, PlateOcrAnalyzer}.ios.kt` (real).
- `library/src/iosMain/.../map/{IosMapBridge, MapView.ios}.kt` (KDoc SPM).
- README.md, BREAKING_CHANGES.md, CHANGELOG.md (novo), IOS_INTEGRATION.md, CLAUDE.md, docs/legacy/*,
  docs/adr/*, docs/backlog.md; catálogo `.claude/skills/kmplib-catalog/SKILL.md`.

### Contratos/APIs novos (aditivos)
- `OnboardingPager(pages, onFinish, ...)` + `OnboardingPage` + `OnboardingTexts` + helpers puros.
- `CrashReporter.initFromBuildConfig(dsn, appSlug, versionName, versionCode, ...)` +
  `crashReporterConfigFromBuildConfig(...)` + `crashReporterRelease(...)` + `CrashEnvironment`.
- iOS: `renderIosPdfPaged`/`IosPageFlow` (internos); geradores iOS reais.

### Riscos / pendências
- **iOS não compilado (Linux).** PDF/câmera iOS são fiéis ao Android mas **exigem compilar + validar
  visualmente em macOS** antes de virar os flags de `PlatformCapabilities`. Pontos de atenção para o Mac:
  assinaturas K/N de Vision/AVFoundation (`VNImageRequestHandler`, `AVCaptureVideoDataOutputSampleBufferDelegateProtocol`,
  `CIContext.createCGImage`, `previewLayer.setFrame`), orientação do `CVPixelBuffer` no OCR.
- Publicação: `publishToMavenLocal` em Linux sai com commonMain + Android (coerente); Central exige Mac.

### Próximo passo
- **dev-mobile:** migrar ~17 apps para `OnboardingPager` e os apps de crash para `initFromBuildConfig`
  (não feito aqui — a auditoria pediu para NÃO tocar apps prontos). → dev-mobile / cto.
- **macOS/CI:** compilar alvos iOS, validar PDFs + câmera, então virar os flags de `PlatformCapabilities`.
