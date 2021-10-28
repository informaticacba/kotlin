/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.psiBased.base

import org.jetbrains.kotlin.analysis.api.descriptors.symbols.base.Fe10KtAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.base.Fe10KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.psi.KtElement

internal interface Fe10PsiKtSymbol<P : KtElement, D : DeclarationDescriptor> : Fe10KtSymbol, Fe10KtAnnotatedSymbol {
    override val psi: P
    val descriptor: D?

    override val annotationsObject: Annotations
        get() = descriptor?.annotations ?: Annotations.EMPTY

    override val origin: KtSymbolOrigin
        get() = withValidityAssertion {
            return if (psi.containingKtFile.isCompiled) {
                KtSymbolOrigin.LIBRARY
            } else {
                KtSymbolOrigin.SOURCE
            }
        }
}