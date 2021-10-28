/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base

import org.jetbrains.kotlin.analysis.api.KtStarProjectionTypeArgument
import org.jetbrains.kotlin.analysis.api.KtTypeArgument
import org.jetbrains.kotlin.analysis.api.KtTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.components.KtDeclarationRendererOptions
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.*
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.psiBased.base.Fe10PsiKtSymbol
import org.jetbrains.kotlin.analysis.api.descriptors.types.*
import org.jetbrains.kotlin.analysis.api.descriptors.types.base.Fe10KtTypeAndAnnotations
import org.jetbrains.kotlin.analysis.api.descriptors.utils.Fe10DeclarationRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.*
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SyntheticFieldDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaCallableMemberDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaForKotlinOverridePropertyDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaPropertyDescriptor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.inference.CapturedType
import org.jetbrains.kotlin.resolve.constants.*
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.sam.SamConstructorDescriptor
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.NewCapturedType
import org.jetbrains.kotlin.types.checker.NewTypeVariableConstructor

internal val MemberDescriptor.ktSymbolKind: KtSymbolKind
    get() = when {
        containingDeclaration is PackageFragmentDescriptor -> KtSymbolKind.TOP_LEVEL
        DescriptorUtils.isLocal(this) -> KtSymbolKind.LOCAL
        else -> KtSymbolKind.CLASS_MEMBER
    }

internal val CallableMemberDescriptor.isExplicitOverride: Boolean
    get() {
        return (this !is PropertyAccessorDescriptor
                && kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE
                && overriddenDescriptors.isNotEmpty())
    }

internal val ClassDescriptor.isInterfaceLike: Boolean
    get() = when (kind) {
        ClassKind.CLASS, ClassKind.ENUM_CLASS, ClassKind.OBJECT, ClassKind.ENUM_ENTRY -> false
        else -> true
    }

internal fun DeclarationDescriptor.toKtSymbol(analysisContext: Fe10AnalysisContext): KtSymbol? {
    if (this is ClassDescriptor && kind == ClassKind.ENUM_ENTRY) {
        return Fe10DescKtEnumEntrySymbol(this, analysisContext)
    }

    return when (this) {
        is ClassifierDescriptor -> toKtClassifierSymbol(analysisContext)
        is CallableDescriptor -> toKtCallableSymbol(analysisContext)
        else -> null
    }
}

internal fun ClassifierDescriptor.toKtClassifierSymbol(analysisContext: Fe10AnalysisContext): KtClassifierSymbol? {
    return when (this) {
        is TypeAliasDescriptor -> Fe10DescKtTypeAliasSymbol(this, analysisContext)
        is TypeParameterDescriptor -> Fe10DescKtTypeParameterSymbol(this, analysisContext)
        is ClassDescriptor -> toKtClassSymbol(analysisContext)
        else -> null
    }
}

internal fun ClassDescriptor.toKtClassSymbol(analysisContext: Fe10AnalysisContext): KtClassOrObjectSymbol {
    return if (DescriptorUtils.isAnonymousObject(this)) {
        Fe10DescKtAnonymousObjectSymbol(this, analysisContext)
    } else {
        Fe10DescKtNamedClassOrObjectSymbol(this, analysisContext)
    }
}

internal fun ConstructorDescriptor.toKtConstructorSymbol(analysisContext: Fe10AnalysisContext): KtConstructorSymbol {
    if (this is TypeAliasConstructorDescriptor) {
        return this.underlyingConstructorDescriptor.toKtConstructorSymbol(analysisContext)
    }

    return Fe10DescKtConstructorSymbol(this, analysisContext)
}

internal val CallableMemberDescriptor.ktHasStableParameterNames: Boolean
    get() = when {
        this is ConstructorDescriptor && isPrimary && constructedClass.kind == ClassKind.ANNOTATION_CLASS -> true
        isExpect -> false
        else -> when (this) {
            is JavaCallableMemberDescriptor -> false
            else -> hasStableParameterNames()
        }
    }

