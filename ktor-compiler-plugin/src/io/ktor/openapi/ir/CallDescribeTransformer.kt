package io.ktor.openapi.ir

import io.ktor.openapi.*
import io.ktor.openapi.ir.generators.*
import io.ktor.openapi.ir.inference.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Finds all route selector calls and chains `describe` calls with relevant details that can be found
 * at compile time.
 */
class CallDescribeTransformer(
    val logger: Logger,
    val pluginContext: IrPluginContext,
    val routes: RouteCallLookup,
    val handlerInferenceEnabled: Boolean,
) : IrElementTransformerVoid(),
    CodeGenContext,
    Logger by logger,
    IrPluginContext by pluginContext {

    companion object {
        const val DESCRIBE_FUNCTION_NAME = "describe"
        const val DESCRIBE_PACKAGE = "io.ktor.server.routing.openapi"
        const val OPENAPI_PACKAGE = "io.ktor.openapi"
    }

    private val callHandlerAnalyzer: CallHandlerAnalyzer =
        CallHandlerAnalyzer(
            IrCallHandlerInference.of(
                CallRespondInference,
                CallReceiveInference,
                ParameterInference,
                RequestHeaderInference,
                AppendResponseHeaderInference,
                ResponseHeaderExtensionInference,
                ResourceRouteCallInference,
            ), this)

    private val describeFunction: IrSimpleFunction by lazy {
        CallableId(
            packageName = FqName(DESCRIBE_PACKAGE),
            callableName = Name.identifier(DESCRIBE_FUNCTION_NAME)
        ).let { callableId ->
            pluginContext.referenceFunctions(callableId).single().owner
        }
    }

    // current file as defined by during traversal
    private var currentFile: IrFile? = null
    // required for building new declarations from function scopes
    private var functionStack = mutableListOf<IrFunction>()

    override val irFile: IrFile? get() = currentFile

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
        val route: RouteCall = routes[expression.coordinates()]
            ?: return super.visitCall(expression)

        // get the nearest declaration up the stack
        val currentFunction = functionStack.lastOrNull()
            ?: return super.visitCall(expression)

        return if (route.isLeaf) {
            // when handler inference is enabled,
            // scan the lambda body for route details
            val fields = route.fields.includeLambdaBody(expression)
            logger.log(buildString {
                append("ROUTE ${route.locationString()}; fields:")
                append(fields.joinToString("\n  - ", prefix = "\n  - "))
            })
            expression.chainDescribeCall(
                parentDeclaration = currentFunction,
                routeFields = fields
            )
        } else if (route.fields.isNotEmpty()) {
            // append the describe function from route fields and continue analysis
            super.visitCall(expression).chainDescribeCall(
                parentDeclaration = currentFunction,
                routeFields = route.fields
            )
        } else {
            // when there's nothing to add, just continue analysis
            super.visitCall(expression)
        }
    }

    /**
     * If handler inference is enabled, scan the lambda body for route details.
     */
    private fun RouteFieldList.includeLambdaBody(expression: IrCall): RouteFieldList =
        if (handlerInferenceEnabled) {
            val fieldsFromLambda = callHandlerAnalyzer.analyze(expression)
            merge(fieldsFromLambda)
        } else {
            this
        }

    private fun IrExpression.coordinates() =
        SourceKey(currentFile?.path, startOffset, endOffset)

    context(context: CodeGenContext)
    private fun IrExpression.chainDescribeCall(
        parentDeclaration: IrFunction,
        routeFields: RouteFieldList,
    ): IrExpression {
        if (RouteField.Ignore in routeFields || routeFields.isEmpty())
            return this
        val parameterFields = mutableListOf<RouteField.Parameter>()
        val responseFields = mutableListOf<RouteField>()
        val describeExpressionBuilder = GeneralDescribeExpressionGenerator(
            delegateParameterField = parameterFields::add,
            delegateResponseField = responseFields::add,
        )

        return chainBuilder(
            parentDeclaration = parentDeclaration,
            functionToCall = describeFunction,
        ) {
            describeExpressionBuilder.generate(routeFields)
            ParametersGenerator.generate(parameterFields)
            ResponsesGenerator.generate(responseFields)
        }
    }
}