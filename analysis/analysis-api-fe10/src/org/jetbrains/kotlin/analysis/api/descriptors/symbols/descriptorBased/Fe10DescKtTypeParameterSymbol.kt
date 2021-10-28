/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased

import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.Fe10DescKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtType
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10NeverRestoringKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtPsiBasedSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

internal class Fe10DescKtTypeParameterSymbol(
    override val descriptor: TypeParameterDescriptor,
    override val analysisContext: Fe10AnalysisContext
) : KtTypeParameterSymbol(), Fe10DescKtSymbol<TypeParameterDescriptor> {
    override val name: Name
        get() = withValidityAssertion { descriptor.name }

    override val upperBounds: List<KtType>
        get() = withValidityAssertion { descriptor.upperBounds.map { it.toKtType(analysisContext) } }

    override val variance: Variance
        get() = withValidityAssertion { descriptor.variance }

    override val isReified: Boolean
        get() = withValidityAssertion { descriptor.isReified }

    override fun createPointer(): KtSymbolPointer<KtTypeParameterSymbol> = withValidityAssertion {
        return KtPsiBasedSymbolPointer.createForSymbolFromSource(this) ?: Fe10NeverRestoringKtSymbolPointer()
    }
}