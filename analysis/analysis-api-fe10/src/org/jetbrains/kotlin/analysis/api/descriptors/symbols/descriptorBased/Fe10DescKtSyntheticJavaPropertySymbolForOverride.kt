/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.base.Fe10KtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.*
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.pointers.Fe10NeverRestoringKtSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtAnnotationCall
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtPsiBasedSymbolPointer
import org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.load.java.descriptors.JavaForKotlinOverridePropertyDescriptor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension

internal class Fe10DescKtSyntheticJavaPropertySymbolForOverride(
    override val descriptor: JavaForKotlinOverridePropertyDescriptor,
    override val analysisContext: Fe10AnalysisContext
) : KtSyntheticJavaPropertySymbol(), Fe10DescMemberKtSymbol<JavaForKotlinOverridePropertyDescriptor> {
    override val name: Name
        get() = withValidityAssertion { descriptor.name }

    override val isFromPrimaryConstructor: Boolean
        get() = withValidityAssertion { descriptor.containingDeclaration is ConstructorDescriptor }

    override val isOverride: Boolean
        get() = withValidityAssertion { descriptor.isExplicitOverride }

    override val isStatic: Boolean
        get() = withValidityAssertion { DescriptorUtils.isStaticDeclaration(descriptor) }

    override val isVal: Boolean
        get() = withValidityAssertion { !descriptor.isVar }

    override val isExtension: Boolean
        get() = withValidityAssertion { descriptor.isExtension }

    override val getter: KtPropertyGetterSymbol
        get() = withValidityAssertion {
            val getter = descriptor.getter ?: return EmptyKtPropertyGetterSymbol(descriptor, analysisContext)
            return Fe10DescKtPropertyGetterSymbol(getter, analysisContext)
        }
    override val javaGetterSymbol: KtFunctionSymbol
        get() = withValidityAssertion { Fe10DescKtFunctionSymbol(descriptor.getterMethod, analysisContext) }

    override val javaSetterSymbol: KtFunctionSymbol?
        get() = withValidityAssertion {
            val setMethod = descriptor.setterMethod ?: return null
            return Fe10DescKtFunctionSymbol(setMethod, analysisContext)
        }

    override val hasSetter: Boolean
        get() = withValidityAssertion { descriptor.setter != null }

    override val setter: KtPropertySetterSymbol?
        get() = withValidityAssertion {
            val setter = descriptor.setter ?: return null
            Fe10DescKtPropertySetterSymbol(setter, analysisContext)
        }

    override val initializer: KtConstantValue?
        get() = withValidityAssertion { descriptor.compileTimeInitializer?.toKtConstantValue() }

    override val callableIdIfNonLocal: CallableId?
        get() = withValidityAssertion { descriptor.callableId }

    override val annotatedType: KtTypeAndAnnotations
        get() = withValidityAssertion { descriptor.type.toKtTypeAndAnnotations(analysisContext) }

    override val receiverType: KtTypeAndAnnotations?
        get() = withValidityAssertion { descriptor.extensionReceiverParameter?.type?.toKtTypeAndAnnotations(analysisContext) }

    override val dispatchType: KtType?
        get() = withValidityAssertion { descriptor.dispatchReceiverParameter?.type?.toKtType(analysisContext) }

    override val origin: KtSymbolOrigin
        get() = withValidityAssertion { KtSymbolOrigin.JAVA_SYNTHETIC_PROPERTY }

    override fun createPointer(): KtSymbolPointer<KtSyntheticJavaPropertySymbol> = withValidityAssertion {
        return KtPsiBasedSymbolPointer.createForSymbolFromSource(this) ?: Fe10NeverRestoringKtSymbolPointer()
    }

    private class EmptyKtPropertyGetterSymbol(
        private val descriptor: JavaForKotlinOverridePropertyDescriptor,
        override val analysisContext: Fe10AnalysisContext
    ) : KtPropertyGetterSymbol(), Fe10KtSymbol {
        override val isDefault: Boolean
            get() = withValidityAssertion { false }

        override val isInline: Boolean
            get() = withValidityAssertion { false }

        override val isOverride: Boolean
            get() = withValidityAssertion { descriptor.isExplicitOverride }

        override val hasBody: Boolean
            get() = withValidityAssertion { false }

        override val valueParameters: List<KtValueParameterSymbol>
            get() = withValidityAssertion { emptyList() }

        override val hasStableParameterNames: Boolean
            get() = withValidityAssertion { true }

        override val callableIdIfNonLocal: CallableId?
            get() = withValidityAssertion { null }

        override val annotatedType: KtTypeAndAnnotations
            get() = withValidityAssertion { descriptor.type.toKtTypeAndAnnotations(analysisContext) }

        override val origin: KtSymbolOrigin
            get() = withValidityAssertion { KtSymbolOrigin.JAVA }

        override val psi: PsiElement?
            get() = withValidityAssertion { null }

        override val receiverType: KtTypeAndAnnotations?
            get() = withValidityAssertion { descriptor.extensionReceiverParameter?.type?.toKtTypeAndAnnotations(analysisContext) }

        override val dispatchType: KtType?
            get() = withValidityAssertion { descriptor.dispatchReceiverParameter?.type?.toKtType(analysisContext) }

        override val modality: Modality
            get() = withValidityAssertion { Modality.FINAL }

        override val visibility: Visibility
            get() = withValidityAssertion { descriptor.ktVisibility }

        override val annotations: List<KtAnnotationCall>
            get() = withValidityAssertion { emptyList() }

        override fun containsAnnotation(classId: ClassId): Boolean {
            withValidityAssertion {
                return false
            }
        }

        override val annotationClassIds: Collection<ClassId>
            get() = withValidityAssertion { emptyList() }

        override fun createPointer(): KtSymbolPointer<KtPropertyGetterSymbol> {
            withValidityAssertion {
                return Fe10NeverRestoringKtSymbolPointer()
            }
        }
    }
}