package io.ktor.openapi.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrValueAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.util.isSubtypeOfClass
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

// val KTOR_PLUGIN_ORIGIN = IrDeclarationOrigin.GeneratedByPlugin("KTOR", null)

context(context: CodeGenContext)
fun IrCall.isApplicationCall() =
    receiverIsType("io.ktor.server.application.ApplicationCall")

context(context: CodeGenContext)
fun IrCall.receiverIsType(fqName: String): Boolean =
    context.referenceClass(ClassId.topLevel(FqName(fqName)))
        ?.let { classSymbol -> receiverIsType(classSymbol) }
        ?: false

fun IrCall.receiverIsType(
    classSymbol: IrClassSymbol,
): Boolean {
    val receiver = functionReceiver ?: return false
    return receiver.type.isSubtypeOfClass(classSymbol)
}

val IrCall.functionReceiver get() =
    symbol.owner.parameters.firstOrNull {
        it.kind == IrParameterKind.DispatchReceiver ||
            it.kind == IrParameterKind.ExtensionReceiver
    }

fun IrExpression.canMoveToParentScope(sourceLambda: IrFunction): Boolean {
    val localDeclarations = sourceLambda.collectLocalDeclarations()
    var hasLocalReferences = false

    acceptVoid(object : IrVisitorVoid() {
        override fun visitValueAccess(expression: IrValueAccessExpression) {
            if (expression.symbol.owner in localDeclarations) {
                hasLocalReferences = true
                return  // Stop traversing once we find a local reference
            }
            super.visitValueAccess(expression)
        }
    })

    return !hasLocalReferences
}

private fun IrFunction.collectLocalDeclarations(): Set<IrValueDeclaration> {
    return buildSet {
        // Add all function parameters
        addAll(parameters)

        // Add all local variables declared in the function body
        body?.acceptVoid(object : IrVisitorVoid() {
            override fun visitVariable(declaration: IrVariable) {
                add(declaration)
                super.visitVariable(declaration)
            }
        })
    }
}