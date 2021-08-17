/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest

import com.intellij.util.containers.FactoryMap
import org.jetbrains.kotlin.test.directives.model.Directive
import org.jetbrains.kotlin.test.services.JUnit5Assertions
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEquals
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertFalse
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertNotEquals
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertTrue
import org.jetbrains.kotlin.test.services.JUnit5Assertions.fail
import org.jetbrains.kotlin.test.services.impl.RegisteredDirectivesParser
import java.io.File

internal class TestProvider(environment: TestEnvironment, testCases: Collection<TestCase>) {
    private val testDataFileToTestCaseMapping = testCases.associateBy { it.origin.file }
    private val testDataFileToTestCompilationMapping = computeCompilations(environment, testCases)

    fun getTestByTestDataFile(testDataFile: File): NativeTest {
        val testCase = testDataFileToTestCaseMapping[testDataFile]
        val testCompilation = testDataFileToTestCompilationMapping[testDataFile]

        if (testCase == null || testCompilation == null)
            fail { "No test case for $testDataFile" }

        val executableFile = testCompilation.resultingArtifact // <-- Compilation happens here.

        val runParameters = when (testCase.kind) {
            TestKind.STANDALONE_NO_TR -> listOfNotNull(
                testCase.extras!!.inputData?.let(TestRunParameter::WithInputData),
                testCase.outputData?.let(TestRunParameter::WithExpectedOutputData)
            )
            TestKind.STANDALONE -> listOfNotNull(
                TestRunParameter.WithGTestLogger,
                testCase.outputData?.let(TestRunParameter::WithExpectedOutputData)
            )
            TestKind.REGULAR -> listOfNotNull(
                TestRunParameter.WithGTestLogger,
                TestRunParameter.WithPackageName(packageName = testCase.nominalPackageName),
                testCase.outputData?.let(TestRunParameter::WithExpectedOutputData)
            )
        }

        return NativeTest(executableFile, runParameters, testCase.origin)
    }

    companion object {
        private fun computeCompilations(environment: TestEnvironment, testCases: Collection<TestCase>): Map<File, TestCompilation> {
            // How exactly regular test cases are grouped?
            val regularTestCaseGroupingKey: (TestCase) -> Any = {
                when (environment.globalEnvironment.testGrouping) {
                    TestGrouping.COMPILER_ARGS -> it.freeCompilerArgs
                    TestGrouping.COMPILER_ARGS_AND_DIRECTORIES -> it.freeCompilerArgs to it.origin.file.parent
                }
            }

            val groupedRegularTestCases = hashMapOf<Any, MutableList<TestCase>>()
            val compilationFactory = TestCompilationFactory(environment)

            val compilations = hashMapOf<File, TestCompilation>()

            testCases.forEach { testCase ->
                when (testCase.kind) {
                    TestKind.STANDALONE, TestKind.STANDALONE_NO_TR -> {
                        // Create a separate compilation per each standalone test case.
                        compilations[testCase.origin.file] = compilationFactory.testCasesToExecutable(listOf(testCase))
                    }
                    TestKind.REGULAR -> {
                        // Group regular test cases.
                        groupedRegularTestCases.getOrPut(regularTestCaseGroupingKey(testCase)) { mutableListOf() } += testCase
                    }
                }
            }

            // Now, create compilations per each group of regular test cases.
            groupedRegularTestCases.forEach { (_, testCasesInGroup) ->
                val compilation = compilationFactory.testCasesToExecutable(testCasesInGroup)
                testCasesInGroup.forEach { testCase -> compilations[testCase.origin.file] = compilation }
            }

            return compilations
        }
    }
}

internal fun createBlackBoxTestProvider(environment: TestEnvironment): TestProvider {
    // Load shared modules on demand.
    val sharedModules = FactoryMap.create<String, TestModule.Shared?> { name ->
        environment.sharedModulesDir
            ?.resolve(name)
            ?.takeIf { it.isDirectory }
            ?.listFiles()
            ?.takeIf { it.isNotEmpty() }
            ?.let { files ->
                val module = TestModule.Shared(name)
                files.forEach { file -> module.files += TestFile.createCommitted(file, module) }
                module
            }
    }

    val testCases = environment.testRoots.roots.flatMap { testRoot ->
        testRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { testDataFile ->
                createTestCase(
                    testDataFile = testDataFile,
                    environment = environment,
                    findSharedModule = sharedModules::get
                )
            }
    }

    return TestProvider(environment, testCases)
}

