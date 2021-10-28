/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.descriptors.components.*
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolProvider
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

@Suppress("LeakingThis")
class Fe10KtAnalysisSession(val analysisContext: Fe10AnalysisContext) : KtAnalysisSession(analysisContext.token) {
    constructor(contextElement: KtElement, token: ValidityToken) :
            this(Fe10AnalysisContext(Fe10AnalysisFacade.getInstance(contextElement.project), contextElement, token))

    override val smartCastProviderImpl: KtSmartCastProvider = Fe10KtSmartCastProvider(this)
    override val diagnosticProviderImpl: KtDiagnosticProvider = Fe10KtDiagnosticProvider(this)
    override val scopeProviderImpl: KtScopeProvider = Fe10KtScopeProvider(this)
    override val containingDeclarationProviderImpl: KtSymbolContainingDeclarationProvider = Fe10KtSymbolContainingDeclarationProvider(this)
    override val symbolProviderImpl: KtSymbolProvider = Fe10KtSymbolProvider(this)
    override val callResolverImpl: KtCallResolver = Fe10KtCallResolver(this)
    override val completionCandidateCheckerImpl: KtCompletionCandidateChecker = Fe10KtCompletionCandidateChecker(this)
    override val symbolDeclarationOverridesProviderImpl: KtSymbolDeclarationOverridesProvider = Fe10KtSymbolDeclarationOverridesProvider(this)
    override val referenceShortenerImpl: KtReferenceShortener = Fe10KtReferenceShortener(this)
    override val symbolDeclarationRendererProviderImpl: KtSymbolDeclarationRendererProvider = Fe10KtSymbolDeclarationRendererProvider(this)
    override val expressionTypeProviderImpl: KtExpressionTypeProvider = Fe10KtExpressionTypeProvider(this)
    override val psiTypeProviderImpl: KtPsiTypeProvider = Fe10KtPsiTypeProvider(this)
    override val typeProviderImpl: KtTypeProvider = Fe10KtTypeProvider(this)
    override val typeInfoProviderImpl: KtTypeInfoProvider = Fe10KtTypeInfoProvider(this)
    override val subtypingComponentImpl: KtSubtypingComponent = Fe10KtSubtypingComponent(this)
    override val expressionInfoProviderImpl: KtExpressionInfoProvider = Fe10KtExpressionInfoProvider(this)
    override val compileTimeConstantProviderImpl: KtCompileTimeConstantProvider = Fe10KtCompileTimeConstantProvider(this)
    override val visibilityCheckerImpl: KtVisibilityChecker = Fe10KtVisibilityChecker(this)
    override val overrideInfoProviderImpl: KtOverrideInfoProvider = Fe10KtOverrideInfoProvider(this)
    override val inheritorsProviderImpl: KtInheritorsProvider = Fe10KtInheritorsProvider(this)
    override val typesCreatorImpl: KtTypeCreator = Fe10KtTypeCreator(this)
    override val samResolverImpl: KtSamResolver = Fe10KtSamResolver(this)
    override val importOptimizerImpl: KtImportOptimizer = Fe10KtImportOptimizer(this)
    override val jvmTypeMapperImpl: KtJvmTypeMapper = Fe10KtJvmTypeMapper(this)
    override val symbolInfoProviderImpl: KtSymbolInfoProvider = Fe10KtSymbolInfoProvider(this)

    override fun createContextDependentCopy(originalKtFile: KtFile, elementToReanalyze: KtElement): KtAnalysisSession {
        return Fe10KtAnalysisSession(elementToReanalyze, token)
    }
}