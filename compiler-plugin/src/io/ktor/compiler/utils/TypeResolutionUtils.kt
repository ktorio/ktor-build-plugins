package io.ktor.compiler.utils

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.resolve.SupertypeSupplier
import org.jetbrains.kotlin.fir.resolve.TypeResolutionConfiguration
import org.jetbrains.kotlin.fir.resolve.typeResolver
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirQualifierPartImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeArgumentListImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.PrivateForInline

/**
 * Resolves a type from its string representation.
 *
 * @param context The checker context to use for type resolution
 * @param reference The string representation of the type to resolve (e.g., "String")
 * @return A ConeKotlinType for the resolved type, or null if the type couldn't be resolved
 */
@OptIn(PrivateForInline::class, SymbolInternals::class)
context(context: CheckerContext)
fun resolveTypeLink(reference: String): ConeKotlinType? {
    try {
        // For resolving, we still need to go through type resolver
        val firTypeRef = createUserTypeRefFromLink(context, reference) ?: return null

        val configuration = TypeResolutionConfiguration(
            scopes = context.scopeSession.scopes().values.flatMap { it.values }.filterIsInstance<FirScope>(),
            containingClassDeclarations = context.containingDeclarations.filterIsInstance<FirClass>(),
            useSiteFile = context.containingFile,
        )

        val resolvedTypeRefResult = context.session.typeResolver.resolveType(
            typeRef = firTypeRef,
            configuration = configuration,
            areBareTypesAllowed = true,
            isOperandOfIsOperator = false,
            resolveDeprecations = true,
            supertypeSupplier = SupertypeSupplier.Default,
        )
        return resolvedTypeRefResult.type
    } catch (e: Exception) {
        return null
    }
}


/**
 * Creates a FirUserTypeRef from a string representation of a type.
 *
 * Note, parameterized types are not accepted.
 */
private fun createUserTypeRefFromLink(
    ctx: CheckerContext,
    reference: String,
): FirUserTypeRef? {
    val containingSource = ctx.containingFile?.source ?: return null
    val ref = reference.trim()
    if (ref.isEmpty()) return null
    // Not supported here: parameterized or nullable types. Fail fast.
    if (ref.any { it == '<' || it == '>' || it == '?' }) return null
    // Allow qualified names, e.g., kotlin.String, java.time.Instant
    val parts = ref.split('.').filter { it.isNotBlank() }
    if (parts.isEmpty()) return null

    return buildUserTypeRef {
        source = containingSource.fakeElement(KtFakeSourceElementKind.Enhancement)
        isMarkedNullable = false
        for (p in parts) {
            qualifier += FirQualifierPartImpl(
                source = null,
                name = Name.identifier(p),
                typeArgumentList = FirTypeArgumentListImpl(null)
            )
        }
    }
}
