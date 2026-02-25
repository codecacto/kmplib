# Cenários de Teste - KmpLib

## 1. Validators (Validadores)

### 1.1 CpfValidator
**Testes Unitários:**
- ✅ CPF válido com formatação (pontos e traço)
- ✅ CPF válido sem formatação (apenas números)
- ✅ CPF inválido - dígitos verificadores errados
- ✅ CPF inválido - todos os dígitos iguais (111.111.111-11, etc)
- ✅ CPF inválido - tamanho incorreto
- ✅ CPF inválido - contém letras
- ✅ CPF vazio
- ✅ CPF null

### 1.2 CnpjValidator
**Testes Unitários:**
- ✅ CNPJ válido numérico com formatação
- ✅ CNPJ válido numérico sem formatação
- ✅ CNPJ alfanumérico válido (novo formato 2026+)
- ✅ CNPJ inválido - dígitos verificadores errados
- ✅ CNPJ inválido - todos os dígitos iguais
- ✅ CNPJ inválido - tamanho incorreto
- ✅ CNPJ vazio
- ⚠️ **FALTANDO**: Teste com caracteres especiais misturados

### 1.3 EmailValidator
**Testes Unitários:**
- ✅ Email válido simples
- ✅ Email válido com subdomínio
- ✅ Email válido com caracteres especiais permitidos
- ✅ Email inválido - sem @
- ✅ Email inválido - sem domínio
- ✅ Email inválido - formato incorreto
- ✅ Email vazio
- ⚠️ **FALTANDO**: Email com caracteres internacionais (IDN)
- ⚠️ **FALTANDO**: Email com múltiplos @ (inválido)

### 1.4 PhoneValidator
**Testes Unitários:**
- ✅ Celular válido (9 dígitos)
- ✅ Telefone fixo válido (8 dígitos)
- ✅ Telefone com formatação válida
- ✅ DDD inválido
- ✅ Telefone com tamanho incorreto
- ✅ Telefone vazio
- ⚠️ **FALTANDO**: Teste com DDDs específicos de cada estado
- ⚠️ **FALTANDO**: Números começando com 0 ou 1 (inválidos)

### 1.5 PasswordValidator
**Testes Unitários:**
- ✅ Senha válida com regras padrão
- ✅ Senha muito curta
- ✅ Senha muito longa
- ✅ Senha sem maiúscula
- ✅ Senha sem minúscula
- ✅ Senha sem dígito
- ✅ Senha sem caractere especial
- ✅ Múltiplos erros simultâneos
- ✅ Regras customizadas
- ✅ Cálculo de força
- ✅ Geração de senha
- ✅ Extensões de propriedades

---

## 2. Masks (Máscaras Compose)

### 2.1 CpfMask
**Testes Unitários:**
- ⚠️ **FALTANDO**: Formatação progressiva (1 → 1, 123 → 123, 12345678901 → 123.456.789-01)
- ⚠️ **FALTANDO**: Remoção de caracteres não numéricos
- ⚠️ **FALTANDO**: Limite de 11 dígitos
- ⚠️ **FALTANDO**: Copy/paste de CPF formatado

**Testes de Integração:**
- ⚠️ **FALTANDO**: Usar máscara em TextField real
- ⚠️ **FALTANDO**: Validação em tempo real com CpfValidator

### 2.2 CnpjMask
**Testes Unitários:**
- ⚠️ **FALTANDO**: Formatação progressiva (12.345.678/0001-00)
- ⚠️ **FALTANDO**: Suporte a formato alfanumérico
- ⚠️ **FALTANDO**: Limite de 14 caracteres

### 2.3 PhoneMask
**Testes Unitários:**
- ⚠️ **FALTANDO**: Formatação celular (11) 98765-4321
- ⚠️ **FALTANDO**: Formatação fixo (11) 3456-7890
- ⚠️ **FALTANDO**: Alteração dinâmica entre formatos

