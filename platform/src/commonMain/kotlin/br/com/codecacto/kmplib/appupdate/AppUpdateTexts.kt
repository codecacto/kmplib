package br.com.codecacto.kmplib.appupdate

/**
 * Textos customizáveis da UI de atualização (Force Update).
 *
 * Todos os valores têm default em português brasileiro. O app pode sobrescrever qualquer um.
 * A mensagem vinda do servidor ([AppUpdateStatus.Hard.message] / [AppUpdateStatus.Soft.message]),
 * quando presente e não vazia, tem prioridade sobre [hardMessage] / [softMessage].
 */
data class AppUpdateTexts(
    // --- Atualização obrigatória (tela bloqueante) ---
    val hardTitle: String = "Atualização necessária",
    val hardMessage: String = "Esta versão do app não é mais compatível. Atualize para continuar usando.",
    val hardUpdateButton: String = "Atualizar agora",
    // --- Atualização opcional (diálogo dispensável) ---
    val softTitle: String = "Nova versão disponível",
    val softMessage: String = "Há uma nova versão do app com melhorias. Que tal atualizar?",
    val softUpdateButton: String = "Atualizar",
    val softDismissButton: String = "Agora não",
    // --- Acessibilidade ---
    val updateIconContentDescription: String = "Atualização disponível",
)