private fun createTestCase(
    testDataFile: File,
    environment: TestEnvironment,
    findSharedModule: (name: String) -> TestModule.Shared?
): TestCase {
    val testDataFileDir = testDataFile.parentFile
    val generatedSourcesDir = environment.testSourcesDir
        .resolve(testDataFileDir.relativeTo(environment.testRoots.baseDir))
        .resolve(testDataFile.nameWithoutExtension)

    val effectivePackageName = computePackageName(testDataDir = environment.testRoots.baseDir, testDataFile = testDataFile)

    val testModules = mutableMapOf<String, TestModule.Exclusive>()
    var currentTestModule: TestModule.Exclusive? = null

    var currentTestFileName: String? = null
    val currentTestFileText = StringBuilder()

    val directivesParser = RegisteredDirectivesParser(TestDirectives, JUnit5Assertions)
    var lastParsedDirective: Directive? = null

    fun switchTestModule(newTestModule: TestModule.Exclusive, location: Location): TestModule.Exclusive {
        // Don't register new test module if there is another one with the same name.
        val testModule = testModules.getOrPut(newTestModule.name) { newTestModule }
        assertTrue(testModule === newTestModule || testModule.haveSameSymbols(newTestModule)) {
            "$location: Two declarations of the same module with different dependencies or friends found:\n$testModule\n$newTestModule"
        }

        currentTestModule = testModule
        return testModule
    }

    fun beginTestFile(fileName: String) {
        assertEquals(null, currentTestFileName)
        currentTestFileName = fileName
    }

    fun finishTestFile(forceFinish: Boolean, location: Location) {
        val needToFinish = forceFinish
                || currentTestFileName != null
                || (currentTestFileName == null /*&& testFiles.isEmpty()*/ && currentTestFileText.hasAnythingButComments())

        if (needToFinish) {
            val fileName = currentTestFileName ?: DEFAULT_FILE_NAME
            val testModule = currentTestModule ?: switchTestModule(TestModule.newDefaultModule(), location)

            testModule.files += TestFile.createUncommitted(
                location = generatedSourcesDir.resolve(fileName),
                module = testModule,
                text = currentTestFileText
            )

            currentTestFileText.clear()
            repeat(location.lineNumber ?: 0) { currentTestFileText.appendLine() }
            currentTestFileName = null
        }
    }

    testDataFile.readLines().forEachIndexed { lineNumber, line ->
        val location = Location(testDataFile, lineNumber)
        val expectFileDirectiveAfterModuleDirective =
            lastParsedDirective == TestDirectives.MODULE // Only FILE directive may follow MODULE directive.

        val rawDirective = RegisteredDirectivesParser.parseDirective(line)
        if (rawDirective != null) {
            val parsedDirective = try {
                directivesParser.convertToRegisteredDirective(rawDirective)
            } catch (e: AssertionError) {
                // Enhance error message with concrete test data file and line number where the error has happened.
                throw AssertionError("$location: Error while parsing directive in test data file.\nCause: ${e.message}", e)
            }

            if (parsedDirective != null) {
                when (val directive = parsedDirective.directive) {
                    TestDirectives.FILE -> {
                        val newFileName = parseFileName(parsedDirective, location)
                        finishTestFile(forceFinish = false, location)
                        beginTestFile(newFileName)
                    }
                    else -> {
                        assertFalse(expectFileDirectiveAfterModuleDirective) {
                            "$location: Directive $directive encountered after ${TestDirectives.MODULE} directive but was expecting ${TestDirectives.FILE}"
                        }

                        when (directive) {
                            TestDirectives.MODULE -> {
                                finishTestFile(forceFinish = false, location)
                                switchTestModule(parseModule(parsedDirective, location), location)
                            }
                            else -> {
                                assertNotEquals(TestDirectives.FILE, lastParsedDirective) {
                                    "$location: Global directive $directive encountered after ${TestDirectives.FILE} directive"
                                }
                                assertNotEquals(TestDirectives.MODULE, lastParsedDirective) {
                                    "$location: Global directive $directive encountered after ${TestDirectives.MODULE} directive"
                                }

                                directivesParser.addParsedDirective(parsedDirective)
                            }
                        }
                    }
                }

                currentTestFileText.appendLine()
                lastParsedDirective = parsedDirective.directive
                return@forEachIndexed
            }
        }

        if (expectFileDirectiveAfterModuleDirective) {
            // Was expecting a line with the FILE directive as this is the only possible continuation of a line with the MODULE directive, but failed.
            fail { "$location: ${TestDirectives.FILE} directive expected after ${TestDirectives.MODULE} directive" }
        }

        currentTestFileText.appendLine(line)
    }

    val location = Location(testDataFile)
    finishTestFile(forceFinish = true, location)

    val registeredDirectives = directivesParser.build()

    val freeCompilerArgs = parseFreeCompilerArgs(registeredDirectives, location)
    val outputData = parseOutputData(baseDir = testDataFileDir, registeredDirectives, location)
    val testKind = parseTestKind(registeredDirectives, location)

    if (testKind == TestKind.REGULAR) {
        // Fix package declarations to avoid unintended conflicts between symbols with the same name in different test cases.
        testModules.values.forEach { testModule ->
            testModule.files.forEach { testFile -> fixPackageDeclaration(testFile, effectivePackageName, testDataFile) }
        }
    }

    val testCase = TestCase(
        kind = testKind,
        modules = testModules.values.toSet(),
        freeCompilerArgs = freeCompilerArgs,
        origin = TestOrigin.SingleTestDataFile(testDataFile),
        nominalPackageName = effectivePackageName,
        outputData = outputData,
        extras = if (testKind == TestKind.STANDALONE_NO_TR) {
            TestCase.StandaloneNoTestRunnerExtras(
                entryPoint = parseEntryPoint(registeredDirectives, location),
                inputData = parseInputData(baseDir = testDataFileDir, registeredDirectives, location)
            )
        } else
            null
    )
    testCase.initialize(findSharedModule)

    return testCase
}

