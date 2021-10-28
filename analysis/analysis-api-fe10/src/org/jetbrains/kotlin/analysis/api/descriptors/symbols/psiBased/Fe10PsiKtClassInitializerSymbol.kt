/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.psiBased

import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.base.Fe10KtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10NeverRestoringKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.KtClassInitializerSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtPsiBasedSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.psi.KtClassInitializer

class Fe10PsiKtClassInitializerSymbol(
    override val psi: KtClassInitializer,
    override val analysisContext: Fe10AnalysisContext
) : KtClassInitializerSymbol(), Fe10KtSymbol {
    override val origin: KtSymbolOrigin
        get() = withValidityAssertion {
            return if (psi.containingKtFile.isCompiled) {
                KtSymbolOrigin.LIBRARY
            } else {
                KtSymbolOrigin.SOURCE
            }
        }

    override val symbolKind: KtSymbolKind
        get() = withValidityAssertion { KtSymbolKind.CLASS_MEMBER }

    override fun createPointer(): KtSymbolPointer<KtClassInitializerSymbol> {
        return KtPsiBasedSymbolPointer.createForSymbolFromSource(this) ?: Fe10NeverRestoringKtSymbolPointer()
    }
}