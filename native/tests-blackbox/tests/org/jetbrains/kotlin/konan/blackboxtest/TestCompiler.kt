/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.compilerRunner.OutputItemsCollectorImpl
import org.jetbrains.kotlin.compilerRunner.processCompilerOutput
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.konan.blackboxtest.TestModule.Companion.allDependencies
import org.jetbrains.kotlin.konan.blackboxtest.TestModule.Companion.allFriends
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEquals
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertFalse
import org.jetbrains.kotlin.test.services.JUnit5Assertions.fail
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.*
import kotlin.system.measureTimeMillis

internal class TestCompilationFactory(private val environment: TestEnvironment) {
    private val sharedModulesOutputDir = environment.testBinariesDir.resolve("__shared_modules__").apply { mkdirs() }
    private val cachedCompilations = hashMapOf<TestCompilationCacheKey, TestCompilation>()

    private sealed interface TestCompilationCacheKey {
        data class Klib(val sourceModules: Set<TestModule>, val freeCompilerArgs: TestCompilerArgs) : TestCompilationCacheKey
        data class Executable(val sourceModules: Set<TestModule>) : TestCompilationCacheKey
    }

    fun testCasesToExecutable(testCases: Collection<TestCase>): TestCompilation {
        val rootModules = testCases.mapToSet { testCase -> testCase.topologicallyOrderedModules.first() as TestModule.Exclusive }
        val cacheKey = TestCompilationCacheKey.Executable(rootModules)

        return cachedCompilations.getOrPut(cacheKey) {
            val freeCompilerArgs = rootModules.first().testCase.freeCompilerArgs
            val entryPoint = testCases.singleOrNull()?.extras?.entryPoint

            TestCompilationImpl(
                environment = environment,
                freeCompilerArgs = freeCompilerArgs,
                sourceModules = rootModules,
                dependencies = TestCompilationDependencies(
                    libraries = rootModules.flatMapToSet { it.allDependencies }.map { moduleToKlib(it, freeCompilerArgs) },
                    friends = rootModules.flatMapToSet { it.allFriends }.map { moduleToKlib(it, freeCompilerArgs) }
                ),
                expectedArtifactFile = artifactFileForExecutable(rootModules),
                specificCompilerArgs = {
                    add("-produce", "program")
                    if (entryPoint != null) add("-entry", entryPoint) else add("-generate-test-runner")
                    environment.globalEnvironment.getRootCacheDirectory(debuggable = true)?.let { rootCacheDir ->
                        add("-Xcache-directory=$rootCacheDir")
                    }
                }
            )
        }
    }

    private fun moduleToKlib(sourceModule: TestModule, freeCompilerArgs: TestCompilerArgs): TestCompilation {
        val sourceModules = setOf(sourceModule)
        val cacheKey = TestCompilationCacheKey.Klib(sourceModules, freeCompilerArgs)

        return cachedCompilations.getOrPut(cacheKey) {
            TestCompilationImpl(
                environment = environment,
                freeCompilerArgs = freeCompilerArgs,
                sourceModules = sourceModules,
                dependencies = TestCompilationDependencies(
                    libraries = sourceModule.allDependencies.map { moduleToKlib(it, freeCompilerArgs) },
                    friends = sourceModule.allFriends.map { moduleToKlib(it, freeCompilerArgs) }
                ),
                expectedArtifactFile = artifactFileForKlib(sourceModule, freeCompilerArgs),
                specificCompilerArgs = { add("-produce", "library") }
            )
        }
    }

    private fun artifactFileForExecutable(modules: Set<TestModule.Exclusive>) = when (modules.size) {
        1 -> artifactFileForExecutable(modules.first())
        else -> multiModuleArtifactFile(modules, environment.globalEnvironment.target.family.exeSuffix)
    }

    private fun artifactFileForExecutable(module: TestModule.Exclusive) =
        singleModuleArtifactFile(module, environment.globalEnvironment.target.family.exeSuffix)

    private fun artifactFileForKlib(module: TestModule, freeCompilerArgs: TestCompilerArgs) = when (module) {
        is TestModule.Exclusive -> singleModuleArtifactFile(module, "klib")
        is TestModule.Shared -> sharedModulesOutputDir.resolve("${module.name}-${prettyHash(freeCompilerArgs.hashCode())}.klib")
    }

    private fun singleModuleArtifactFile(module: TestModule.Exclusive, extension: String): File {
        val artifactFileName = buildString {
            append(module.testCase.nominalPackageName.replace('.', '_')).append('.')
            if (extension == "klib") append(module.name).append('.')
            append(extension)
        }
        return artifactDirForPackageName(module.testCase.nominalPackageName).resolve(artifactFileName)
    }

