package io.ktor.openapi.ir

import io.ktor.openapi.Logger
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFile

interface CodeGenContext: Logger, IrPluginContext {
    val irFile: IrFile?
}