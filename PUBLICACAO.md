# Instruções de Publicação - KmpLib

## ⚠️ IMPORTANTE

**A publicação desta biblioteca é MANUAL.**

Não executar scripts automáticos de publicação sem revisão prévia.

---

## Versão Atual

**Versão:** 1.0.0
**Data:** 30/01/2026
**Status:** ✅ Pronta para publicação

---

## Conteúdo da Versão 1.0.0

### Fase 1 - Core
- ✅ Validadores (CPF, CNPJ, Email, Telefone, Senha)
- ✅ Máscaras Compose (CPF, CNPJ, Telefone, CEP, Currency)
- ✅ Dados Brasileiros (Estados, extensões de String)
- ✅ Utilitários (TimeUtils, AppLogger)

### Fase 2 - Firebase
- ✅ AuthRepository (Email/Senha, Google, Apple)
- ✅ FirestoreService (CRUD, Queries, Real-time)
- ✅ StorageService (Download, Delete)

### Fase 3 - Platform
- ✅ UrlLauncher (URLs, Email, Phone, WhatsApp, Maps)
- ✅ ShareHandler (Text, Image, File)
- ✅ BiometricAuth (Face ID, Touch ID, Fingerprint)
- ✅ NotificationScheduler (Local notifications)

### Fase 4 - UI Components (NOVO)
- ✅ GenericLoginScreen (Login completo customizável)
- ✅ Dialogs (ConfirmationDialog, InputDialog)
- ✅ Form Components (FormContainer, AppTextField)
- ✅ Buttons (AppButton, AppOutlinedButton)

---

## Estatísticas

- **Total de Testes:** 251 testes
- **Cobertura:** ~35%
- **Linhas de Código:** ~5.000 linhas
- **Componentes:** 30+ componentes e utilitários
- **Plataformas:** Android + iOS

---

## Checklist de Publicação

### Pré-Publicação
- [x] Código completo e testado
- [x] Documentação atualizada (README.md)
- [x] Exemplos de uso criados
- [x] Testes executados (188 testes core + 63 testes UI)
- [x] Changelog criado
- [x] ProGuard rules adicionadas
- [x] Versão definida (1.0.0)

### Configuração Maven Central
- [ ] Verificar credenciais Sonatype
- [ ] Verificar GPG key configurada
- [ ] Verificar gradle.properties local com:
  - `mavenCentralUsername`
  - `mavenCentralPassword`
  - `signing.keyId`
  - `signing.password`
  - `signing.secretKeyRingFile`

### Build e Testes
- [ ] Executar `./gradlew clean`
- [ ] Executar `./gradlew build`
- [ ] Executar `./gradlew test`
- [ ] Verificar erros de compilação
- [ ] Verificar warnings do ProGuard

### Publicação
- [ ] Executar `./gradlew publishToMavenCentral --no-configuration-cache`
- [ ] Aguardar validação do Sonatype
- [ ] Aprovar release no Sonatype Nexus
- [ ] Aguardar sync com Maven Central (~2-4 horas)
- [ ] Verificar artifact disponível em Maven Central

### Pós-Publicação
- [ ] Criar Git tag: `git tag v1.0.0`
- [ ] Push da tag: `git push origin v1.0.0`
- [ ] Criar GitHub Release com changelog
- [ ] Atualizar documentação de versão
- [ ] Notificar equipe da nova versão

---

## Comandos de Publicação

### Build Local
```bash
./gradlew clean build
```

### Testes
```bash
./gradlew test
./gradlew testDebugUnitTest
```

### Publicar no Maven Central
```bash
# ATENÇÃO: Executar apenas quando estiver pronto!
./gradlew publishToMavenCentral --no-configuration-cache

# Ou para release automatizado:
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

### Verificar Publicação
```bash
# Verificar no Maven Central Repository
# https://repo1.maven.org/maven2/br/com/codecacto/kmplib/

# Ou usar no projeto:
# implementation("br.com.codecacto:kmplib:1.0.0")
```

---

## Rollback

Caso seja necessário fazer rollback:

1. **Não é possível remover versão do Maven Central**
2. A solução é publicar uma nova versão (1.0.1) com as correções
3. Deprecar a versão problemática na documentação

---

## Contatos

- **Repositório:** https://github.com/codecacto/kmplib
- **Issues:** https://github.com/codecacto/kmplib/issues
- **Maven Central:** https://central.sonatype.com/

---

## Notas da Versão 1.0.0

Esta é a primeira versão pública da biblioteca KmpLib, incluindo:

- ✅ Todos os utilitários core (validadores, máscaras, dados brasileiros)
- ✅ Integração completa com Firebase (Auth, Firestore, Storage)
- ✅ Features específicas de plataforma (Biometric, Share, Notifications)
- ✅ **NOVO:** Componentes de UI Compose (Login Screen, Dialogs, Forms)

**Total de 30+ componentes prontos para uso em projetos Android e iOS!**

---

**Última Atualização:** 30/01/2026
**Por:** Claude Code (Anthropic)
