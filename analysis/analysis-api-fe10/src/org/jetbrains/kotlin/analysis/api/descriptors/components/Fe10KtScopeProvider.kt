/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.components

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiver
import org.jetbrains.kotlin.analysis.api.components.KtScopeContext
import org.jetbrains.kotlin.analysis.api.components.KtScopeProvider
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.components.base.Fe10KtAnalysisSessionComponent
import org.jetbrains.kotlin.analysis.api.descriptors.scopes.Fe10EmptyKtScope
import org.jetbrains.kotlin.analysis.api.descriptors.scopes.Fe10KtPackageScope
import org.jetbrains.kotlin.analysis.api.descriptors.scopes.LexicalFe10KtScope
import org.jetbrains.kotlin.analysis.api.descriptors.scopes.MemberFe10KtScope
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.Fe10FileKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.Fe10KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.base.Fe10KtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.Fe10DescKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtType
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.psiBased.base.Fe10PsiKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.psiBased.base.getResolutionScope
import org.jetbrains.kotlin.analysis.api.descriptors.types.base.Fe10KtType
import org.jetbrains.kotlin.analysis.api.impl.base.scopes.SimpleKtCompositeScope
import org.jetbrains.kotlin.analysis.api.scopes.*
import org.jetbrains.kotlin.analysis.api.symbols.KtFileSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithDeclarations
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.packageFragments
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.scopes.ChainedMemberScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.utils.getImplicitReceiversHierarchy
import org.jetbrains.kotlin.util.containingNonLocalDeclaration

internal class Fe10KtScopeProvider(
    override val analysisSession: Fe10KtAnalysisSession
) : KtScopeProvider(), Fe10KtAnalysisSessionComponent {
    private companion object {
        val LOG = Logger.getInstance(Fe10KtScopeProvider::class.java)
    }

    override val token: ValidityToken
        get() = analysisSession.token

    override fun getMemberScope(classSymbol: KtSymbolWithMembers): KtMemberScope = withValidityAssertion {
        val descriptor = getDescriptor<ClassDescriptor>(classSymbol)
            ?: return object : Fe10EmptyKtScope(token), KtMemberScope {
                override val owner get() = classSymbol
            }

        // TODO either this or declared scope should return a different set of members
        return object : MemberFe10KtScope(descriptor.unsubstitutedMemberScope, analysisContext), KtMemberScope {
            override val owner get() = classSymbol
        }
    }

    override fun getDeclaredMemberScope(classSymbol: KtSymbolWithMembers): KtDeclaredMemberScope = withValidityAssertion {
        val descriptor = getDescriptor<ClassDescriptor>(classSymbol)
            ?: return object : Fe10EmptyKtScope(token), KtDeclaredMemberScope {
                override val owner get() = classSymbol
            }

        // TODO: need to return declared members only
        return object : MemberFe10KtScope(descriptor.unsubstitutedMemberScope, analysisContext), KtDeclaredMemberScope {
            override val owner get() = classSymbol
        }
    }

    override fun getDelegatedMemberScope(classSymbol: KtSymbolWithMembers): KtDelegatedMemberScope = withValidityAssertion {
        val descriptor = getDescriptor<ClassDescriptor>(classSymbol)
            ?: return object : KtFe10EmptyScope(token), KtDelegatedMemberScope {
                override val owner get() = classSymbol
            }

        // TODO: need to return delegated members only
        return object : KtFe10ScopeMember(descriptor.unsubstitutedMemberScope, analysisSession), KtDelegatedMemberScope {
            override val owner get() = classSymbol
        }
    }

    override fun getStaticMemberScope(symbol: KtSymbolWithMembers): KtScope = withValidityAssertion {
        val descriptor = getDescriptor<ClassDescriptor>(symbol) ?: return Fe10EmptyKtScope(token)
        return MemberFe10KtScope(descriptor.staticScope, analysisContext)
    }

    override fun getFileScope(fileSymbol: KtFileSymbol): KtDeclarationScope<KtSymbolWithDeclarations> = withValidityAssertion {
        require(fileSymbol is Fe10FileKtSymbol)
        val scope = analysisContext.resolveSession.fileScopeProvider.getFileResolutionScope(fileSymbol.psi)

        return object : LexicalFe10KtScope(scope, analysisContext), KtDeclarationScope<KtSymbolWithDeclarations> {
            override val owner: KtSymbolWithDeclarations
                get() = withValidityAssertion { fileSymbol }
        }
    }

    override fun getPackageScope(packageSymbol: KtPackageSymbol): KtPackageScope = withValidityAssertion {
        require(packageSymbol is Fe10KtPackageSymbol)
        val packageFragments = analysisContext.resolveSession.packageFragmentProvider.packageFragments(packageSymbol.fqName)
        val scopeDescription = "Compound scope for package \"${packageSymbol.fqName}\""
        val chainedScope = ChainedMemberScope.create(scopeDescription, packageFragments.map { it.getMemberScope() })
        return Fe10KtPackageScope(chainedScope, packageSymbol, analysisContext)
    }

    override fun getCompositeScope(subScopes: List<KtScope>): KtCompositeScope = withValidityAssertion {
        return SimpleKtCompositeScope(subScopes, token)
    }

    override fun getTypeScope(type: KtType): KtScope = withValidityAssertion {
        require(type is Fe10KtType)
        return MemberFe10KtScope(type.type.memberScope, analysisContext)
    }

    override fun getScopeContextForPosition(originalFile: KtFile, positionInFakeFile: KtElement): KtScopeContext = withValidityAssertion {
        val elementToAnalyze = positionInFakeFile.containingNonLocalDeclaration() ?: originalFile
        val bindingContext = analysisContext.analyze(elementToAnalyze)

        val lexicalScope = positionInFakeFile.getResolutionScope(bindingContext)
        if (lexicalScope != null) {
            val compositeScope = SimpleKtCompositeScope(listOf(LexicalFe10KtScope(lexicalScope, analysisContext)), token)
            return KtScopeContext(compositeScope, collectImplicitReceivers(lexicalScope))
        }

        val fileScope = analysisContext.resolveSession.fileScopeProvider.getFileResolutionScope(originalFile)
        val compositeScope = SimpleKtCompositeScope(listOf(LexicalFe10KtScope(fileScope, analysisContext)), token)
        return KtScopeContext(compositeScope, collectImplicitReceivers(fileScope))
    }

    private inline fun <reified T : DeclarationDescriptor> getDescriptor(symbol: KtSymbol): T? {
        return when (symbol) {
            is Fe10DescKtSymbol<*> -> symbol.descriptor as? T
            is Fe10PsiKtSymbol<*, *> -> symbol.descriptor as? T
            else -> {
                require(symbol is Fe10KtSymbol) { "Unrecognized symbol implementation found" }
                null
            }
        }
    }

    private fun collectImplicitReceivers(scope: LexicalScope): MutableList<KtImplicitReceiver> {
        val result = mutableListOf<KtImplicitReceiver>()

        for (implicitReceiver in scope.getImplicitReceiversHierarchy()) {
            val type = implicitReceiver.type.toKtType(analysisContext)
            val ownerDescriptor = implicitReceiver.containingDeclaration
            val owner = ownerDescriptor.toKtSymbol(analysisContext)

            if (owner == null) {
                LOG.error("Unexpected implicit receiver owner: $ownerDescriptor (${ownerDescriptor.javaClass})")
                continue
            }

            result += KtImplicitReceiver(token, type, owner)
        }

        return result
    }
}
