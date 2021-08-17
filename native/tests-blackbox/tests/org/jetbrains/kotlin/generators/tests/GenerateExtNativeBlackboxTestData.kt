/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators.tests

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.pom.PomModel
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.pom.tree.TreeAspect
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.generators.tests.ExtTestDataFile.Companion.THREAD_LOCAL_ANNOTATION
import org.jetbrains.kotlin.konan.blackboxtest.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.addRemoveModifier.addAnnotationEntry
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.checkers.OptInNames
import org.jetbrains.kotlin.test.*
import org.jetbrains.kotlin.test.InTextDirectivesUtils.isCompatibleTarget
import org.jetbrains.kotlin.test.InTextDirectivesUtils.isIgnoredTarget
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertTrue
import org.jetbrains.kotlin.test.services.JUnit5Assertions.fail
import org.jetbrains.kotlin.test.util.KtTestUtil
import org.jetbrains.kotlin.utils.addIfNotNull
import java.io.File

internal fun generateExtNativeBlackboxTestData(
    testDataSource: String,
    testDataDestination: String,
    sharedModules: String,
    init: ExtTestDataConfig.() -> Unit
) {
    val testDataConfig = ExtTestDataConfig(testDataSource, testDataDestination, sharedModules)
    testDataConfig.init()
    testDataConfig.generateTestData()
}

internal class ExtTestDataConfig(
    private val testDataSource: String,
    private val testDataDestination: String,
    private val sharedModules: String
) {
    private val includes = linkedSetOf<String>()
    private val excludes = hashSetOf<String>()

    fun include(testDataSubPath: String) {
        includes += testDataSubPath
    }

    fun exclude(testDataSubPath: String) {
        excludes += testDataSubPath
    }

    fun generateTestData() {
        val testDataSourceDir = getAbsoluteFile(testDataSource)
        assertTrue(testDataSourceDir.isDirectory) { "The directory with the source testData doe not exist: $testDataSourceDir" }

        val testDataDestinationDir = getAbsoluteFile(testDataDestination)
        testDataDestinationDir.deleteRecursivelyWithLogging()
        testDataDestinationDir.mkdirsWithLogging()

        val sharedModulesDir = getAbsoluteFile(sharedModules)
        sharedModulesDir.deleteRecursivelyWithLogging()
        sharedModulesDir.mkdirsWithLogging()

        val roots = if (includes.isNotEmpty())
            includes.map { testDataSourceDir.resolve(it) }
        else
            listOf(testDataSourceDir)

        val excludedItems = excludes.map { testDataSourceDir.resolve(it) }.toSet()
        val sharedModules = SharedTestModules()

        val structureFactory = ExtTestDataFileStructureFactory()
        try {
            roots.forEach { root ->
                root.walkTopDown()
                    .onEnter { directory -> directory !in excludedItems }
                    .filter { file -> file.isFile && file.extension == "kt" && file !in excludedItems }
                    .forEach { file ->
                        ExtTestDataFile(
                            structureFactory = structureFactory,
                            testDataFile = file,
                            testDataSourceDir = testDataSourceDir,
                            testDataDestinationDir = testDataDestinationDir
                        ).generateNewTestDataFileIfNecessary(sharedModules)
                    }
            }
        } finally {
            Disposer.dispose(structureFactory)
        }

        sharedModules.dumpToDir(sharedModulesDir)
    }
}

