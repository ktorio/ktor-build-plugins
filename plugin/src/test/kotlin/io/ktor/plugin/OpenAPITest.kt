package io.ktor.plugin

import io.ktor.plugin.features.*
import io.ktor.plugin.internal.*
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import kotlin.test.Test

class OpenAPITest {
    private val project = createProject()

    @Test
    fun `can configure openapi plugin`() {
        project.applyKtorPlugin {
            getExtension<OpenAPIExtension>().enabled.set(true)
        }
        val compile = project.tasks.withType<KotlinJvmCompile>().first()
    }
}