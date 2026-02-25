# KmpLib - Biblioteca KMP CodeCacto

Biblioteca Kotlin Multiplatform com utilitários reutilizáveis para Android e iOS.

## Índice

- [Instalação](#instalação)
  - [Opção 1: Maven Local (Desenvolvimento)](#opção-1-maven-local-desenvolvimentotestes)
  - [Opção 2: Maven Central (Produção)](#opção-2-maven-central-produção)
- [Inicialização](#inicialização)
- [Fase 1 - Core](#fase-1---core)
  - [Validadores](#validadores)
  - [Máscaras Compose](#máscaras-compose)
  - [Dados Brasileiros](#dados-brasileiros)
  - [Utilitários](#utilitários)
- [Fase 2 - Firebase](#fase-2---firebase)
  - [Autenticação](#autenticação)
  - [Firestore](#firestore)
  - [Storage](#storage)
- [Fase 3 - Platform](#fase-3---platform)
  - [URL Launcher](#url-launcher)
  - [Share Handler](#share-handler)
  - [Biometric Auth](#biometric-auth)
  - [Notification Scheduler](#notification-scheduler)
- [Fase 4 - UI Components](#fase-4---ui-components)
  - [GenericLoginScreen](#genericloginscreen)
  - [Dialogs](#dialogs)
  - [Form Components](#form-components)
  - [Buttons](#buttons)

---

## Instalação

### Opção 1: Maven Local (Desenvolvimento/Testes)

Para usar a biblioteca localmente durante desenvolvimento:

#### 1. Publicar no Maven Local

Na pasta do kmplib:
```bash
# Windows
.\gradlew publishToMavenLocal

# Linux/Mac
./gradlew publishToMavenLocal

# Ou use o script:
.\publish-local.bat
```

A biblioteca será publicada em:
- **Windows:** `C:\Users\{seu-usuario}\.m2\repository\br\com\codecacto\kmplib\1.0.0\`
- **Linux/Mac:** `~/.m2/repository/br/com/codecacto/kmplib/1.0.0/`

#### 2. Configurar Projeto Consumidor

**settings.gradle.kts:**
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()  // ← ADICIONAR PRIMEIRO (prioridade máxima)
        google()
        mavenCentral()
    }
}
```

**build.gradle.kts (módulo app/composeApp):**
```kotlin
dependencies {
    implementation("br.com.codecacto:kmplib:1.0.0")
}
```

#### 3. Sync Gradle

- Android Studio/IntelliJ: Clique em **"Sync Now"**
- Ou: File → Sync Project with Gradle Files

#### 4. Atualizar Após Mudanças

Sempre que fizer mudanças na kmplib:
```bash
# 1. Publicar novamente
cd E:\CodeCacto\Lib\kmplib
.\gradlew publishToMavenLocal

# 2. Limpar cache no projeto consumidor
cd E:\CodeCacto\{SeuProjeto}
.\gradlew clean --refresh-dependencies
```

---

### Opção 2: Maven Central (Produção)

Para usar a versão publicada no Maven Central:

**settings.gradle.kts:**
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

**build.gradle.kts:**
```kotlin
dependencies {
    implementation("br.com.codecacto:kmplib:1.0.0")
}
```

> **Nota:** A publicação no Maven Central é feita manualmente. Consulte [PUBLICACAO.md](PUBLICACAO.md) para detalhes.

---

### 📖 Mais Informações

- **Desenvolvimento Local Completo:** Veja [DESENVOLVIMENTO_LOCAL.md](DESENVOLVIMENTO_LOCAL.md) para guia detalhado
- **Publicação Maven Central:** Veja [PUBLICACAO.md](PUBLICACAO.md) para instruções de publicação
- **Exemplos de Uso:** Veja [UI_COMPONENTS_EXAMPLES.md](UI_COMPONENTS_EXAMPLES.md) para exemplos práticos

---

### Dependências necessárias no projeto consumidor

Para usar Firebase, adicione no seu projeto:

```kotlin
// Android - build.gradle.kts
plugins {
    id("com.google.gms.google-services")
}

// Adicione google-services.json na pasta app/
```

---

## Inicialização

### Android

```kotlin
// Application.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KmpLib.init(this)
    }
}

// MainActivity.kt (para funcionalidades que precisam de Activity, como biometria)
class MainActivity : FragmentActivity() {
    override fun onResume() {
        super.onResume()
        KmpLib.setActivity(this)
    }

    override fun onPause() {
        super.onPause()
        KmpLib.clearActivity()
    }
}
```

### iOS

Não requer inicialização especial. Configure o Firebase conforme documentação oficial (GoogleService-Info.plist).

---

# Fase 1 - Core

## Validadores

### CpfValidator

Valida CPF brasileiro com verificação de dígitos verificadores (algoritmo módulo 11).

```kotlin
import br.com.codecacto.kmplib.validation.CpfValidator

// Verificar se é válido
val isValid = CpfValidator.isValid("529.982.247-25") // true
val isValid2 = CpfValidator.isValid("52998224725")   // true
val isValid3 = CpfValidator.isValid("11111111111")   // false (todos iguais)
val isValid4 = CpfValidator.isValid("12345678900")   // false (dígito inválido)

// Remover máscara
val unmasked = CpfValidator.unmask("529.982.247-25") // "52998224725"

// Aplicar máscara
val formatted = CpfValidator.format("52998224725") // "529.982.247-25"

// Validação com mensagem de erro (útil para formulários)
val error: String? = CpfValidator.validate("123")
// Retorna: "CPF deve ter 11 dígitos" ou "CPF inválido" ou null se válido
```

#### Regras de validação:
- Deve ter exatamente 11 dígitos numéricos
- Não pode ter todos os dígitos iguais (111.111.111-11)
- Dígitos verificadores devem ser válidos (algoritmo módulo 11)

#### Mensagens de erro (validate()):
- `"CPF é obrigatório"` - string vazia
- `"CPF deve ter 11 dígitos"` - tamanho incorreto
- `"CPF inválido"` - dígitos verificadores incorretos ou todos iguais

---

### CnpjValidator

Valida CNPJ brasileiro, incluindo o novo formato alfanumérico (a partir de 2026).

```kotlin
import br.com.codecacto.kmplib.validation.CnpjValidator

// CNPJ numérico tradicional
val isValid = CnpjValidator.isValid("11.222.333/0001-81") // true
val isValid2 = CnpjValidator.isValid("11222333000181")    // true

// CNPJ alfanumérico (novo formato 2026)
val isValid3 = CnpjValidator.isValid("AB.CDE.FGH/0001-00") // validação estrutural

// Remover máscara
val unmasked = CnpjValidator.unmask("11.222.333/0001-81") // "11222333000181"

// Aplicar máscara
val formatted = CnpjValidator.format("11222333000181") // "11.222.333/0001-81"

// Validação com mensagem de erro
val error: String? = CnpjValidator.validate("11222333000182")
// Retorna: "CNPJ inválido" (dígito verificador errado)
```

#### Regras de validação:
- Deve ter exatamente 14 caracteres (dígitos ou alfanuméricos)
- Não pode ter todos os caracteres iguais
- Dígitos verificadores devem ser válidos

---

### EmailValidator

Valida formato de e-mail.

```kotlin
import br.com.codecacto.kmplib.validation.EmailValidator

// Verificar se é válido
val isValid = EmailValidator.isValid("usuario@exemplo.com")      // true
val isValid2 = EmailValidator.isValid("user.name+tag@gmail.com") // true
val isValid3 = EmailValidator.isValid("invalido")                // false
val isValid4 = EmailValidator.isValid("sem@dominio")             // false

// Validação com mensagem de erro
val error: String? = EmailValidator.validate("")
// Retorna: "Email é obrigatório"

val error2: String? = EmailValidator.validate("invalido")
// Retorna: "Email inválido"

// Normalizar (trim + lowercase)
val normalized = EmailValidator.normalize("  USER@Example.COM  ")
// Retorna: "user@example.com"
```

#### Regras de validação:
- Deve conter exatamente um @
- Deve ter parte local (antes do @)
- Deve ter domínio válido com pelo menos um ponto
- Não pode estar vazio ou conter apenas espaços

---

### PhoneValidator

Valida telefones brasileiros (celular e fixo) com verificação de DDD.

```kotlin
import br.com.codecacto.kmplib.validation.PhoneValidator

// Verificar se é válido (celular ou fixo)
val isValid = PhoneValidator.isValid("11987654321")      // true (celular)
val isValid2 = PhoneValidator.isValid("(11) 98765-4321") // true (celular)
val isValid3 = PhoneValidator.isValid("1134567890")      // true (fixo)
val isValid4 = PhoneValidator.isValid("(11) 3456-7890")  // true (fixo)

// Verificar tipo
val isMobile = PhoneValidator.isMobile("11987654321")   // true
val isLandline = PhoneValidator.isLandline("1134567890") // true

// Extrair DDD
val ddd = PhoneValidator.extractDdd("11987654321")      // 11
val ddd2 = PhoneValidator.extractDdd("(21) 98765-4321") // 21

// Remover máscara
val unmasked = PhoneValidator.unmask("(11) 98765-4321") // "11987654321"

// Aplicar máscara
val formatted = PhoneValidator.format("11987654321")    // "(11) 98765-4321"
val formatted2 = PhoneValidator.format("1134567890")    // "(11) 3456-7890"

// Validação com mensagem de erro
val error: String? = PhoneValidator.validate("119876")
// Retorna: "Telefone inválido"
```

#### Regras de validação:
- **Celular**: 11 dígitos, terceiro dígito deve ser 9
- **Fixo**: 10 dígitos, terceiro dígito não pode ser 9
- **DDD**: Deve estar entre 11-99 (não pode ser 00 ou 01)

#### DDDs válidos por região:
| Região | DDDs |
|--------|------|
| SP Capital | 11 |
| SP Interior | 12-19 |
| RJ | 21, 22, 24 |
| ES | 27, 28 |
| MG | 31-35, 37, 38 |
| PR | 41-46 |
| SC | 47-49 |
| RS | 51-55 |
| DF/GO/TO/MT/MS | 61-69 |
| BA/SE | 71, 73-75, 77, 79 |
| PE/AL/PB/RN | 81-84, 86, 87 |
| CE/PI/MA | 85, 86, 88, 89, 91-99 |
| Norte | 91-97, 99 |

---

### PasswordValidator

Valida senhas com regras configuráveis.

```kotlin
import br.com.codecacto.kmplib.validation.PasswordValidator
import br.com.codecacto.kmplib.validation.PasswordRules

// Usar regras padrão
val result = PasswordValidator.validate("Senha123!")
if (result.isValid) {
    println("Senha válida!")
} else {
    result.errors.forEach { println(it) }
}

// Personalizar regras
val customRules = PasswordRules(
    minLength = 10,
    maxLength = 50,
    requireUppercase = true,
    requireLowercase = true,
    requireDigit = true,
    requireSpecialChar = true,
    specialChars = "!@#$%^&*()_+-=[]{}|;:,.<>?"
)

val result2 = PasswordValidator.validate("MinhaSenha123!", customRules)

// Verificar força da senha
val strength = PasswordValidator.getStrength("Senha123!")
// Retorna: PasswordStrength.STRONG

// Gerar senha aleatória
val randomPassword = PasswordValidator.generatePassword(
    length = 16,
    includeUppercase = true,
    includeLowercase = true,
    includeDigits = true,
    includeSpecial = true
)
// Exemplo: "Kj9#mP2$xL5@nQ8&"
```

#### Níveis de força:
| Nível | Descrição |
|-------|-----------|
| `WEAK` | Menos de 6 caracteres ou muito simples |
| `FAIR` | 6-7 caracteres com alguma complexidade |
| `GOOD` | 8-11 caracteres com boa complexidade |
| `STRONG` | 12+ caracteres com alta complexidade |

#### Regras padrão:
- Mínimo 8 caracteres
- Máximo 128 caracteres
- Requer pelo menos uma letra maiúscula
- Requer pelo menos uma letra minúscula
- Requer pelo menos um dígito
- Requer pelo menos um caractere especial

---

## Máscaras Compose

Máscaras para uso com `TextField` do Jetpack Compose / Compose Multiplatform.

### CpfMask

```kotlin
import br.com.codecacto.kmplib.mask.CpfVisualTransformation

@Composable
fun CpfField() {
    var cpf by remember { mutableStateOf("") }

    TextField(
        value = cpf,
        onValueChange = { newValue ->
            // Aceita apenas dígitos, máximo 11
            cpf = newValue.filter { it.isDigit() }.take(11)
        },
        visualTransformation = CpfVisualTransformation(),
        label = { Text("CPF") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    // Usuário digita: 52998224725
    // Exibe: 529.982.247-25
}
```

---

### CnpjMask

```kotlin
import br.com.codecacto.kmplib.mask.CnpjVisualTransformation

@Composable
fun CnpjField() {
    var cnpj by remember { mutableStateOf("") }

    TextField(
        value = cnpj,
        onValueChange = { newValue ->
            // Aceita dígitos e letras (novo CNPJ), máximo 14
            cnpj = newValue.filter { it.isLetterOrDigit() }.take(14).uppercase()
        },
        visualTransformation = CnpjVisualTransformation(),
        label = { Text("CNPJ") }
    )

    // Usuário digita: 11222333000181
    // Exibe: 11.222.333/0001-81
}
```

---

### PhoneMask

```kotlin
import br.com.codecacto.kmplib.mask.PhoneVisualTransformation

@Composable
fun PhoneField() {
    var phone by remember { mutableStateOf("") }

    TextField(
        value = phone,
        onValueChange = { newValue ->
            phone = newValue.filter { it.isDigit() }.take(11)
        },
        visualTransformation = PhoneVisualTransformation(),
        label = { Text("Telefone") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
    )

    // Celular (11 dígitos): 11987654321 -> (11) 98765-4321
    // Fixo (10 dígitos): 1134567890 -> (11) 3456-7890
}
```

---

### CurrencyMask

```kotlin
import br.com.codecacto.kmplib.mask.CurrencyVisualTransformation
import br.com.codecacto.kmplib.mask.parseCurrencyToLong
import br.com.codecacto.kmplib.mask.formatCurrency

@Composable
fun PriceField() {
    var priceInCents by remember { mutableStateOf(0L) }

    TextField(
        value = priceInCents.toString(),
        onValueChange = { newValue ->
            priceInCents = newValue.filter { it.isDigit() }.toLongOrNull() ?: 0L
        },
        visualTransformation = CurrencyVisualTransformation(
            currencySymbol = "R$",
            decimalSeparator = ',',
            thousandsSeparator = '.'
        ),
        label = { Text("Preço") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    // Usuário digita: 19990
    // Exibe: R$ 199,90
}

// Funções auxiliares
val cents = parseCurrencyToLong("R$ 199,90") // 19990
val formatted = formatCurrency(19990L)        // "R$ 199,90"

// Personalizado
val usd = formatCurrency(
    valueInCents = 19990L,
    currencySymbol = "$",
    decimalSeparator = '.',
    thousandsSeparator = ','
) // "$ 199.90"
```

---

### CepMask

```kotlin
import br.com.codecacto.kmplib.mask.CepVisualTransformation

@Composable
fun CepField() {
    var cep by remember { mutableStateOf("") }

    TextField(
        value = cep,
        onValueChange = { newValue ->
            cep = newValue.filter { it.isDigit() }.take(8)
        },
        visualTransformation = CepVisualTransformation(),
        label = { Text("CEP") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    // Usuário digita: 01310100
    // Exibe: 01310-100
}
```

---

## Dados Brasileiros

### BrazilianStates

Lista completa dos estados brasileiros com informações.

```kotlin
import br.com.codecacto.kmplib.brdata.BrazilianStates
import br.com.codecacto.kmplib.brdata.BrazilianState
import br.com.codecacto.kmplib.brdata.Region

// Obter todos os estados
val allStates: List<BrazilianState> = BrazilianStates.all

// Buscar por sigla
val sp: BrazilianState? = BrazilianStates.findByAbbreviation("SP")
// BrazilianState(code="35", abbreviation="SP", name="São Paulo", region=SUDESTE)

// Buscar por nome
val rj: BrazilianState? = BrazilianStates.findByName("Rio de Janeiro")

// Buscar por código IBGE
val mg: BrazilianState? = BrazilianStates.findByCode("31")

// Filtrar por região
val sulStates = BrazilianStates.byRegion(Region.SUL)
// [Paraná, Santa Catarina, Rio Grande do Sul]

// Obter lista de siglas (útil para dropdowns)
val abbreviations: List<String> = BrazilianStates.abbreviations
// ["AC", "AL", "AP", "AM", "BA", ...]

// Obter lista de nomes
val names: List<String> = BrazilianStates.names
// ["Acre", "Alagoas", "Amapá", ...]

// Usar em Compose
@Composable
fun StateDropdown() {
    var selectedState by remember { mutableStateOf<BrazilianState?>(null) }

    DropdownMenu(...) {
        BrazilianStates.all.forEach { state ->
            DropdownMenuItem(
                text = { Text("${state.name} (${state.abbreviation})") },
                onClick = { selectedState = state }
            )
        }
    }
}
```

#### Estrutura do BrazilianState:
```kotlin
data class BrazilianState(
    val name: String,         // "São Paulo"
    val abbreviation: String, // "SP"
    val region: Region,       // Region.SOUTHEAST
    val ibgeCode: Int         // 35
)

enum class Region {
    NORTH,      // Norte
    NORTHEAST,  // Nordeste
    MIDWEST,    // Centro-Oeste
    SOUTHEAST,  // Sudeste
    SOUTH       // Sul
}
```

---

### BrazilianCities

Lista completa de municípios brasileiros organizados por estado.

```kotlin
import br.com.codecacto.kmplib.brdata.BrazilianCities
import br.com.codecacto.kmplib.brdata.City

// Obter todos os municípios
val allCities: List<City> = BrazilianCities.all
println("Total de municípios: ${BrazilianCities.count}")

// Buscar município por código IBGE
val saoPaulo: City? = BrazilianCities.findByCode("3550308")
// City(ibgeCode="3550308", name="São Paulo", stateCode="35")

// Buscar município por nome
val campinas: City? = BrazilianCities.findByName("Campinas")

// Obter todos os municípios de um estado (por código IBGE)
val citiesSP = BrazilianCities.getByState("35")

// Obter municípios de um estado (por sigla)
val citiesRJ = BrazilianCities.getByState("RJ")

// Buscar municípios por nome parcial
val results = BrazilianCities.search("Santos", limit = 10)
// Retorna até 10 municípios que contenham "Santos" no nome

// Busca sem limite
val allSantos = BrazilianCities.search("Santos")

// Obter apenas os nomes dos municípios de um estado
val cityNames = BrazilianCities.getCityNames("SP")
// ["Campinas", "Guarulhos", "Santo André", ...]

// Contar municípios por estado
val count = BrazilianCities.countByState("SP")
println("São Paulo tem $count municípios")
```

#### Estrutura do City:

```kotlin
data class City(
    val ibgeCode: String,    // Código IBGE (7 dígitos)
    val name: String,        // Nome do município
    val stateCode: String    // Código IBGE do estado (2 dígitos)
) {
    val state: BrazilianState?  // Estado deste município
    val fullName: String        // "Cidade - UF"
}

// Exemplo de uso
val city = BrazilianCities.findByName("Rio de Janeiro")
println(city?.fullName)      // "Rio de Janeiro - RJ"
println(city?.state?.name)   // "Rio de Janeiro"
```

#### Integração com BrazilianStates:

```kotlin
// Obter cidade e estado juntos
val city = BrazilianCities.findByName("Belo Horizonte")
city?.let {
    println("Cidade: ${it.name}")
    println("Estado: ${it.state?.name}")
    println("Região: ${it.state?.region}")
    println("Código IBGE: ${it.ibgeCode}")
}

// Output:
// Cidade: Belo Horizonte
// Estado: Minas Gerais
// Região: SOUTHEAST
// Código IBGE: 3106200
```

#### Uso em Compose (Dropdown de cidades):

```kotlin
@Composable
fun CitySelector() {
    var selectedState by remember { mutableStateOf<BrazilianState?>(null) }
    var selectedCity by remember { mutableStateOf<City?>(null) }

    Column {
        // Dropdown de estados
        StateDropdown(
            selectedState = selectedState,
            onStateSelected = { state ->
                selectedState = state
                selectedCity = null
            }
        )

        // Dropdown de cidades (somente se estado selecionado)
        selectedState?.let { state ->
            val cities = BrazilianCities.getByState(state.code)

            CityDropdown(
                cities = cities,
                selectedCity = selectedCity,
                onCitySelected = { city ->
                    selectedCity = city
                }
            )
        }

        // Exibir seleção
        selectedCity?.let { city ->
            Text("Selecionado: ${city.fullName}")
        }
    }
}
```

#### Busca com autocomplete:

```kotlin
@Composable
fun CitySearchField() {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<City>>(emptyList()) }

    Column {
        AppTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                suggestions = if (newQuery.length >= 2) {
                    BrazilianCities.search(newQuery, limit = 10)
                } else {
                    emptyList()
                }
            },
            label = "Buscar cidade",
            placeholder = "Digite o nome da cidade..."
        )

        // Exibir sugestões
        suggestions.forEach { city ->
            Text(
                text = city.fullName,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        query = city.name
                        suggestions = emptyList()
                        // Usar cidade selecionada
                    }
                    .padding(8.dp)
            )
        }
    }
}
```

**Nota**: A biblioteca contém as principais cidades de todos os 27 estados brasileiros. Aproximadamente 200 municípios de exemplo estão incluídos, cobrindo as capitais e principais cidades de cada estado.

---

### StringExtensions

Extensões úteis para strings, especialmente para texto em português.

```kotlin
import br.com.codecacto.kmplib.brdata.removeAccents
import br.com.codecacto.kmplib.brdata.containsIgnoringAccents
import br.com.codecacto.kmplib.brdata.equalsIgnoringAccents
import br.com.codecacto.kmplib.brdata.normalizeForSearch

// Remover acentos
val text = "São Paulo é uma cidade incrível!".removeAccents()
// "Sao Paulo e uma cidade incrivel!"

// Busca ignorando acentos
val contains = "São Paulo".containsIgnoringAccents("sao")      // true
val contains2 = "São Paulo".containsIgnoringAccents("SAO")     // true (case insensitive)

// Comparação ignorando acentos
val equals = "café".equalsIgnoringAccents("cafe")              // true
val equals2 = "CAFÉ".equalsIgnoringAccents("cafe")             // true

// Normalizar para busca (remove acentos + lowercase + trim)
val normalized = "  São PAULO  ".normalizeForSearch()
// "sao paulo"

// Exemplo prático: filtro de lista
val cities = listOf("São Paulo", "Brasília", "Goiânia", "Curitiba")
val searchTerm = "sao"

val filtered = cities.filter { city ->
    city.containsIgnoringAccents(searchTerm)
}
// ["São Paulo"]
```

---

## Utilitários

### TimeUtils

Utilitários para manipulação de data/hora usando kotlinx-datetime.

```kotlin
import br.com.codecacto.kmplib.core.util.TimeUtils
import br.com.codecacto.kmplib.core.util.currentTimeMillis

// Timestamp atual em milissegundos
val now: Long = currentTimeMillis()

// Formatar timestamp
val formatted = TimeUtils.formatTimestamp(now, "dd/MM/yyyy HH:mm")
// "29/01/2026 14:30"

// Formatar data brasileira
val dateBr = TimeUtils.formatDateBrazilian(now)
// "29/01/2026"

// Formatar data/hora brasileira
val dateTimeBr = TimeUtils.formatDateTimeBrazilian(now)
// "29/01/2026 14:30"

// Tempo relativo (estilo "há X minutos")
val relative = TimeUtils.getRelativeTime(now - 3600000) // 1 hora atrás
// "há 1 hora"

val relative2 = TimeUtils.getRelativeTime(now - 86400000) // 1 dia atrás
// "há 1 dia"

// Verificar se é hoje
val isToday = TimeUtils.isToday(now) // true

// Verificar se é ontem
val wasYesterday = TimeUtils.isYesterday(now - 86400000) // true

// Início do dia (00:00:00)
val startOfDay = TimeUtils.startOfDay(now)

// Fim do dia (23:59:59.999)
val endOfDay = TimeUtils.endOfDay(now)

// Adicionar/subtrair dias
val nextWeek = TimeUtils.addDays(now, 7)
val lastWeek = TimeUtils.addDays(now, -7)
```

---

### AppLogger

Logger multiplataforma (Android: Logcat, iOS: NSLog/print).

```kotlin
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.Level

// Níveis de log
AppLogger.d("MinhaTag", "Mensagem de debug")
AppLogger.i("MinhaTag", "Mensagem informativa")
AppLogger.w("MinhaTag", "Mensagem de aviso")
AppLogger.e("MinhaTag", "Mensagem de erro")

// Com exceção
try {
    // código
} catch (e: Exception) {
    AppLogger.e("MinhaTag", "Erro ao processar", e)
}

// Configurar nível mínimo de log (em produção)
AppLogger.setMinLevel(Level.WARN) // Só mostra WARN e ERROR
```

---

# Fase 2 - Firebase

## Autenticação

### AuthRepository

Gerencia autenticação com Firebase Auth (email, Google, Apple).

```kotlin
import br.com.codecacto.kmplib.firebase.auth.AuthRepository
import br.com.codecacto.kmplib.firebase.auth.User

val authRepository = AuthRepository()

// ========== ESTADO DO USUÁRIO ==========

// Usuário atual (null se não logado)
val currentUser: User? = authRepository.currentUser

// Observar mudanças no estado de autenticação
authRepository.currentUser.collect { user ->
    if (user != null) {
        println("Logado como: ${user.email}")
        println("Provider: ${user.providerId}")
    } else {
        println("Não logado")
    }
}

// ========== LOGIN COM EMAIL ==========

// Criar conta
val result = authRepository.signUpWithEmail(
    email = "usuario@exemplo.com",
    password = "SenhaSegura123!"
)
result.onSuccess { user ->
    println("Conta criada: ${user.uid}")
}.onFailure { error ->
    println("Erro: ${error.message}")
}

// Login
val loginResult = authRepository.signInWithEmail(
    email = "usuario@exemplo.com",
    password = "SenhaSegura123!"
)

// Recuperar senha
val resetResult = authRepository.sendPasswordResetEmail("usuario@exemplo.com")
resetResult.onSuccess {
    println("Email de recuperação enviado")
}

// ========== LOGIN COM GOOGLE ==========

// Android: precisa do idToken do Google Sign-In
// Obtenha o idToken usando a biblioteca Google Sign-In do Android

val googleResult = authRepository.signInWithGoogle(
    idToken = "eyJhbGciOiJSUzI1NiIs..." // Token do Google
)
googleResult.onSuccess { user ->
    println("Logado com Google: ${user.displayName}")
}

// ========== LOGIN COM APPLE ==========

// iOS: precisa do identityToken e nonce do Apple Sign-In
// Android: precisa do identityToken do Apple Sign-In (via web)

val appleResult = authRepository.signInWithApple(
    identityToken = "eyJraWQiOiJX...",
    nonce = "random_nonce_string" // Mesmo nonce usado na requisição
)
appleResult.onSuccess { user ->
    println("Logado com Apple: ${user.email}")
}

// ========== LOGOUT ==========

authRepository.signOut()

// ========== DELETAR CONTA ==========

val deleteResult = authRepository.deleteAccount()
deleteResult.onSuccess {
    println("Conta deletada")
}.onFailure { error ->
    // Pode precisar reautenticar se a sessão for antiga
    println("Erro: ${error.message}")
}

```

#### Estrutura do User:
```kotlin
data class User(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isEmailVerified: Boolean,
    val provider: AuthProvider
)

enum class AuthProvider {
    EMAIL,
    GOOGLE,
    APPLE,
    UNKNOWN
}
```

---

## Firestore

### FirestoreService

Serviço completo para operações no Firestore.

#### Configuração do Modelo

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "",
    val name: String,
    val email: String,
    val age: Int,
    val createdAt: Long = 0L,
    val tags: List<String> = emptyList()
)

@Serializable
data class Post(
    val id: String = "",
    val userId: String,
    val title: String,
    val content: String,
    val likes: Int = 0,
    val createdAt: Long = 0L
)
```

#### Operações CRUD

```kotlin
import br.com.codecacto.kmplib.firebase.firestore.FirestoreService
import br.com.codecacto.kmplib.firebase.firestore.QueryFilter

val firestoreService = FirestoreService()

// ========== CREATE ==========

// Criar documento com ID específico
val user = User(
    id = "user123",
    name = "João Silva",
    to = "joao@exemplo.com",
    age = 30,
    createdAt = currentTimeMillis()
)

val createResult = firestoreService.setDocument(
    collection = "users",
    documentId = "user123",
    data = user,
    serializer = User.serializer()
)

createResult.onSuccess {
    println("Usuário criado!")
}.onFailure { error ->
    println("Erro: ${error.message}")
}

// Criar com merge (atualiza apenas campos enviados)
firestoreService.setDocument(
    collection = "users",
    documentId = "user123",
    data = mapOf("age" to 31), // Só atualiza idade
    merge = true
)

// Criar documento com ID automático
val post = Post(
    userId = "user123",
    title = "Meu primeiro post",
    content = "Conteúdo do post...",
    createdAt = currentTimeMillis()
)

val addResult = firestoreService.addDocument(
    collection = "posts",
    data = post,
    serializer = Post.serializer()
)

addResult.onSuccess { generatedId ->
    println("Post criado com ID: $generatedId")
}

// ========== READ ==========

// Ler documento único
val readResult = firestoreService.getDocument<User>(
    collection = "users",
    documentId = "user123",
    deserializer = User.serializer()
)

readResult.onSuccess { user ->
    if (user != null) {
        println("Nome: ${user.name}")
    } else {
        println("Usuário não encontrado")
    }
}

// Ler todos os documentos de uma coleção
val allUsersResult = firestoreService.getCollection<User>(
    collection = "users",
    deserializer = User.serializer()
)

allUsersResult.onSuccess { users ->
    users.forEach { user ->
        println("${user.name} - ${user.email}")
    }
}

// ========== UPDATE ==========

// Atualizar campos específicos
val updateResult = firestoreService.updateDocument(
    collection = "users",
    documentId = "user123",
    updates = mapOf(
        "name" to "João Santos",
        "age" to 31
    )
)

// Atualizar com timestamp do servidor
firestoreService.updateWithServerTimestamp(
    collection = "users",
    documentId = "user123",
    field = "updatedAt"
)

// ========== DELETE ==========

val deleteResult = firestoreService.deleteDocument(
    collection = "users",
    documentId = "user123"
)
```

#### Queries

```kotlin
import br.com.codecacto.kmplib.firebase.firestore.QueryFilter

// Query simples com filtro
val activeUsersResult = firestoreService.query<User>(
    collection = "users",
    deserializer = User.serializer(),
    filters = listOf(
        QueryFilter.EqualTo("status", "active")
    )
)

// Query com múltiplos filtros
val filteredResult = firestoreService.query<User>(
    collection = "users",
    deserializer = User.serializer(),
    filters = listOf(
        QueryFilter.EqualTo("status", "active"),
        QueryFilter.GreaterThan("age", 18),
        QueryFilter.LessThanOrEqualTo("age", 65)
    ),
    orderBy = "createdAt",
    descending = true,
    limit = 20
)

// Filtros disponíveis
QueryFilter.EqualTo("field", value)           // field == value
QueryFilter.NotEqualTo("field", value)        // field != value
QueryFilter.LessThan("field", value)          // field < value
QueryFilter.LessThanOrEqualTo("field", value) // field <= value
QueryFilter.GreaterThan("field", value)       // field > value
QueryFilter.GreaterThanOrEqualTo("field", value) // field >= value
QueryFilter.ArrayContains("field", value)     // field array contém value
QueryFilter.In("field", listOf(v1, v2, v3))   // field está em [v1, v2, v3]
```

#### Observação em Tempo Real

```kotlin
// Observar documento
firestoreService.observeDocument<User>(
    collection = "users",
    documentId = "user123",
    deserializer = User.serializer()
).collect { user ->
    if (user != null) {
        println("Atualização: ${user.name}")
    }
}

// Observar coleção
firestoreService.observeCollection<User>(
    collection = "users",
    deserializer = User.serializer()
).collect { users ->
    println("Total de usuários: ${users.size}")
}

// Observar query
firestoreService.observeQuery<Post>(
    collection = "posts",
    deserializer = Post.serializer(),
    filters = listOf(QueryFilter.EqualTo("userId", "user123")),
    orderBy = "createdAt",
    descending = true,
    limit = 10
).collect { posts ->
    println("Posts do usuário: ${posts.size}")
}
```

#### Subcoleções

```kotlin
// Adicionar documento em subcoleção
// users/{userId}/posts/{postId}

val postResult = firestoreService.addToSubcollection(
    parentCollection = "users",
    parentId = "user123",
    subcollectionName = "posts",
    data = post,
    serializer = Post.serializer()
)

// Observar subcoleção
firestoreService.observeSubcollection<Post>(
    parentCollection = "users",
    parentId = "user123",
    subcollection = "posts",
    deserializer = Post.serializer()
).collect { posts ->
    println("Posts do usuário: ${posts.size}")
}

// Acesso direto à referência da subcoleção
val subcollectionRef = firestoreService.subcollection(
    parentCollection = "users",
    parentId = "user123",
    subcollection = "posts"
)
```

#### Operações em Batch

```kotlin
// Executar múltiplas operações atomicamente (até 500)
val batchResult = firestoreService.batch {
    // this = BatchScope

    // Criar/atualizar
    set("users", "user1", user1, User.serializer())
    set("users", "user2", user2, User.serializer())

    // Atualizar campos
    update("users", "user3", mapOf("status" to "inactive"))

    // Deletar
    delete("users", "user4")
}

batchResult.onSuccess {
    println("Batch executado com sucesso!")
}.onFailure { error ->
    println("Erro no batch: ${error.message}")
}
```

---

## Storage

### StorageService

Serviço para Firebase Storage (download/delete).

> **Nota**: Upload nao esta implementado na lib. Use a referencia do Storage e APIs platform-specific quando precisar enviar arquivos.
```kotlin
import br.com.codecacto.kmplib.firebase.storage.StorageService
import br.com.codecacto.kmplib.firebase.storage.StorageException

val storageService = StorageService()

// ========== DOWNLOAD URL ==========

// Obter URL de download de um arquivo
val urlResult = storageService.getDownloadUrl("users/user123/avatar.jpg")

urlResult.onSuccess { url ->
    println("URL: $url")
    // Use a URL para carregar a imagem (Coil, Glide, etc.)
}.onFailure { error ->
    when (error) {
        is StorageException.FileNotFound -> println("Arquivo não existe")
        is StorageException.Unauthorized -> println("Sem permissão")
        else -> println("Erro: ${error.message}")
    }
}

// ========== VERIFICAR EXISTÊNCIA ==========

val exists = storageService.exists("users/user123/avatar.jpg")
if (exists) {
    println("Arquivo existe")
}

// ========== DELETAR ==========

// Deletar arquivo único
val deleteResult = storageService.deleteFile("users/user123/avatar.jpg")

deleteResult.onSuccess {
    println("Arquivo deletado")
}

// Deletar múltiplos arquivos
val deleteMultipleResult = storageService.deleteFiles(
    listOf(
        "users/user123/photo1.jpg",
        "users/user123/photo2.jpg",
        "users/user123/photo3.jpg"
    )
)

deleteMultipleResult.onSuccess { count ->
    println("$count arquivos deletados")
}

// ========== REFERÊNCIA DIRETA ==========

// Para operacoes mais avancadas (ex: upload), use a referencia e APIs platform-specific
val reference = storageService.reference("users/user123/documents")

// ========== MIME TYPES ==========

// Obter MIME type baseado na extensão
val mimeType = storageService.getMimeType("documento.pdf")
// "application/pdf"

val mimeType2 = storageService.getMimeType("foto.jpg")
// "image/jpeg"
```

#### Tipos de exceção:
```kotlin
sealed class StorageException {
    FileNotFound    // Arquivo não existe
    Unauthorized    // Sem permissão
    Cancelled       // Operação cancelada
    QuotaExceeded   // Limite de armazenamento
    NetworkError    // Erro de conexão
    Unknown         // Erro desconhecido
}
```

---

# Fase 3 - Platform

## URL Launcher

Abre URLs, apps externos, mapas, WhatsApp, etc.

```kotlin
import br.com.codecacto.kmplib.platform.getUrlLauncher

val urlLauncher = getUrlLauncher()

// ========== ABRIR URL ==========

urlLauncher.openUrl("https://www.exemplo.com")

// ========== EMAIL ==========

// Email simples
urlLauncher.openEmail("contato@exemplo.com")

// Email com assunto e corpo
urlLauncher.openEmail(
    to = "suporte@exemplo.com",
    subject = "Ajuda com o app",
    body = "Olá, preciso de ajuda com..."
)

// ========== TELEFONE ==========

// Abrir discador
urlLauncher.openPhone("11987654321")
urlLauncher.openPhone("+5511987654321")

// ========== WHATSAPP ==========

// Abrir conversa (número com código do país, sem +)
urlLauncher.openWhatsApp("5511987654321")

// Com mensagem pré-definida
urlLauncher.openWhatsApp(
    phone = "5511987654321",
    message = "Olá! Vi seu anúncio e gostaria de mais informações."
)

// ========== MAPAS ==========

// Abrir localização por coordenadas
urlLauncher.openMap(
    latitude = -23.550520,
    longitude = -46.633308
)

// Com label/nome do local
urlLauncher.openMap(
    latitude = -23.550520,
    longitude = -46.633308,
    label = "Praça da Sé, São Paulo"
)

// Abrir por endereço
urlLauncher.openMapByAddress("Av. Paulista, 1000, São Paulo, SP")

// ========== LOJA DE APPS ==========

// Abrir página do app na loja
urlLauncher.openStorePage(
    androidPackageName = "com.exemplo.meuapp",  // Para Play Store
    iosAppId = "123456789"                       // Para App Store
)

// ========== CONFIGURAÇÕES ==========

// Abrir configurações do app (permissões)
urlLauncher.openAppSettings()
```

---

## Share Handler

Compartilha texto, imagens e arquivos.

> **Android**: configure um FileProvider no app consumidor e exponha cache/shared_files em paths.xml.

```kotlin
import br.com.codecacto.kmplib.platform.getShareHandler

val shareHandler = getShareHandler()

// ========== COMPARTILHAR TEXTO ==========

shareHandler.shareText(
    text = "Confira este app incrível!",
    title = "Compartilhar" // Título do chooser (Android)
)

// ========== COMPARTILHAR IMAGEM ==========

// A partir de ByteArray
val imageBytes: ByteArray = // ... bytes da imagem
shareHandler.shareImage(
    imageData = imageBytes,
    fileName = "foto.jpg",
    title = "Compartilhar foto"
)

// ========== COMPARTILHAR ARQUIVO ==========

shareHandler.shareFile(
    filePath = "/path/to/document.pdf",
    mimeType = "application/pdf",
    title = "Compartilhar documento"
)

// MIME types comuns:
// - "image/jpeg", "image/png" - Imagens
// - "application/pdf" - PDF
// - "text/plain" - Texto
// - "application/zip" - ZIP
// - "*/*" - Qualquer tipo
```

---

## Biometric Auth

Autenticação biométrica (impressão digital, Face ID, etc.).

```kotlin
import br.com.codecacto.kmplib.platform.getBiometricAuth
import br.com.codecacto.kmplib.platform.BiometricType
import br.com.codecacto.kmplib.platform.BiometricResult

val biometricAuth = getBiometricAuth()

// ========== VERIFICAR DISPONIBILIDADE ==========

val isAvailable = biometricAuth.isAvailable()
if (!isAvailable) {
    println("Biometria não disponível neste dispositivo")
    return
}

// ========== VERIFICAR TIPO ==========

val biometricType = biometricAuth.getBiometricType()
when (biometricType) {
    BiometricType.FINGERPRINT -> println("Impressão digital disponível")
    BiometricType.FACE -> println("Reconhecimento facial disponível")
    BiometricType.IRIS -> println("Scanner de íris disponível")
    BiometricType.MULTIPLE -> println("Múltiplos tipos disponíveis")
    BiometricType.NONE -> println("Nenhuma biometria configurada")
}

// ========== AUTENTICAR ==========

biometricAuth.authenticate(
    title = "Autenticação necessária",
    subtitle = "Use sua biometria para continuar",
    negativeButtonText = "Cancelar", // Texto do botão de cancelar (Android)
    onResult = { result ->
        when (result) {
            BiometricResult.SUCCESS -> {
                println("Autenticação bem-sucedida!")
                // Prosseguir com a ação protegida
            }
            BiometricResult.CANCELLED -> {
                println("Usuário cancelou")
            }
            BiometricResult.ERROR -> {
                println("Erro na autenticação")
            }
            BiometricResult.NOT_AVAILABLE -> {
                println("Biometria não disponível")
            }
        }
    }
)
```

#### Exemplo de uso completo:

```kotlin
@Composable
fun SecureScreen() {
    val biometricAuth = remember { getBiometricAuth() }
    var isAuthenticated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (biometricAuth.isAvailable()) {
            biometricAuth.authenticate(
                title = "Área restrita",
                subtitle = "Confirme sua identidade",
                negativeButtonText = "Usar senha",
                onResult = { result ->
                    isAuthenticated = result == BiometricResult.SUCCESS
                }
            )
        } else {
            // Fallback para senha
        }
    }

    if (isAuthenticated) {
        // Mostrar conteúdo protegido
        SecureContent()
    } else {
        // Mostrar tela de autenticação
        AuthenticationPrompt()
    }
}
```

---

## Notification Scheduler

Agenda e exibe notificações locais.

### Android - Configuração adicional

```xml
<!-- AndroidManifest.xml -->
<manifest>
    <!-- Permissões necessárias -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application>
        <!-- Receiver para notificações agendadas -->
        <receiver
            android:name="br.com.codecacto.kmplib.platform.NotificationReceiver"
            android:exported="false" />

        <!-- Receiver para reagendar após reboot -->
        <receiver
            android:name="br.com.codecacto.kmplib.platform.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```


### Android - Permissão (Android 13+)

Para solicitar a permissão via `requestPermission()`, a Activity deve repassar o resultado:

```kotlin
override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    NotificationSchedulerHolder.handlePermissionResult(requestCode, grantResults)
}
```
### Uso

```kotlin
import br.com.codecacto.kmplib.platform.getNotificationScheduler
import br.com.codecacto.kmplib.core.util.currentTimeMillis

val notificationScheduler = getNotificationScheduler()

// ========== NOTIFICAÇÃO IMEDIATA ==========

notificationScheduler.showNotificationNow(
    id = 1,
    title = "Nova mensagem",
    body = "Você recebeu uma nova mensagem de João",
    data = mapOf(
        "type" to "message",
        "senderId" to "user123"
    )
)

// ========== AGENDAR NOTIFICAÇÃO ==========

// Agendar para daqui 1 hora
val oneHourFromNow = currentTimeMillis() + (60 * 60 * 1000)

notificationScheduler.scheduleNotification(
    id = 100,
    title = "Lembrete",
    body = "Não esqueça de beber água!",
    triggerAtMillis = oneHourFromNow,
    data = mapOf("type" to "reminder")
)

// Agendar para data/hora específica
val scheduledTime = TimeUtils.parseDate("2026-01-30 09:00", "yyyy-MM-dd HH:mm")

notificationScheduler.scheduleNotification(
    id = 101,
    title = "Reunião",
    body = "Sua reunião começa em 15 minutos",
    triggerAtMillis = scheduledTime
)

// ========== CANCELAR NOTIFICAÇÃO ==========

// Cancelar por ID
notificationScheduler.cancelNotification(100)

// ========== NOTIFICAÇÃO COM AÇÃO ==========

// Os dados (data) são passados para a Activity/ViewController
// quando o usuário toca na notificação

// Android: receba os dados em onNewIntent
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    val type = intent?.getStringExtra("type")
    val senderId = intent?.getStringExtra("senderId")
    // Navegar para a tela apropriada
}

// iOS: implemente UNUserNotificationCenterDelegate
func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    didReceive response: UNNotificationResponse
) {
    let userInfo = response.notification.request.content.userInfo
    let type = userInfo["type"] as? String
    // Navegar para a tela apropriada
}
```

#### Exemplo de lembretes diários:

```kotlin
fun scheduleWaterReminders() {
    val scheduler = getNotificationScheduler()
    val now = currentTimeMillis()

    // Agendar lembretes a cada 2 horas (8h às 20h)
    val hours = listOf(8, 10, 12, 14, 16, 18, 20)

    hours.forEachIndexed { index, hour ->
        val triggerTime = TimeUtils.setTime(now, hour, 0, 0)

        // Se já passou, agendar para amanhã
        val finalTime = if (triggerTime < now) {
            TimeUtils.addDays(triggerTime, 1)
        } else {
            triggerTime
        }

        scheduler.scheduleNotification(
            id = 1000 + index,
            title = "Hora de beber água!",
            body = "Mantenha-se hidratado. Beba um copo d'água agora.",
            triggerAtMillis = finalTime
        )
    }
}
```

---

# Fase 4 - UI Components

Componentes de interface genéricos e totalmente customizáveis para Jetpack Compose Multiplatform.

## LoginScreen (Stateless)

> **IMPORTANTE**: A partir da versão 2.0.0, a `GenericLoginScreen` foi refatorada para ser **stateless** e aceitar estado externo via ViewModel. Para migração da versão 1.0.0, veja [BREAKING_CHANGES.md](BREAKING_CHANGES.md).

Tela de login completa e configurável com suporte a múltiplos métodos de autenticação usando arquitetura MVI.

### Características

- **Stateless**: Aceita estado externo via `LoginState`
- **MVI Pattern**: State/Action/Effect para gerenciamento de estado
- **Totalmente Customizável**: Cores, textos, logo, métodos de autenticação
- **i18n**: Textos como `@Composable` lambdas (suporta `stringResource`)
- **Múltiplos Métodos**: Email/Senha, Google, Apple
- **Social Login**: Callbacks com tokens (idToken, accessToken)
- **Type-safe**: Ações tipadas com sealed interfaces
- **Material 3**: Usa componentes Material Design 3

### Arquitetura MVI

```kotlin
// 1. State - Estado completo da tela
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isAppleLoading: Boolean = false,
    val errorMessage: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null
)

// 2. Action - Todas as ações possíveis
sealed interface LoginAction {
    sealed interface Input : LoginAction {
        data class EmailChanged(val email: String) : Input
        data class PasswordChanged(val password: String) : Input
    }
    sealed interface Click : LoginAction {
        data object Login : Click
        data object GoogleLogin : Click
        data object Register : Click
    }
}

// 3. Effect - Efeitos colaterais (navegação, etc)
sealed interface LoginEffect {
    sealed interface Navigate : LoginEffect {
        data object ToHome : Navigate
        data object ToRegister : Navigate
    }
    sealed interface SocialLogin : LoginEffect {
        data object LaunchGoogle : SocialLogin
    }
}
```

### Exemplo Básico

```kotlin
@Composable
fun LoginRoute(
    navController: NavController,
    viewModel: LoginViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Observar efeitos
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LoginEffect.Navigate.ToHome -> {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
                is LoginEffect.Navigate.ToRegister -> {
                    navController.navigate("register")
                }
            }
        }
    }

    LoginScreen(
        state = state,
        onAction = viewModel::onAction
    )
}
```

### Exemplo com Customização e i18n

```kotlin
@Composable
fun CustomLoginScreen(
    viewModel: LoginViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LoginScreen(
        state = state,
        onAction = viewModel::onAction,

        // Logo customizado
        logo = painterResource(Res.drawable.app_logo),

        // Cores customizadas
        colors = LoginColors(
            primary = Color(0xFFF97316),      // Laranja
            onPrimary = Color.White,
            background = Color(0xFFF5F5F5),
            textPrimary = Color(0xFF1A1A1A)
        ),

        // Textos com i18n (stringResource)
        texts = LoginTexts(
            title = { stringResource(Res.string.login_title) },
            emailLabel = { stringResource(Res.string.email) },
            passwordLabel = { stringResource(Res.string.password) },
            loginButton = { stringResource(Res.string.sign_in) },
            forgotPassword = { stringResource(Res.string.forgot_password) },
            registerPrompt = { stringResource(Res.string.register_prompt) },
            registerLink = { stringResource(Res.string.register_link) }
        ),

        // Métodos de autenticação
        authMethods = AuthMethods(
            emailPassword = true,
            google = true,
            apple = true
        ),

        // URLs de termos
        termsUrl = "https://myapp.com/terms",
        privacyUrl = "https://myapp.com/privacy"
    )
}
```

### ViewModel Completo

```kotlin
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    private val _effect = Channel<LoginEffect>()
    val effect = _effect.receiveAsFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.Input.EmailChanged -> {
                _state.update { it.copy(
                    email = action.email,
                    emailError = null
                ) }
            }

            is LoginAction.Input.PasswordChanged -> {
                _state.update { it.copy(
                    password = action.password,
                    passwordError = null
                ) }
            }

            is LoginAction.Click.Login -> {
                login()
            }

            is LoginAction.Click.GoogleLogin -> {
                viewModelScope.launch {
                    _effect.send(LoginEffect.SocialLogin.LaunchGoogle)
                }
            }

            is LoginAction.Click.Register -> {
                viewModelScope.launch {
                    _effect.send(LoginEffect.Navigate.ToRegister)
                }
            }
        }
    }

    private fun login() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            authRepository.signInWithEmail(
                _state.value.email,
                _state.value.password
            ).onSuccess {
                _effect.send(LoginEffect.Navigate.ToHome)
            }.onFailure { error ->
                _state.update { it.copy(
                    isLoading = false,
                    errorMessage = error.message
                ) }
            }
        }
    }

    fun handleGoogleSignIn(idToken: String, accessToken: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isGoogleLoading = true) }

            authRepository.signInWithGoogle(idToken, accessToken)
                .onSuccess {
                    _effect.send(LoginEffect.Navigate.ToHome)
                }
                .onFailure { error ->
                    _state.update { it.copy(
                        isGoogleLoading = false,
                        errorMessage = error.message
                    ) }
                }
        }
    }
}
```

### Social Login com Tokens

```kotlin
@Composable
fun LoginWithSocialAuth(
    viewModel: LoginViewModel,
    googleHandler: GoogleLoginHandler
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LoginEffect.SocialLogin.LaunchGoogle -> {
                    // Lançar fluxo de Google Sign-In
                    googleHandler.signIn(
                        onSuccess = { result ->
                            // Passar tokens para o ViewModel/Backend
                            viewModel.handleGoogleSignIn(
                                idToken = result.idToken,
                                accessToken = result.accessToken
                            )
                        },
                        onError = { error ->
                            viewModel.showError(error)
                        }
                    )
                }
            }
        }
    }

    LoginScreen(state = state, onAction = viewModel::onAction)
}

// Interface para implementação específica de plataforma
interface GoogleLoginHandler {
    suspend fun signIn(
        onSuccess: (GoogleSignInResult) -> Unit,
        onError: (String) -> Unit
    )
}

data class GoogleSignInResult(
    val idToken: String,
    val accessToken: String?
)
```

### LoginColors

```kotlin
data class LoginColors(
    val primary: Color = Color(0xFF6C63FF),
    val secondary: Color? = null,
    val onPrimary: Color = Color.White,
    val background: Color = Color(0xFFF5F5F5),
    val surface: Color = Color.White,
    val error: Color = Color(0xFFD32F2F),
    val textPrimary: Color = Color(0xFF1A1A1A),
    val textSecondary: Color = Color(0xFF757575),
    val border: Color = Color(0xFFE0E0E0)
)

// Temas prontos
val locadoraColors = LoginColors(
    primary = Color(0xFFF97316)  // Laranja
)

val advogadoColors = LoginColors(
    primary = Color(0xFF10B981)  // Verde Esmeralda
)
```

### LoginTexts (i18n)

```kotlin
data class LoginTexts(
    val title: @Composable (() -> String)? = null,
    val emailLabel: @Composable () -> String = { "Email" },
    val emailPlaceholder: @Composable () -> String = { "seu@email.com" },
    val passwordLabel: @Composable () -> String = { "Senha" },
    val passwordPlaceholder: @Composable () -> String = { "••••••••" },
    val loginButton: @Composable () -> String = { "Entrar" },
    val forgotPassword: @Composable () -> String = { "Esqueci minha senha" },
    val registerPrompt: @Composable () -> String = { "Não tem uma conta?" },
    val registerLink: @Composable () -> String = { "Cadastre-se" },
    val orContinueWith: @Composable () -> String = { "ou continue com" },
    val googleLogin: @Composable () -> String = { "Continuar com Google" },
    val appleLogin: @Composable () -> String = { "Continuar com Apple" }
)

// Exemplo com stringResource (i18n)
val i18nTexts = LoginTexts(
    title = { stringResource(Res.string.login_title) },
    emailLabel = { stringResource(Res.string.email) },
    loginButton = { stringResource(Res.string.sign_in) }
)
```

### AuthMethods

```kotlin
data class AuthMethods(
    val emailPassword: Boolean = true,
    val google: Boolean = false,
    val apple: Boolean = false
)
```

---

## RegisterScreen (Stateless)

Tela de registro completa com campos customizáveis.

### Exemplo Básico

```kotlin
@Composable
fun RegisterRoute(
    navController: NavController,
    viewModel: RegisterViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is RegisterEffect.Navigate.ToHome -> {
                    navController.navigate("home") {
                        popUpTo("register") { inclusive = true }
                    }
                }
            }
        }
    }

    RegisterScreen(
        state = state,
        onAction = viewModel::onAction,
        fields = RegisterFields(
            showNameField = true,
            showPhoneField = true,
            showTermsCheckbox = true
        ),
        termsUrl = "https://myapp.com/terms",
        privacyUrl = "https://myapp.com/privacy"
    )
}
```

### RegisterFields

```kotlin
data class RegisterFields(
    val showNameField: Boolean = true,
    val showPhoneField: Boolean = true,
    val showTermsCheckbox: Boolean = true,
    val phoneMask: VisualTransformation = PhoneVisualTransformation()
)
```

### RegisterState/Action

```kotlin
data class RegisterState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val acceptedTerms: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val nameError: String? = null,
    val emailError: String? = null
)

sealed interface RegisterAction {
    sealed interface Input : RegisterAction {
        data class NameChanged(val name: String) : Input
        data class EmailChanged(val email: String) : Input
        data class PhoneChanged(val phone: String) : Input
        data class PasswordChanged(val password: String) : Input
        data class ConfirmPasswordChanged(val password: String) : Input
        data class TermsAcceptedChanged(val accepted: Boolean) : Input
    }
    sealed interface Click : RegisterAction {
        data object Register : Click
        data object Login : Click
        data object Terms : Click
        data object Privacy : Click
    }
}
```

---

## Componentes Atômicos de Autenticação

Componentes individuais reutilizáveis para criação de formulários customizados.

### EmailField

```kotlin
@Composable
fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Email",
    placeholder: String = "seu@email.com",
    errorMessage: String? = null,
    enabled: Boolean = true,
    primaryColor: Color = Color(0xFF6C63FF)
)
```

### PasswordField

```kotlin
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Senha",
    errorMessage: String? = null
)
```

### NameField

```kotlin
@Composable
fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Nome completo"
)
```

### PhoneField

```kotlin
@Composable
fun PhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Telefone",
    visualTransformation: VisualTransformation = PhoneVisualTransformation()
)
```

### Links e Navegação

```kotlin
// Link "Esqueci minha senha"
@Composable
fun ForgotPasswordLink(
    onClick: () -> Unit,
    text: String = "Esqueci minha senha"
)

// Link de navegação Login/Registro
@Composable
fun AuthNavigationLink(
    promptText: String,
    linkText: String,
    onClick: () -> Unit
)

// Checkbox de termos
@Composable
fun TermsCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    termsText: String = "Termos de Uso",
    privacyText: String = "Política de Privacidade"
)

// Divider com texto
@Composable
fun OrDivider(
    text: String = "ou continue com"
)
```

### Exemplo de Formulário Customizado

```kotlin
@Composable
fun CustomLoginForm(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        EmailField(
            value = email,
            onValueChange = onEmailChange,
            primaryColor = Color(0xFF6C63FF)
        )

        Spacer(modifier = Modifier.height(16.dp))

        PasswordField(
            value = password,
            onValueChange = onPasswordChange
        )

        ForgotPasswordLink(onClick = onForgotPasswordClick)

        AppButton(
            text = "Entrar",
            onClick = onLoginClick
        )
    }
}
```

---

## Dialogs

### ConfirmationDialog

Dialog de confirmação genérico.

```kotlin
@Composable
fun DeleteAccountConfirmation() {
    var showDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Button(onClick = { showDialog = true }) {
        Text("Excluir Conta")
    }

    ConfirmationDialog(
        show = showDialog,
        title = "Excluir Conta",
        message = "Tem certeza que deseja excluir sua conta? Esta ação não pode ser desfeita.",
        confirmText = "Excluir",
        cancelText = "Cancelar",
        onConfirm = {
            isLoading = true
            // Executar exclusão
        },
        onDismiss = {
            showDialog = false
        },
        isLoading = isLoading,
        primaryColor = Color(0xFFD32F2F),  // Vermelho para ação destrutiva
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFD32F2F)
            )
        }
    )
}
```

### InputDialog

Dialog com campo de entrada de texto.

```kotlin
@Composable
fun ChangeEmailDialog() {
    var showDialog by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    InputDialog(
        show = showDialog,
        title = "Alterar Email",
        message = "Digite seu novo endereço de email",
        textFieldValue = email,
        onTextFieldValueChange = {
            email = it
            emailError = null
        },
        textFieldLabel = "Novo email",
        textFieldPlaceholder = "novo@email.com",
        textFieldError = emailError,
        confirmText = "Alterar",
        onConfirm = {
            if (EmailValidator.isValid(email)) {
                isLoading = true
                // Alterar email
            } else {
                emailError = "Email inválido"
            }
        },
        onDismiss = {
            showDialog = false
            email = ""
            emailError = null
        },
        isLoading = isLoading,
        primaryColor = Color(0xFF6C63FF)
    )
}
```

### Dialog com senha

```kotlin
InputDialog(
    show = showDialog,
    title = "Confirmar Senha",
    message = "Digite sua senha para continuar",
    textFieldValue = password,
    onTextFieldValueChange = { password = it },
    textFieldLabel = "Senha",
    isPassword = true,  // Campo de senha com toggle
    confirmText = "Confirmar",
    onConfirm = { /* validar senha */ },
    onDismiss = { showDialog = false }
)
```

---

## Form Components

### FormContainer

Container genérico para formulários com scroll e gerenciamento de teclado.

```kotlin
@Composable
fun RegisterForm() {
    FormContainer(
        horizontalPadding = 24.dp,
        verticalPadding = 16.dp,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Criar Conta", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nome completo",
            leadingIcon = Icons.Default.Person
        )

        AppTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardType = KeyboardType.Email,
            leadingIcon = Icons.Default.Email
        )

        AppTextField(
            value = password,
            onValueChange = { password = it },
            label = "Senha",
            isPassword = true,
            leadingIcon = Icons.Default.Lock
        )

        AppButton(
            text = "Cadastrar",
            onClick = { /* criar conta */ }
        )
    }
}
```

### AppTextField

TextField customizado com estilo padronizado.

```kotlin
@Composable
fun EmailField() {
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AppTextField(
        value = email,
        onValueChange = {
            email = it
            error = if (EmailValidator.isValid(it)) null else "Email inválido"
        },
        label = "Email",
        placeholder = "seu@email.com",
        leadingIcon = Icons.Default.Email,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        errorMessage = error,
        primaryColor = Color(0xFF6C63FF),
        borderColor = Color(0xFFE0E0E0),
        labelColor = Color(0xFF9E9E9E)
    )
}
```

### AppTextField com Máscara

```kotlin
@Composable
fun PhoneField() {
    var phone by remember { mutableStateOf("") }

    AppTextField(
        value = phone,
        onValueChange = { phone = it },
        label = "Telefone",
        keyboardType = KeyboardType.Phone,
        visualTransformation = PhoneVisualTransformation(),
        leadingIcon = Icons.Default.Phone
    )
}
```

### AppTextField com Senha

```kotlin
AppTextField(
    value = password,
    onValueChange = { password = it },
    label = "Senha",
    isPassword = true,  // Adiciona toggle automático de visibilidade
    leadingIcon = Icons.Default.Lock
)
```

---

## Buttons

### AppButton

Botão primário com estado de loading.

```kotlin
@Composable
fun LoginButton() {
    var isLoading by remember { mutableStateOf(false) }

    AppButton(
        text = "Entrar",
        onClick = {
            isLoading = true
            // Executar login
        },
        isLoading = isLoading,
        enabled = email.isNotEmpty() && password.isNotEmpty(),
        primaryColor = Color(0xFF6C63FF),
        contentColor = Color.White
    )
}
```

### AppOutlinedButton

Botão outlined (secundário) com loading.

```kotlin
@Composable
fun GoogleLoginButton() {
    var isLoading by remember { mutableStateOf(false) }

    AppOutlinedButton(
        text = "Continuar com Google",
        onClick = {
            isLoading = true
            // Login com Google
        },
        isLoading = isLoading,
        primaryColor = Color(0xFF4285F4)
    )
}
```

### GoogleLoginButton

Botão de login com Google com ícone integrado.

```kotlin
@Composable
fun GoogleLogin() {
    var isLoading by remember { mutableStateOf(false) }

    GoogleLoginButton(
        text = "Continuar com Google",
        onClick = {
            isLoading = true
            // Executar login com Google
        },
        isLoading = isLoading,
        enabled = true
    )
}
```

### AppleLoginButton

Botão de login com Apple com ícone integrado.

```kotlin
@Composable
fun AppleLogin() {
    var isLoading by remember { mutableStateOf(false) }

    AppleLoginButton(
        text = "Continuar com Apple",
        onClick = {
            isLoading = true
            // Executar login com Apple
        },
        isLoading = isLoading,
        enabled = true
    )
}
```

---

## Componentes de Interface Genéricos

### AppTextField com Contador de Caracteres

TextField aprimorado com contador de caracteres e limite máximo.

```kotlin
@Composable
fun UsernameField() {
    var username by remember { mutableStateOf("") }

    AppTextField(
        value = username,
        onValueChange = { username = it },
        label = "Nome de usuário",
        placeholder = "Digite seu nome",
        maxLength = 20,
        showCharCounter = true,
        leadingIcon = Icons.Default.Person
    )
    // Exibe: "5/20" abaixo do campo
}
```

### AppTextArea

Campo de texto multilinha com contador de caracteres.

```kotlin
@Composable
fun DescriptionField() {
    var description by remember { mutableStateOf("") }

    AppTextArea(
        value = description,
        onValueChange = { description = it },
        label = "Descrição",
        placeholder = "Descreva o problema...",
        maxLength = 500,
        minLines = 3,
        maxLines = 8,
        showCharCounter = true
    )
}

// Sem limite de linhas
AppTextArea(
    value = notes,
    onValueChange = { notes = it },
    label = "Notas",
    minLines = 5,
    maxLines = null  // Expande infinitamente
)
```

### NumberField

Campo numérico com suporte a decimais e validação de limites.

```kotlin
@Composable
fun PriceField() {
    var price by remember { mutableStateOf("") }
    val priceValue = price.toDoubleFromNumberField()

    NumberField(
        value = price,
        onValueChange = { price = it },
        label = "Preço",
        allowDecimals = true,
        minValue = 0.0,
        maxValue = 999999.99
    )

    // priceValue é Double? (null se inválido)
    priceValue?.let {
        Text("Valor: R$ ${"%.2f".format(it)}")
    }
}

// Campo de quantidade (somente inteiros)
@Composable
fun QuantityField() {
    var quantity by remember { mutableStateOf("") }
    val quantityInt = quantity.toIntFromNumberField()

    NumberField(
        value = quantity,
        onValueChange = { quantity = it },
        label = "Quantidade",
        allowDecimals = false,
        minValue = 1.0,
        maxValue = 999.0
    )
}
```

#### Funções de conversão:

```kotlin
// Converte string com vírgula para Double
val value = "123,45".toDoubleFromNumberField()  // 123.45

// Converte string para Int
val quantity = "10".toIntFromNumberField()  // 10
```

### AppBadge

Badge genérico com múltiplos estilos.

```kotlin
@Composable
fun NotificationIcon() {
    BadgedBox(
        badge = {
            AppBadge(
                count = 5,
                style = BadgeStyle.CIRCULAR,
                backgroundColor = Color.Red,
                textColor = Color.White
            )
        }
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = "Notificações",
            modifier = Modifier.size(24.dp)
        )
    }
}

// Badge com texto customizado
AppBadge(
    text = "NEW",
    style = BadgeStyle.PILL,
    backgroundColor = Color(0xFF10B981)
)

// Badge apenas ponto (dot)
AppBadge(
    style = BadgeStyle.DOT,
    backgroundColor = Color.Red
)

// Limite de contagem (99+)
AppBadge(
    count = 150,
    maxCount = 99  // Exibe "99+"
)
```

#### BadgeStyle:

| Estilo | Uso |
|--------|-----|
| `CIRCULAR` | Números (1, 5, 99+) |
| `PILL` | Texto ("NEW", "PRO") |
| `DOT` | Apenas indicador visual |

### EmptyState

Estado vazio genérico para listas e telas.

```kotlin
@Composable
fun MessagesList(messages: List<Message>) {
    if (messages.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Email,
            title = "Nenhuma mensagem",
            description = "Você ainda não recebeu mensagens",
            action = {
                AppButton(
                    text = "Atualizar",
                    onClick = { /* recarregar */ }
                )
            }
        )
    } else {
        // Exibir lista
    }
}

// EmptyState em tela inteira
@Composable
fun EmptyProductsScreen() {
    FullScreenEmptyState(
        icon = Icons.Default.ShoppingCart,
        title = "Carrinho vazio",
        description = "Adicione produtos ao carrinho para continuar",
        action = {
            AppButton(
                text = "Ver produtos",
                onClick = { /* navegar */ }
            )
        }
    )
}
```

### AppDialog

Sistema de diálogos genéricos.

```kotlin
// Dialog genérico com conteúdo customizado
@Composable
fun CustomDialog() {
    var showDialog by remember { mutableStateOf(false) }

    AppDialog(
        show = showDialog,
        onDismiss = { showDialog = false },
        title = "Informações",
        icon = Icons.Default.Info
    ) {
        Text("Conteúdo customizado aqui")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Pode ter qualquer composable")

        AppButton(
            text = "OK",
            onClick = { showDialog = false }
        )
    }
}
```

### AppAlertDialog

Dialog de alerta com botões de confirmação/cancelamento.

```kotlin
@Composable
fun DeleteConfirmation() {
    var showDialog by remember { mutableStateOf(false) }

    AppAlertDialog(
        show = showDialog,
        onDismiss = { showDialog = false },
        title = "Confirmar exclusão",
        message = "Tem certeza que deseja excluir este item? Esta ação não pode ser desfeita.",
        icon = Icons.Default.Warning,
        confirmText = "Excluir",
        cancelText = "Cancelar",
        onConfirm = {
            // Executar exclusão
            showDialog = false
        },
        confirmButtonColor = Color.Red  // Ação destrutiva
    )
}
```

### AppInputDialog

Dialog com campo de entrada de texto.

```kotlin
@Composable
fun RenameDialog() {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    AppInputDialog(
        show = showDialog,
        onDismiss = {
            showDialog = false
            name = ""
        },
        title = "Renomear",
        message = "Digite o novo nome",
        inputValue = name,
        onInputChange = { name = it },
        inputLabel = "Nome",
        inputPlaceholder = "Digite o nome",
        confirmText = "Salvar",
        cancelText = "Cancelar",
        onConfirm = {
            // Salvar novo nome
            showDialog = false
        }
    )
}
```

### AppTopBar

Top AppBar genérico com navegação e ações.

```kotlin
@Composable
fun ProfileScreen() {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Perfil",
                navigationType = NavigationType.BACK,
                onNavigationClick = { /* voltar */ },
                actions = {
                    IconButton(onClick = { /* editar */ }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { /* configurações */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                }
            )
        }
    ) { padding ->
        // Conteúdo
    }
}

// Helpers tipados
BackTopBar(
    title = "Detalhes",
    onBackClick = { /* voltar */ }
)

MenuTopBar(
    title = "Início",
    onMenuClick = { /* abrir menu */ },
    actions = { /* ações */ }
)
```

#### NavigationType:

| Tipo | Descrição |
|------|-----------|
| `NONE` | Sem botão de navegação |
| `BACK` | Seta de voltar |
| `MENU` | Ícone de menu (hambúrguer) |

### AppBottomNavBar

Bottom navigation bar genérico.

```kotlin
@Composable
fun MainScreen() {
    var selectedRoute by remember { mutableStateOf("home") }

    val navItems = listOf(
        BottomNavItem(
            icon = Icons.Default.Home,
            label = "Início",
            route = "home"
        ),
        BottomNavItem(
            icon = Icons.Default.Search,
            label = "Buscar",
            route = "search"
        ),
        BottomNavItem(
            icon = Icons.Default.Notifications,
            label = "Notificações",
            route = "notifications",
            badge = 5  // Badge com contador
        ),
        BottomNavItem(
            icon = Icons.Default.Person,
            label = "Perfil",
            route = "profile"
        )
    )

    Scaffold(
        bottomBar = {
            AppBottomNavBar(
                items = navItems,
                selectedRoute = selectedRoute,
                onItemClick = { item ->
                    selectedRoute = item.route
                    // Navegar
                }
            )
        }
    ) { padding ->
        // Conteúdo navegável
    }
}
```

### Toast System

Sistema de notificações toast com animações.

```kotlin
@Composable
fun MainApp() {
    val toastState = rememberToastState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Conteúdo principal
        MyScreen(toastState = toastState)

        // Toast host (no topo da hierarquia)
        ToastHost(
            toastState = toastState,
            topPadding = 80.dp  // Evitar sobreposição com TopBar
        )
    }
}

@Composable
fun MyScreen(toastState: ToastState) {
    Column {
        AppButton(
            text = "Sucesso",
            onClick = {
                toastState.showSuccess("Operação realizada com sucesso!")
            }
        )

        AppButton(
            text = "Erro",
            onClick = {
                toastState.showError("Erro ao processar requisição")
            }
        )

        AppButton(
            text = "Aviso",
            onClick = {
                toastState.showWarning("Atenção: verifique os dados")
            }
        )

        AppButton(
            text = "Info",
            onClick = {
                toastState.showInfo("Nova atualização disponível")
            }
        )

        // Toast customizado
        AppButton(
            text = "Custom",
            onClick = {
                toastState.showToast(
                    ToastData(
                        message = "Toast customizado",
                        type = ToastType.INFO,
                        duration = 5000L  // 5 segundos
                    )
                )
            }
        )
    }
}
```

#### ToastType:

| Tipo | Cor | Ícone | Uso |
|------|-----|-------|-----|
| `SUCCESS` | Verde | CheckCircle | Operações bem-sucedidas |
| `ERROR` | Vermelho | Error | Erros e falhas |
| `WARNING` | Laranja | Warning | Avisos e alertas |
| `INFO` | Azul | Info | Informações gerais |

#### ToastState API:

```kotlin
// Helpers tipados
toastState.showSuccess(message: String, duration: Long = 3000L)
toastState.showError(message: String, duration: Long = 3000L)
toastState.showWarning(message: String, duration: Long = 3000L)
toastState.showInfo(message: String, duration: Long = 3000L)

// Genérico
toastState.showToast(data: ToastData)

// Ocultar manualmente
toastState.hideToast()
```

---

## Tema e Estilo

### AppTheme

Sistema de tema completo com suporte a Light/Dark mode.

```kotlin
@Composable
fun MyApp() {
    AppTheme(
        darkTheme = false,  // ou isSystemInDarkTheme()
        colorPalette = AppColorPalettes.Green,
        fontFamily = FontFamily.Default
    ) {
        // Seu app aqui
        MainScreen()
    }
}
```

### Paletas de Cores Prontas

```kotlin
// Paleta padrão roxa
AppTheme(colorPalette = AppColorPalettes.Default)

// Paleta laranja (Locadora)
AppTheme(colorPalette = AppColorPalettes.Orange)

// Paleta verde (Advogado)
AppTheme(colorPalette = AppColorPalettes.Green)

// Paleta azul
AppTheme(colorPalette = AppColorPalettes.Blue)

// Paleta rosa
AppTheme(colorPalette = AppColorPalettes.Pink)

// Paleta vermelha
AppTheme(colorPalette = AppColorPalettes.Red)
```

### Paleta Customizada

```kotlin
val myPalette = AppColorPalette(
    primary = Color(0xFF6C63FF),
    secondary = Color(0xFFEC4899),
    tertiary = Color(0xFF3B82F6),
    error = Color(0xFFDC3545),
    success = Color(0xFF10B981),
    warning = Color(0xFFF59E0B),
    info = Color(0xFF3B82F6)
)

AppTheme(colorPalette = myPalette) {
    // Seu app
}
```

### Acessar Cores Customizadas

```kotlin
@Composable
fun SuccessMessage() {
    val colors = AppColors.current

    Text(
        text = "Sucesso!",
        color = colors.success
    )
}

@Composable
fun WarningBadge() {
    val colors = AppColors.current

    AppBadge(
        text = "Aviso",
        backgroundColor = colors.warning
    )
}
```

### Typography

Sistema de tipografia Material 3 completo.

```kotlin
@Composable
fun TextExamples() {
    Column {
        // Display - Textos maiores
        Text(
            text = "Display Large",
            style = MaterialTheme.typography.displayLarge
        )

        // Headline - Títulos principais
        Text(
            text = "Headline Medium",
            style = MaterialTheme.typography.headlineMedium
        )

        // Title - Títulos de seções
        Text(
            text = "Title Large",
            style = MaterialTheme.typography.titleLarge
        )

        // Body - Texto principal
        Text(
            text = "Body Medium - Este é o texto principal do conteúdo",
            style = MaterialTheme.typography.bodyMedium
        )

        // Label - Botões, labels
        Text(
            text = "LABEL LARGE",
            style = MaterialTheme.typography.labelLarge
        )
    }
}
```

#### Hierarquia de Typography:

| Estilo | Tamanho | Uso |
|--------|---------|-----|
| `displayLarge` | 57sp | Hero text, landing pages |
| `displayMedium` | 45sp | Títulos grandes |
| `displaySmall` | 36sp | Títulos de destaque |
| `headlineLarge` | 32sp | Títulos principais |
| `headlineMedium` | 28sp | Títulos de seção |
| `headlineSmall` | 24sp | Subtítulos |
| `titleLarge` | 22sp | Títulos de cards/listas |
| `titleMedium` | 16sp | Títulos pequenos |
| `titleSmall` | 14sp | Títulos mínimos |
| `bodyLarge` | 16sp | Texto principal (destaque) |
| `bodyMedium` | 14sp | Texto principal (padrão) |
| `bodySmall` | 12sp | Texto secundário |
| `labelLarge` | 14sp | Botões, tabs |
| `labelMedium` | 12sp | Labels, badges |
| `labelSmall` | 11sp | Labels pequenos |

### Tipografia Customizada

```kotlin
// Com fonte customizada
val myFontFamily = FontFamily(
    Font(Res.font.custom_regular, FontWeight.Normal),
    Font(Res.font.custom_bold, FontWeight.Bold)
)

AppTheme(fontFamily = myFontFamily) {
    // Todos os textos usarão a fonte customizada
}
```

---

## Arquitetura MVI

### BaseViewModel

ViewModel base com suporte ao padrão MVI (Model-View-Intent).

#### Definir State, Action e Effect

```kotlin
// State - Estado completo da tela
data class ProfileState(
    val name: String = "",
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

// Action - Todas as ações do usuário
sealed interface ProfileAction {
    data class NameChanged(val name: String) : ProfileAction
    data class EmailChanged(val email: String) : ProfileAction
    data object SaveClicked : ProfileAction
    data object LogoutClicked : ProfileAction
}

// Effect - Efeitos colaterais (navegação, toast, etc)
sealed interface ProfileEffect {
    data object NavigateToLogin : ProfileEffect
    data class ShowToast(val message: String) : ProfileEffect
}
```

#### Implementar ViewModel

```kotlin
class ProfileViewModel(
    private val userRepository: UserRepository
) : BaseViewModel<ProfileState, ProfileAction, ProfileEffect>(
    initialState = ProfileState()
) {
    override fun onAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.NameChanged -> {
                updateState { it.copy(name = action.name) }
            }

            is ProfileAction.EmailChanged -> {
                updateState { it.copy(email = action.email) }
            }

            is ProfileAction.SaveClicked -> {
                saveProfile()
            }

            is ProfileAction.LogoutClicked -> {
                logout()
            }
        }
    }

    private fun saveProfile() {
        launch {
            updateState { it.copy(isLoading = true) }

            userRepository.updateProfile(
                name = currentState.name,
                email = currentState.email
            ).onSuccess {
                emitEffect(ProfileEffect.ShowToast("Perfil atualizado!"))
            }.onError { error ->
                updateState { it.copy(
                    isLoading = false,
                    errorMessage = error.message
                ) }
            }
        }
    }

    private fun logout() {
        launch {
            userRepository.logout()
            emitEffect(ProfileEffect.NavigateToLogin)
        }
    }
}
```

#### Usar no Composable

```kotlin
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = koinViewModel(),
    navController: NavController,
    toastState: ToastState
) {
    val state by viewModel.state.collectAsState()

    // Observar efeitos
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ProfileEffect.NavigateToLogin -> {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is ProfileEffect.ShowToast -> {
                    toastState.showSuccess(effect.message)
                }
            }
        }
    }

    ProfileContent(
        state = state,
        onAction = viewModel::onAction
    )
}

@Composable
fun ProfileContent(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        AppTextField(
            value = state.name,
            onValueChange = { onAction(ProfileAction.NameChanged(it)) },
            label = "Nome"
        )

        AppTextField(
            value = state.email,
            onValueChange = { onAction(ProfileAction.EmailChanged(it)) },
            label = "Email"
        )

        state.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        AppButton(
            text = "Salvar",
            onClick = { onAction(ProfileAction.SaveClicked) },
            isLoading = state.isLoading
        )

        AppOutlinedButton(
            text = "Sair",
            onClick = { onAction(ProfileAction.LogoutClicked) }
        )
    }
}
```

### Benefícios do BaseViewModel

- **Type-safe**: State, Action e Effect são tipos definidos
- **Unidirecional**: Fluxo de dados claro e previsível
- **Testável**: Estado e lógica isolados da UI
- **Reativo**: Mudanças de estado propagam automaticamente
- **One-shot events**: Effects garantem eventos únicos (navegação, toasts)

---

## Tratamento de Erros

### AppResult e AppError

Sistema de tratamento de erros type-safe.

```kotlin
import br.com.codecacto.kmplib.core.error.*

// Função que retorna AppResult
suspend fun loadUser(id: String): AppResult<User> {
    return runCatchingAppResultAsync {
        // Operação que pode falhar
        userRepository.getUser(id)
    }
}

// Usar o resultado
val result = loadUser("123")

result
    .onSuccess { user ->
        println("Usuário: ${user.name}")
    }
    .onError { error ->
        when (error) {
            is AppError.Network -> {
                println("Erro de conexão")
            }
            is AppError.NotFound -> {
                println("Usuário não encontrado")
            }
            is AppError.Unauthorized -> {
                println("Não autorizado")
            }
            else -> {
                println(error.message)
            }
        }
    }

// Ou obter o valor
val user = result.getOrNull()
val userOrDefault = result.getOrElse(User.empty())
```

### Tipos de AppError

| Tipo | Uso |
|------|-----|
| `Network` | Erros de conexão, timeout |
| `Server` | Erros 5xx do servidor |
| `Unauthorized` | Erro 401 - Sessão expirada |
| `Forbidden` | Erro 403 - Sem permissão |
| `NotFound` | Erro 404 - Recurso não encontrado |
| `Validation` | Erros de validação de campos |
| `Business` | Erros de regra de negócio |
| `Unknown` | Erros não mapeados |

### Converter Result para AppResult

```kotlin
// Kotlin Result<T> -> AppResult<T>
val kotlinResult: Result<User> = runCatching {
    fetchUser()
}

val appResult = kotlinResult.toAppResult()

appResult.onSuccess { user ->
    // Usar user
}.onError { error ->
    // Tratar erro
}
```

### Uso em ViewModel

```kotlin
class UserViewModel : BaseViewModel<UserState, UserAction, UserEffect>(
    initialState = UserState()
) {
    override fun onAction(action: UserAction) {
        when (action) {
            is UserAction.LoadUser -> loadUser(action.userId)
        }
    }

    private fun loadUser(userId: String) {
        launch {
            updateState { it.copy(isLoading = true) }

            val result = userRepository.getUser(userId)

            result
                .onSuccess { user ->
                    updateState { it.copy(
                        user = user,
                        isLoading = false
                    ) }
                }
                .onError { error ->
                    updateState { it.copy(
                        isLoading = false,
                        errorMessage = error.message
                    ) }

                    // Mostrar toast com erro
                    when (error) {
                        is AppError.Network -> {
                            emitEffect(UserEffect.ShowToast("Sem conexão"))
                        }
                        is AppError.Unauthorized -> {
                            emitEffect(UserEffect.NavigateToLogin)
                        }
                        else -> {
                            emitEffect(UserEffect.ShowToast(error.message))
                        }
                    }
                }
        }
    }
}
```

---

## Tratamento de Erros Legado

Todas as operações que podem falhar retornam `Result<T>`. Use as extensões padrão do Kotlin:

```kotlin
// Padrão com onSuccess/onFailure
result.onSuccess { value ->
    // Sucesso
}.onFailure { error ->
    // Erro
}

// Com getOrNull
val value = result.getOrNull()
if (value != null) {
    // Usar valor
}

// Com getOrElse
val value = result.getOrElse { defaultValue }

// Com getOrThrow (lança exceção se falhar)
try {
    val value = result.getOrThrow()
} catch (e: Exception) {
    // Tratar erro
}

// Encadeamento com map/mapCatching
result
    .map { it.transform() }
    .onSuccess { transformed -> }
```

---

## Licença

Apache 2.0

---

## Suporte

Para bugs e sugestões, abra uma issue no repositório.









