package br.com.codecacto.kmplib.inspection.capture

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.uname
import platform.posix.utsname

/** O identificador de hardware (`hw.machine`, ex.: "iPhone15,2") — o `UIDevice.model` diz só "iPhone". */
@OptIn(ExperimentalForeignApi::class)
actual fun inspectionDeviceModel(): String = memScoped {
    val info = alloc<utsname>()
    if (uname(info.ptr) == 0) info.machine.toKString() else "iOS"
}