private class ExtTestDataFile(
    structureFactory: ExtTestDataFileStructureFactory,
    private val testDataFile: File,
    private val testDataSourceDir: File,
    private val testDataDestinationDir: File
) {
    private val structure = structureFactory.ExtTestDataFileStructure(testDataFile) { line ->
        if (DIRECTIVE_REGEX.matches(line)) {
            // Remove all directives from test files. These directives are not needed anymore as they are already
            // read and stored in [settings] property. Moreover, these directives if left in test file can potentially conflict with
            // Native-specific test directives to be added (see [TestDirectives] for details). Also, they will create unnecessary "noise".
            // Examples:
            //   // !LANGUAGE: +NewInference
            //   // LANGUAGE: -ApproximateIntegerLiteralTypesInReceiverPosition
            //   // !USE_EXPERIMENTAL: kotlin.contracts.ExperimentalContracts
            null
        } else {
            // Remove all diagnostic parameters from the text. Examples:
            //   <!NO_TAIL_CALLS_FOUND!>, <!NON_TAIL_RECURSIVE_CALL!>, <!>.
            line.replace(DIAGNOSTIC_REGEX) { match -> match.groupValues[1] }
        }
    }

    private val settings = run {
        val optIns = structure.directives.multiValues(OPT_IN_DIRECTIVE)
        val optInsForSourceCode = optIns subtract OPT_INS_PURELY_FOR_COMPILER
        val optInsForCompiler = optIns intersect OPT_INS_PURELY_FOR_COMPILER

        ExtTestDataFileSettings(
            languageSettings = structure.directives.multiValues(LANGUAGE_DIRECTIVE) {
                it != "+NewInference" /* It is already on by default, but passing it explicitly turns on a special "compatibility mode" in FE which is not desirable. */
            },
            optInsForSourceCode = optInsForSourceCode + structure.directives.multiValues(USE_EXPERIMENTAL_DIRECTIVE),
            optInsForCompiler = optInsForCompiler,
            expectActualLinker = EXPECT_ACTUAL_LINKER_DIRECTIVE in structure.directives,
            effectivePackageName = computePackageName(testDataDir = testDataSourceDir, testDataFile = testDataFile)
        )
    }

    private fun shouldBeGenerated(): Boolean =
        isCompatibleTarget(TargetBackend.NATIVE, testDataFile) // Checks TARGET_BACKEND/DONT_TARGET_EXACT_BACKEND directives.
                && !isIgnoredTarget(TargetBackend.NATIVE, testDataFile) // Checks IGNORE_BACKEND directive.
                && settings.languageSettings.none { it in INCOMPATIBLE_LANGUAGE_SETTINGS }
                && INCOMPATIBLE_DIRECTIVES.none { it in structure.directives }
                && structure.directives[API_VERSION_DIRECTIVE] !in INCOMPATIBLE_API_VERSIONS
                && structure.directives[LANGUAGE_VERSION_DIRECTIVE] !in INCOMPATIBLE_LANGUAGE_VERSIONS

    private fun assembleFreeCompilerArgs(): List<String> {
        val args = mutableListOf<String>()
        settings.languageSettings.sorted().mapTo(args) { "-XXLanguage:$it" }
        settings.optInsForCompiler.sorted().mapTo(args) { "-Xopt-in=$it" }
        if (settings.expectActualLinker) args += "-Xexpect-actual-linker"
        return args
    }

    fun generateNewTestDataFileIfNecessary(sharedTestModules: SharedTestModules) {
        if (!shouldBeGenerated()) return

        val isStandaloneTest = determineIfStandaloneTest()
        makeObjectsMutable()
        patchPackageNames(isStandaloneTest)
        val fileLevelAnnotations = patchFileLevelAnnotations()
        val entryPointFunctionFQN = findEntryPoint()
        generateTestLauncher(isStandaloneTest, entryPointFunctionFQN, fileLevelAnnotations)

        val relativeFile = testDataFile.relativeTo(testDataSourceDir)
        val destinationFile = testDataDestinationDir.resolve(relativeFile)

        destinationFile.writeFileWithLogging(structure.generateTextExcludingSupportModule(isStandaloneTest, assembleFreeCompilerArgs()))
        structure.generateSharedSupportModule(sharedTestModules::addFile)
    }

    /** Determine if the current test should be compiled as a standalone test, i.e.
     * - package names are not patched
     * - test is compiled independently of any other tests
     */
    private fun determineIfStandaloneTest(): Boolean = with(structure) {
        var isStandaloneTest = false

        filesToTransform.forEach { handler ->
            handler.accept(object : KtTreeVisitorVoid() {
                override fun visitKtFile(file: KtFile) = when {
                    isStandaloneTest -> Unit
                    file.packageFqName.startsWith(StandardNames.BUILT_INS_PACKAGE_NAME) -> {
                        // We can't fully patch packages for tests containing source code in any of kotlin.* packages.
                        // So, such tests should be run in standalone mode to avoid possible signature clashes with other tests.
                        isStandaloneTest = true
                    }
                    else -> super.visitKtFile(file)
                }

                override fun visitCallExpression(expression: KtCallExpression) = when {
                    isStandaloneTest -> Unit
                    expression.getChildOfType<KtNameReferenceExpression>()?.getReferencedNameAsName() == TYPE_OF_NAME -> {
                        // Found a call of `typeOf()` function. It means that this is most likely a reflection-oriented test
                        // that might compare the obtained name of a type against some string literal (ex: "foo.Bar<A>"),
                        // which is obviously not patched during package names patching step because this step is not so smart.
                        // So, let's avoid patching package names for this test and let's run it in standalone mode.
                        isStandaloneTest = true
                    }
                    else -> super.visitCallExpression(expression)
                }
            })
        }

        isStandaloneTest
    }

    /** Annotate all objects and companion objects with [THREAD_LOCAL_ANNOTATION] to make them mutable. */
    private fun makeObjectsMutable() = with(structure) {
        filesToTransform.forEach { handler ->
            handler.accept(object : KtTreeVisitorVoid() {
                override fun visitObjectDeclaration(objectDeclaration: KtObjectDeclaration) {
                    if (!objectDeclaration.isObjectLiteral()) {
                        // FIXME: find only those that have vars inside
                        addAnnotationEntry(
                            objectDeclaration,
                            handler.psiFactory.createAnnotationEntry(THREAD_LOCAL_ANNOTATION)
                        ).ensureSurroundedByWhiteSpace(" ")
                    }

                    super.visitObjectDeclaration(objectDeclaration)
                }
            })
        }
    }

    /**
     * For every Kotlin file (*.kt) stored in this text:
     *
     * - If there is a "package" declaration, patch it to prepend unique package prefix.
     *   Example: package foo -> package codegen.box.annotations.genericAnnotations.foo
     *
     * - If there is no "package" declaration, add one with the package name equal to unique package prefix.
     *   Example (new line added): package codegen.box.annotations.genericAnnotations
     *
     * - All "import" declarations are patched to reflect appropriate changes in "package" declarations.
     *   Example: import foo.* -> import codegen.box.annotations.genericAnnotations.foo.*
     *
     * - All fully-qualified references are patched to reflect appropriate changes in "package" declarations.
     *   Example: val x = foo.Bar() -> val x = codegen.box.annotations.genericAnnotations.foo.Bar()
     *
     * The "unique package prefix" is computed individually for every test file and reflects relative path to the test file.
     * Example: codegen/box/annotations/genericAnnotations.kt -> codegen.box.annotations.genericAnnotations
     *
     * Note that packages with fully-qualified name starting with "kotlin." and "helpers." are kept unchanged.
     * Examples: package kotlin.coroutines -> package kotlin.coroutines
     *           import kotlin.test.* -> import kotlin.test.*
     */
    private fun patchPackageNames(isStandaloneTest: Boolean) = with(structure) {
        if (isStandaloneTest) return // Don't patch packages for standalone tests.

        val basePackageName = FqName(settings.effectivePackageName)

        val oldPackageNames: Set<FqName> = filesToTransform.mapToSet { it.packageFqName }
        val oldToNewPackageNameMapping: Map<FqName, FqName> = oldPackageNames.associateWith { oldPackageName ->
            basePackageName.child(oldPackageName)
        }

        filesToTransform.forEach { handler ->
            handler.accept(object : KtVisitor<Unit, Set<Name>>() {
                override fun visitKtElement(element: KtElement, parentAccessibleDeclarationNames: Set<Name>) {
                    element.getChildrenOfType<KtElement>().forEach { child ->
                        child.accept(this, parentAccessibleDeclarationNames)
                    }
                }

                override fun visitKtFile(file: KtFile, unused: Set<Name>) {
                    // Patch package directive.
                    val oldPackageDirective = file.packageDirective
                    val oldPackageName = oldPackageDirective?.fqName ?: FqName.ROOT

                    val newPackageName = oldToNewPackageNameMapping.getValue(oldPackageName)
                    val newPackageDirective = handler.psiFactory.createPackageDirective(newPackageName)

                    if (oldPackageDirective != null) {
                        // Replace old package directive by the new one.
                        oldPackageDirective.replace(newPackageDirective).ensureSurroundedByWhiteSpace("\n\n")
                    } else {
                        // Insert the package directive immediately after file-level annotations.
                        file.addAfter(newPackageDirective, file.fileAnnotationList).ensureSurroundedByWhiteSpace("\n\n")
                    }

                    visitKtElement(file, file.collectAccessibleDeclarationNames())
                }

                override fun visitPackageDirective(directive: KtPackageDirective, unused: Set<Name>) = Unit

                override fun visitImportDirective(importDirective: KtImportDirective, unused: Set<Name>) {
                    // Patch import directive if necessary.
                    val importedFqName = importDirective.importedFqName
                    if (importedFqName == null
                        || importedFqName.startsWith(StandardNames.BUILT_INS_PACKAGE_NAME)
                        || importedFqName.startsWith(HELPERS_PACKAGE_NAME)
                    ) {
                        return
                    }

                    val newImportPath = ImportPath(
                        fqName = basePackageName.child(importedFqName),
                        isAllUnder = importDirective.isAllUnder,
                        alias = importDirective.aliasName?.let(Name::identifier)
                    )
                    importDirective.replace(handler.psiFactory.createImportDirective(newImportPath))
                }

                override fun visitTypeAlias(typeAlias: KtTypeAlias, parentAccessibleDeclarationNames: Set<Name>) =
                    super.visitTypeAlias(typeAlias, parentAccessibleDeclarationNames + typeAlias.collectAccessibleDeclarationNames())

                override fun visitClassOrObject(classOrObject: KtClassOrObject, parentAccessibleDeclarationNames: Set<Name>) =
                    super.visitClassOrObject(
                        classOrObject,
                        parentAccessibleDeclarationNames + classOrObject.collectAccessibleDeclarationNames()
                    )

                override fun visitClassBody(classBody: KtClassBody, parentAccessibleDeclarationNames: Set<Name>) =
                    super.visitClassBody(classBody, parentAccessibleDeclarationNames + classBody.collectAccessibleDeclarationNames())

                override fun visitPropertyAccessor(accessor: KtPropertyAccessor, parentAccessibleDeclarationNames: Set<Name>) =
                    transformDeclarationWithBody(accessor, parentAccessibleDeclarationNames)

                override fun visitNamedFunction(function: KtNamedFunction, parentAccessibleDeclarationNames: Set<Name>) =
                    transformDeclarationWithBody(function, parentAccessibleDeclarationNames)

                override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor, parentAccessibleDeclarationNames: Set<Name>) =
                    transformDeclarationWithBody(constructor, parentAccessibleDeclarationNames)

                override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor, parentAccessibleDeclarationNames: Set<Name>) =
                    transformDeclarationWithBody(constructor, parentAccessibleDeclarationNames)

                private fun transformDeclarationWithBody(
                    declarationWithBody: KtDeclarationWithBody,
                    parentAccessibleDeclarationNames: Set<Name>
                ) {
                    val (expressions, nonExpressions) = declarationWithBody.getChildrenOfType<KtElement>().partition { it is KtExpression }

                    val accessibleDeclarationNames =
                        parentAccessibleDeclarationNames + declarationWithBody.collectAccessibleDeclarationNames()
                    nonExpressions.forEach { it.accept(this, accessibleDeclarationNames) }

                    val bodyAccessibleDeclarationNames =
                        accessibleDeclarationNames + declarationWithBody.valueParameters.map { it.nameAsSafeName }
                    expressions.forEach { it.accept(this, bodyAccessibleDeclarationNames) }
                }

                override fun visitExpression(expression: KtExpression, parentAccessibleDeclarationNames: Set<Name>) =
                    if (expression is KtFunctionLiteral)
                        transformDeclarationWithBody(expression, parentAccessibleDeclarationNames)
                    else
                        super.visitExpression(expression, parentAccessibleDeclarationNames)

                override fun visitBlockExpression(expression: KtBlockExpression, parentAccessibleDeclarationNames: Set<Name>) {
                    val accessibleDeclarationNames = parentAccessibleDeclarationNames.toMutableSet()
                    expression.getChildrenOfType<KtElement>().forEach { child ->
                        child.accept(this, accessibleDeclarationNames)
                        accessibleDeclarationNames.addIfNotNull(child.name?.let(Name::identifier))
                    }
                }

                override fun visitDotQualifiedExpression(
                    dotQualifiedExpression: KtDotQualifiedExpression,
                    accessibleDeclarationNames: Set<Name>
                ) {
                    val names = dotQualifiedExpression.collectNames()

                    val newDotQualifiedExpression =
                        visitPossiblyTypeReferenceWithFullyQualifiedName(names, accessibleDeclarationNames) { newPackageName ->
                            val newDotQualifiedExpression = handler.psiFactory
                                .createFile("val x = ${newPackageName.asString()}.${dotQualifiedExpression.text}")
                                .getChildOfType<KtProperty>()!!
                                .getChildOfType<KtDotQualifiedExpression>()!!

                            dotQualifiedExpression.replace(newDotQualifiedExpression) as KtDotQualifiedExpression
                        } ?: dotQualifiedExpression

                    super.visitDotQualifiedExpression(newDotQualifiedExpression, accessibleDeclarationNames)
                }

                override fun visitUserType(userType: KtUserType, accessibleDeclarationNames: Set<Name>) {
                    val names = userType.collectNames()

                    val newUserType =
                        visitPossiblyTypeReferenceWithFullyQualifiedName(names, accessibleDeclarationNames) { newPackageName ->
                            val newUserType = handler.psiFactory
                                .createFile("val x: ${newPackageName.asString()}.${userType.text}")
                                .getChildOfType<KtProperty>()!!
                                .getChildOfType<KtTypeReference>()!!
                                .typeElement as KtUserType

                            userType.replace(newUserType) as KtUserType
                        } ?: userType

                    newUserType.typeArgumentList?.let { visitKtElement(it, accessibleDeclarationNames) }
                }

                private fun <T : KtElement> visitPossiblyTypeReferenceWithFullyQualifiedName(
                    names: List<Name>,
                    accessibleDeclarationNames: Set<Name>,
                    action: (newSubPackageName: FqName) -> T
                ): T? {
                    if (names.size < 2) return null

                    if (names.first() in accessibleDeclarationNames) return null

                    for (index in 1 until names.size) {
                        val subPackageName = names.fqNameBeforeIndex(index)
                        val newPackageName = oldToNewPackageNameMapping[subPackageName]
                        if (newPackageName != null)
                            return action(newPackageName.removeSuffix(subPackageName))
                    }

                    return null
                }
            }, emptySet())
        }
    }

    /**
     * 1. Collect all file-level annotations that should be added to the launcher. See [generateTestLauncher].
     * 2. Make sure that the OptIns specified in test directives (see [ExtTestDataFileSettings.optInsForSourceCode]) are represented
     *    as file-level annotations in every individual test file.
     */
    private fun patchFileLevelAnnotations(): List<String> = with(structure) {
        val allFileLevelAnnotations = hashSetOf<String>()

        fun getAnnotationText(fullyQualifiedName: String) = "@file:${OPT_IN_ANNOTATION_NAME.asString()}($fullyQualifiedName::class)"

        // Every OptIn specified in test directive should be represented as a file-level annotation.
        settings.optInsForSourceCode.mapTo(allFileLevelAnnotations, ::getAnnotationText)

        // Now, collect file-level annotations already present in test files.
        filesToTransform.forEach { handler ->
            handler.accept(object : KtTreeVisitorVoid() {
                override fun visitKtFile(file: KtFile) {
                    val importDirectives: Map<String, String> by lazy {
                        file.importDirectives.mapNotNull { importDirective ->
                            val importedFqName = importDirective.importedFqName ?: return@mapNotNull null
                            val name = importDirective.alias?.name ?: importedFqName.shortName().asString()
                            name to importedFqName.asString()
                        }.toMap()
                    }

                    file.annotationEntries.mapNotNullTo(allFileLevelAnnotations) { annotationEntry ->
                        val constructorCallee = annotationEntry.getChildOfType<KtConstructorCalleeExpression>()
                        val constructorType = constructorCallee?.typeReference?.getChildOfType<KtUserType>()
                        val constructorTypeName = constructorType?.collectNames()?.singleOrNull()

                        if (constructorTypeName != OPT_IN_ANNOTATION_NAME) return@mapNotNullTo null

                        val valueArgument = annotationEntry.valueArguments.singleOrNull()
                        val classLiteral = valueArgument?.getArgumentExpression() as? KtClassLiteralExpression

                        if (classLiteral?.getChildOfType<KtDotQualifiedExpression>() != null)
                            annotationEntry.text
                        else
                            classLiteral?.getChildOfType<KtNameReferenceExpression>()?.getReferencedName()
                                ?.let(importDirectives::get)
                                ?.let { fullyQualifiedName -> getAnnotationText(fullyQualifiedName) }
                    }
                }
            })
        }

        val allFileLevelAnnotationsSorted = allFileLevelAnnotations.sorted()

        // Finally, make sure that every test file contains all the necessary file-level annotations.
        if (allFileLevelAnnotationsSorted.isNotEmpty()) {
            filesToTransform.forEach { handler ->
                handler.accept(object : KtTreeVisitorVoid() {
                    override fun visitKtFile(file: KtFile) {
                        val oldFileAnnotationList = file.fileAnnotationList

                        val newFileAnnotationsList = handler.psiFactory.createFile(buildString {
                            allFileLevelAnnotationsSorted.forEach(::appendLine)
                        }).fileAnnotationList!!

                        if (oldFileAnnotationList != null) {
                            // Replace old annotations list by the new one.
                            oldFileAnnotationList.replace(newFileAnnotationsList).ensureSurroundedByWhiteSpace("\n\n")
                        } else {
                            // Insert the annotations list immediately before package directive.
                            file.addBefore(newFileAnnotationsList, file.packageDirective).ensureSurroundedByWhiteSpace("\n\n")
                        }
                    }
                })
            }
        }

        return allFileLevelAnnotationsSorted
    }

    /** Finds the fully-qualified name of the entry point function (aka `fun box(): String`). */
    private fun findEntryPoint(): String = with(structure) {
        val result = mutableListOf<String>()

        filesToTransform.forEach { handler ->
            handler.accept(object : KtTreeVisitorVoid() {
                override fun visitKtFile(file: KtFile) {
                    val hasBoxFunction = file.getChildrenOfType<KtNamedFunction>().any { function ->
                        function.name == BOX_FUNCTION_NAME.asString() && function.valueParameters.isEmpty()
                    }

                    if (hasBoxFunction) {
                        val boxFunctionFqName = file.packageFqName.child(BOX_FUNCTION_NAME).asString()
                        result += boxFunctionFqName

                        handler.module.markAsMain()
                    }
                }
            })
        }

        return result.singleOrNull()
            ?: fail {
                "Exactly one entry point function is expected in $testDataFile. " +
                        "But ${if (result.size == 0) "none" else result.size} were found: $result"
            }
    }

    /** Adds a wrapper to run it as Kotlin test. */
    private fun generateTestLauncher(isStandaloneTest: Boolean, entryPointFunctionFQN: String, fileLevelAnnotations: List<String>) {
        val fileText = buildString {
            if (fileLevelAnnotations.isNotEmpty()) {
                fileLevelAnnotations.forEach(this::appendLine)
                appendLine()
            }

            if (!isStandaloneTest) {
                append("package ").appendLine(settings.effectivePackageName)
                appendLine()
            }

            append(
                """
                    @kotlin.test.Test
                    fun runTest() {
                        val result = $entryPointFunctionFQN()
                        kotlin.test.assertEquals("OK", result, "Test failed with: ${'$'}result")
                    }
             
                """.trimIndent()
            )
        }

        structure.addFileToMainModule(fileName = "__launcher__.kt", text = fileText)
    }

    companion object {
        private val INCOMPATIBLE_DIRECTIVES = setOf("FULL_JDK", "JVM_TARGET", "DIAGNOSTICS")

        private const val API_VERSION_DIRECTIVE = "API_VERSION"
        private val INCOMPATIBLE_API_VERSIONS = setOf("1.4")

        private const val LANGUAGE_VERSION_DIRECTIVE = "LANGUAGE_VERSION"
        private val INCOMPATIBLE_LANGUAGE_VERSIONS = setOf("1.3", "1.4")

        private const val LANGUAGE_DIRECTIVE = "LANGUAGE"
        private val INCOMPATIBLE_LANGUAGE_SETTINGS = setOf(
            "-ProperIeee754Comparisons",                            // K/N supports only proper IEEE754 comparisons
            "-ReleaseCoroutines",                                   // only release coroutines
            "-DataClassInheritance",                                // old behavior is not supported
            "-ProhibitAssigningSingleElementsToVarargsInNamedForm", // Prohibit these assignments
            "-ProhibitDataClassesOverridingCopy",                   // Prohibit as no longer supported
            "-ProhibitOperatorMod",                                 // Prohibit as no longer supported
            "-UseBuilderInferenceOnlyIfNeeded",                     // Run only default one
            "-UseCorrectExecutionOrderForVarargArguments"           // Run only correct one
        )

        private const val EXPECT_ACTUAL_LINKER_DIRECTIVE = "EXPECT_ACTUAL_LINKER"
        private const val USE_EXPERIMENTAL_DIRECTIVE = "USE_EXPERIMENTAL"

        private const val OPT_IN_DIRECTIVE = "OPT_IN"
        private val OPT_INS_PURELY_FOR_COMPILER = setOf(
            OptInNames.REQUIRES_OPT_IN_FQ_NAME.asString()
        )

        private fun Directives.multiValues(key: String, predicate: (String) -> Boolean = { true }): Set<String> =
            listValues(key)?.flatMap { it.split(' ') }?.filter(predicate)?.toSet().orEmpty()

        private val DIRECTIVE_REGEX = Regex("^// !?[A-Z_]+(:?\\s+.*|\\s*)$")
        private val DIAGNOSTIC_REGEX = Regex("<!.*?!>(.*?)<!>")

        private const val THREAD_LOCAL_ANNOTATION = "@kotlin.native.ThreadLocal"

        private val BOX_FUNCTION_NAME = Name.identifier("box")
        private val OPT_IN_ANNOTATION_NAME = Name.identifier("OptIn")
        private val HELPERS_PACKAGE_NAME = Name.identifier("helpers")
        private val TYPE_OF_NAME = Name.identifier("typeOf")
    }
}

