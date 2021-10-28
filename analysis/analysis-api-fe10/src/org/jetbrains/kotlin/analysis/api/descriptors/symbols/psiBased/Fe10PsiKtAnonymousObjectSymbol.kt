/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.psiBased

import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisFacade.AnalysisMode
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10NeverRestoringKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.psiBased.base.Fe10PsiKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.utils.cached
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtPsiBasedSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.resolve.BindingContext

internal class Fe10PsiKtAnonymousObjectSymbol(
    override val psi: KtObjectDeclaration,
    override val analysisContext: Fe10AnalysisContext
) : KtAnonymousObjectSymbol(), Fe10PsiKtSymbol<KtObjectDeclaration, ClassDescriptor> {
    override val descriptor: ClassDescriptor? by cached {
        val bindingContext = analysisContext.analyze(psi, AnalysisMode.PARTIAL)
        bindingContext[BindingContext.CLASS, psi]
    }

    override val superTypes: List<KtTypeAndAnnotations>
        get() = withValidityAssertion {
            descriptor?.typeConstructor?.supertypes?.map { it.toKtTypeAndAnnotations(analysisContext) } ?: emptyList()
        }

    override fun createPointer(): KtSymbolPointer<KtAnonymousObjectSymbol> = withValidityAssertion {
        return KtPsiBasedSymbolPointer.createForSymbolFromSource(this) ?: Fe10NeverRestoringKtSymbolPointer()
    }
}