internal fun CallableDescriptor.toKtCallableSymbol(analysisContext: Fe10AnalysisContext): KtCallableSymbol? {
    return when (this) {
        is PropertyGetterDescriptor -> Fe10DescKtPropertyGetterSymbol(this, analysisContext)
        is PropertySetterDescriptor -> Fe10DescKtPropertySetterSymbol(this, analysisContext)
        is SamConstructorDescriptor -> Fe10DescKtSamConstructorSymbol(this, analysisContext)
        is ConstructorDescriptor -> toKtConstructorSymbol(analysisContext)
        is FunctionDescriptor -> {
            if (DescriptorUtils.isAnonymousFunction(this)) {
                Fe10DescKtAnonymousFunctionSymbol(this, analysisContext)
            } else {
                Fe10DescKtFunctionSymbol(this, analysisContext)
            }
        }
        is SyntheticFieldDescriptor -> Fe10DescKtSyntheticFieldSymbol(this, analysisContext)
        is LocalVariableDescriptor -> Fe10DescKtLocalVariableSymbol(this, analysisContext)
        is ValueParameterDescriptor -> Fe10DescKtValueParameterSymbol(this, analysisContext)
        is SyntheticJavaPropertyDescriptor -> Fe10DescKtSyntheticJavaPropertySymbol(this, analysisContext)
        is JavaForKotlinOverridePropertyDescriptor -> Fe10DescKtSyntheticJavaPropertySymbolForOverride(this, analysisContext)
        is JavaPropertyDescriptor -> Fe10DescKtJavaFieldSymbol(this, analysisContext)
        is PropertyDescriptorImpl -> Fe10DescKtKotlinPropertySymbol(this, analysisContext)
        else -> null
    }
}

internal fun KotlinType.toKtType(analysisContext: Fe10AnalysisContext): KtType {
    return when (val unwrappedType = unwrap()) {
        is FlexibleType -> Fe10KtFlexibleType(unwrappedType, analysisContext)
        is DefinitelyNotNullType -> Fe10KtDefinitelyNotNullType(unwrappedType, analysisContext)
        is ErrorType -> Fe10KtClassErrorType(unwrappedType, analysisContext)
        is CapturedType -> Fe10KtCapturedType(unwrappedType, analysisContext)
        is NewCapturedType -> Fe10KtNewCapturedType(unwrappedType, analysisContext)
        is SimpleType -> {
            val typeParameterDescriptor = TypeUtils.getTypeParameterDescriptorOrNull(unwrappedType)
            if (typeParameterDescriptor != null) {
                return Fe10KtTypeParameterType(unwrappedType, typeParameterDescriptor, analysisContext)
            }

            val typeConstructor = unwrappedType.constructor

            if (typeConstructor is NewTypeVariableConstructor) {
                val newTypeParameterDescriptor = typeConstructor.originalTypeParameter
                return if (newTypeParameterDescriptor != null) {
                    Fe10KtTypeParameterType(unwrappedType, newTypeParameterDescriptor, analysisContext)
                } else {
                    Fe10KtClassErrorType(ErrorUtils.createErrorType("Unresolved type parameter type") as ErrorType, analysisContext)
                }
            }

            if (typeConstructor is IntersectionTypeConstructor) {
                return Fe10KtIntersectionType(unwrappedType, typeConstructor.supertypes, analysisContext)
            }

            return when (val typeDeclaration = typeConstructor.declarationDescriptor) {
                is FunctionClassDescriptor -> Fe10KtFunctionalType(unwrappedType, typeDeclaration, analysisContext)
                is ClassDescriptor -> Fe10KtUsualClassType(unwrappedType, typeDeclaration, analysisContext)
                else -> {
                    val errorType = ErrorUtils.createErrorTypeWithCustomConstructor("Unresolved class type", typeConstructor)
                    Fe10KtClassErrorType(errorType as ErrorType, analysisContext)
                }
            }

        }
        else -> error("Unexpected type $this")
    }
}

internal fun KotlinType.toKtTypeAndAnnotations(analysisContext: Fe10AnalysisContext): KtTypeAndAnnotations {
    return Fe10KtTypeAndAnnotations(toKtType(analysisContext), this, analysisContext.token)
}

internal fun TypeProjection.toKtTypeArgument(analysisContext: Fe10AnalysisContext): KtTypeArgument {
    return if (isStarProjection) {
        KtStarProjectionTypeArgument(analysisContext.token)
    } else {
        KtTypeArgumentWithVariance(type.toKtType(analysisContext), this.projectionKind, analysisContext.token)
    }
}

internal val KotlinType.ktNullability: KtTypeNullability
    get() = when {
        this.isNullabilityFlexible() -> KtTypeNullability.UNKNOWN
        this.isMarkedNullable -> KtTypeNullability.NULLABLE
        else -> KtTypeNullability.NON_NULLABLE
    }

internal val DeclarationDescriptorWithVisibility.ktVisibility: Visibility
    get() = when (visibility) {
        DescriptorVisibilities.PUBLIC -> Visibilities.Public
        DescriptorVisibilities.PROTECTED -> Visibilities.Protected
        DescriptorVisibilities.INTERNAL -> Visibilities.Internal
        DescriptorVisibilities.PRIVATE -> Visibilities.Private
        DescriptorVisibilities.PRIVATE_TO_THIS -> Visibilities.PrivateToThis
        DescriptorVisibilities.LOCAL -> Visibilities.Local
        DescriptorVisibilities.INVISIBLE_FAKE -> Visibilities.InvisibleFake
        DescriptorVisibilities.INHERITED -> Visibilities.Inherited
        else -> Visibilities.Unknown
    }

