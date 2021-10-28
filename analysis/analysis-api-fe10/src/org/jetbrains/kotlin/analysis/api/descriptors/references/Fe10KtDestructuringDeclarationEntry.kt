/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.references

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisFacade.AnalysisMode
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.references.base.CliKtFe10Reference
import org.jetbrains.kotlin.analysis.api.descriptors.references.base.Fe10KtReference
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.idea.references.KtDestructuringDeclarationReference
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.resolve.BindingContext

abstract class Fe10KtDestructuringDeclarationEntry(
    element: KtDestructuringDeclarationEntry
) : KtDestructuringDeclarationReference(element), Fe10KtReference {
    override fun KtAnalysisSession.resolveToSymbols(): Collection<KtSymbol> {
        check(this is Fe10KtAnalysisSession)

        val bindingContext = analysisContext.analyze(element, AnalysisMode.PARTIAL)
        val descriptor = bindingContext[BindingContext.COMPONENT_RESOLVED_CALL, element]?.resultingDescriptor
        return listOfNotNull(descriptor?.toKtCallableSymbol(analysisContext))
    }
}

internal class CliFe10KtDestructuringDeclarationEntry(
    element: KtDestructuringDeclarationEntry
) : Fe10KtDestructuringDeclarationEntry(element), CliKtFe10Reference {
    override fun canRename(): Boolean {
        return false
    }
}