### 2.4 CurrencyMask (BRL)
**Testes Unitários:**
- ⚠️ **FALTANDO**: Formatação com símbolo R$
- ⚠️ **FALTANDO**: Separador de milhares
- ⚠️ **FALTANDO**: Duas casas decimais
- ⚠️ **FALTANDO**: Valores grandes (milhões, bilhões)
- ⚠️ **FALTANDO**: Zero e valores negativos (se aplicável)

### 2.5 CepMask
**Testes Unitários:**
- ⚠️ **FALTANDO**: Formatação 12345-678
- ⚠️ **FALTANDO**: Limite de 8 dígitos

---

## 3. Firebase (Repositórios e Serviços)

### 3.1 AuthRepository
**Testes Unitários (com Mocks):**
- ⚠️ **FALTANDO**: signUpWithEmail - sucesso
- ⚠️ **FALTANDO**: signUpWithEmail - email já em uso
- ⚠️ **FALTANDO**: signUpWithEmail - senha fraca
- ⚠️ **FALTANDO**: signInWithEmail - sucesso
- ⚠️ **FALTANDO**: signInWithEmail - credenciais inválidas
- ⚠️ **FALTANDO**: signInWithEmail - usuário não encontrado
- ⚠️ **FALTANDO**: signInWithGoogle - sucesso
- ⚠️ **FALTANDO**: signInWithApple - sucesso
- ⚠️ **FALTANDO**: sendPasswordResetEmail - sucesso
- ⚠️ **FALTANDO**: updateProfile - sucesso
- ⚠️ **FALTANDO**: updateProfile - não autenticado
- ⚠️ **FALTANDO**: changePassword - sucesso
- ⚠️ **FALTANDO**: changePassword - senha atual incorreta
- ⚠️ **FALTANDO**: deleteAccount - sucesso
- ⚠️ **FALTANDO**: signOut - sucesso
- ⚠️ **FALTANDO**: getIdToken - sucesso
- ⚠️ **FALTANDO**: currentUser Flow - mudanças de estado
- ⚠️ **FALTANDO**: Mapeamento de exceções (AuthException)

**Testes de Integração (Firebase Emulator):**
- ⚠️ **FALTANDO**: Fluxo completo de cadastro → login → logout
- ⚠️ **FALTANDO**: Recuperação de senha
- ⚠️ **FALTANDO**: Alteração de senha com reautenticação
- ⚠️ **FALTANDO**: Deleção de conta

### 3.2 FirestoreService
**Testes Unitários (com Mocks):**
- ⚠️ **FALTANDO**: getDocument - documento existe
- ⚠️ **FALTANDO**: getDocument - documento não existe
- ⚠️ **FALTANDO**: setDocument - criar novo
- ⚠️ **FALTANDO**: setDocument - sobrescrever
- ⚠️ **FALTANDO**: updateDocument - sucesso
- ⚠️ **FALTANDO**: updateDocument - documento não existe
- ⚠️ **FALTANDO**: deleteDocument - sucesso
- ⚠️ **FALTANDO**: queryDocuments - filtros simples
- ⚠️ **FALTANDO**: queryDocuments - orderBy
- ⚠️ **FALTANDO**: queryDocuments - limit
- ⚠️ **FALTANDO**: queryDocuments - whereIn, whereArrayContains
- ⚠️ **FALTANDO**: observeDocument - mudanças em tempo real
- ⚠️ **FALTANDO**: observeQuery - mudanças em tempo real
- ⚠️ **FALTANDO**: batchOperations - múltiplas escritas
- ⚠️ **FALTANDO**: getSubcollection - subcoleções
- ⚠️ **FALTANDO**: Serialização/deserialização de objetos complexos
- ⚠️ **FALTANDO**: Tratamento de erros de rede

**Testes de Integração (Firebase Emulator):**
- ⚠️ **FALTANDO**: CRUD completo em coleção
- ⚠️ **FALTANDO**: Queries complexas com múltiplos filtros
- ⚠️ **FALTANDO**: Subcoleções aninhadas
- ⚠️ **FALTANDO**: Batch operations com rollback

