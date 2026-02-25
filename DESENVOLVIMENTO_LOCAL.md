# Desenvolvimento e Testes Locais - KmpLib

Guia para testar a biblioteca localmente nos projetos sem publicar no Maven Central.

---

## 🎯 Opção 1: Maven Local (Recomendado)

A forma mais simples e rápida. Publica a biblioteca no repositório Maven local (`~/.m2/repository`).

### Passo 1: Publicar no Maven Local

Na pasta do **kmplib**, execute:

```bash
# Windows
.\gradlew publishToMavenLocal

# Linux/Mac
./gradlew publishToMavenLocal
```

Isso vai publicar em:
- **Windows:** `C:\Users\{seu-usuario}\.m2\repository\br\com\codecacto\kmplib\1.0.0\`
- **Linux/Mac:** `~/.m2/repository/br/com/codecacto/kmplib/1.0.0/`

### Passo 2: Configurar Projeto Consumidor

Nos seus projetos (Locadora, Meu Advogado, TechPromos, etc):

#### **settings.gradle.kts**
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()  // ← Adicionar PRIMEIRO (prioridade)
        google()
        mavenCentral()
    }
}
```

#### **build.gradle.kts (app/composeApp)**
```kotlin
dependencies {
    implementation("br.com.codecacto:kmplib:1.0.0")
}
```

### Passo 3: Sync e Testar

1. Sync Gradle
2. Testar os componentes

### ✅ Vantagens
- ✅ Rápido (1 comando)
- ✅ Funciona com todos os projetos
- ✅ Não precisa de configuração complexa

### ⚠️ Atenção
- Sempre executar `publishToMavenLocal` após fazer mudanças
- Limpar cache Gradle se não reconhecer mudanças: `./gradlew clean --refresh-dependencies`

---

## 🔧 Opção 2: Composite Build (Mais Avançado)

Inclui o projeto kmplib diretamente como parte do build. Mudanças aparecem automaticamente.

### Configuração

#### **settings.gradle.kts** do projeto consumidor
```kotlin
// No início do arquivo
includeBuild("E:\\CodeCacto\\Lib\\kmplib") {
    dependencySubstitution {
        substitute(module("br.com.codecacto:kmplib"))
            .using(project(":library"))
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MeuProjeto"
include(":composeApp")
```

#### **build.gradle.kts**
```kotlin
dependencies {
    // Mesmo código, Gradle resolve automaticamente
    implementation("br.com.codecacto:kmplib:1.0.0")
}
```

### ✅ Vantagens
- ✅ Mudanças aparecem automaticamente
- ✅ Não precisa publicar toda hora
- ✅ Debugging direto no código da lib

### ⚠️ Desvantagens
- ⚠️ Build pode ficar mais lento
- ⚠️ Precisa ajustar em cada projeto

---

## 📦 Opção 3: Repositório Local (Customizado)

Criar um repositório Maven local customizado em uma pasta específica.

### Passo 1: Configurar kmplib

#### **library/build.gradle.kts**
```kotlin
plugins {
    // ... plugins existentes
    `maven-publish`
}

publishing {
    repositories {
        maven {
            name = "LocalRepo"
            url = uri("E:/CodeCacto/Lib/local-repo")
        }
    }
}

// Resto da configuração...
```

### Passo 2: Publicar

```bash
./gradlew publishAllPublicationsToLocalRepoRepository
```

### Passo 3: Consumir nos Projetos

#### **settings.gradle.kts**
```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("E:/CodeCacto/Lib/local-repo") }
        google()
        mavenCentral()
    }
}
```

---

## 🚀 Workflow Recomendado

### Durante Desenvolvimento

**Use Opção 1 (Maven Local)** - Mais simples:

```bash
# 1. Fazer mudanças na kmplib
# 2. Publicar localmente
cd E:\CodeCacto\Lib\kmplib
.\gradlew publishToMavenLocal

# 3. Ir para projeto que vai testar
cd E:\CodeCacto\Locadora
.\gradlew clean --refresh-dependencies
.\gradlew build
```

### Testes Intensivos

**Use Opção 2 (Composite Build)** - Se estiver fazendo muitas mudanças:

1. Configurar `includeBuild` uma vez
2. Fazer mudanças na kmplib
3. Sync Gradle no projeto consumidor
4. Testar imediatamente

---

## 🔄 Script de Publicação Rápida

Crie um script para publicar rapidamente:

### **publish-local.bat** (Windows)
```batch
@echo off
echo ========================================
echo Publicando kmplib no Maven Local...
echo ========================================

cd /d E:\CodeCacto\Lib\kmplib
call gradlew.bat clean publishToMavenLocal

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo ✓ Publicado com sucesso!
    echo ========================================
    echo.
    echo Versao: 1.0.0
    echo Local: %USERPROFILE%\.m2\repository\br\com\codecacto\kmplib\1.0.0
    echo.
    echo Para usar nos projetos:
    echo implementation("br.com.codecacto:kmplib:1.0.0")
    echo.
) else (
    echo.
    echo ========================================
    echo ✗ Erro na publicacao
    echo ========================================
)

pause
```

