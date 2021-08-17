/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest

import org.jetbrains.kotlin.konan.blackboxtest.DFSWithoutCycles.topologicalOrder
import org.jetbrains.kotlin.konan.blackboxtest.TestModule.Companion.allDependencies
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEquals
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertTrue
import org.jetbrains.kotlin.test.services.JUnit5Assertions.fail
import java.io.File

internal typealias PackageName = String

/**
 * Helps to track the origin of the [TestCompilation] or [NativeTest]. Used for reporting purposes.
 */
internal interface TestOrigin {
    class SingleTestDataFile(val file: File) : TestOrigin {
        override fun toString(): String = file.path
    }

    class ManyTestDataFiles(private val files: Set<File>) : TestOrigin {
        fun format(indentation: String = ""): String =
            files.map { it.path }.sorted().joinToString("\n") { path -> "$indentation$path" }

        override fun toString() = format()
    }
}

/**
 * Represents a single file that will be supplied to the compiler.
 */
internal class TestFile<M : TestModule> private constructor(
    val location: File,
    val module: M,
    private var state: State
) {
    private sealed interface State {
        object Committed : State
        class Uncommitted(var text: String) : State
    }

    fun update(transformation: (String) -> String) {
        when (val state = state) {
            is State.Uncommitted -> state.text = transformation(state.text)
            is State.Committed -> fail { "File $location is already committed." }
        }
    }

    // An optimization to release the memory occupied by numerous file texts.
    fun commit() {
        state = when (val state = state) {
            is State.Uncommitted -> {
                location.parentFile.mkdirs()
                location.writeText(state.text)
                State.Committed
            }
            is State.Committed -> state
        }
    }

    override fun equals(other: Any?) = other === this || (other as? TestFile<*>)?.location?.path == location.path
    override fun hashCode() = location.path.hashCode()
    override fun toString() = "TestFile(location=$location, module.name=${module.name}, state=${state::class.java.simpleName})"

    companion object {
        fun <M : TestModule> createUncommitted(location: File, module: M, text: CharSequence) =
            TestFile(location, module, State.Uncommitted(text.toString()))

        fun <M : TestModule> createCommitted(location: File, module: M) =
            TestFile(location, module, State.Committed)
    }
}

/**
 * One or more [TestFile]s that are always compiled together.
 *
 * Please note that [TestModule] is the minimal possible compilation unit, but not always the maximal possible compilation unit.
 * In certain test modes (ex: [TestMode.ONE_STAGE], [TestMode.TWO_STAGE]) modules represented by [TestModule] are ignored, and
 * all [TestFile]s are compiled together in one shot.
 *
 * [TestModule.Exclusive] represents a collection of [TestFile]s used exclusively for an individual [TestCase].
 * [TestModule.Shared] represents a "shared" module, i.e. the auxiliary module that can be used in multiple [TestCase]s.
 */
internal sealed class TestModule {
    abstract val name: String
    abstract val files: Set<TestFile<*>>

    data class Exclusive(
        override val name: String,
        val directDependencySymbols: Set<String>,
        val directFriendSymbols: Set<String>
    ) : TestModule() {
        override val files: FailOnDuplicatesSet<TestFile<Exclusive>> = FailOnDuplicatesSet()

        lateinit var directDependencies: Set<TestModule>
        lateinit var directFriends: Set<TestModule>

        // N.B. The following two properties throw an exception on attempt to resolve cyclic dependencies.
        val allDependencies: Set<TestModule> by SM.lazyNeighbors({ directDependencies }, { it.allDependencies })
        val allFriends: Set<TestModule> by SM.lazyNeighbors({ directFriends }, { it.allFriends })

        lateinit var testCase: TestCase

        fun commit() {
            files.forEach { it.commit() }
        }

        fun haveSameSymbols(other: Exclusive) =
            other.directDependencySymbols == directDependencySymbols && other.directFriendSymbols == directFriendSymbols
    }

    class Shared(override val name: String) : TestModule() {
        override val files: FailOnDuplicatesSet<TestFile<Shared>> = FailOnDuplicatesSet()
    }

