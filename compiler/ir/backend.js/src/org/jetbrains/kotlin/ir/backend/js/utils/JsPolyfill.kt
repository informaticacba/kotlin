package org.jetbrains.kotlin.ir.backend.js.utils

import org.jetbrains.kotlin.js.common.JsLanguageFeature

class JsPolyfill(val feature: JsLanguageFeature, val getPolyfillImpl: () -> String)