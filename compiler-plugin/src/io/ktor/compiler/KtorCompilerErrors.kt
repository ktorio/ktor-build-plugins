package io.ktor.compiler

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies

object KtorCompilerErrors {
    val FAILED_INFERENCE: KtDiagnosticFactory1<String> = KtDiagnosticFactory1(
        "KTOR_FAILED_INFERENCE",
        Severity.WARNING,
        SourceElementPositioningStrategies.DEFAULT,
        PsiElement::class
    )
}