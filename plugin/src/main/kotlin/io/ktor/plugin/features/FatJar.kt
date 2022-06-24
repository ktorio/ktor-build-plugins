package io.ktor.plugin.features

import com.github.jengelman.gradle.plugins.shadow.ShadowApplicationPlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.TaskContainer

abstract class FatJarExtension(project: Project) {
    /**
     * Specifies the fat jar archive name. Defaults to `"${project.name}-all.jar"`.
     */
    val archiveFileName = project.property(defaultValue = "${project.name}-all.jar")
}

const val FAT_JAR_EXTENSION_NAME = "fatJar"

const val BUILD_FAT_JAR_TASK_NAME = "buildFatJar"
private const val BUILD_FAT_JAR_TASK_DESCRIPTION = "Builds a combined JAR of project and runtime dependencies."

const val RUN_FAT_JAR_TASK_NAME = "runFatJar"
private const val RUN_FAT_JAR_TASK_DESCRIPTION =
    "Builds a combined JAR of project and runtime dependencies and runs it."

/**
 * We need to set `mainClassName` even if `mainClass` is set, because ShadowJar Plugin v6 needs it.
 * We can remove this function when we move to ShadowJar Plugin v7 or above.
 */
private fun configureMainClass(project: Project) {
    val application = project.extensions.getByType(JavaApplication::class.java)
    val mainClassName = application.mainClass.orNull
        ?: throw GradleException("You should point application.mainClass to your main class in order to build a Fat JAR")
    application.mainClassName = mainClassName
}

@Suppress("UnstableApiUsage")
private fun Task.shadowTaskIsNotCompatibleWithConfigurationCache(taskName: String) {
    notCompatibleWithConfigurationCache(
        "`$taskName` is not compatible yet: " +
                "https://github.com/johnrengelman/shadow/issues/775"
    )
}

private val INCOMPATIBLE_SHADOW_TASK_NAMES = arrayOf(
    ShadowApplicationPlugin.getSHADOW_INSTALL_TASK_NAME(),
    ShadowApplicationPlugin.getSHADOW_RUN_TASK_NAME()
)

private fun markShadowTasksAsNotCompatibleWithConfigurationCache(tasks: TaskContainer) {
    INCOMPATIBLE_SHADOW_TASK_NAMES.forEach { taskName ->
        tasks.named(taskName) {
            it.shadowTaskIsNotCompatibleWithConfigurationCache(taskName)
        }
    }
}

fun configureFatJar(project: Project) {
    project.plugins.apply(ShadowPlugin::class.java)
    val tasks = project.tasks

    markShadowTasksAsNotCompatibleWithConfigurationCache(tasks)

    val fatJarExtension = project.createKtorExtension<FatJarExtension>(FAT_JAR_EXTENSION_NAME)
    val shadowJar = tasks.named(ShadowJavaPlugin.getSHADOW_JAR_TASK_NAME(), ShadowJar::class.java) {
        configureMainClass(project)
        it.archiveFileName.set(fatJarExtension.archiveFileName)
    }

    val buildFatJar = tasks.registerKtorTask(BUILD_FAT_JAR_TASK_NAME, BUILD_FAT_JAR_TASK_DESCRIPTION) {
        dependsOn(shadowJar)
    }

    val runShadow = tasks.named(ShadowApplicationPlugin.getSHADOW_RUN_TASK_NAME()) {
        it.dependsOn(buildFatJar)
    }

    tasks.registerKtorTask(RUN_FAT_JAR_TASK_NAME, RUN_FAT_JAR_TASK_DESCRIPTION) {
        dependsOn(runShadow)
    }
}
