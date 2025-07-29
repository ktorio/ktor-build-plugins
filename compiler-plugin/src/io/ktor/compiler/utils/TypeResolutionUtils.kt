package io.ktor.compiler.utils

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.resolve.SupertypeSupplier
import org.jetbrains.kotlin.fir.resolve.TypeResolutionConfiguration
import org.jetbrains.kotlin.fir.resolve.transformers.FirTypeResolveTransformer
import org.jetbrains.kotlin.fir.resolve.typeResolver
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.coneTypeSafe
import org.jetbrains.kotlin.fir.types.hasError
import org.jetbrains.kotlin.fir.types.impl.FirQualifierPartImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeArgumentListImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.PrivateForInline

/**
 * Resolves a type from its string representation.
 *
 * @param context The checker context to use for type resolution
 * @param typeString The string representation of the type to resolve (e.g., "kotlin.String", "List<Int>")
 * @return A ConeKotlinType for the resolved type, or null if the type couldn't be resolved
 */
@OptIn(PrivateForInline::class, SymbolInternals::class)
fun resolveTypeFromString(
    context: CheckerContext,
    typeString: String,
): ConeKotlinType? {
    try {
        // For resolving, we still need to go through type resolver
        val firTypeRef = createUserTypeRefFromString(context, typeString) ?: return null

//        val scopeClassDeclaration = ScopeClassDeclaration(
//            scopes = context.scopeSession.scopes().values.flatMap { it.values }.filterIsInstance<FirScope>(),
//            containingDeclarations = context.containingDeclarations,
//            topContainer = null,
//            containerDeclaration = null,
//        )

        val resolveTypeTransformer = FirTypeResolveTransformer(
            session = context.session,
            scopeSession = context.scopeSession,
            initialScopes = context.scopeSession.scopes().values.flatMap { it.values }.filterIsInstance<FirScope>(),
            initialCurrentFile = context.containingFile,
            classDeclarationsStack = context.containingDeclarations.filterIsInstanceTo(ArrayDeque()),
        )

        val resolvedTypeRef = resolveTypeTransformer.transformTypeRef(firTypeRef, data = null)

        return resolvedTypeRef.coneType
    } catch (e: Exception) {
        return null
    }
}


/**
 * Creates a FirUserTypeRef from a string representation of a type.
 */
private fun createUserTypeRefFromString(
    context: CheckerContext,
    typeString: String,
): FirUserTypeRef? {
    // TODO generics
//    val parts = typeString.split(".")
//    val qualifierParts = parts.dropLast(1).map { part ->
//        buildQualifierPart {
//            name = Name.identifier(part)
//        }
//    }

//    val lastPart = parts.last()
//    val genericParts = if (lastPart.contains("<")) {
//        // Simple parsing for generic types like List<String>
//        val baseName = lastPart.substringBefore("<")
//        val genericParam = lastPart.substringAfter("<").substringBefore(">")
//
//        // Add the base name
//        val baseQualifier = buildQualifierPart {
//            name = Name.identifier(baseName)
//        }
//
//        // For simplicity, we're handling only simple cases here.
//        // For complex generic structures, you'd need more sophisticated parsing.
//        val typeArguments = listOf(
//            buildTypeProjectionWithTypeRef {
//                typeRef = createUserTypeRefFromString(session, genericParam)
//            }
//        )
//
//        qualifierParts + baseQualifier.copy(typeArguments = typeArguments)
//    } else {
//        // Simple type without generics
//        qualifierParts + buildQualifierPart {
//            name = Name.identifier(lastPart)
//        }
//    }

    val containingSource = context.containingFile?.source ?: return null

    return buildUserTypeRef {
        source = containingSource.fakeElement(KtFakeSourceElementKind.Enhancement)
        isMarkedNullable = false
        qualifier += FirQualifierPartImpl(
            source = null,
            name = Name.identifier(typeString),
            typeArgumentList = FirTypeArgumentListImpl(null)
        )
    }
}
