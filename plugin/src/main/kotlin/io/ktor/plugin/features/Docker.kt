package io.ktor.plugin.features

import com.google.cloud.tools.jib.gradle.JibExtension
import com.google.cloud.tools.jib.gradle.JibPlugin
import com.google.cloud.tools.jib.gradle.TargetImageParameters
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

@Suppress("unused")
enum class JreVersion(val numeric: Int) {
    JRE_8(8), JRE_11(11), JRE_17(17)
}

abstract class DockerExtension {
    /**
     * Specifies the JRE version to use in the image. Defaults to [JreVersion.JRE_11].
     */
    var jreVersion = JreVersion.JRE_11

    /**
     * Specifies a tag to use in the image. Defaults to `"latest"`.
     */
    var imageTag = "latest"

    /**
     * Specifies an image name for local builds. Defaults to `"ktor-docker-image"`.
     */
    var localImageName = "ktor-docker-image"

    /**
     * Specifies an external registry to push the image into. Default is not set.
     */
    var externalRegistry: DockerImageRegistry? = null
}

interface DockerImageRegistry {
    /**
     * Specifies a link for [JibExtension.to.image][TargetImageParameters.image].
     */
    val toImage: String

    /**
     * Specifies a username for a given registry.
     */
    val username: String

    /**
     * Specifies a password for a given registry.
     */
    val password: String

    companion object {
        /**
         * Creates a [DockerImageRegistry] for [DockerHub](https://hub.docker.com/)
         * from a given [appName], [username] and [password].
         */
        @Suppress("unused")
        fun dockerHub(
            appName: String,
            username: String,
            password: String
        ): DockerImageRegistry = DockerHubRegistry(appName, username, password)

        /**
         * Creates a [DockerImageRegistry] for [Google Container Registry](https://cloud.google.com/container-registry)
         * from a given [appName] and [username].
         */
        @Suppress("unused")
        fun googleContainerRegistry(
            projectName: String,
            appName: String,
            username: String,
            password: String
        ): DockerImageRegistry = GoogleContainerRegistry(projectName, appName, username, password)
    }
}

private class DockerHubRegistry(
    appName: String,
    override val username: String,
    override val password: String
) : DockerImageRegistry {
    override val toImage: String = "$username/$appName"
}

private class GoogleContainerRegistry(
    projectName: String,
    appName: String,
    override val username: String,
    override val password: String
) : DockerImageRegistry {
    override val toImage: String = "gcr.io/$projectName/$appName"
}

private const val DOCKER_EXTENSION_NAME = "docker"

// JIB related tasks
private const val JIB_BUILD_INTO_LOCAL_DOCKER_TASK_NAME = JibPlugin.BUILD_DOCKER_TASK_NAME
private const val JIB_BUILD_INTO_TAR_TASK_NAME = JibPlugin.BUILD_TAR_TASK_NAME
private const val JIB_BUILD_IMAGE_AND_PUBLISH_TASK_NAME = JibPlugin.BUILD_IMAGE_TASK_NAME

// Ktor related tasks
const val PUBLISH_IMAGE_TO_LOCAL_REGISTRY_TASK_NAME = "publishImageToLocalRegistry"
const val PUBLISH_IMAGE_TO_EXTERNAL_REGISTRY_TASK_NAME = "publishImage"
const val BUILD_IMAGE_TASK_NAME = "buildImage"
const val RUN_DOCKER_TASK_NAME = "runDocker"

// Ktor configuration tasks
private const val SETUP_JIB_LOCAL_TASK_NAME = "setupJibLocal"
private const val SETUP_JIB_EXTERNAL_TASK_NAME = "setupJibExternal"

private abstract class SetupJibTask : DefaultTask() {
    @get:Input
    abstract val setupExternalRegistry: Property<Boolean>

    init {
        @Suppress("LeakingThis")
        setupExternalRegistry.convention(false)
    }

    @TaskAction
    fun execute() {
        val jibExtension = project.extensions.getByType(JibExtension::class.java)
        val dockerExtension = project.getKtorExtension<DockerExtension>()
        jibExtension.from.image = "eclipse-temurin:${dockerExtension.jreVersion.numeric}-jre"
        jibExtension.to.image = dockerExtension.localImageName + ":" + dockerExtension.imageTag

        if (setupExternalRegistry.get()) {
            val externalRegistry = requireNotNull(dockerExtension.externalRegistry) {
                throw RuntimeException("External registry is not set")
            }
            jibExtension.to.image = externalRegistry.toImage + ":" + dockerExtension.imageTag
            jibExtension.to.auth.username = externalRegistry.username
            jibExtension.to.auth.password = externalRegistry.password
        }
    }
}

private abstract class RunDockerTask : DefaultTask() {
    @TaskAction
    fun execute() {
        project.exec { exec ->
            val dockerExtension = project.getKtorExtension<DockerExtension>()
            val fullImageName = dockerExtension.localImageName + ":" + dockerExtension.imageTag
            exec.commandLine("docker", "run", "-p", "8080:8080", fullImageName)
        }
    }
}

fun configureDocker(project: Project) {
    project.createKtorExtension<DockerExtension>(DOCKER_EXTENSION_NAME)
    project.plugins.apply(JibPlugin::class.java)

    val tasks = project.tasks

    val setupJibLocalTask = tasks.register(SETUP_JIB_LOCAL_TASK_NAME, SetupJibTask::class.java) {
        it.setupExternalRegistry.set(false)
    }
    val setupJibExternalTask = tasks.register(SETUP_JIB_EXTERNAL_TASK_NAME, SetupJibTask::class.java) {
        it.setupExternalRegistry.set(true)
    }
    val setupJibTasks = arrayOf(setupJibLocalTask, setupJibExternalTask)

    val jibBuildIntoLocalDockerTask = tasks.named(JIB_BUILD_INTO_LOCAL_DOCKER_TASK_NAME)
    val jibBuildIntoTarTask = tasks.named(JIB_BUILD_INTO_TAR_TASK_NAME)
    val jibBuildImageAndPublishTask = tasks.named(JIB_BUILD_IMAGE_AND_PUBLISH_TASK_NAME)
    val jibTasks = arrayOf(jibBuildIntoLocalDockerTask, jibBuildIntoTarTask, jibBuildImageAndPublishTask)

    for (jibTask in jibTasks) {
        jibTask.configure {
            it.mustRunAfter(*setupJibTasks)
        }
    }

    tasks.registerKtorTask(
        RUN_DOCKER_TASK_NAME,
        "Builds a project's image to a Docker daemon and runs it.",
        RunDockerTask::class
    ) {
        dependsOn(
            setupJibLocalTask,
            jibBuildIntoLocalDockerTask
        )
    }

    tasks.registerKtorTask(
        PUBLISH_IMAGE_TO_LOCAL_REGISTRY_TASK_NAME,
        "Builds and publishes a project's Docker image to a local registry."
    ) {
        dependsOn(
            setupJibLocalTask,
            jibBuildIntoLocalDockerTask
        )
    }

    tasks.registerKtorTask(
        PUBLISH_IMAGE_TO_EXTERNAL_REGISTRY_TASK_NAME,
        "Builds and publishes a project's Docker image to an external registry."
    ) {
        dependsOn(
            setupJibExternalTask,
            jibBuildImageAndPublishTask
        )
    }

    tasks.registerKtorTask(
        BUILD_IMAGE_TASK_NAME,
        "Builds a project's Docker image to a tarball."
    ) {
        dependsOn(
            setupJibLocalTask,
            jibBuildIntoTarTask
        )
    }
}