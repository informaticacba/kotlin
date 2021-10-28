/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.components

import org.jetbrains.kotlin.analysis.api.components.KtSubtypingComponent
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.components.base.Fe10KtAnalysisSessionComponent
import org.jetbrains.kotlin.analysis.api.descriptors.types.base.Fe10KtType
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.withValidityAssertion

internal class Fe10KtSubtypingComponent(
    override val analysisSession: Fe10KtAnalysisSession
) : KtSubtypingComponent(), Fe10KtAnalysisSessionComponent {
    override val token: ValidityToken
        get() = analysisSession.token

    override fun isEqualTo(first: KtType, second: KtType): Boolean = withValidityAssertion {
        require(first is Fe10KtType)
        require(second is Fe10KtType)
        return analysisContext.resolveSession.kotlinTypeCheckerOfOwnerModule.equalTypes(first.type, second.type)
    }

    override fun isSubTypeOf(subType: KtType, superType: KtType): Boolean = withValidityAssertion {
        require(subType is Fe10KtType)
        require(superType is Fe10KtType)
        val typeChecker = analysisContext.resolveSession.kotlinTypeCheckerOfOwnerModule
        return typeChecker.isSubtypeOf(subType.type, superType.type)
    }
}