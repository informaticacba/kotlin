/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest

import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEquals
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertTrue
import kotlin.properties.Delegates

internal fun NativeTest.runAndVerify() {
    val programArgs = mutableListOf<String>(executableFile.path)
    runParameters.forEach { it.applyTo(programArgs) }

    val startTimeMillis = System.currentTimeMillis()
    val process = ProcessBuilder(programArgs).directory(executableFile.parentFile).start()
    runParameters.get<TestRunParameter.WithInputData> {
        process.outputStream.write(inputData.toByteArray())
        process.outputStream.flush()
    }

    TestOutput(runParameters, programArgs, process, startTimeMillis, origin).verify()
}

private class TestOutput(
    private val runParameters: List<TestRunParameter>,
    private val programArgs: List<String>,
    private val process: Process,
    private val startTimeMillis: Long,
    private val origin: TestOrigin.SingleTestDataFile
) {
    private var exitCode: Int by Delegates.notNull()
    private lateinit var stdOut: String
    private lateinit var stdErr: String
    private var finishTimeMillis by Delegates.notNull<Long>()

    fun verify() {
        waitUntilExecutionFinished()

        assertEquals(0, exitCode) { "Process exited with non-zero code.${details()}" }

        if (runParameters.has<TestRunParameter.WithGTestLogger>()) {
            verifyTestWithGTestRunner()
        } else {
            verifyPlainTest()
        }
    }

    private fun verifyTestWithGTestRunner() {
        val testStatuses = mutableMapOf<TestStatus, MutableSet<TestName>>()
        val cleanStdOut = StringBuilder()

        var expectStatusLine = false
        stdOut.lines().forEach { line ->
            when {
                expectStatusLine -> {
                    val matcher = GTEST_STATUS_LINE_REGEX.matchEntire(line)
                    if (matcher != null) {
                        // Read the line with test status.
                        val testStatus = matcher.groupValues[1]
                        val testName = matcher.groupValues[2]
                        testStatuses.getOrPut(testStatus) { hashSetOf() } += testName
                        expectStatusLine = false
                    } else {
                        // If current line is not a status line then it could be only the line with the process' output.
                        cleanStdOut.appendLine(line)
                    }
                }
                line.startsWith(GTEST_RUN_LINE_PREFIX) -> {
                    expectStatusLine = true // Next line contains either  test status.
                }
                else -> Unit
            }
        }

        assertTrue(testStatuses.isNotEmpty()) { "No tests have been executed.${details()}" }

        val passedTests = testStatuses[GTEST_STATUS_OK]?.size ?: 0
        assertTrue(passedTests > 0) { "No passed tests.${details()}" }

        runParameters.get<TestRunParameter.WithPackageName> {
            val excessiveTests = testStatuses.getValue(GTEST_STATUS_OK).filter { testName -> !testName.startsWith(packageName) }
            assertTrue(excessiveTests.isEmpty()) { "Excessive tests have been executed: $excessiveTests.${details()}" }
        }

        val failedTests = (testStatuses - GTEST_STATUS_OK).values.sumOf { it.size }
        assertEquals(0, failedTests) { "There are failed tests.${details()}" }

        runParameters.get<TestRunParameter.WithExpectedOutputData> {
            val mergedOutput = cleanStdOut.toString() + stdErr
            assertEquals(expectedOutputData, mergedOutput) { "Process output mismatch.${details()}" }
        }
    }

    private fun verifyPlainTest() {
        runParameters.get<TestRunParameter.WithExpectedOutputData> {
            val mergedOutput = stdOut + stdErr
            assertEquals(expectedOutputData, mergedOutput) { "Process output mismatch.${details()}" }
        }
    }

    private fun waitUntilExecutionFinished() {
        exitCode = process.waitFor()
        finishTimeMillis = System.currentTimeMillis()
        stdOut = process.inputStream.bufferedReader().readText()
        stdErr = process.errorStream.bufferedReader().readText()
    }

    private fun details() = buildString {
        append("\n\nProgram arguments: [\n")
        append(formatProcessArguments(programArgs, indentation = "\t"))
        append("\n]\n")
        append(formatProcessOutput(exitCode, stdOut, stdErr, finishTimeMillis - startTimeMillis, origin))
    }

    companion object {
        private const val GTEST_RUN_LINE_PREFIX = "[ RUN      ]"
        private val GTEST_STATUS_LINE_REGEX = Regex("^\\[\\s+([A-Z]+)\\s+]\\s+(\\S+)\\s+.*")
        private const val GTEST_STATUS_OK = "OK"
    }
}

private typealias TestStatus = String
private typealias TestName = String
