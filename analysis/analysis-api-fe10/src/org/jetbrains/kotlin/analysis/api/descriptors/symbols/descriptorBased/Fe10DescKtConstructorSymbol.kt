/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased

import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.*
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10DescFunctionLikeKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10NeverRestoringKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtPsiBasedSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.name.ClassId

internal class Fe10DescKtConstructorSymbol(
    override val descriptor: ConstructorDescriptor,
    override val analysisContext: Fe10AnalysisContext
) : KtConstructorSymbol(), Fe10DescMemberKtSymbol<ConstructorDescriptor> {
    override val isPrimary: Boolean
        get() = withValidityAssertion { descriptor.isPrimary }

    override val containingClassIdIfNonLocal: ClassId?
        get() = withValidityAssertion { descriptor.constructedClass.classId }

    override val valueParameters: List<KtValueParameterSymbol>
        get() = withValidityAssertion { descriptor.valueParameters.map { Fe10DescKtValueParameterSymbol(it, analysisContext) } }

    override val hasStableParameterNames: Boolean
        get() = withValidityAssertion { descriptor.ktHasStableParameterNames }

    override val annotatedType: KtTypeAndAnnotations
        get() = withValidityAssertion { descriptor.returnType.toKtTypeAndAnnotations(analysisContext) }

    override val dispatchType: KtType?
        get() = withValidityAssertion {
            val containingClass = descriptor.constructedClass.containingDeclaration as? ClassDescriptor ?: return null
            return containingClass.defaultType.toKtType(analysisContext)
        }

    override val typeParameters: List<KtTypeParameterSymbol>
        get() = withValidityAssertion { descriptor.typeParameters.map { Fe10DescKtTypeParameterSymbol(it, analysisContext) } }

    override fun createPointer(): KtSymbolPointer<KtConstructorSymbol> = withValidityAssertion {
        val pointerByPsi = KtPsiBasedSymbolPointer.createForSymbolFromSource(this)
        if (pointerByPsi != null) {
            return pointerByPsi
        }

        val callableId = descriptor.callableId
        if (callableId != null && !callableId.isLocal) {
            val signature = descriptor.getSymbolPointerSignature(analysisContext)
            return Fe10DescFunctionLikeKtSymbolPointer(callableId, signature)
        }

        return Fe10NeverRestoringKtSymbolPointer()
    }
}