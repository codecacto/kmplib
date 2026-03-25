package br.com.codecacto.kmplib.platform

import androidx.compose.ui.graphics.ImageBitmap

expect fun encodeBitmapToPng(bitmap: ImageBitmap): ByteArray
