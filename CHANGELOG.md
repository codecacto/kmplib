# Changelog — kmplib

Histórico de versões. Fonte de verdade viva da superfície de APIs = skill `kmplib-catalog`;
breaking curados = `BREAKING_CHANGES.md`; decisões = `docs/adr/`.

> Nota: este arquivo foi (re)criado na 2.78.0 (auditoria — não havia `CHANGELOG.md` de raiz; a
> história pré-2.78 está no catálogo por versão e no `docs/legacy/CHANGELOG_UI_COMPONENTS.md`).

## 2.78.1 — fix: FileProvider paths cobrem photos/ e videos/ (jul/2026)

Sem breaking. Correção de bug introduzido na 2.78.0.

### Fix
- `kmplib_file_paths.xml`: o FileProvider da lib expunha **apenas** `shared_files/`, mas os
  componentes `ImagePicker` (câmera → `cacheDir/photos`) e `VideoPicker` (`cacheDir/videos`)
  gravam em subpastas não declaradas. Em qualquer app consumidor que use a câmera/vídeo,
  `FileProvider.getUriForFile` lançava `IllegalArgumentException` e a ação falhava silenciosamente.
  Agora o `kmplib_file_paths.xml` cobre os três caminhos usados pela própria lib:
  `photos/`, `videos/` e `shared_files/`.

## 2.78.0 — Onda de manutenção da auditoria (jul/2026)

Sem breaking. Higiene, docs e três evoluções aditivas + quitação de dívida iOS (pendente de
validação em macOS).

### Higiene (P1-11)
- `git rm --cached` dos arquivos-lixo que estavam rastreados apesar do `.gitignore`: `teste.txt`,
  `test-output.log`, `cities_generated.txt`, `municipios.json`, `generate_cities.ps1`,
  `generate_kotlin.ps1` e as **configs Firebase órfãs** `docs/firebase/google-services.json` +
  `docs/firebase/GoogleService-Info.plist` (mantidos em disco, fora do versionamento).

### Docs (P1-1, P2-16, P2-1)
- `README.md`: coordenada `2.2.0` → `2.78.0`; nota apontando o catálogo como fonte viva + resumo
  das mudanças estruturais (sem Firestore, crashes→GlitchTip, sem AdMob).
- `BREAKING_CHANGES.md`: cabeçalho + tabela-resumo dos breaking 2.x (antes parava em 2.0.0).
- Planejamento morto da raiz arquivado em `docs/legacy/` (`ANALISE_CENTRALIZACAO.md`,
  `KMPLIB_REUSE_ANALYSIS.md`, `AJUSTES_REALIZADOS.md`, `TEST_SCENARIOS.md`,
  `UI_COMPONENTS_EXAMPLES.md`, `CHANGELOG_UI_COMPONENTS.md`, `FEEDBACK_USER_SYNC.md`).
- KDoc do mapa iOS: CocoaPods → **SPM `googlemaps/ios-maps-sdk`** (`IosMapBridge.kt`, `MapView.ios.kt`);
  `IOS_INTEGRATION.md` passou a listar o pacote SPM do Google Maps.
- **ADRs criados** (`docs/adr/`): ADR-001 (gate de cota offline) e ADR-0003 (render de PDF iOS
  nativo) — antes citados no código sem existirem como documento.

### Novo — `ui/components/OnboardingPager` (P1-5)
- Carrossel de introdução **config-driven** (`HorizontalPager` + indicadores + Pular/Próximo/Começar),
  tema via `AppTheme`, responsivo (`LocalIsCompact`). Elimina a maior duplicação real do portfólio
  (17 apps reimplementavam à mão). Lógica pura testável (`onboardingIsLastPage`/`...PrimaryLabel`/
  `...ShowSkip`/`...NextIndex`/`...PreviousIndex`). Testes `OnboardingPagerTest` (8).

### Novo — `observability/CrashReporter.initFromBuildConfig` (P2-2)
- Helper aditivo que elimina o boilerplate `expect/actual` de DSN/versão/debug repetido em ~37 apps:
  `initFromBuildConfig(dsn, appSlug, versionName, versionCode)` deriva `environment`/`release`
  canônicos + gate de DSN vazio, lendo `isDebug` do `BuildInfo` já existente. Derivação pura
  `crashReporterConfigFromBuildConfig(...)` + `crashReporterRelease(...)` + `CrashEnvironment`.
  Testes `CrashReporterBuildConfigTest` (6).

### Teste — `firebase/auth` (P2-11)
- `FakeAuthRepository` reutilizável (`commonTest`) + `AuthTest` (7): contrato do `IAuthRepository`,
  helpers de provider do `User`, transições do `AuthStateManager`. Fecha a lacuna "firebase/auth sem teste".

### Dívida iOS quitada em código (P1-9, P1-10) — PENDENTE DE VALIDAÇÃO EM macOS
- **PDF iOS multi-página:** renderer `renderIosPdfPaged` + `IosPageFlow` (marca d'água por página) e
  primitivas novas no `IosPdfCanvas` (`strokeRect`/`strokeRoundRect`/`fillCircle`/`imageCrop`/
  `measureWrappedHeight`). Os **7 geradores** que eram stub agora são reais espelhando o par Android:
  `Document`, `TableReport`, `FinanceReport`, `HoursReport`, `VaccinationCard`, `WorkReport`,
  `Inspection` (com os 2 de recibo, os 9 geradores estão implementados em código).
- **Câmera/OCR iOS:** `PlateOcrAnalyzer.ios` real via **Apple Vision** (`VNRecognizeTextRequest`) e
  `CameraView.ios` real via **AVFoundation** (`AVCaptureSession` + `AVCaptureVideoDataOutput` +
  preview) + Vision + JPEG do frame reconhecido.
- **`PlatformCapabilities.pdfGeneration`/`cameraCapture` permanecem `false`**: o build Kotlin/Native
  iOS não roda em Linux — o flip para `true` é o passo final após **compilar + validar em macOS**.
