package io.ktor.compiler.utils

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.processAllProperties
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType

fun CheckerContext.getAllPropertiesFromType(
    coneType: ConeKotlinType,
): List<FirCallableSymbol<*>> {
    val classSymbol = coneType.toSymbol(session) as? FirClassSymbol<*>
        ?: return emptyList()

    // TODO generics require substitution w/ type params
    val scope = classSymbol.unsubstitutedScope(this)
    return buildList {
        scope.processAllProperties(::add)
    }
}
