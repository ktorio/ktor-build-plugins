package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.*
import io.ktor.openapi.model.JsonSchema.Companion.asJsonSchema
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RoutingFunctionConstants.HTTP_METHODS
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class ResourceRoutingCallInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isResourceRouteFunction(expression)) return RoutingReferenceResult.None
        val invocation = expression.getLocation() ?: return RoutingReferenceResult.None

        val routeNode = RouteNode.Route(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                // Extract the resource type from the type parameters
                val resourceType = expression.typeArguments.firstOrNull()?.toConeTypeProjection()?.resolveType() ?: return@Route emptyList()
                val fullPath = getFullResourcePath(resourceType) ?: return@Route emptyList()

                buildList {
                    // kDoc fields
                    addAll(invocation.parseKDoc().resolveSchemaReferences())

                    // path from @Resource annotation
                    add(RouteField.Path(fullPath))

                    // method from function name
                    getMethod(expression)?.let {
                        add(RouteField.Method(it))
                    }

                    val pathParamKeys = Regex("\\{([^}]+)}")
                        .findAll(fullPath)
                        .map { it.groupValues[1] }.toSet()

                    // Add path parameters
                    getPathParameters(resourceType, pathParamKeys).forEach { paramInfo ->
                        add(RouteField.PathParam(
                            name = paramInfo.name,
                            schema = paramInfo.schema
                        ))
                    }

                    // Add query parameters
                    getQueryParameters(resourceType, pathParamKeys).forEach { paramInfo ->
                        add(RouteField.QueryParam(
                            name = paramInfo.name,
                            schema = paramInfo.schema
                        ))
                    }
                }
            },
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun isResourceRouteFunction(call: FirFunctionCall): Boolean =
        call.isInPackage("io.ktor.server.resources") &&
                call.getFunctionName() in HTTP_METHODS &&
                call.typeArguments.isNotEmpty()

    /**
     * Gets the full path by traversing the resource hierarchy
     */
    context(checker: CheckerContext, stack: RouteStack, reporter: DiagnosticReporter)
    private fun getFullResourcePath(resourceType: ConeKotlinType): String? {
        val paths = buildList {
            var currentType: ConeKotlinType? = resourceType

            while (currentType != null) {
                val classId = currentType.classId ?: break
                val resourceClass = stack.session.symbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol ?: break

                // Get the path from the Resource annotation
                val annotation = resourceClass.getAnnotationByClassId(ClassId(
                    FqName("io.ktor.server.resources"),
                    Name.identifier("Resource")
                ), checker.session) ?: break

                val path = annotation.argumentMapping.mapping.entries
                    .firstOrNull { it.key.asString() == "path" }
                    ?.value?.evaluate()?.asString() ?: ""

                add(0, path)

                // Find parent field if it exists
                currentType = findParentResourceType(resourceClass)
            }
        }

        // Combine paths, handling leading/trailing slashes correctly
        return if (paths.isEmpty()) null else {
            paths.joinToString("") { segment ->
                if (segment.isEmpty() || segment == "/") "" else {
                    if (segment.startsWith("/")) segment else "/$segment"
                }
            }
        }
    }

    /**
     * Finds the parent resource type if this resource has a parent field
     */
    context(stack: RouteStack)
    private fun findParentResourceType(resourceClass: FirRegularClassSymbol): ConeKotlinType? {
        // Look for a constructor parameter named "parent"
        val primaryConstructor = resourceClass.primaryConstructorIfAny(stack.session) ?: return null
        val parentParameter = primaryConstructor.valueParameterSymbols.find { it.name.asString() == "parent" } ?: return null
        
        return parentParameter.resolvedReturnType
    }

    /**
     * Extracts path parameters from the resource class
     */
    context(stack: RouteStack)
    private fun getPathParameters(resourceType: ConeKotlinType, pathParams: Set<String>): List<ParameterInfo> {
        val result = mutableListOf<ParameterInfo>()
        var currentType: ConeKotlinType? = resourceType
        while (currentType != null) {
            val classId = currentType.classId ?: break
            val resourceClass = stack.session.symbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol ?: break
            
            // Check constructor parameters for matches with path parameters
            val primaryConstructor = resourceClass.primaryConstructorIfAny(stack.session) ?: break
            for (param in primaryConstructor.valueParameterSymbols) {
                val paramName = param.name.asString()
                if (paramName != "parent" && pathParams.contains(paramName)) {
                    result.add(
                        ParameterInfo(
                            name = paramName,
                            schema = SchemaReference.Resolved(param.resolvedReturnType.asJsonSchema(fullSchema = false)),
                            hasDefault = param.hasDefaultValue
                        )
                    )
                }
            }
            
            currentType = findParentResourceType(resourceClass)
        }
        
        return result
    }

    /**
     * Extracts query parameters from the resource class
     */
    @OptIn(SymbolInternals::class)
    context(stack: RouteStack)
    private fun getQueryParameters(resourceType: ConeKotlinType, pathParams: Set<String>): List<ParameterInfo> {
        val result = mutableListOf<ParameterInfo>()
        var currentType: ConeKotlinType? = resourceType
        while (currentType != null) {
            val classId = currentType.classId ?: break
            val resourceClass = stack.session.symbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol ?: break
            
            // Check constructor parameters for non-path parameters (these are query parameters)
            val primaryConstructor = resourceClass.primaryConstructorIfAny(stack.session) ?: break
            for (param in primaryConstructor.valueParameterSymbols) {
                val paramName = param.name.asString()
                if (paramName != "parent" && !pathParams.contains(paramName)) {
                    result.add(
                        ParameterInfo(
                            name = paramName,
                            schema = SchemaReference.Resolved(param.resolvedReturnType.asJsonSchema(fullSchema = false)),
                            hasDefault = param.hasDefaultValue
                        )
                    )
                }
            }
            
            currentType = findParentResourceType(resourceClass)
        }
        
        return result
    }

    /**
     * Gets the HTTP method from the function name
     */
    context(stack: RouteStack)
    private fun getMethod(expression: FirFunctionCall): String? =
        expression.getFunctionName().takeIf { it in HTTP_METHODS }
        
    /**
     * Helper class to store parameter information
     */
    private data class ParameterInfo(
        val name: String,
        val schema: SchemaReference,
        val hasDefault: Boolean
    )
}