package io.ktor.plugin.extension

import com.google.cloud.tools.jib.gradle.JibExtension
import com.google.cloud.tools.jib.gradle.JibPlugin
import org.gradle.api.Project

enum class JreVersion(val numeric: Int) {
    JRE_8(8), JRE_11(11), JRE_17(17)
}

abstract class DockerExtension {
    var jreVersion = JreVersion.JRE_11
    var imageTag = "latest"
    var imageName = "ktor-docker-image"
}

private const val DOCKER_EXTENSION = "docker"
private const val JIB_BUILD_DOCKER = JibPlugin.BUILD_DOCKER_TASK_NAME
private const val JIB_BUILD_TAR = JibPlugin.BUILD_TAR_TASK_NAME
private const val SETUP_DOCKER = "setupDocker"
private const val RUN_DOCKER = "runDocker"
private const val PUBLISH_IMAGE_TO_LOCAL_REGISTRY = "publishImageToLocalRegistry"
private const val BUILD_IMAGE = "buildImage"

fun configureDocker(project: Project) {
    project.createKtorExtension<DockerExtension>(DOCKER_EXTENSION)
    project.plugins.apply(JibPlugin::class.java)
    project.tasks.create(SETUP_DOCKER) {
        it.doLast {
            val jibExtension = project.extensions.getByType(JibExtension::class.java)
            val dockerExtension = project.getKtorExtension<DockerExtension>()
            jibExtension.from.image = "eclipse-temurin:${dockerExtension.jreVersion.numeric}-jre"
            jibExtension.to.tags = setOfNotNull(dockerExtension.imageTag)
            jibExtension.to.image = dockerExtension.imageName
        }
    }
    project.tasks.create(RUN_DOCKER) {
        it.dependsOn(JIB_BUILD_DOCKER)
        it.doLast {
            project.exec { exec ->
                val dockerExtension = project.getKtorExtension<DockerExtension>()
                val fullImageName = dockerExtension.imageName + ":" + dockerExtension.imageTag
                exec.commandLine(listOf("docker", "run", "-p", "8080:8080", fullImageName))
            }
        }
    }
    project.tasks.named(JIB_BUILD_DOCKER) {
        it.dependsOn(SETUP_DOCKER)
    }
    project.tasks.named(JIB_BUILD_TAR) {
        it.dependsOn(SETUP_DOCKER)
    }
    project.tasks.create(PUBLISH_IMAGE_TO_LOCAL_REGISTRY) {
        it.dependsOn(JIB_BUILD_DOCKER)
    }
    project.tasks.create(BUILD_IMAGE) {
        it.dependsOn(JIB_BUILD_TAR)
    }
}