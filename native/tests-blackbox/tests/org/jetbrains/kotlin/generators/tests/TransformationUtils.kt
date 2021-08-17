/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators.tests

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.nextLeaf
import org.jetbrains.kotlin.psi.psiUtil.prevLeaf
import org.jetbrains.kotlin.test.services.JUnit5Assertions
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertTrue
import org.jetbrains.kotlin.utils.addIfNotNull

internal inline fun String.transformByLines(transform: (String) -> String?): String =
    lines().mapNotNull(transform).joinToString("\n")

internal fun String.insert(insertionIndex: Int, insertion: String): String {
    assertTrue(insertionIndex in 0 until length) { "Bad insertion index $insertionIndex for string [$this]." }
    return substring(0, insertionIndex) + insertion + substring(insertionIndex)
}

internal fun PsiElement.ensureHasWhiteSpaceBefore(whiteSpace: String): PsiElement {
    val (fileBoundaryReached, whiteSpaceBefore) = whiteSpaceBefore()
    if (!fileBoundaryReached and !whiteSpaceBefore.endsWith(whiteSpace)) {
        parent.addBefore(KtPsiFactory(project).createWhiteSpace(whiteSpace), this)
    }
    return this
}

internal fun PsiElement.ensureHasWhiteSpaceAfter(whiteSpace: String): PsiElement {
    val (fileBoundaryReached, whiteSpaceAfter) = whiteSpaceAfter()
    if (!fileBoundaryReached and !whiteSpaceAfter.startsWith(whiteSpace)) {
        parent.addAfter(KtPsiFactory(project).createWhiteSpace(whiteSpace), this)
    }
    return this
}

internal fun PsiElement.ensureSurroundedByWhiteSpace(whiteSpace: String): PsiElement =
    ensureHasWhiteSpaceBefore(whiteSpace).ensureHasWhiteSpaceAfter(whiteSpace)

private fun PsiElement.whiteSpaceBefore(): Pair<Boolean, String> {
    var fileBoundaryReached = false

    fun PsiElement.prevWhiteSpace(): PsiWhiteSpace? = when (val prevLeaf = prevLeaf(skipEmptyElements = true)) {
        null -> {
            fileBoundaryReached = true
            null
        }
        else -> prevLeaf as? PsiWhiteSpace
    }

    val whiteSpace = buildString {
        generateSequence(prevWhiteSpace()) { it.prevWhiteSpace() }.toList().asReversed().forEach { append(it.text) }
    }

    return fileBoundaryReached to whiteSpace
}

private fun PsiElement.whiteSpaceAfter(): Pair<Boolean, String> {
    var fileBoundaryReached = false

    fun PsiElement.nextWhiteSpace(): PsiWhiteSpace? = when (val nextLeaf = nextLeaf(skipEmptyElements = true)) {
        null -> {
            fileBoundaryReached = true
            null
        }
        else -> nextLeaf as? PsiWhiteSpace
    }

    val whiteSpace = buildString {
        generateSequence(nextWhiteSpace()) { it.nextWhiteSpace() }.forEach { append(it.text) }
    }

    return fileBoundaryReached to whiteSpace
}

internal fun KtDotQualifiedExpression.collectNames(): List<Name> {
    val output = mutableListOf<Name>()

    fun KtExpression.recurse(): Boolean {
        children.forEach { child ->
            when (child) {
                is KtExpression -> when (child) {
                    is KtDotQualifiedExpression -> if (!child.recurse()) return false
                    is KtCallExpression,
                    is KtArrayAccessExpression,
                    is KtClassLiteralExpression,
                    is KtPostfixExpression -> {
                        child.recurse()
                        return false
                    }
                    is KtCallableReferenceExpression -> {
                        // 'T' from 'T::foo' should be considered, '::foo' should be discarded.
                        child.getChildrenOfType<KtNameReferenceExpression>()
                            .takeIf { it.size == 2 }
                            ?.first()
                            ?.let { output += it.getReferencedNameAsName() }
                        return false
                    }
                    is KtSafeQualifiedExpression -> {
                        // Consider only the first KtNameReferenceExpression child.
                        output.addIfNotNull(child.getChildOfType<KtNameReferenceExpression>()?.getReferencedNameAsName())
                        return false
                    }
                    is KtNameReferenceExpression -> output += child.getReferencedNameAsName()
                    else -> return false
                }
                else -> return false
            }
        }
        return true
    }

    recurse()
    return output
}

internal fun KtUserType.collectNames(output: MutableList<Name> = mutableListOf()): List<Name> {
    children.forEach { child ->
        when (child) {
            is KtUserType -> child.collectNames(output)
            is KtNameReferenceExpression -> output += child.getReferencedNameAsName()
            else -> Unit
        }
    }

    return output
}

internal fun FqName.child(child: FqName): FqName =
    child.pathSegments().fold(this) { accumulator, segment -> accumulator.child(segment) }

internal fun List<Name>.fqNameBeforeIndex(toIndexExclusive: Int): FqName =
    if (toIndexExclusive == 0) FqName.ROOT else FqName(subList(0, toIndexExclusive).joinToString("."))

internal fun FqName.removeSuffix(suffix: FqName): FqName {
    val pathSegments = pathSegments()
    val suffixPathSegments = suffix.pathSegments()

    val suffixStart = pathSegments.size - suffixPathSegments.size
    JUnit5Assertions.assertEquals(suffixPathSegments, pathSegments.subList(suffixStart, pathSegments.size))

    return FqName(pathSegments.take(suffixStart).joinToString("."))
}

internal fun KtElement.collectAccessibleDeclarationNames(): Set<Name> {
    val names = mutableSetOf<Name>()

    if (this is KtTypeParameterListOwner) {
        typeParameters.mapTo(names) { it.nameAsSafeName }
    }

    children.forEach { child ->
        if (child is KtNamedDeclaration) {
            when (child) {
                is KtClassLikeDeclaration,
                is KtVariableDeclaration,
                is KtParameter,
                is KtTypeParameter -> names += child.nameAsSafeName
            }
        }

        if (child is KtDestructuringDeclaration) {
            child.entries.mapTo(names) { it.nameAsSafeName }
        }

    }

    return names
}
