package org.jetbrains.kotlin.ir.backend.js.utils

import org.jetbrains.kotlin.js.common.JsLanguageFeature

// https://mathiasbynens.be/notes/globalthis
val globalThis = JsPolyfill(JsLanguageFeature.GLOBAL_THIS) {
    """
       (function() {
            if (typeof globalThis === 'object') return; 
            Object.defineProperty(Object.prototype, '__magic__', {
                get: function() {
                    return this;
                },
                configurable: true
            });
            __magic__.globalThis = __magic__;
            delete Object.prototype.__magic__;
        }()); 
    """.trimIndent()
}

val jsPolyfills = listOf(
    globalThis
)