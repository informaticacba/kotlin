/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest

import com.intellij.testFramework.TestDataFile
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(NativeBlackBoxTestSupport::class)
abstract class AbstractNativeBlackBoxTest {
    internal lateinit var blackBoxTestProvider: TestProvider

    fun runTest(@TestDataFile testDataFilePath: String) {
        blackBoxTestProvider.getTestByTestDataFile(getAbsoluteFile(testDataFilePath)).runAndVerify()
    }
}
