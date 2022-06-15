package io.ktor.plugin.features

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project
import org.gradle.api.plugins.JavaApplication

abstract class FatJarExtension {
    /**
     * Specifies the fat jar archive name.
     */
    var archiveFileName: String? = null
}

const val FAT_JAR_EXTENSION_NAME = "fatJar"

const val BUILD_FAT_JAR_TASK_NAME = "buildFatJar"
private const val BUILD_FAT_JAR_TASK_DESCRIPTION = "Builds a combined JAR of project and runtime dependencies."

const val RUN_FAT_JAR_TASK_NAME = "runFatJar"
private const val RUN_FAT_JAR_TASK_DESCRIPTION =
    "Builds a combined JAR of project and runtime dependencies and runs it."

private const val CONFIGURE_SHADOW_JAR_TASK_NAME = "configureShadowJar"

fun configureFatJar(project: Project) {
    project.plugins.apply(ShadowPlugin::class.java)
    val tasks = project.tasks

    val configureShadowJar = tasks.register(CONFIGURE_SHADOW_JAR_TASK_NAME) {
        it.doLast {
            // We need to set `mainClassName` even if `mainClass` is set, because ShadowJar Plugin v6 needs it.
            // We can remove this block when we move to ShadowJar Plugin v7 or above.
            if (project.findProperty("mainClassName") == null) {
                val application = project.extensions.getByType(JavaApplication::class.java)
                application.mainClass.orNull?.let { mainClassName ->
                    application.mainClassName = mainClassName
                }
            }
        }
    }

    val fatJarExtension = project.createKtorExtension<FatJarExtension>(FAT_JAR_EXTENSION_NAME)
    val shadowJar = tasks.named("shadowJar", ShadowJar::class.java) {
        it.dependsOn(configureShadowJar)
        fatJarExtension.archiveFileName?.let { name -> it.archiveFileName.set(name) }
    }

    val buildFatJar = tasks.registerKtorTask(BUILD_FAT_JAR_TASK_NAME, BUILD_FAT_JAR_TASK_DESCRIPTION) {
        dependsOn(shadowJar)
    }

    tasks.registerKtorTask(RUN_FAT_JAR_TASK_NAME, RUN_FAT_JAR_TASK_DESCRIPTION) {
        dependsOn(buildFatJar)
        doLast {
            project.javaexec { java ->
                java.classpath(shadowJar.flatMap { it.archiveFile })
            }
        }
    }
}