### 3.3 StorageService
**Testes Unitários (com Mocks):**
- ⚠️ **FALTANDO**: getDownloadUrl - arquivo existe
- ⚠️ **FALTANDO**: getDownloadUrl - arquivo não existe
- ⚠️ **FALTANDO**: deleteFile - sucesso
- ⚠️ **FALTANDO**: deleteFile - arquivo não existe
- ⚠️ **FALTANDO**: Detecção de MIME type

**Testes de Integração (Firebase Emulator):**
- ⚠️ **FALTANDO**: Upload de arquivo (quando implementado)
- ⚠️ **FALTANDO**: Download e delete de arquivo

---

## 4. Platform (Recursos de Plataforma)

### 4.1 BiometricAuth
**Testes Unitários (Android):**
- ⚠️ **FALTANDO**: authenticate - biometria disponível - sucesso
- ⚠️ **FALTANDO**: authenticate - biometria disponível - falha
- ⚠️ **FALTANDO**: authenticate - biometria disponível - cancelado
- ⚠️ **FALTANDO**: authenticate - biometria não disponível
- ⚠️ **FALTANDO**: canAuthenticate - biometria disponível
- ⚠️ **FALTANDO**: canAuthenticate - biometria não disponível

**Testes Unitários (iOS):**
- ⚠️ **FALTANDO**: authenticate - Face ID/Touch ID - sucesso
- ⚠️ **FALTANDO**: authenticate - Face ID/Touch ID - falha
- ⚠️ **FALTANDO**: canAuthenticate - disponibilidade

**Testes de Integração:**
- ⚠️ **FALTANDO**: Fluxo completo em app real (manual)

### 4.2 UrlLauncher
**Testes Unitários (Android):**
- ⚠️ **FALTANDO**: openUrl - URL válida
- ⚠️ **FALTANDO**: openUrl - URL inválida
- ⚠️ **FALTANDO**: openEmail - com parâmetros
- ⚠️ **FALTANDO**: openPhone - número válido
- ⚠️ **FALTANDO**: openWhatsApp - número e mensagem
- ⚠️ **FALTANDO**: openStorePage - Play Store
- ⚠️ **FALTANDO**: openMap - coordenadas e endereço

**Testes Unitários (iOS):**
- ⚠️ **FALTANDO**: openUrl - URL válida
- ⚠️ **FALTANDO**: openEmail - com parâmetros
- ⚠️ **FALTANDO**: openPhone - número válido
- ⚠️ **FALTANDO**: openWhatsApp - número e mensagem
- ⚠️ **FALTANDO**: openStorePage - App Store
- ⚠️ **FALTANDO**: openMap - coordenadas e endereço

### 4.3 ShareHandler
**Testes Unitários (Android):**
- ⚠️ **FALTANDO**: shareText - texto simples
- ⚠️ **FALTANDO**: shareImage - URI válida
- ⚠️ **FALTANDO**: shareFile - arquivo válido
- ⚠️ **FALTANDO**: Tratamento de erros

**Testes Unitários (iOS):**
- ⚠️ **FALTANDO**: shareText - texto simples
- ⚠️ **FALTANDO**: shareImage - URL válida
- ⚠️ **FALTANDO**: shareFile - arquivo válido

### 4.4 NotificationScheduler
**Testes Unitários (Android):**
- ⚠️ **FALTANDO**: scheduleNotification - notificação futura
- ⚠️ **FALTANDO**: scheduleNotification - notificação imediata
- ⚠️ **FALTANDO**: cancelNotification - sucesso
- ⚠️ **FALTANDO**: Permissões de notificação

**Testes Unitários (iOS):**
- ⚠️ **FALTANDO**: scheduleNotification - notificação futura
- ⚠️ **FALTANDO**: cancelNotification - sucesso
- ⚠️ **FALTANDO**: Permissões de notificação

---

## 5. BrData (Dados Brasileiros)

