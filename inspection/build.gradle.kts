plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

// `kmplib-inspection` (2.319.0) — a UI do **Laudo Kit**: o motor de vistoria compartilhado (Minha
// Vistoria, Meus Aluguéis, ChecklistVeicular). Componentes temáveis (marca + fontes + neutros vêm do
// produto; semáforo fixo), dirigidos por ESTADO DE TELA próprio do kit (view-state + eventos).
// 2.320.0: o DOMÍNIO — espelho do contrato do `backlib-inspection` (`data/`), cliente HTTP sobre o
// `DomainApiClient`, `InspectionSession`, fila durável de mídia (`DirectUploadOutbox` do kmplib-sync) e
// os mapeadores domínio → view-state (`mapping/`). Especificação: `MinhaVistoria/docs/design/laudo-kit.md`.
//
// NÃO está no umbrella (`br.com.codecacto:kmplib`): traz câmera (CameraX/AVFoundation), leitor de PDF
// e QR, e só app de vistoria precisa — o mesmo critério do `kmplib-chat`.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            // Área de transferência, permissões e o SignaturePad (assinar no aparelho).
            api(project(":kmplib-platform"))
            // InspectionCamera sobre o GuidedCameraController/GuidedCameraViewport.
            api(project(":kmplib-camera"))
            // Leitor do laudo (PdfViewer/PdfSource aparecem na API do InspectionReportViewer).
            api(project(":kmplib-pdf"))
            // QR de verificação do ProofCard.
            api(project(":kmplib-qr"))
            // Fila durável de mídia (DirectUploadOutbox) e o DomainApiClient/DomainResult na API pública.
            api(project(":kmplib-sync"))
            api(libs.kotlinx.serialization.json)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
