/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.Fe10KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.name.FqName

internal class Fe10PackageKtSymbolPointer(private val packageName: FqName) : KtSymbolPointer<KtPackageSymbol>() {
    @Deprecated("Consider using org.jetbrains.kotlin.analysis.api.KtAnalysisSession.restoreSymbol")
    override fun restoreSymbol(analysisSession: KtAnalysisSession): KtPackageSymbol {
        check(analysisSession is Fe10KtAnalysisSession)
        return Fe10KtPackageSymbol(packageName, analysisSession.analysisContext)
    }
}