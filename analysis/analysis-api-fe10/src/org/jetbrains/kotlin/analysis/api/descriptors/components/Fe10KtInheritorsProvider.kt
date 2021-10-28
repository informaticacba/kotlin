/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.components

import org.jetbrains.kotlin.analysis.api.components.KtInheritorsProvider
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.components.base.Fe10KtAnalysisSessionComponent
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.Fe10DescKtEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.getSymbolDescriptor
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind

internal class Fe10KtInheritorsProvider(
    override val analysisSession: Fe10KtAnalysisSession
) : KtInheritorsProvider(), Fe10KtAnalysisSessionComponent {
    override val token: ValidityToken
        get() = analysisSession.token

    override fun getInheritorsOfSealedClass(classSymbol: KtNamedClassOrObjectSymbol): List<KtNamedClassOrObjectSymbol> {
        val classDescriptor = getSymbolDescriptor(classSymbol) as? ClassDescriptor ?: return emptyList()

        val inheritorsProvider = analysisContext.resolveSession.sealedClassInheritorsProvider
        val allowInDifferentFiles = analysisContext.resolveSession.languageVersionSettings
            .supportsFeature(LanguageFeature.AllowSealedInheritorsInDifferentFilesOfSamePackage)

        return inheritorsProvider.computeSealedSubclasses(classDescriptor, allowInDifferentFiles)
            .mapNotNull { it.toKtClassifierSymbol(analysisContext) as? KtNamedClassOrObjectSymbol }
    }

    override fun getEnumEntries(classSymbol: KtNamedClassOrObjectSymbol): List<KtEnumEntrySymbol> = withValidityAssertion {
        val enumDescriptor = getSymbolDescriptor(classSymbol) as? ClassDescriptor ?: return emptyList()
        if (enumDescriptor.kind != ClassKind.ENUM_CLASS) {
            return emptyList()
        }

        val result = mutableListOf<KtEnumEntrySymbol>()

        for (entryDescriptor in enumDescriptor.unsubstitutedMemberScope.getContributedDescriptors()) {
            if (entryDescriptor is ClassDescriptor && entryDescriptor.kind == ClassKind.ENUM_ENTRY) {
                result += Fe10DescKtEnumEntrySymbol(entryDescriptor, analysisContext)
            }
        }

        return result
    }
}