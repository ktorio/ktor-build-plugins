package io.ktor.plugin.features

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.ktor.plugin.internal.*
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider

public abstract class FatJarExtension internal constructor(project: Project) {
    /**
     * Specifies the fat jar archive name. Defaults to `"${project.name}-all.jar"`.
     */
    public val archiveFileName: Property<String> = project.property(defaultValue = "${project.name}-all.jar")
    public val allowZip64: Property<Boolean> = project.property(defaultValue = false)

    public companion object {
        public const val NAME: String = "fatJar"
    }
}

@Deprecated(
    "Use FatJarExtension.NAME instead",
    ReplaceWith(
        "FatJarExtension.NAME",
        "io.ktor.plugin.features.FatJarExtension"
    ),
)
public const val FAT_JAR_EXTENSION_NAME: String = FatJarExtension.NAME

public const val BUILD_FAT_JAR_TASK_NAME: String = "buildFatJar"
private const val BUILD_FAT_JAR_TASK_DESCRIPTION = "Builds a combined JAR of project and runtime dependencies."

public const val RUN_FAT_JAR_TASK_NAME: String = "runFatJar"
private const val RUN_FAT_JAR_TASK_DESCRIPTION =
    "Builds a combined JAR of project and runtime dependencies and runs it."

private const val SHADOW_RUN_TASK_NAME = "runShadow"
private const val SHADOW_JAR_TASK_NAME = "shadowJar"

internal fun Project.configureFatJar() {
    val fatJarExtension = createKtorExtension<FatJarExtension>(FatJarExtension.NAME)

    // Apply Shadow plugin only when the application plugin is applied.
    // TODO: KMP support will be added in Shadow 9.0.0
    //   https://github.com/GradleUp/shadow/pull/1333
    pluginManager.withPlugin(ApplicationPlugin.APPLICATION_PLUGIN_NAME) {
        apply<ShadowPlugin>()
    }

    // By using `withPlugin` we handle the case when a user explicitly applies Shadow plugin.
    pluginManager.withPlugin(SHADOW_PLUGIN_ID) {
        configureShadowPlugin(fatJarExtension)
    }
}

private fun Project.configureShadowPlugin(fatJarExtension: FatJarExtension) {
    val shadowJar: TaskProvider<ShadowJar> = tasks.named(SHADOW_JAR_TASK_NAME, ShadowJar::class.java) {
        it.archiveFileName.set(fatJarExtension.archiveFileName)
        it.isZip64 = fatJarExtension.allowZip64.get()
    }

    val buildFatJar = tasks.registerKtorTask(BUILD_FAT_JAR_TASK_NAME, BUILD_FAT_JAR_TASK_DESCRIPTION) {
        dependsOn(shadowJar)
    }

    val runShadow = tasks.named(SHADOW_RUN_TASK_NAME) {
        it.dependsOn(buildFatJar)
    }

    tasks.registerKtorTask(RUN_FAT_JAR_TASK_NAME, RUN_FAT_JAR_TASK_DESCRIPTION) {
        dependsOn(runShadow)
    }
}
