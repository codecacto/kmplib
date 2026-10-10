plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

// `kmplib-chat` (2.287.0 — GAP-PT-M12, App do Personal L-K2; 2º consumidor: Minha Estadia GAP-ME-02).
// Conversa 1:1 sem WebSocket: envio otimista com id do cliente (UUID v7), reenvio que não duplica,
// fila de pendências durável (BlobStore do core), não lidas e atualização por consulta periódica
// `after=`. A nota de voz vem do `kmplib-media` (gravador + player com onda).
//
// NÃO está no umbrella (`br.com.codecacto:kmplib`): só app com conversa declara — o mesmo critério do
// `kmplib-workout`/`kmplib-health` (peso no link do iOS de quem não usa).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            // VoiceNoteRecorderState/VoiceNotePlayer/RecordedAudio aparecem na API pública do compositor.
            api(project(":kmplib-media"))
            api(libs.ktor.client.core)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