private class ExtTestDataFileSettings(
    val languageSettings: Set<String>,
    val optInsForSourceCode: Set<String>,
    val optInsForCompiler: Set<String>,
    val expectActualLinker: Boolean,
    val effectivePackageName: PackageName
)

private class ExtTestDataFileStructureFactory : Disposable {
    private val psiFactory = createPsiFactory(this)

    override fun dispose() = Unit

    inner class ExtTestDataFileStructure(
        originalTestDataFile: File,
        initialCleanUpTransformation: (String) -> String? // Line -> transformed line (or null if the line should be omitted).
    ) {
        private val originalTestDataFileRelativePath = originalTestDataFile.relativeTo(File(KtTestUtil.getHomeDirectory()))

        private val filesAndModules = FilesAndModules(originalTestDataFile, initialCleanUpTransformation)

        val directives: Directives get() = filesAndModules.directives

        val filesToTransform: Iterable<CurrentFileHandler>
            get() = filesAndModules.parsedFiles.map { (file, psiFile) ->
                object : CurrentFileHandler {
                    override val packageFqName get() = psiFile.packageFqName
                    override val module = object : CurrentFileHandler.ModuleHandler {
                        override fun markAsMain() {
                            file.module.isMain = true
                        }
                    }
                    override val psiFactory get() = this@ExtTestDataFileStructureFactory.psiFactory

                    override fun accept(visitor: KtVisitor<*, *>): Unit = psiFile.accept(visitor)
                    override fun <D> accept(visitor: KtVisitor<*, D>, data: D) {
                        psiFile.accept(visitor, data)
                    }
                }
            }

        fun addFileToMainModule(fileName: String, text: String): Unit = filesAndModules.addFileToMainModule(fileName, text)

        fun generateTextExcludingSupportModule(isStandaloneTest: Boolean, freeCompilerArgs: Collection<String>): String {
            checkModulesConsistency()

            // Update texts of parsed test files.
            filesAndModules.parsedFiles.forEach { (file, psiFile) -> file.text = psiFile.text }

            return buildString {
                appendAutogeneratedSourceCodeWarning()

                if (isStandaloneTest) appendLine("// KIND: STANDALONE")

                if (freeCompilerArgs.isNotEmpty()) {
                    append("// FREE_COMPILER_ARGS: ")
                    freeCompilerArgs.joinTo(this, separator = " ")
                    appendLine()
                }

                filesAndModules.modules.entries.sortedBy { it.key }.forEach { (_, module) ->
                    if (module.isSupport) return@forEach // Skip support module.

                    // MODULE line:
                    append("// MODULE: ${module.name}")
                    if (module.dependencies.isNotEmpty() || module.friends.isNotEmpty()) {
                        append('(')
                        module.dependencies.joinTo(this, separator = ",")
                        append(')')
                        if (module.friends.isNotEmpty()) {
                            append('(')
                            module.friends.joinTo(this, separator = ",")
                            append(')')
                        }
                    }
                    appendLine()

                    module.files.forEach { file ->
                        // FILE line:
                        appendLine("// FILE: ${file.name}")

                        // FILE contents:
                        appendLine(file.text)
                    }
                }
            }
        }

        fun generateSharedSupportModule(action: (moduleName: String, fileName: String, fileText: String) -> Unit) {
            filesAndModules.modules[SUPPORT_MODULE_NAME]?.let { supportModule ->
                supportModule.files.forEach { file ->
                    action(supportModule.name, file.name, file.text)
                }
            }
        }

        @Suppress("UNNECESSARY_SAFE_CALL", "UselessCallOnCollection")
        private fun checkModulesConsistency() {
            filesAndModules.modules.values.forEach { module ->
                val unknownFriends = (module.friendsSymbols + module.friends.mapNotNull { it?.name }).toSet() - filesAndModules.modules.keys
                assertTrue(unknownFriends.isEmpty()) { "Module $module has unknown friends: $unknownFriends" }

                val unknownDependencies =
                    (module.dependenciesSymbols + module.dependencies.mapNotNull { it?.name }).toSet() - filesAndModules.modules.keys
                assertTrue(unknownDependencies.isEmpty()) { "Module $module has unknown dependencies: $unknownDependencies" }

                assertTrue(module.files.isNotEmpty()) { "Module $module has no files" }
            }
        }

        private fun StringBuilder.appendAutogeneratedSourceCodeWarning() {
            appendLine(
                """
                /*
                 * This file was generated automatically.
                 * PLEASE DO NOT MODIFY IT MANUALLY.
                 *
                 * Generator tool: $GENERATOR_TOOL_NAME
                 * Original file: $originalTestDataFileRelativePath
                 */

            """.trimIndent()
            )
        }
    }

