package io.ktor.openapi.ir

import io.ktor.openapi.Logger
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext

interface CodeGenContext: Logger, IrPluginContext