private fun CharSequence.hasAnythingButComments(): Boolean {
    var result = false
    runForFirstMeaningfulStatement { _, _ -> result = true }
    return result
}

private fun fixPackageDeclaration(
    testFile: TestFile<TestModule.Exclusive>,
    packageName: PackageName,
    testDataFile: File
) = testFile.update { text ->
    var existingPackageDeclarationLine: String? = null
    var existingPackageDeclarationLineNumber: Int? = null

    text.runForFirstMeaningfulStatement { lineNumber, line ->
        // First meaningful line.
        val trimmedLine = line.trim()
        if (trimmedLine.startsWith("package ")) {
            existingPackageDeclarationLine = trimmedLine
            existingPackageDeclarationLineNumber = lineNumber
        }
    }

    if (existingPackageDeclarationLine != null) {
        val existingPackageName = existingPackageDeclarationLine!!.substringAfter("package ").trimStart()
        assertTrue(
            existingPackageName == packageName
                    || (existingPackageName.length > packageName.length
                    && existingPackageName.startsWith(packageName)
                    && existingPackageName[packageName.length] == '.')
        ) {
            val location = Location(testDataFile, existingPackageDeclarationLineNumber)
            "$location: Invalid package name declaration found: $existingPackageDeclarationLine\nExpected: package $packageName"

        }
        text
    } else
        "package $packageName $text"
}

private inline fun CharSequence.runForFirstMeaningfulStatement(action: (lineNumber: Int, line: String) -> Unit) {
    var inMultilineComment = false

    for ((lineNumber, line) in lines().withIndex()) {
        val trimmedLine = line.trim()
        when {
            inMultilineComment -> inMultilineComment = !trimmedLine.endsWith("*/")
            trimmedLine.startsWith("/*") -> inMultilineComment = true
            trimmedLine.isEmpty() -> Unit
            trimmedLine.startsWith("//") -> Unit
            trimmedLine.startsWith("@file:") -> Unit
            else -> {
                action(lineNumber, line)
                break
            }
        }
    }
}
