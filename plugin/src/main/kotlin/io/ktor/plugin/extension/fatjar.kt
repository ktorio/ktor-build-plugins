package io.ktor.plugin.extension

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project

abstract class FatJarExtension {
    var archiveFileName: String? = null
}

fun configureFatJar(project: Project) {
    project.plugins.apply(ShadowPlugin::class.java)
    project.tasks.create("buildFatJar") { it.dependsOn("shadowJar"); }
    val fatJarExtension = project.createKtorExtension<FatJarExtension>("fatJar")
    project.tasks.withType(ShadowJar::class.java) {
        fatJarExtension.archiveFileName?.let { name -> it.archiveFileName.set(name) }
    }
}