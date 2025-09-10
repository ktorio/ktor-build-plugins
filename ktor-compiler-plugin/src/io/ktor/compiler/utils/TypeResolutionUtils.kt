package io.ktor.compiler.utils

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.constructStarProjectedType
import org.jetbrains.kotlin.name.ClassId

context(context: CheckerContext)
fun resolveTypeLink(reference: String): ConeClassLikeType? =
    resolveTypeSymbol(reference)?.constructStarProjectedType(isMarkedNullable = false)

context(context: CheckerContext)
fun resolveTypeSymbol(reference: String): FirClassSymbol<*>? {
    try {
        // Parse the reference to a ClassId
        val classId = ClassId.fromString(reference)

        // Use the session to resolve the class symbol
        return context.session.symbolProvider.getClassLikeSymbolByClassId(classId) as? FirClassSymbol<*>
    } catch (e: Exception) {
        // If there's any error during parsing or resolution, return null
        return null
    }
}