    private class TestModule(
        name: String,
        dependencies: List<String>,
        friends: List<String>
    ) : KotlinBaseTest.TestModule(name, dependencies, friends) {
        val files = mutableListOf<TestFile>()

        val isSupport get() = name == SUPPORT_MODULE_NAME
        var isMain = false

        override fun equals(other: Any?) = (other as? TestModule)?.name == name
        override fun hashCode() = name.hashCode()
    }

    private class TestFile(
        val name: String,
        val module: TestModule,
        var text: String
    ) {
        init {
            module.files += this
        }

        override fun equals(other: Any?) = (other as? TestFile)?.name == name
        override fun hashCode() = name.hashCode()
    }

    private class TestFileFactory : TestFiles.TestFileFactory<TestModule, TestFile> {
        private val defaultModule by lazy { createModule(DEFAULT_MODULE_NAME, emptyList(), emptyList(), emptyList()) }
        private val supportModule by lazy { createModule(SUPPORT_MODULE_NAME, emptyList(), emptyList(), emptyList()) }

        lateinit var directives: Directives

        fun createFile(module: TestModule, fileName: String, text: String): TestFile =
            TestFile(getSanitizedFileName(fileName), module, text)

        override fun createFile(module: TestModule?, fileName: String, text: String, directives: Directives): TestFile {
            this.directives = directives
            return createFile(
                module = module ?: if (fileName == "CoroutineUtil.kt") supportModule else defaultModule,
                fileName = fileName,
                text = text
            )
        }

