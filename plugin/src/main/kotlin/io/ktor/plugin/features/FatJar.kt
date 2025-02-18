package io.ktor.plugin.features

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

abstract class FatJarExtension(project: Project) {
    /**
     * Specifies the fat jar archive name. Defaults to `"${project.name}-all.jar"`.
     */
    val archiveFileName = project.property(defaultValue = "${project.name}-all.jar")
    val allowZip64 = project.property(defaultValue = false)
}

const val FAT_JAR_EXTENSION_NAME = "fatJar"

const val BUILD_FAT_JAR_TASK_NAME = "buildFatJar"
private const val BUILD_FAT_JAR_TASK_DESCRIPTION = "Builds a combined JAR of project and runtime dependencies."

const val RUN_FAT_JAR_TASK_NAME = "runFatJar"
private const val RUN_FAT_JAR_TASK_DESCRIPTION =
    "Builds a combined JAR of project and runtime dependencies and runs it."

private const val SHADOW_RUN_TASK_NAME = "runShadow"
private const val SHADOW_JAR_TASK_NAME = "shadowJar"

internal fun Project.configureFatJar() {
    plugins.apply(ShadowPlugin::class.java)

    val fatJarExtension = createKtorExtension<FatJarExtension>(FAT_JAR_EXTENSION_NAME)
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
