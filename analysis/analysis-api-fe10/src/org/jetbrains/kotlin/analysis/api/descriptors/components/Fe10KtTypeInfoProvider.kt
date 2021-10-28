/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.components

import org.jetbrains.kotlin.analysis.api.components.KtTypeInfoProvider
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.components.base.Fe10KtAnalysisSessionComponent
import org.jetbrains.kotlin.analysis.api.descriptors.types.base.Fe10KtType
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.load.java.sam.JavaSingleAbstractMethodUtils
import org.jetbrains.kotlin.types.TypeUtils

internal class Fe10KtTypeInfoProvider(
    override val analysisSession: Fe10KtAnalysisSession
) : KtTypeInfoProvider(), Fe10KtAnalysisSessionComponent {
    override val token: ValidityToken
        get() = analysisSession.token

    override fun isFunctionalInterfaceType(type: KtType): Boolean = withValidityAssertion {
        require(type is Fe10KtType)
        return JavaSingleAbstractMethodUtils.isSamType(type.type)
    }

    override fun canBeNull(type: KtType): Boolean = withValidityAssertion {
        require(type is Fe10KtType)
        return TypeUtils.isNullableType(type.type)
    }
}