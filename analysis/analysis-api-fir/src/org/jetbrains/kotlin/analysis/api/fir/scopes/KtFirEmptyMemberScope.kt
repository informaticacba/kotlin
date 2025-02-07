/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.scopes

import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.analysis.api.ValidityTokenOwner
import org.jetbrains.kotlin.analysis.api.scopes.KtDeclaredMemberScope
import org.jetbrains.kotlin.analysis.api.scopes.KtDelegatedMemberScope
import org.jetbrains.kotlin.analysis.api.scopes.KtMemberScope
import org.jetbrains.kotlin.analysis.api.scopes.KtScopeNameFilter
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithMembers
import org.jetbrains.kotlin.name.Name

internal class KtFirEmptyMemberScope(
    override val owner: KtSymbolWithMembers
) : KtMemberScope, KtDeclaredMemberScope, KtDelegatedMemberScope, ValidityTokenOwner {
    override fun getPossibleCallableNames(): Set<Name> = emptySet()

    override fun getPossibleClassifierNames(): Set<Name> = emptySet()

    override fun getCallableSymbols(nameFilter: KtScopeNameFilter): Sequence<KtCallableSymbol> =
        emptySequence()

    override fun getClassifierSymbols(nameFilter: KtScopeNameFilter): Sequence<KtClassifierSymbol> =
        emptySequence()

    override fun getConstructors(): Sequence<KtConstructorSymbol> =
        emptySequence()

    override fun mayContainName(name: Name): Boolean = false

    override val token: ValidityToken
        get() = owner.token
}
