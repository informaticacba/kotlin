/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.types

import org.jetbrains.kotlin.analysis.api.KtTypeArgument
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.Fe10DescKtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.ktNullability
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.maybeLocalClassId
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtTypeArgument
import org.jetbrains.kotlin.analysis.api.descriptors.types.base.Fe10KtType
import org.jetbrains.kotlin.analysis.api.descriptors.types.base.asStringForDebugging
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.types.SimpleType

internal class Fe10KtUsualClassType(
    override val type: SimpleType,
    private val descriptor: ClassDescriptor,
    override val analysisContext: Fe10AnalysisContext
) : KtUsualClassType(), Fe10KtType {
    override fun asStringForDebugging(): String = withValidityAssertion { type.asStringForDebugging() }

    override val classId: ClassId
        get() = withValidityAssertion { descriptor.maybeLocalClassId }

    override val classSymbol: KtClassLikeSymbol
        get() = withValidityAssertion { Fe10DescKtNamedClassOrObjectSymbol(descriptor, analysisContext) }

    override val typeArguments: List<KtTypeArgument>
        get() = withValidityAssertion { type.arguments.map { it.toKtTypeArgument(analysisContext) } }

    override val nullability: KtTypeNullability
        get() = withValidityAssertion { type.ktNullability }

}