### 5.1 BrazilianStates
**Testes Unitários:**
- ⚠️ **FALTANDO**: all - retorna todos os 27 estados
- ⚠️ **FALTANDO**: findByCode - código válido
- ⚠️ **FALTANDO**: findByCode - código inválido
- ⚠️ **FALTANDO**: findByAbbreviation - sigla válida (maiúscula/minúscula)
- ⚠️ **FALTANDO**: findByAbbreviation - sigla inválida
- ⚠️ **FALTANDO**: findByName - nome exato
- ⚠️ **FALTANDO**: findByName - nome com acentos
- ⚠️ **FALTANDO**: findByName - nome sem acentos
- ⚠️ **FALTANDO**: findByName - case insensitive
- ⚠️ **FALTANDO**: findByName - nome não existe
- ⚠️ **FALTANDO**: filter - query vazia (retorna todos)
- ⚠️ **FALTANDO**: filter - por parte do nome
- ⚠️ **FALTANDO**: filter - por sigla
- ⚠️ **FALTANDO**: byRegion - região válida
- ⚠️ **FALTANDO**: byRegion - cada região (Norte, Nordeste, Centro-Oeste, Sudeste, Sul)
- ⚠️ **FALTANDO**: abbreviations - lista de 27 siglas
- ⚠️ **FALTANDO**: names - lista de 27 nomes

### 5.2 String Extensions (removeAccents)
**Testes Unitários:**
- ⚠️ **FALTANDO**: removeAccents - texto com acentos
- ⚠️ **FALTANDO**: removeAccents - texto sem acentos
- ⚠️ **FALTANDO**: removeAccents - string vazia
- ⚠️ **FALTANDO**: removeAccents - caracteres especiais brasileiros (ç, ã, õ, etc)

---

## 6. Core (Utilitários)

### 6.1 AppLogger
**Testes Unitários:**
- ⚠️ **FALTANDO**: Logs em diferentes níveis (d, i, w, e)
- ⚠️ **FALTANDO**: Logs com e sem exceção
- ⚠️ **FALTANDO**: Verificar saída no Android (Logcat)
- ⚠️ **FALTANDO**: Verificar saída no iOS (NSLog)

### 6.2 TimeUtils
**Testes Unitários:**
- ⚠️ **FALTANDO**: formatDate - padrões diferentes (dd/MM/yyyy, yyyy-MM-dd, etc)
- ⚠️ **FALTANDO**: formatTime - HH:mm, HH:mm:ss
- ⚠️ **FALTANDO**: parseDate - string válida
- ⚠️ **FALTANDO**: parseDate - string inválida
- ⚠️ **FALTANDO**: parseTime - string válida
- ⚠️ **FALTANDO**: parseTime - string inválida
- ⚠️ **FALTANDO**: currentTimeMillis - retorna valor válido
- ⚠️ **FALTANDO**: Cálculos de diferença entre datas

---

## 7. Testes de Integração End-to-End

### 7.1 Fluxo de Autenticação + Firestore
**Cenário:**
1. Usuário cria conta com email/senha
2. Cria documento no Firestore com seus dados
3. Atualiza perfil
4. Lê documento do Firestore
5. Faz logout
6. Faz login novamente
7. Deleta documento
8. Deleta conta

### 7.2 Fluxo de Validação + Máscara + Firestore
**Cenário:**
1. Input de CPF com máscara
2. Validação em tempo real
3. Se válido, salvar no Firestore
4. Recuperar e exibir formatado

### 7.3 Fluxo de Plataforma Completo
**Cenário (Android):**
1. Autenticação biométrica
2. Compartilhar conteúdo
3. Abrir URL externa
4. Agendar notificação

**Cenário (iOS):**
1. Autenticação Face ID/Touch ID
2. Compartilhar conteúdo
3. Abrir URL externa
4. Agendar notificação

---

## 8. Testes de Performance

### 8.1 Validators
- ⚠️ **FALTANDO**: Validar 10.000 CPFs em sequência
- ⚠️ **FALTANDO**: Validar 10.000 CNPJs em sequência
- ⚠️ **FALTANDO**: Validar 10.000 emails em sequência

### 8.2 Firestore
- ⚠️ **FALTANDO**: Query com 1.000+ documentos
- ⚠️ **FALTANDO**: Batch write de 500 documentos
- ⚠️ **FALTANDO**: Observação de coleção com alta frequência de updates

