package io.ktor.plugin.extension

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

enum class OpenJdkVersion(val numeric: Int) {
    JDK_8(8), JDK_11(11), JDK_17(17)
}

abstract class DockerExtension {
    var jdkVersion = OpenJdkVersion.JDK_11
    var imageTag = "my-application"
}

fun configureDocker(project: Project) {
    project.createKtorExtension<DockerExtension>("docker")
    project.tasks.create("runDocker", RunDockerTask::class.java)
}

abstract class RunDockerTask : DefaultTask() {
    @TaskAction
    fun execute() {
        val dockerExtension = project.getKtorExtension<DockerExtension>()
        buildDockerfile(dockerExtension)
        val imageTag = dockerExtension.imageTag
        project.exec { exec -> exec.commandLine("docker", "build", "-t", imageTag, ".") }
        project.exec { exec -> exec.commandLine("docker", "run", "-p", "8080:8080", imageTag) }
    }

    private fun buildDockerfile(dockerExtension: DockerExtension) {
        val dockerfile = project.projectDir.resolve("Dockerfile")
        if (!dockerfile.exists()) {
            val jdkVersion = dockerExtension.jdkVersion.numeric
            dockerfile.writeText(
                """
                FROM gradle:7-jdk$jdkVersion AS build
                COPY --chown=gradle:gradle . /home/gradle/src
                WORKDIR /home/gradle/src
                RUN gradle buildFatJar --no-daemon

                FROM openjdk:$jdkVersion
                EXPOSE 8080:8080
                RUN mkdir /app
                COPY --from=build /home/gradle/src/build/libs/*.jar /app/fat.jar
                ENTRYPOINT ["java","-jar","/app/fat.jar"]
                """.trimIndent()
            )
        }
    }
}

