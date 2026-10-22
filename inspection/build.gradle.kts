plugins {
    id("kmplib.module.compose")
}

// `kmplib-inspection` (2.318.0) — a UI do **Laudo Kit**: o motor de vistoria compartilhado (Minha
// Vistoria, Meus Aluguéis, ChecklistVeicular). Componentes temáveis (marca + fontes + neutros vêm do
// produto; semáforo fixo), dirigidos por ESTADO DE TELA próprio do kit (view-state + eventos) — o
// domínio e o cliente HTTP (espelho do `backlib-inspection`) entram numa rodada seguinte, com um
// mapeador domínio → view-state. Especificação: `MinhaVistoria/docs/design/laudo-kit.md`.
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
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
    }
}
