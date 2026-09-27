package br.com.codecacto.kmplib.platform

/** iOS: a câmera entrega a imagem em memória — não há original em disco a varrer. */
actual fun clearCameraCaptureFiles(olderThanMillis: Long): Int = 0
