package org.jetbrains.kotlin.ir.backend.js.utils

import org.jetbrains.kotlin.js.common.JsLanguageFeature
import org.jetbrains.kotlin.js.util.TextOutput

class JsLanguageFeaturesContext(val polyfills: List<JsPolyfill>) {
    private val requestedFeatures = mutableSetOf<JsLanguageFeature>()

    fun requestFeature(feature: JsLanguageFeature) {
        requestedFeatures.add(feature)
    }

    fun addAllNeededPolyfillsTo(output: TextOutput) {
        output.print("// region block: polyfills\n")
        output.print(getAllNeededPolyfills())
        output.print("// endregion\n")
    }

    private fun getAllNeededPolyfills() =
        polyfills
            .filter { requestedFeatures.contains(it.feature) }
            .joinToString(separator = "\n", postfix = "\n") { it.getPolyfillImpl() }
}