/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.references

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.references.base.CliKtFe10Reference
import org.jetbrains.kotlin.analysis.api.descriptors.references.base.Fe10KtReference
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.references.KtPropertyDelegationMethodsReference
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.resolve.BindingContext

abstract class Fe10KtPropertyDelegationMethodsReference(
    expression: KtPropertyDelegate
) : KtPropertyDelegationMethodsReference(expression), Fe10KtReference {
    override fun KtAnalysisSession.resolveToSymbols(): Collection<KtSymbol> {
        require(this is Fe10KtAnalysisSession)

        val property = expression.parent as? KtProperty
        if (property == null || property.delegate !== element) {
            return emptyList()
        }

        val bindingContext = analysisContext.analyze(property)
        val propertyDescriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, property]

        if (propertyDescriptor is PropertyDescriptor) {
            return listOfNotNull(propertyDescriptor.getter, propertyDescriptor.setter)
                .mapNotNull { accessor ->
                    val descriptor = bindingContext[BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL, accessor]?.resultingDescriptor
                    descriptor?.toKtCallableSymbol(analysisContext)
                }
        }

        return emptyList()
    }
}

internal class CliFe10KtPropertyDelegationMethodsReference(
    expression: KtPropertyDelegate
) : Fe10KtPropertyDelegationMethodsReference(expression), CliKtFe10Reference