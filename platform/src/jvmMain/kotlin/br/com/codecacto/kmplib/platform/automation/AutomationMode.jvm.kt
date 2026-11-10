package br.com.codecacto.kmplib.platform.automation

import androidx.compose.ui.Modifier

// Desktop (2.353.0): sem Test Lab, sem Test Harness, sem marca do runner de QA. O modo automação no
// desktop liga pelo `AutomationMode.activate()` ou pelo dublê da loja, como em qualquer plataforma.
internal actual fun isFirebaseTestLab(): Boolean = false

internal actual fun isDeviceInTestHarness(): Boolean = false

internal actual fun isQaRunnerMarked(): Boolean = false

/** Desktop: não há `resource-id`; o `testTag` já é o que o teste do Compose Desktop enxerga. */
actual fun Modifier.exposeTestTagsAsResourceId(): Modifier = this
