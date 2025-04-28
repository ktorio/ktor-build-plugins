package io.ktor.plugin.features

import io.ktor.plugin.features.KtorExtension.Companion.DEVELOPMENT_MODE_PROPERTY
import io.ktor.plugin.internal.*
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.ApplicationPlugin.APPLICATION_GROUP
import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

internal fun Project.configureApplication(extension: KtorExtension) {
    whenKotlinJvmApplied {
        configureJvmApplication(extension)
    }

    whenKotlinMultiplatformApplied {
        if (KotlinVersion.parse(getKotlinPluginVersion()) < KotlinVersion.V2_1_20) {
            configureJvmApplication(extension)
        } else {
            configureMultiplatformApplication(extension)
        }
    }
}

private fun Project.configureJvmApplication(extension: KtorExtension) {
    apply<ApplicationPlugin>()

    afterEvaluate {
        if (extension.development.get()) {
            application {
                applicationDefaultJvmArgs = applicationDefaultJvmArgs.withDevelopmentModeEnabled()
            }
        }
    }
}

private fun Project.configureMultiplatformApplication(extension: KtorExtension) {
    tasks.configureEach<JavaExec> { task ->
        val developmentModeArgument = provider {
            val shouldAddArgument = task.isAddedByKotlinPlugin() && extension.development.get()
            if (shouldAddArgument) listOf(DEVELOPMENT_MODE_ENABLED) else emptyList()
        }
        task.jvmArguments.addAll(developmentModeArgument)
    }
}

// See org.jetbrains.kotlin.gradle.targets.jvm.DefaultKotlinJvmBinariesDsl implementation
private fun JavaExec.isAddedByKotlinPlugin(): Boolean {
    return group == APPLICATION_GROUP && description?.startsWith("Run Kotlin") == true
}

private const val DEVELOPMENT_MODE_PREFIX = "-D$DEVELOPMENT_MODE_PROPERTY="
private const val DEVELOPMENT_MODE_ENABLED = DEVELOPMENT_MODE_PREFIX + "true"

private fun Iterable<String>.withDevelopmentModeEnabled(): Iterable<String> {
    return if (none { it.startsWith(DEVELOPMENT_MODE_PREFIX) }) {
        this + DEVELOPMENT_MODE_ENABLED
    } else {
        this
    }
}