        override fun createModule(name: String, dependencies: List<String>, friends: List<String>, abiVersions: List<Int>): TestModule =
            TestModule(name, dependencies, friends)
    }

    private inner class FilesAndModules(originalTestDataFile: File, initialCleanUpTransformation: (String) -> String?) {
        private val testFileFactory = TestFileFactory()
        private val generatedFiles = TestFiles.createTestFiles(DEFAULT_FILE_NAME, originalTestDataFile.readText(), testFileFactory)

        private val lazyData: Triple<Map<String, TestModule>, Map<TestFile, KtFile>, MutableList<TestFile>> by lazy {
            // Clean up contents of every individual test file. Important: This should be done only after parsing testData file,
            // because parsing of testData file relies on certain directives which could be removed by the transformation.
            generatedFiles.forEach { file -> file.text = file.text.transformByLines(initialCleanUpTransformation) }

            val modules = generatedFiles.map { it.module }.associateBy { it.name }

            val (supportModuleFiles, nonSupportModuleFiles) = generatedFiles.partition { it.module.isSupport }
            val parsedFiles = nonSupportModuleFiles.associateWith { psiFactory.createFile(it.name, it.text) }
            val nonParsedFiles = supportModuleFiles.toMutableList()

            // Explicitly add support module to other modules' dependencies (as it is not listed there by default).
            val supportModule = modules[SUPPORT_MODULE_NAME]
            if (supportModule != null) {
                modules.forEach { (moduleName, module) ->
                    if (moduleName != SUPPORT_MODULE_NAME && supportModule !in module.dependencies) {
                        module.dependencies += supportModule
                    }
                }
            }

            Triple(modules, parsedFiles, nonParsedFiles)
        }

        val directives: Directives get() = testFileFactory.directives

        val modules: Map<String, TestModule> get() = lazyData.first
        val parsedFiles: Map<TestFile, KtFile> get() = lazyData.second
        private val nonParsedFiles: MutableList<TestFile> get() = lazyData.third

        fun addFileToMainModule(fileName: String, text: String) {
            val foundModules = modules.values.filter { it.isMain }
            val mainModule = when (val size = foundModules.size) {
                1 -> foundModules.first()
                else -> fail { "Exactly one main module is expected. But ${if (size == 0) "none" else size} were found." }
            }

            nonParsedFiles += testFileFactory.createFile(mainModule, fileName, text)
        }

    }

