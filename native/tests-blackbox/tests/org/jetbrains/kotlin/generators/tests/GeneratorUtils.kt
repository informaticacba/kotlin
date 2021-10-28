/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("unused")

package org.jetbrains.kotlin.generators.tests

import org.jetbrains.kotlin.generators.TestGroup
import org.jetbrains.kotlin.generators.TestGroupSuite
import org.jetbrains.kotlin.konan.blackboxtest.deleteRecursivelyWithLogging
import org.jetbrains.kotlin.konan.blackboxtest.getAbsoluteFile
import kotlin.system.measureTimeMillis

/** Fully clean-up the tests root directory before generating new test classes. */
internal fun TestGroupSuite.cleanTestGroup(testsRoot: String, testDataRoot: String, init: TestGroup.() -> Unit) {
    getAbsoluteFile(localPath = testsRoot).deleteRecursivelyWithLogging()
    testGroup(testsRoot, testDataRoot, init = init)
}

internal fun runAndLogDuration(actionDescription: String, action: () -> Unit) {
    println("$actionDescription...")

    val duration = measureTimeMillis(action)
    val seconds = duration / 1000
    val millis = duration % 100

    println(
        buildString {
            append(actionDescription)
            append(" took ")
            if (seconds > 0) append(seconds).append("s ")
            append(millis).append("ms")
        }
    )
}