    final override fun equals(other: Any?) =
        other === this || (other is TestModule && other.javaClass == javaClass && other.name == name && other.files == files)

    final override fun hashCode() = (javaClass.hashCode() * 31 + name.hashCode()) * 31 + files.hashCode()
    final override fun toString() = "${javaClass.canonicalName}[name=$name]"

    companion object {
        fun newDefaultModule() = Exclusive(DEFAULT_MODULE_NAME, emptySet(), emptySet())

        val TestModule.allDependencies: Set<TestModule>
            get() = when (this) {
                is Exclusive -> allDependencies
                is Shared -> emptySet()
            }

        val TestModule.allFriends: Set<TestModule>
            get() = when (this) {
                is Exclusive -> allFriends
                is Shared -> emptySet()
            }

        private val SM = LockBasedStorageManager(TestModule::class.java.name)
    }
}

/**
 * A minimal testable unit.
 *
 * [modules] - the collection of [TestModule.Exclusive] modules with [TestFile]s that need to be compiled to run this test.
 *             Note: There can also be [TestModule.Shared] modules as dependencies of either of [TestModule.Exclusive] modules.
 *             See [TestModule.Exclusive.allDependencies] for details.
 * [origin] - the origin of the test case.
 * [nominalPackageName] - the unique package name that was computed for this [TestCase] based on [origin]'s actual path.
 *                        Note: It depends on the concrete [TestKind] whether the package name will be enforced for the [TestFile]s or not.
 */
internal class TestCase(
    val kind: TestKind,
    val modules: Set<TestModule.Exclusive>,
    val freeCompilerArgs: TestCompilerArgs,
    val origin: TestOrigin.SingleTestDataFile,
    val nominalPackageName: PackageName,
    val outputData: String?,
    val extras: StandaloneNoTestRunnerExtras? = null
) {
    val topologicallyOrderedModules: List<TestModule> by lazy {
        // Make sure that there are no cycles between modules, compute the topological order of modules.
        val topologicallyOrderedModules = topologicalOrder<TestModule>(modules, { module ->
            when (module) {
                is TestModule.Exclusive -> module.allDependencies + module.allFriends
                is TestModule.Shared -> emptyList()
            }
        })

        val rootModule = topologicallyOrderedModules.first()
        assertTrue(rootModule is TestModule.Exclusive) { "Root module is not exclusive module: $rootModule" }

        val orphanedModules = modules.toHashSet()
        orphanedModules.removeAll(rootModule.allDependencies)
        orphanedModules.remove(rootModule)

        assertTrue(orphanedModules.isEmpty()) { "There are modules that are inaccessible from the root module: $rootModule, $orphanedModules" }

        topologicallyOrderedModules
    }

    class StandaloneNoTestRunnerExtras(val entryPoint: String, val inputData: String?)

    init {
        assertEquals(extras != null, kind == TestKind.STANDALONE_NO_TR)
    }

    fun initialize(findSharedModule: (name: String) -> TestModule.Shared?) {
        // Check that there are no duplicated files among different modules.
        val duplicatedFiles = modules.flatMap { it.files }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(duplicatedFiles.isEmpty()) { "$origin: Duplicated test files encountered: $duplicatedFiles" }

        // Check that there are modules with duplicated names.
        val exclusiveModules: Map</* regular module name */ String, TestModule.Exclusive> = modules.toIdentitySet()
            .groupingBy { module -> module.name }
            .aggregate { name, _: TestModule.Exclusive?, module, isFirst ->
                assertTrue(isFirst) { "$origin: Multiple test modules with the same name found: $name" }
                module
            }

        fun findModule(name: String): TestModule = exclusiveModules[name]
            ?: findSharedModule(name)
            ?: fail { "$origin: Module $name not found" }

        modules.forEach { module ->
            module.commit() // Save to the file system and release the memory.
            module.testCase = this
            module.directDependencies = module.directDependencySymbols.mapToSet(::findModule)
            module.directFriends = module.directFriendSymbols.mapToSet(::findModule)
        }
    }
}