    interface CurrentFileHandler {
        interface ModuleHandler {
            fun markAsMain()
        }

        val packageFqName: FqName
        val module: ModuleHandler
        val psiFactory: KtPsiFactory

        fun accept(visitor: KtVisitor<*, *>)
        fun <D> accept(visitor: KtVisitor<*, D>, data: D)
    }

    companion object {
        private val GENERATOR_TOOL_NAME =
            "${::generateExtNativeBlackboxTestData.javaClass.`package`.name}.${::generateExtNativeBlackboxTestData.name}()"

        private fun createPsiFactory(parentDisposable: Disposable): KtPsiFactory {
            val configuration: CompilerConfiguration = KotlinTestUtils.newConfiguration()
            configuration.put(CommonConfigurationKeys.MODULE_NAME, "native-blackbox-test-patching-module")

            val environment = KotlinCoreEnvironment.createForProduction(
                parentDisposable = parentDisposable,
                configuration = configuration,
                configFiles = EnvironmentConfigFiles.METADATA_CONFIG_FILES
            )

            CoreApplicationEnvironment.registerApplicationDynamicExtensionPoint(TreeCopyHandler.EP_NAME.name, TreeCopyHandler::class.java)

            val project = environment.project as MockProject
            project.registerService(PomModel::class.java, PomModelImpl::class.java)
            project.registerService(TreeAspect::class.java)

            return KtPsiFactory(environment.project)
        }
    }
}