    private fun multiModuleArtifactFile(modules: Collection<TestModule>, extension: String): File {
        var filesCount = 0
        var hash = 0
        val uniquePackageNames = hashSetOf<PackageName>()

        modules.forEach { module ->
            module.files.forEach { file ->
                filesCount++
                hash = hash * 31 + file.hashCode()
            }

            if (module is TestModule.Exclusive)
                uniquePackageNames += module.testCase.nominalPackageName
        }

        val commonPackageName = uniquePackageNames.findCommonPackageName()

        val artifactFileName = buildString {
            val prefix = filesCount.toString()
            repeat(4 - prefix.length) { append('0') }
            append(prefix).append('-')

            if (commonPackageName != null)
                append(commonPackageName.replace('.', '_')).append('-')

            append(prettyHash(hash))

            append('.').append(extension)
        }

        return artifactDirForPackageName(commonPackageName).resolve(artifactFileName)
    }

    private fun artifactDirForPackageName(packageName: PackageName?): File {
        val baseDir = environment.testBinariesDir
        val outputDir = if (packageName != null) baseDir.resolve(packageName.replace('.', '_')) else baseDir

        outputDir.mkdirs()

        return outputDir
    }

    private fun prettyHash(hash: Int) = hash.toUInt().toString(16).padStart(8, '0')
}

internal interface TestCompilation {
    val output: TestCompilationOutput

    val resultingArtifact: File
        get() = output.safeAs<TestCompilationOutput.Success>()?.resultingArtifact
            ?: fail { "Compilation $this does not have resulting artifact. The output is $output." }
}

internal sealed class TestCompilationOutput {
    data class Success(val resultingArtifact: File) : TestCompilationOutput()
    data class Failure(val throwable: Throwable) : TestCompilationOutput()
    data class DependencyFailures(val causes: Set<Failure>) : TestCompilationOutput()
}

/**
 * The dependencies of a particular [TestCompilationImpl].
 *
 * [libraries] - the [TestCompilation]s (modules) that should yield KLIBs to be consumed as dependency libraries in the current compilation.
 * [friends] - similarly but friend modules (-friend-modules).
 * [includedLibraries] - similarly but included modules (-Xinclude).
 */
private class TestCompilationDependencies(
    val libraries: Collection<TestCompilation> = emptyList(),
    val friends: Collection<TestCompilation> = emptyList(),
    val includedLibraries: Collection<TestCompilation> = emptyList()
) {
    fun collectFailures(): Set<TestCompilationOutput.Failure> = listOf(libraries, friends, includedLibraries)
        .flatten()
        .flatMapToSet { compilation ->
            when (val output = compilation.output) {
                is TestCompilationOutput.Failure -> listOf(output)
                is TestCompilationOutput.DependencyFailures -> output.causes
                is TestCompilationOutput.Success -> emptyList()
            }
        }

    companion object {
        val EMPTY = TestCompilationDependencies(emptyList(), emptyList(), emptyList())
    }
}

private class TestCompilationImpl(
    private val environment: TestEnvironment,
    private val freeCompilerArgs: TestCompilerArgs,
    private val sourceModules: Collection<TestModule>,
    private val dependencies: TestCompilationDependencies,
    private val expectedArtifactFile: File,
    private val specificCompilerArgs: ArgsBuilder.() -> Unit
) : TestCompilation {
    // Runs the compiler and memorizes the result on property access.
    override val output: TestCompilationOutput by lazy {
        val failures = dependencies.collectFailures()
        if (failures.isNotEmpty())
            TestCompilationOutput.DependencyFailures(causes = failures)
        else try {
            doCompile()
            TestCompilationOutput.Success(expectedArtifactFile)
        } catch (t: Throwable) {
            TestCompilationOutput.Failure(t)
        }
    }

    private fun ArgsBuilder.applyCommonArgs() {
        add(
            "-enable-assertions",
            "-g",
            "-target", environment.globalEnvironment.target.name,
            "-repo", environment.globalEnvironment.kotlinNativeHome.resolve("klib").path,
            "-output", expectedArtifactFile.path,
            "-Xskip-prerelease-check",
            "-Xverify-ir"
        )

        addFlattened(dependencies.libraries) { library -> listOf("-l", library.resultingArtifact.path) }
        dependencies.friends.takeIf(Collection<*>::isNotEmpty)?.let { friends ->
            add("-friend-modules", friends.joinToString(File.pathSeparator) { friend -> friend.resultingArtifact.path })
        }
        add(dependencies.includedLibraries) { include -> "-Xinclude=${include.resultingArtifact.path}" }
        add(freeCompilerArgs.compilerArgs)
    }

    private fun ArgsBuilder.applySources() {
        addFlattenedTwice(sourceModules, { it.files }) { it.location.path }
    }

    private fun doCompile() {
        val args = buildArgs {
            applyCommonArgs()
            specificCompilerArgs()
            applySources()
        }

        val origin = TestOrigin.ManyTestDataFiles(
            sourceModules.mapNotNullToSet { module -> (module as? TestModule.Exclusive)?.testCase?.origin?.file }
        )

        runCompiler(args, origin, expectedArtifactFile, environment.globalEnvironment.lazyKotlinNativeClassLoader)
    }
}

