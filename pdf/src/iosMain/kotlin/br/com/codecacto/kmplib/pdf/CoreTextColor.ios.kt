@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.pdf

import platform.CoreGraphics.CGColorRef
import platform.CoreGraphics.CGColorRetain
import platform.Foundation.CFBridgingRelease
import platform.UIKit.UIColor

/**
 * Valor de `kCTForegroundColorAttributeName` ("CTForegroundColor") pronto para um
 * `NSAttributedString`: o `CGColor` como OBJETO Objective-C.
 *
 * ⚠️ Passar o `CGColorRef` direto em `addAttribute(value = …)` compila, mas o Kotlin/Native
 * **embrulha o ponteiro num objeto Kotlin** (`Kotlin_ObjCExport…`/`…_kobjcc`) em vez de entregar o
 * `CGColor`. O CoreText, ao desenhar a linha, manda `-CGColor` para esse embrulho e o processo morre
 * com `NSInvalidArgumentException: unrecognized selector` — todo PDF com texto derrubava o app no iOS
 * (kmplib ≤ 2.226.0; achado pelo `PdfCanvasIosRenderTest` no simulador).
 *
 * A ponte oficial para um tipo Core Foundation virar `id` é `CFBridgingRelease` sobre uma referência
 * RETIDA (+1): o `CGColorRetain` equilibra a posse que o `CFBridgingRelease` transfere ao GC.
 */
internal fun UIColor.coreTextForegroundColor(): Any? = CGColor?.asObjCObject()

internal fun CGColorRef.asObjCObject(): Any? = CFBridgingRelease(CGColorRetain(this))