internal fun ConstantValue<*>.toKtConstantValue(): KtConstantValue {
    return when (this) {
        is BooleanValue -> KtLiteralConstantValue(ConstantValueKind.Boolean, value, null)
        is CharValue -> KtLiteralConstantValue(ConstantValueKind.Char, value, null)
        is ByteValue -> KtLiteralConstantValue(ConstantValueKind.Byte, value, null)
        is UByteValue -> KtLiteralConstantValue(ConstantValueKind.UnsignedByte, value, null)
        is ShortValue -> KtLiteralConstantValue(ConstantValueKind.Short, value, null)
        is UShortValue -> KtLiteralConstantValue(ConstantValueKind.UnsignedShort, value, null)
        is IntValue -> KtLiteralConstantValue(ConstantValueKind.Int, value, null)
        is UIntValue -> KtLiteralConstantValue(ConstantValueKind.UnsignedInt, value, null)
        is LongValue -> KtLiteralConstantValue(ConstantValueKind.Long, value, null)
        is ULongValue -> KtLiteralConstantValue(ConstantValueKind.UnsignedLong, value, null)
        is FloatValue -> KtLiteralConstantValue(ConstantValueKind.Float, value, null)
        is DoubleValue -> KtLiteralConstantValue(ConstantValueKind.Double, value, null)
        is NullValue -> KtLiteralConstantValue(ConstantValueKind.Null, null, null)
        is StringValue -> KtLiteralConstantValue(ConstantValueKind.String, value, null)
        is ArrayValue -> KtArrayConstantValue(value.map { it.toKtConstantValue() }, null)
        is EnumValue -> KtEnumEntryConstantValue(CallableId(enumClassId, enumEntryName), null)
        is AnnotationValue -> {
            val arguments = value.allValueArguments.map { (name, v) -> KtNamedConstantValue(name.asString(), v.toKtConstantValue()) }
            KtAnnotationConstantValue(value.annotationClass?.classId, arguments, null)
        }
        else -> KtUnsupportedConstantValue
    }
}

internal val CallableMemberDescriptor.callableId: CallableId?
    get() {
        var current: DeclarationDescriptor = containingDeclaration

        val localName = mutableListOf<String>()
        val className = mutableListOf<String>()

        while (true) {
            when (current) {
                is PackageFragmentDescriptor -> {
                    return CallableId(
                        packageName = current.fqName,
                        className = if (className.isNotEmpty()) FqName.fromSegments(className.asReversed()) else null,
                        callableName = name,
                        pathToLocal = if (localName.isNotEmpty()) FqName.fromSegments(localName.asReversed()) else null
                    )
                }
                is ClassDescriptor -> className += current.name.asString()
                is PropertyAccessorDescriptor -> {} // Filter out property accessors
                is CallableDescriptor -> localName += current.name.asString()
            }

            current = current.containingDeclaration ?: return null
        }
    }

internal fun getSymbolDescriptor(symbol: KtSymbol): DeclarationDescriptor? {
    return when (symbol) {
        is Fe10DescKtSymbol<*> -> symbol.descriptor
        is Fe10PsiKtSymbol<*, *> -> symbol.descriptor
        else -> null
    }
}

internal val ClassifierDescriptor.classId: ClassId?
    get() = when (val owner = containingDeclaration) {
        is PackageFragmentDescriptor -> ClassId(owner.fqName, name)
        is ClassifierDescriptorWithTypeParameters -> owner.classId?.createNestedClassId(name)
        else -> null
    }

internal val ClassifierDescriptor.maybeLocalClassId: ClassId
    get() = classId ?: ClassId(containingPackage() ?: FqName.ROOT, FqName.topLevel(this.name), true)

internal fun ClassDescriptor.getSupertypesWithAny(): Collection<KotlinType> {
    val supertypes = typeConstructor.supertypes
    if (isInterfaceLike) {
        return supertypes
    }

    val hasClassSupertype = supertypes.any { (it.constructor.declarationDescriptor as? ClassDescriptor)?.kind == ClassKind.CLASS }
    return if (hasClassSupertype) supertypes else listOf(builtIns.anyType) + supertypes
}

internal fun DeclarationDescriptor.render(analysisContext: Fe10AnalysisContext, options: KtDeclarationRendererOptions): String {
    val renderer = Fe10DeclarationRenderer(analysisContext, options)
    val consumer = StringBuilder()
    renderer.render(this, consumer)
    return consumer.toString().trim()
}

internal fun CallableMemberDescriptor.getSymbolPointerSignature(analysisContext: Fe10AnalysisContext): String {
    return render(analysisContext, KtDeclarationRendererOptions.DEFAULT)
}