private class ArgsBuilder {
    private val args = mutableListOf<String>()

    fun add(vararg args: String) {
        this.args += args
    }

    fun add(args: Iterable<String>) {
        this.args += args
    }

    inline fun <T> add(rawArgs: Iterable<T>, transform: (T) -> String) {
        rawArgs.mapTo(args) { transform(it) }
    }

    inline fun <T> addFlattened(rawArgs: Iterable<T>, transform: (T) -> Iterable<String>) {
        rawArgs.flatMapTo(args) { transform(it) }
    }

    inline fun <T, R> addFlattenedTwice(rawArgs: Iterable<T>, transform1: (T) -> Iterable<R>, transform2: (R) -> String) {
        rawArgs.forEach { add(transform1(it), transform2) }
    }

    fun build(): Array<String> = args.toTypedArray()
}

private inline fun buildArgs(builderAction: ArgsBuilder.() -> Unit): Array<String> {
    return ArgsBuilder().apply(builderAction).build()
}

private fun runCompiler(
    args: Array<String>,
    origin: TestOrigin.ManyTestDataFiles,
    expectedArtifactFile: File,
    lazyKotlinNativeClassLoader: Lazy<ClassLoader>
) {
    val compilerArgsFile = dumpCompilerArgs(args, expectedArtifactFile)

    val compilerXmlOutput: ByteArrayOutputStream
    val exitCode: ExitCode

    val durationMillis = measureTimeMillis {
        val kotlinNativeClassLoader by lazyKotlinNativeClassLoader

        val servicesClass = Class.forName(Services::class.java.canonicalName, true, kotlinNativeClassLoader)
        val emptyServices = servicesClass.getField("EMPTY").get(servicesClass)

        val compilerClass = Class.forName("org.jetbrains.kotlin.cli.bc.K2Native", true, kotlinNativeClassLoader)
        val entryPoint = compilerClass.getMethod(
            "execAndOutputXml",
            PrintStream::class.java,
            servicesClass,
            Array<String>::class.java
        )

        compilerXmlOutput = ByteArrayOutputStream()
        exitCode = PrintStream(compilerXmlOutput).use { printStream ->
            val result = entryPoint.invoke(compilerClass.getDeclaredConstructor().newInstance(), printStream, emptyServices, args)
            ExitCode.valueOf(result.toString())
        }
    }

    val messageCollector: MessageCollector
    val compilerOutput: String

    ByteArrayOutputStream().use { outputStream ->
        PrintStream(outputStream).use { printStream ->
            messageCollector = GroupingMessageCollector(
                PrintingMessageCollector(printStream, MessageRenderer.SYSTEM_INDEPENDENT_RELATIVE_PATHS, true),
                false
            )
            processCompilerOutput(
                messageCollector,
                OutputItemsCollectorImpl(),
                compilerXmlOutput,
                exitCode
            )
            messageCollector.flush()
        }
        compilerOutput = outputStream.toString(Charsets.UTF_8.name())
    }

    val formattedCompilerOutput = formatCompilerOutput(exitCode, compilerOutput, durationMillis, compilerArgsFile, origin)
    dumpCompilerOutput(formattedCompilerOutput, expectedArtifactFile)

    assertEquals(ExitCode.OK, exitCode) { "Compilation finished with non-zero exit code.\n\n$formattedCompilerOutput" }
    assertFalse(messageCollector.hasErrors()) { "Compilation finished with errors.\n\n$formattedCompilerOutput" }
}

private fun dumpCompilerArgs(args: Array<String>, expectedArtifactFile: File): File {
    val outputFile = expectedArtifactFile.resolveSibling(expectedArtifactFile.name + ".args")
    outputFile.writeText(formatProcessArguments(args.asList()))
    return outputFile
}

private fun dumpCompilerOutput(formattedCompilerOutput: String, expectedArtifactFile: File) {
    val outputFile = expectedArtifactFile.resolveSibling(expectedArtifactFile.name + ".out")
    outputFile.writeText(formattedCompilerOutput)
}
