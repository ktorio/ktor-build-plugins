package io.ktor.openapi.ir

import io.ktor.openapi.Logger
import io.ktor.openapi.ir.generators.GeneralAnnotateExpressionGenerator
import io.ktor.openapi.ir.generators.ParametersGenerator
import io.ktor.openapi.ir.generators.ResponsesGenerator
import io.ktor.openapi.ir.interpreters.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Finds all route selector calls and chains `annotate` calls with relevant details that can be found
 * at compile time.
 *
 * TODO follow functions that pass call/response/request as args
 */
class CallAnnotateTransformer(
    val logger: Logger,
    val pluginContext: IrPluginContext,
    val routes: RouteCallLookup,
) : IrElementTransformerVoid(),
    CodeGenContext,
    Logger by logger,
    IrPluginContext by pluginContext {

    companion object {
        const val ANNOTATE_PACKAGE = "io.ktor.annotate"
        const val OPENAPI_PACKAGE = "io.ktor.openapi"
        const val ANNOTATE_FUNCTION_NAME = "annotate"
    }

    private val callHandlerAnalyzer: CallHandlerAnalyzer =
        CallHandlerAnalyzer(
            IrCallHandlerInterpreter.of(
                CallRespondInterpreter,
                CallReceiveInterpreter,
                ParameterInterpreter,
                RequestHeaderExtensionInterpreter,
                AppendResponseHeaderInterpreter,
                ResponseHeaderExtensionInterpreter,
                ResourceRouteCallInterpreter,
            ),
            this,
        )

    private val annotateFunction: IrSimpleFunction by lazy {
        CallableId(
            packageName = FqName(ANNOTATE_PACKAGE),
            callableName = Name.identifier(ANNOTATE_FUNCTION_NAME)
        ).let { callableId ->
            pluginContext.referenceFunctions(callableId)
                .single() // or use .first() / .firstOrNull() with additional filtering
                .owner
        }
    }

    // current file as defined by during traversal
    private var currentFile: IrFile? = null
    // required for building new declarations from function scopes
    private var functionStack = mutableListOf<IrFunction>()

    override fun visitFile(declaration: IrFile): IrFile {
        try {
            currentFile = declaration
            return super.visitFile(declaration)
        } finally {
            currentFile = null
        }
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        try {
            functionStack.add(declaration)
            return super.visitFunction(declaration)
        } finally {
            functionStack.removeLast()
        }
    }

    /**
     * Check our cache for routing details for the call location, as populated from the FIR analysis of the KDoc.
     *
     * If this is a match, we can populate more details from the lambda argument by looking for any call
     * references, like `call.respond(...)`, for example.
     */
    override fun visitCall(expression: IrCall): IrExpression {
        // check for route info from the FIR analysis step
        val route = routes[expression.coordinates()]
            ?: return super.visitCall(expression)

        // get the nearest declaration up the stack
        val currentFunction = functionStack.lastOrNull()
            ?: return super.visitCall(expression)

        return if (route.isLeaf) {
            // scans the lambda body for any useful call references
            val fieldsFromLambda = callHandlerAnalyzer.analyze(expression)
            expression.chainAnnotationCall(
                parentDeclaration = currentFunction,
                routeFields = route.fields.merge(fieldsFromLambda)
            )
        } else if (route.fields.isNotEmpty()) {
            // append the annotate function from route fields and continue analysis
            super.visitCall(expression).chainAnnotationCall(
                parentDeclaration = currentFunction,
                routeFields = route.fields
            )
        } else {
            // when there's nothing to add, just continue analysis
            super.visitCall(expression)
        }
    }

    private fun IrExpression.coordinates() =
        SourceKey(currentFile?.path, startOffset, endOffset)

    context(context: CodeGenContext)
    private fun IrExpression.chainAnnotationCall(
        parentDeclaration: IrFunction,
        routeFields: RouteFieldList,
    ): IrExpression {
        if (RouteField.Ignore in routeFields || routeFields.isEmpty())
            return this
        val parameterFields = mutableListOf<RouteField.Parameter>()
        val responseFields = mutableListOf<RouteField>()
        val annotateExpressionBuilder = GeneralAnnotateExpressionGenerator(
            delegateParameterField = parameterFields::add,
            delegateResponseField = responseFields::add,
        )

        return chainBuilder(
            parentDeclaration = parentDeclaration,
            functionToCall = annotateFunction,
        ) {
            annotateExpressionBuilder.generate(routeFields)
            ParametersGenerator.generate(parameterFields)
            ResponsesGenerator.generate(responseFields)
        }
    }
}