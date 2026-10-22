package br.com.codecacto.kmplib.inspection.capture

import android.os.Build

actual fun inspectionDeviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
