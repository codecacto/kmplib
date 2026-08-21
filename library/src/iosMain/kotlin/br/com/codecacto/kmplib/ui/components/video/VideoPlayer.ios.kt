package br.com.codecacto.kmplib.ui.components.video

/*
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 *  ESTE ARQUIVO FICOU VAZIO — pode ser apagado.
 *
 *  Ele continha o player EMBUTIDO (`VideoPlayer` / `VideoPlayerDialog`), que não funciona: dentro
 *  de uma coluna rolável — e também dentro de um `Dialog` do Compose — a view nativa de vídeo
 *  disputa camada com a árvore de composição, e o resultado é sempre o mesmo trio: pisca, fica
 *  preto e o áudio toca por baixo, com os controles inalcançáveis.
 *
 *  O substituto é o `VideoLauncher`: o vídeo ganha a própria janela do sistema (Activity no
 *  Android, UIViewController no iOS), onde não há Compose para disputar nada.
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 */
