/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.components

import org.jetbrains.kotlin.analysis.api.components.KtSymbolContainingDeclarationProvider
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.components.base.Fe10KtAnalysisSessionComponent
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.Fe10DescKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.psiBased.base.Fe10PsiKtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithKind
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.cfg.getElementParentDeclaration

internal class Fe10KtSymbolContainingDeclarationProvider(
    override val analysisSession: Fe10KtAnalysisSession
) : KtSymbolContainingDeclarationProvider(), Fe10KtAnalysisSessionComponent {
    override val token: ValidityToken
        get() = analysisSession.token

    override fun getContainingDeclaration(symbol: KtSymbol): KtSymbolWithKind? {
        if (symbol is KtSymbolWithKind && symbol.symbolKind == KtSymbolKind.TOP_LEVEL) {
            return null
        }

        return when (symbol) {
            is KtPackageSymbol -> null
            is KtBackingFieldSymbol -> symbol.owningProperty
            is Fe10DescKtSymbol<*> -> symbol.descriptor.containingDeclaration?.toKtSymbol(analysisContext) as? KtSymbolWithKind
            is Fe10PsiKtSymbol<*, *> -> {
                val parentDeclaration = symbol.psi.getElementParentDeclaration()
                if (parentDeclaration != null) {
                    return with(analysisSession) {
                        parentDeclaration.getSymbol() as? KtSymbolWithKind
                    }
                }

                return null
            }
            else -> null
        }
    }
}