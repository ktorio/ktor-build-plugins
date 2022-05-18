package io.ktor.plugin.extension

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project

abstract class FatJarExtension {
    var archiveFileName: String? = null
}

fun configureFatJar(project: Project) {
    project.plugins.apply(ShadowPlugin::class.java)
    val shadowJar = project.tasks.withType(ShadowJar::class.java)
    project.tasks.register("buildFatJar") { it.dependsOn(shadowJar) }
    val fatJarExtension = project.createKtorExtension<FatJarExtension>("fatJar")
    shadowJar.configureEach {
        fatJarExtension.archiveFileName?.let { name -> it.archiveFileName.set(name) }
    }
}