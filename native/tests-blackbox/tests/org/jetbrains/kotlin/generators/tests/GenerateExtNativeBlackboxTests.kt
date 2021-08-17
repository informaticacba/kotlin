/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators.tests

import org.jetbrains.kotlin.generators.model.annotation
import org.jetbrains.kotlin.generators.generateTestGroupSuiteWithJUnit5
import org.jetbrains.kotlin.konan.blackboxtest.AbstractNativeBlackBoxTest

fun main() {
    System.setProperty("java.awt.headless", "true")

    runAndLogDuration("Generating test data for external Kotlin/Native blackbox tests") {
        generateExtNativeBlackboxTestData(
            testDataSource = "compiler/testData",
            testDataDestination = "native/tests-blackbox/ext-testData",
            sharedModules = "native/tests-blackbox/ext-testData/__shared_modules__"
        ) {
            include("codegen/box")
            include("codegen/boxInline")

            exclude("codegen/box/compileKotlinAgainstKotlin/specialBridgesInDependencies.kt")             // KT-42723
            exclude("codegen/box/collections/kt41123.kt")                                                 // KT-42723
            exclude("codegen/box/multiplatform/multiModule/expectActualTypealiasLink.kt")                 // KT-40137
            exclude("codegen/box/multiplatform/multiModule/expectActualMemberLink.kt")                    // KT-33091
            exclude("codegen/box/multiplatform/multiModule/expectActualLink.kt")                          // KT-41901
            exclude("codegen/box/coroutines/multiModule/")                                                // KT-40121
            exclude("codegen/box/compileKotlinAgainstKotlin/clashingFakeOverrideSignatures.kt")           // KT-42020
            exclude("codegen/box/callableReference/genericConstructorReference.kt")                       // ???
            exclude("codegen/boxInline/multiplatform/defaultArguments/receiversAndParametersInLambda.kt") // KT-36880

            // Temporarily disabled because of java.lang.IllegalStateException: public final expect fun lastIndex(start: kotlin.Int, end: kotlin.Int = ...): kotlin.Unit defined in codegen.box.multiplatform.multiModule.defaultArgument.Test[SimpleFunctionDescriptorImpl@364e8b0e]
            //        at org.jetbrains.kotlin.backend.konan.lower.ExpectToActualDefaultValueCopier$copyDefaultArgumentsFromExpectToActual$1.visitValueParameter(ExpectDeclarationsRemoving.kt:238)
            //        at org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid$DefaultImpls.visitValueParameter(IrElementVisitorVoid.kt:81)
            //        at org.jetbrains.kotlin.backend.konan.lower.ExpectToActualDefaultValueCopier$copyDefaultArgumentsFromExpectToActual$1.visitValueParameter(ExpectDeclarationsRemoving.kt:68)
            //        at org.jetbrains.kotlin.backend.konan.lower.ExpectToActualDefaultValueCopier$copyDefaultArgumentsFromExpectToActual$1.visitValueParameter(ExpectDeclarationsRemoving.kt:68)
            //        at org.jetbrains.kotlin.ir.declarations.IrValueParameter.accept(IrValueParameter.kt:46)
            //        at org.jetbrains.kotlin.ir.declarations.IrFunction.acceptChildren(IrFunction.kt:56)
            //        at org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoidKt.acceptChildrenVoid(IrElementVisitorVoid.kt:287)
            //        at org.jetbrains.kotlin.backend.konan.lower.ExpectToActualDefaultValueCopier$copyDefaultArgumentsFromExpectToActual$1.visitElement(ExpectDeclarationsRemoving.kt:70)
            //        at org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid$DefaultImpls.visitDeclaration(IrElementVisitorVoid.kt:40)
            //        at org.jetbrains.kotlin.backend.konan.lower.ExpectToActualDefaultValueCopier$copyDefaultArgumentsFromExpectToActual$1.visitDeclaration(ExpectDeclarationsRemoving.kt:68)
            //        at org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid$DefaultImpls.visitFunction(IrElementVisitorVoid.kt:49)
            //        at org.jetbrains.kotlin.backend.konan.lower.ExpectToActualDefaultValueCopier$copyDefaultArgumentsFromExpectToActual$1.visitFunction(ExpectDeclarationsRemoving.kt:68)
            //        at org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid$DefaultImpls.visitSimpleFunction(IrElementVisitorVoid.kt:52)
            //        at org.jetbrains.kotlin.backend.konan.lower.ExpectToActualDefaultValueCopier$copyDefaultArgumentsFromExpectToActual$1.visitSimpleFunction(ExpectDeclarationsRemoving.kt:68)
            //        at org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid$DefaultImpls.visitSimpleFunction(IrElementVisitorVoid.kt:53)
            // Test fails with the same exception in old test infra.
            exclude("codegen/box/multiplatform/multiModule/defaultArgument.kt")

        }
    }

    runAndLogDuration("Generating external Kotlin/Native blackbox tests") {
        generateTestGroupSuiteWithJUnit5 {
            cleanTestGroup(
                testsRoot = "native/tests-blackbox/ext-tests-gen",
                testDataRoot = "native/tests-blackbox/ext-testData"
            ) {
                testClass<AbstractNativeBlackBoxTest>(
                    suiteTestClassName = "NativeExtBlackBoxTestGenerated",
                    annotations = listOf(annotation(SharedModulesPath::class.java, "native/tests-blackbox/ext-testData/__shared_modules__"))
                ) {
                    model("codegen")
                }
            }
        }
    }
}
