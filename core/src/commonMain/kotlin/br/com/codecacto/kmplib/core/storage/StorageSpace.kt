package br.com.codecacto.kmplib.core.storage

/**
 * Espaço livre no volume onde o app guarda os próprios dados, em bytes — ou `null` quando a
 * plataforma não respondeu (nesse caso, quem chama **não** bloqueia nada: não saber não é motivo
 * para recusar a captura do usuário).
 *
 * É a pergunta certa antes de **gravar mídia** em app offline: gravar até o disco encher corrompe o
 * arquivo e às vezes o banco local.
 *
 * - **Android:** `StatFs(noBackupFilesDir).availableBytes` — o que o app pode usar de fato (o
 *   `freeBytes` inclui a reserva do sistema).
 * - **iOS:** `NSURLVolumeAvailableCapacityForImportantUsageKey` — o número que a Apple manda usar para
 *   conteúdo que o **usuário** está criando (conta o que o sistema liberaria limpando caches); cai
 *   para `NSFileSystemFreeSize` se a chave não responder.
 *
 * Exige `KmpLib.init(context)` / `initKmpLibCore` no Android (o mesmo holder do [BlobStore]).
 */
expect fun availableStorageBytes(): Long?
