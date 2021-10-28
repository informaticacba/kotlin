/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased

import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.Fe10DescMemberKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.classId
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10DescEnumEntryKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10NeverRestoringKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.KtEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtPsiBasedSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal class Fe10DescKtEnumEntrySymbol(
    override val descriptor: ClassDescriptor,
    override val analysisContext: Fe10AnalysisContext
) : KtEnumEntrySymbol(), Fe10DescMemberKtSymbol<ClassDescriptor> {
    private val enumDescriptor: ClassDescriptor
        get() = descriptor.containingDeclaration as ClassDescriptor

    override val containingEnumClassIdIfNonLocal: ClassId?
        get() = withValidityAssertion { enumDescriptor.classId }

    override val callableIdIfNonLocal: CallableId?
        get() = withValidityAssertion {
            val enumClassId = enumDescriptor.classId ?: return null
            CallableId(
                packageName = enumClassId.packageFqName,
                className = enumClassId.relativeClassName,
                callableName = descriptor.name
            )
        }

    override val annotatedType: KtTypeAndAnnotations
        get() = withValidityAssertion { enumDescriptor.defaultType.toKtTypeAndAnnotations(analysisContext) }

    override val name: Name
        get() = withValidityAssertion { descriptor.name }

    override fun createPointer(): KtSymbolPointer<KtEnumEntrySymbol> = withValidityAssertion {
        val pointerByPsi = KtPsiBasedSymbolPointer.createForSymbolFromSource(this)
        if (pointerByPsi != null) {
            return pointerByPsi
        }

        val enumClassId = enumDescriptor.classId
        if (enumClassId != null) {
            return Fe10DescEnumEntryKtSymbolPointer(enumClassId, descriptor.name)
        }

        return Fe10NeverRestoringKtSymbolPointer()
    }
}