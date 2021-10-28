/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.components

import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.components.base.Fe10KtAnalysisSessionComponent
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.Fe10FileKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.Fe10KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtClassSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.psiBased.*
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter

internal class Fe10KtSymbolProvider(
    override val analysisSession: Fe10KtAnalysisSession
) : KtSymbolProvider(), Fe10KtAnalysisSessionComponent {
    override val token: ValidityToken
        get() = analysisSession.token

    override val ROOT_PACKAGE_SYMBOL: KtPackageSymbol
        get() = Fe10KtPackageSymbol(FqName.ROOT, analysisContext)

    override fun getFileSymbol(psi: KtFile): KtFileSymbol = withValidityAssertion {
        return Fe10FileKtSymbol(psi, analysisContext)
    }

    override fun getParameterSymbol(psi: KtParameter): KtValueParameterSymbol = withValidityAssertion {
        return Fe10PsiKtValueParameterSymbol(psi, analysisContext)
    }

    override fun getFunctionLikeSymbol(psi: KtNamedFunction): KtFunctionLikeSymbol = withValidityAssertion {
        return if (psi.hasBody() && (psi.funKeyword == null || psi.nameIdentifier == null)) {
            getAnonymousFunctionSymbol(psi)
        } else {
            Fe10PsiKtFunctionSymbol(psi, analysisContext)
        }
    }

    override fun getConstructorSymbol(psi: KtConstructor<*>): KtConstructorSymbol = withValidityAssertion {
        return Fe10PsiKtConstructorSymbol(psi, analysisContext)
    }

    override fun getTypeParameterSymbol(psi: KtTypeParameter): KtTypeParameterSymbol = withValidityAssertion {
        return Fe10PsiKtTypeParameterSymbol(psi, analysisContext)
    }

    override fun getTypeAliasSymbol(psi: KtTypeAlias): KtTypeAliasSymbol = withValidityAssertion {
        return Fe10PsiKtTypeAliasSymbol(psi, analysisContext)
    }

    override fun getEnumEntrySymbol(psi: KtEnumEntry): KtEnumEntrySymbol = withValidityAssertion {
        return Fe10PsiKtEnumEntrySymbol(psi, analysisContext)
    }

    override fun getAnonymousFunctionSymbol(psi: KtNamedFunction): KtAnonymousFunctionSymbol = withValidityAssertion {
        return Fe10PsiKtAnonymousFunctionSymbol(psi, analysisContext)
    }

    override fun getAnonymousFunctionSymbol(psi: KtFunctionLiteral): KtAnonymousFunctionSymbol = withValidityAssertion {
        return Fe10PsiLiteralKtAnonymousFunctionSymbol(psi, analysisContext)
    }

    override fun getVariableSymbol(psi: KtProperty): KtVariableSymbol = withValidityAssertion {
        return if (psi.isLocal) {
            Fe10PsiKtLocalVariableSymbol(psi, analysisContext)
        } else {
            Fe10PsiKtKotlinPropertySymbol(psi, analysisContext)
        }
    }

    override fun getAnonymousObjectSymbol(psi: KtObjectLiteralExpression): KtAnonymousObjectSymbol = withValidityAssertion {
        return Fe10PsiKtAnonymousObjectSymbol(psi.objectDeclaration, analysisContext)
    }

    override fun getClassOrObjectSymbol(psi: KtClassOrObject): KtClassOrObjectSymbol = withValidityAssertion {
        return if (psi is KtObjectDeclaration && psi.isObjectLiteral()) {
            Fe10PsiKtAnonymousObjectSymbol(psi, analysisContext)
        } else {
            Fe10PsiKtNamedClassOrObjectSymbol(psi, analysisContext)
        }
    }

    override fun getNamedClassOrObjectSymbol(psi: KtClassOrObject): KtNamedClassOrObjectSymbol? = withValidityAssertion {
        if (psi is KtEnumEntry || psi.nameIdentifier == null) {
            return null
        }

        return Fe10PsiKtNamedClassOrObjectSymbol(psi, analysisContext)
    }

    override fun getPropertyAccessorSymbol(psi: KtPropertyAccessor): KtPropertyAccessorSymbol = withValidityAssertion {
        return if (psi.isGetter) {
            Fe10PsiKtPropertyGetterSymbol(psi, analysisContext)
        } else {
            Fe10PsiKtPropertySetterSymbol(psi, analysisContext)
        }
    }

    override fun getClassInitializerSymbol(psi: KtClassInitializer): KtClassInitializerSymbol = withValidityAssertion {
        return Fe10PsiKtClassInitializerSymbol(psi, analysisContext)
    }

    override fun getClassOrObjectSymbolByClassId(classId: ClassId): KtClassOrObjectSymbol? = withValidityAssertion {
        val descriptor = analysisContext.resolveSession.moduleDescriptor.findClassAcrossModuleDependencies(classId) ?: return null
        return descriptor.toKtClassSymbol(analysisContext)
    }

    override fun getTopLevelCallableSymbols(packageFqName: FqName, name: Name): Sequence<KtSymbol> = withValidityAssertion {
        val packageViewDescriptor = analysisContext.resolveSession.moduleDescriptor.getPackage(packageFqName)
        return packageViewDescriptor.memberScope.getContributedDescriptors(DescriptorKindFilter.ALL, nameFilter = { it == name })
            .asSequence()
            .filter { it.name == name }
            .mapNotNull { it.toKtSymbol(analysisContext) as? KtCallableSymbol }
    }
}