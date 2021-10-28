/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.Fe10DescKtSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.load.java.sam.JvmSamConversionOracle
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.resolve.sam.createSamConstructorFunction
import org.jetbrains.kotlin.resolve.sam.getSingleAbstractMethodOrNull

class Fe10DescSamConstructorKtSymbolPointer(private val classId: ClassId) : KtSymbolPointer<KtSamConstructorSymbol>() {
    @Deprecated("Consider using org.jetbrains.kotlin.analysis.api.KtAnalysisSession.restoreSymbol")
    override fun restoreSymbol(analysisSession: KtAnalysisSession): KtSamConstructorSymbol? {
        check(analysisSession is Fe10KtAnalysisSession)
        val analysisContext = analysisSession.analysisContext

        val samInterface = analysisContext.resolveSession.moduleDescriptor.findClassAcrossModuleDependencies(classId)
        if (samInterface == null || getSingleAbstractMethodOrNull(samInterface) == null) {
            return null
        }

        val constructorDescriptor = createSamConstructorFunction(
            samInterface.containingDeclaration,
            samInterface,
            analysisContext.resolveSession.samConversionResolver,
            JvmSamConversionOracle(analysisContext.resolveSession.languageVersionSettings)
        )

        return Fe10DescKtSamConstructorSymbol(constructorDescriptor, analysisContext)
    }
}