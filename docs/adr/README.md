# ADRs — Architecture Decision Records (kmplib)

Decisões de arquitetura da lib, no formato leve (contexto → decisão → consequências). O **código**
referencia estes ADRs por número (ex.: `OfflineQuotaGate` implementa a **ADR-001**; os renderers de
PDF iOS citam a **ADR-0003**). Este diretório existe para que essa referência aponte para algum lugar.

> Reconstruídos em jul/2026 (auditoria — os ADRs eram citados no código mas não existiam como
> documento). O texto foi derivado do **código real** e dos KDocs que os citam.

| ADR | Título | Status |
|-----|--------|--------|
| [ADR-001](ADR-001-quota-offline-gate.md) | Gate de cota freemium offline-tolerante | Aceito · implementado (`monetization/quota`) |
| [ADR-0003](ADR-0003-ios-pdf-native-rendering.md) | Render de PDF iOS nativo (CoreText/UIGraphicsPDFRenderer), sem workaround | Aceito · parcialmente implementado (recibo real; 7 multi-página pendentes) |