private class SharedTestModules {
    val modules = mutableMapOf<String, SharedTestModule>()

    fun addFile(moduleName: String, fileName: String, fileText: String) {
        modules.getOrPut(moduleName) { SharedTestModule() }.files.getOrPut(fileName) { hashSetOf() } += SharedTestFile(fileText)
    }

    fun dumpToDir(outputDir: File) {
        modules.forEach { (moduleName, module) ->
            val moduleDir = outputDir.resolve(moduleName)
            moduleDir.mkdirs()

            module.files.forEach { (fileName, files) ->
                val singleFileText: String = when (files.size) {
                    1 -> files.first().text
                    2 -> {
                        val (file1, file2) = files.toList()
                        tryToMerge(file1.text, file2.text)
                    }
                    else -> null
                } ?: fail { "Multiple variations of the same shared test file found: $fileName (${files.size})" }

                moduleDir.resolve(fileName).writeText(singleFileText)
            }
        }
    }

    // Try to merge two files (covers the most trivial case).
    private fun tryToMerge(text1: String, text2: String): String? {
        val trimmedText1 = text1.trim()
        val trimmedText2 = text2.trim()

        return when {
            trimmedText1.startsWith(trimmedText2) -> text1
            trimmedText2.startsWith(trimmedText1) -> text2
            else -> null
        }
    }
}

@JvmInline
private value class SharedTestModule(val files: MutableMap<String, MutableSet<SharedTestFile>>) {
    constructor() : this(mutableMapOf())
}

@JvmInline
private value class SharedTestFile(val text: String)
