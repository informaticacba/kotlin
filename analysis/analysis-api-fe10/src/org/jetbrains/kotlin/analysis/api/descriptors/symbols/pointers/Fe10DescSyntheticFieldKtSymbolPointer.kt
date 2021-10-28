/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.Fe10DescKtSyntheticFieldSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.getSymbolDescriptor
import org.jetbrains.kotlin.analysis.api.symbols.KtBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertyAccessorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtPsiBasedSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.descriptors.PropertyAccessorDescriptor
import org.jetbrains.kotlin.descriptors.impl.SyntheticFieldDescriptor

class Fe10DescSyntheticFieldKtSymbolPointer(
    private val psiPointer: KtPsiBasedSymbolPointer<KtPropertyAccessorSymbol>
) : KtSymbolPointer<KtBackingFieldSymbol>() {
    @Deprecated("Consider using org.jetbrains.kotlin.analysis.api.KtAnalysisSession.restoreSymbol")
    override fun restoreSymbol(analysisSession: KtAnalysisSession): KtBackingFieldSymbol? {
        check(analysisSession is Fe10KtAnalysisSession)
        val analysisContext = analysisSession.analysisContext

        @Suppress("DEPRECATION")
        val accessorSymbol = psiPointer.restoreSymbol(analysisSession) ?: return null

        val accessorDescriptor = getSymbolDescriptor(accessorSymbol) as? PropertyAccessorDescriptor ?: return null
        val syntheticFieldDescriptor = SyntheticFieldDescriptor(accessorDescriptor, accessorDescriptor.correspondingProperty.source)
        return Fe10DescKtSyntheticFieldSymbol(syntheticFieldDescriptor, analysisContext)
    }
}