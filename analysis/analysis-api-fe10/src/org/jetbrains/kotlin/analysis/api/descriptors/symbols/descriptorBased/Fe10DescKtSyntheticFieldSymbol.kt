/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased

import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.base.Fe10KtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10DescSyntheticFieldKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10NeverRestoringKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.KtBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertyAccessorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtPsiBasedSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SyntheticFieldDescriptor
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.resolve.source.getPsi

internal class Fe10DescKtSyntheticFieldSymbol(
    private val descriptor: SyntheticFieldDescriptor,
    override val analysisContext: Fe10AnalysisContext
) : KtBackingFieldSymbol(), Fe10KtSymbol {
    override val owningProperty: KtKotlinPropertySymbol
        get() = withValidityAssertion {
            val kotlinProperty = descriptor.propertyDescriptor as PropertyDescriptorImpl
            Fe10DescKtKotlinPropertySymbol(kotlinProperty, analysisContext)
        }

    override val annotatedType: KtTypeAndAnnotations
        get() = withValidityAssertion { descriptor.propertyDescriptor.type.toKtTypeAndAnnotations(analysisContext) }

    override fun createPointer(): KtSymbolPointer<KtVariableLikeSymbol> = withValidityAssertion {
        val accessorPsi = descriptor.containingDeclaration.toSourceElement.getPsi()
        if (accessorPsi is KtPropertyAccessor) {
            val accessorPointer = KtPsiBasedSymbolPointer<KtPropertyAccessorSymbol>(accessorPsi.createSmartPointer())
            return Fe10DescSyntheticFieldKtSymbolPointer(accessorPointer)
        }

        return Fe10NeverRestoringKtSymbolPointer()
    }
}