/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors

import org.jetbrains.kotlin.analysis.api.InvalidWayOfUsingAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtAnalysisSessionProvider
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.tokens.ValidityTokenFactory
import org.jetbrains.kotlin.psi.KtElement

@InvalidWayOfUsingAnalysisSession
class Fe10KtAnalysisSessionProvider : KtAnalysisSessionProvider() {
    @InvalidWayOfUsingAnalysisSession
    override fun getAnalysisSession(contextElement: KtElement, factory: ValidityTokenFactory): KtAnalysisSession {
        return Fe10KtAnalysisSession(contextElement, factory.create(contextElement.project))
    }

    @InvalidWayOfUsingAnalysisSession
    override fun getAnalysisSessionBySymbol(contextSymbol: KtSymbol): KtAnalysisSession {
        throw UnsupportedOperationException("getAnalysisSessionBySymbol() should not be used on KtFe10AnalysisSession")
    }

    override fun clearCaches() {}
}