### 8.3 Masks
- ⚠️ **FALTANDO**: Digitação rápida em campo com máscara
- ⚠️ **FALTANDO**: Copy/paste de texto grande

---

## 9. Testes de Segurança

### 9.1 AuthRepository
- ⚠️ **FALTANDO**: Validar que tokens não são logados
- ⚠️ **FALTANDO**: Validar que senhas não são logadas
- ⚠️ **FALTANDO**: Validar que emails não são logados (✅ CORRIGIDO)

### 9.2 FirestoreService
- ⚠️ **FALTANDO**: Validar regras de segurança (via emulator)
- ⚠️ **FALTANDO**: Tentar acessar documento sem autenticação
- ⚠️ **FALTANDO**: Injeção de SQL/NoSQL em queries

---

## 10. Testes de Acessibilidade

### 10.1 Compose Masks
- ⚠️ **FALTANDO**: Leitores de tela leem valores formatados corretamente
- ⚠️ **FALTANDO**: Navegação por teclado funciona corretamente

---

## Resumo de Cobertura Atual

| Módulo | Testes Existentes | Testes Faltantes | Cobertura Estimada |
|--------|-------------------|------------------|-------------------|
| CpfValidator | ✅ | - | ~95% |
| CnpjValidator | ✅ | 1 | ~90% |
| EmailValidator | ✅ | 2 | ~85% |
| PhoneValidator | ✅ | 2 | ~85% |
| PasswordValidator | ✅ | - | ~95% |
| **Masks** | ❌ | 20+ | **0%** |
| **AuthRepository** | ❌ | 18+ | **0%** |
| **FirestoreService** | ❌ | 20+ | **0%** |
| **StorageService** | ❌ | 5+ | **0%** |
| **BiometricAuth** | ❌ | 8+ | **0%** |
| **UrlLauncher** | ❌ | 12+ | **0%** |
| **ShareHandler** | ❌ | 6+ | **0%** |
| **NotificationScheduler** | ❌ | 6+ | **0%** |
| **BrazilianStates** | ❌ | 16+ | **0%** |
| **String Extensions** | ❌ | 4+ | **0%** |
| **AppLogger** | ❌ | 4+ | **0%** |
| **TimeUtils** | ❌ | 8+ | **0%** |

**Cobertura Total Estimada: ~15%**
**Meta: >80%**

---

## Prioridades de Implementação

### ALTA (Implementar Imediatamente)
1. ✅ PasswordValidator - **COMPLETO**
2. BrazilianStates (16 testes)
3. Masks básicos (CpfMask, PhoneMask, CurrencyMask) - 15 testes
4. TimeUtils (8 testes)

### MÉDIA (Implementar em Sprint 2)
5. AuthRepository unit tests (com mocks) - 18 testes
6. FirestoreService unit tests (com mocks) - 20 testes
7. UrlLauncher (12 testes)
8. String Extensions (4 testes)

### BAIXA (Implementar em Sprint 3)
9. Testes de integração com Firebase Emulator
10. Testes de plataforma (BiometricAuth, ShareHandler, NotificationScheduler)
11. Testes de performance
12. Testes de segurança
13. Testes de acessibilidade

---

## Ferramentas Necessárias

### Para Testes Unitários
- ✅ kotlin-test (já configurado)
- ⚠️ MockK ou Mockito para mocks do Firebase
- ⚠️ Turbine para testar Flows

### Para Testes de Integração
- ⚠️ Firebase Emulator Suite
- ⚠️ Robolectric (Android)
- ⚠️ XCTest (iOS)

### Para Testes de UI
- ⚠️ Compose UI Testing
- ⚠️ Espresso (Android)
- ⚠️ XCUITest (iOS)

### Para Cobertura de Código
- ⚠️ Kover (Kotlin Coverage)
- ⚠️ JaCoCo

### Para CI/CD
- ✅ GitHub Actions (já configurado)
- ⚠️ Adicionar step de coverage report
- ⚠️ Adicionar step de testes de integração
