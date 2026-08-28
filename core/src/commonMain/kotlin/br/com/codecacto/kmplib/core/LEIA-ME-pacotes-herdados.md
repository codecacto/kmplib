# Por que o `kmplib-core` contém pacotes chamados `firebase`, `monetization` e `sync`

Quatro arquivos deste módulo moram em pacotes que levam o nome de OUTRO módulo:

| Arquivo | Pacote | O que é |
|---|---|---|
| `IAuthRepository.kt` | `…firebase.auth` | contrato de autenticação — só `Flow`, nada de Firebase |
| `User.kt` | `…firebase.auth` | o usuário logado, um `data class` serializável |
| `QuotaExceeded.kt` | `…monetization.entitlement` | leitura do 402/429 que o servidor devolve |
| `Entitlement.kt` | `…monetization.entitlement` | os modelos `Entitlement`, `UsageSnapshot` e `Plan` |
| `DomainApiClient.kt` | `…sync.rest` | cliente REST autenticado, com tratamento de cota |

Os quatro são **contratos neutros**: nenhum deles nomeia um tipo do Firebase, do RevenueCat ou do
SQLDelight. Estavam nos módulos das implementações por acidente de história, e enquanto a lib era
um módulo só isso não custava nada.

Na modularização passou a custar: `DomainApiClient` é usado por `auth`, por `firebase` e pelo
próprio `sync`, e como ele importava `IAuthRepository` (firebase) e `QuotaExceeded` (monetization),
qualquer app que apenas fizesse login acabava arrastando SQLDelight, Firebase Storage e o SDK de
compras junto. Com os quatro aqui embaixo, o grafo fica acíclico e cada módulo carrega só o que
usa.

**O pacote não mudou de nome de propósito.** `br.com.codecacto.kmplib.sync.rest.DomainApiClient` é
o import que está escrito em dezenas de arquivos dos apps (só o Minha Arena tem mais de dez).
Renomear o pacote tornaria esta reorganização visível para todos eles — e o objetivo é o oposto.
Kotlin não exige que a pasta corresponda ao pacote; aqui a pasta acompanha o pacote, e é o módulo
que difere.
