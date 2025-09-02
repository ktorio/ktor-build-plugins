package io.ktor.compiler.utils

import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.fir.analysis.checkers.fullyExpandedClassId
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.processAllProperties
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible

context(context: RouteStack)
fun getAllPropertiesFromType(
    coneType: ConeClassLikeType,
): List<FirCallableSymbol<*>> {
    // Normalize aliases and flexible types before resolving the symbol.
    val expanded = coneType
        .lowerBoundIfFlexible()           // peel off flexible types
        .fullyExpandedType(context.session) // expand typealiases

    val classSymbol =
        (expanded.toSymbol(context.session) as? FirClassSymbol<*>)
            ?: expanded
                .fullyExpandedClassId(context.session)
                ?.let {
                    context.session.symbolProvider
                        .getClassLikeSymbolByClassId(it) as? FirClassSymbol<*>
                }
            ?: return emptyList()

    val scope = classSymbol.unsubstitutedScope(
        context.session,
        context.scopeSession,
        withForcedTypeCalculator = true,
        memberRequiredPhase = FirResolvePhase.STATUS,
    )
    return buildList {
        scope.processAllProperties(::add)
    }
}
