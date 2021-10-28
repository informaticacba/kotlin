/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.references.base

import org.jetbrains.kotlin.analysis.api.descriptors.references.*
import org.jetbrains.kotlin.idea.references.KotlinPsiReferenceRegistrar
import org.jetbrains.kotlin.idea.references.KotlinReferenceProviderContributor

class Fe10KotlinReferenceProviderContributor : KotlinReferenceProviderContributor {
    override fun registerReferenceProviders(registrar: KotlinPsiReferenceRegistrar) {
        with(registrar) {
            registerProvider(factory = ::CliFe10KtSimpleNameReference)
            registerProvider(factory = ::CliFe10KtForLoopInReference)
            registerProvider(factory = ::CliFe10KtInvokeFunctionReference)
            registerProvider(factory = ::CliFe10KtPropertyDelegationMethodsReference)
            registerProvider(factory = ::CliFe10KtDestructuringDeclarationEntry)
            registerProvider(factory = ::CliFe10KtArrayAccessReference)
            registerProvider(factory = ::CliFe10KtConstructorDelegationReference)
            registerProvider(factory = ::CliFe10KtCollectionLiteralReference)
        }
    }
}