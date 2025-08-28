package io.ktor.compiler.utils

import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.processAllProperties
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType

context(context: RouteStack)
fun getAllPropertiesFromType(
    coneType: ConeClassLikeType,
): List<FirCallableSymbol<*>> {
    val classSymbol = coneType.toSymbol(context.session) as? FirClassSymbol<*>
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
