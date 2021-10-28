/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.descriptors.components

import org.jetbrains.kotlin.analysis.api.ImplicitReceiverSmartCast
import org.jetbrains.kotlin.analysis.api.ImplicitReceiverSmartcastKind
import org.jetbrains.kotlin.analysis.api.components.KtSmartCastProvider
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.descriptors.components.base.Fe10KtAnalysisSessionComponent
import org.jetbrains.kotlin.analysis.api.descriptors.symbols.descriptorBased.base.toKtType
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.smartcasts.MultipleSmartCasts
import org.jetbrains.kotlin.types.TypeIntersector

internal class Fe10KtSmartCastProvider(
    override val analysisSession: Fe10KtAnalysisSession
) : KtSmartCastProvider(), Fe10KtAnalysisSessionComponent {
    override val token: ValidityToken
        get() = analysisSession.token

    override fun getSmartCastedToType(expression: KtExpression): KtType? {
        withValidityAssertion {
            val bindingContext = analysisContext.analyze(expression)
            val smartCasts = bindingContext[BindingContext.SMARTCAST, expression] ?: return null

            if (smartCasts is MultipleSmartCasts) {
                return TypeIntersector.intersectTypes(smartCasts.map.values)?.toKtType(analysisContext)
            }

            return smartCasts.defaultType?.toKtType(analysisContext)
        }
    }

    override fun getImplicitReceiverSmartCast(expression: KtExpression): Collection<ImplicitReceiverSmartCast> {
        withValidityAssertion {
            val bindingContext = analysisContext.analyze(expression)
            val smartCasts = bindingContext[BindingContext.IMPLICIT_RECEIVER_SMARTCAST, expression] ?: return emptyList()
            return smartCasts.receiverTypes.map { (_, type) ->
                val kind = ImplicitReceiverSmartcastKind.DISPATCH // TODO provide precise kind
                ImplicitReceiverSmartCast(type.toKtType(analysisContext), kind)
            }
        }
    }
}