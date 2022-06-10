package io.ktor.plugin.extension

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

const val BUILD_FAT_JAR_TASK_NAME = "buildFatJar"
const val BUILD_FAT_JAR_TASK_DESCRIPTION = "Builds a combined JAR of project and runtime dependencies."
const val FAT_JAR_EXTENSION_NAME = "fatJar"

fun configureFatJar(project: Project) {
    project.plugins.apply(ShadowPlugin::class.java)
    val tasks = project.tasks

    // We need to set `mainClassName` even if `mainClass` is set, because ShadowJar Plugin v6 needs it.
    // We can remove this block when we move to ShadowJar Plugin v7 or above.
    val shadowJar = tasks.withType(ShadowJar::class.java)
    val configureShadowJar = tasks.register("configureShadowJar") {
        it.doLast {
            if (project.findProperty("mainClassName") == null) {
                val application = project.extensions.getByType(JavaApplication::class.java)
                application.mainClass.orNull?.let { mainClassName ->
                    application.mainClassName = mainClassName
                }
            }
        }
    }

    val fatJarExtension = project.createKtorExtension<FatJarExtension>(FAT_JAR_EXTENSION_NAME)
    shadowJar.configureEach {
        fatJarExtension.archiveFileName?.let { name -> it.archiveFileName.set(name) }
        it.dependsOn(configureShadowJar)
    }

    tasks.registerKtorTask(BUILD_FAT_JAR_TASK_NAME, BUILD_FAT_JAR_TASK_DESCRIPTION) {
        dependsOn(shadowJar)
    }
}
