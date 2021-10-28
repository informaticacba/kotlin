/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.base.Fe10KtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10PackageKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.descriptors.utils.cached
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.name.FqName

internal class Fe10KtPackageSymbol(
    private val packageName: FqName,
    override val analysisContext: Fe10AnalysisContext
) : KtPackageSymbol(), Fe10KtSymbol {
    override val fqName: FqName
        get() = withValidityAssertion { packageName }

    override val psi: PsiElement? by cached {
        val project = analysisContext.resolveSession.project
        JavaPsiFacade.getInstance(project).findPackage(fqName.asString())
    }

    override fun createPointer(): KtSymbolPointer<KtPackageSymbol> = withValidityAssertion {
        return Fe10PackageKtSymbolPointer(fqName)
    }

    override val origin: KtSymbolOrigin
        get() = withValidityAssertion {
            val virtualFile = PsiUtilCore.getVirtualFile(psi)
            return if (virtualFile != null) {
                analysisContext.getOrigin(virtualFile)
            } else {
                KtSymbolOrigin.LIBRARY
            }
        }
}