### **publish-local.sh** (Linux/Mac)
```bash
#!/bin/bash

echo "========================================"
echo "Publicando kmplib no Maven Local..."
echo "========================================"

cd ~/CodeCacto/Lib/kmplib
./gradlew clean publishToMavenLocal

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "✓ Publicado com sucesso!"
    echo "========================================"
    echo ""
    echo "Versão: 1.0.0"
    echo "Local: ~/.m2/repository/br/com/codecacto/kmplib/1.0.0"
    echo ""
    echo "Para usar nos projetos:"
    echo 'implementation("br.com.codecacto:kmplib:1.0.0")'
    echo ""
else
    echo ""
    echo "========================================"
    echo "✗ Erro na publicação"
    echo "========================================"
fi
```

Tornar executável (Linux/Mac):
```bash
chmod +x publish-local.sh
```

---

## 🧪 Testando Mudanças

### Workflow de Teste

1. **Fazer mudança na kmplib**
   ```kotlin
   // Exemplo: Adicionar nova cor em LoginColors
   data class LoginColors(
       val primary: Color = Color(0xFF6C63FF),
       val newColor: Color = Color.Red  // Nova cor
   )
   ```

2. **Publicar localmente**
   ```bash
   cd E:\CodeCacto\Lib\kmplib
   .\gradlew publishToMavenLocal
   ```

3. **Atualizar projeto consumidor**
   ```bash
   cd E:\CodeCacto\Locadora
   .\gradlew clean --refresh-dependencies
   ```

4. **Usar a mudança**
   ```kotlin
   GenericLoginScreen(
       colors = LoginColors(
           newColor = Color.Blue  // Usar nova cor
       )
   )
   ```

---

## 🔍 Verificar Publicação Local

### Windows
```powershell
dir "%USERPROFILE%\.m2\repository\br\com\codecacto\kmplib\1.0.0"
```

### Linux/Mac
```bash
ls -la ~/.m2/repository/br/com/codecacto/kmplib/1.0.0/
```

Deve mostrar:
```
kmplib-1.0.0.aar
kmplib-1.0.0.pom
kmplib-1.0.0.module
kmplib-android-1.0.0.aar
kmplib-iosarm64-1.0.0.klib
kmplib-iosx64-1.0.0.klib
...
```

---

## 🐛 Troubleshooting

### Gradle não encontra a biblioteca

**Solução 1:** Limpar cache
```bash
.\gradlew clean --refresh-dependencies
```

**Solução 2:** Deletar cache Gradle
```bash
# Fechar Android Studio/IDE primeiro
rm -rf ~/.gradle/caches
rm -rf ~/.m2/repository/br/com/codecacto
```

Depois publicar novamente:
```bash
.\gradlew publishToMavenLocal
```

### Mudanças não aparecem

**Solução:** Invalidar cache do IDE
- **Android Studio:** File → Invalidate Caches → Invalidate and Restart
- **IntelliJ IDEA:** File → Invalidate Caches → Invalidate and Restart

### Erro de duplicação de classes

**Causa:** Duas versões da lib no classpath

**Solução:** Garantir que está usando apenas `mavenLocal()`:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()  // Primeiro
        google()
        mavenCentral()
    }
}
```

---

## 📝 Checklist de Teste Local

Antes de publicar no Maven Central, testar localmente:

- [ ] Publicar no Maven Local: `./gradlew publishToMavenLocal`
- [ ] Adicionar `mavenLocal()` no projeto consumidor
- [ ] Sync Gradle
- [ ] Testar **GenericLoginScreen**
  - [ ] Com cores customizadas
  - [ ] Com textos customizados
  - [ ] Com logo
  - [ ] Login Email/Senha
  - [ ] Google login
  - [ ] Apple login (iOS)
- [ ] Testar **AppTextField**
  - [ ] Validação
  - [ ] Máscaras (CPF, Phone, etc)
  - [ ] Campo de senha
- [ ] Testar **Dialogs**
  - [ ] ConfirmationDialog
  - [ ] InputDialog
- [ ] Testar **Validadores**
  - [ ] CPF, CNPJ, Email, Phone
- [ ] Testar **Firebase** (se aplicável)
  - [ ] AuthRepository
  - [ ] FirestoreService
- [ ] Build release: `./gradlew assembleRelease`
- [ ] Testar em dispositivo físico

---

## 🎯 Resumo

| Método | Velocidade | Complexidade | Quando Usar |
|--------|-----------|--------------|-------------|
| **Maven Local** | ⚡⚡⚡ Rápido | 🟢 Simples | Mudanças ocasionais |
| **Composite Build** | ⚡⚡ Médio | 🟡 Médio | Desenvolvimento ativo |
| **Repo Local Custom** | ⚡⚡ Médio | 🟡 Médio | Compartilhar na equipe |

**Recomendação:** Use **Maven Local** para a maioria dos casos. É rápido, simples e funciona bem.

---

**Última Atualização:** 30/01/2026
