package io.ktor.plugin.features

import com.google.cloud.tools.jib.gradle.JibExtension
import com.google.cloud.tools.jib.gradle.JibPlugin
import com.google.cloud.tools.jib.gradle.JibTask
import com.google.cloud.tools.jib.gradle.TargetImageParameters
import org.gradle.api.*
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import javax.inject.Inject

enum class JreVersion(val javaVersion: JavaVersion) {
    JRE_1_8(JavaVersion.VERSION_1_8),
    JRE_11(JavaVersion.VERSION_11),
    JRE_17(JavaVersion.VERSION_17);

    val majorVersion = javaVersion.majorVersion.toInt()
}

@Suppress("MemberVisibilityCanBePrivate") // Provides a public API
abstract class DockerExtension(project: Project) {
    private companion object {
        private fun Provider<String>.zipWithTag(tag: Provider<String>): Provider<String> =
            zip(tag) { imageName, imageTag ->
                "$imageName:$imageTag"
            }
    }

    /**
     * Specifies the JRE version to use in the image. Defaults to [JreVersion.JRE_17].
     */
    val jreVersion = project.property(defaultValue = JreVersion.JRE_17)

    /**
     * Specifies a tag to use in the image. Defaults to `"latest"`.
     */
    val imageTag = project.property(defaultValue = "latest")

    /**
     * Specifies an image name for local builds. Defaults to `"ktor-docker-image"`.
     */
    val localImageName = project.property(defaultValue = "ktor-docker-image")

    /**
     * Specifies an external registry to push the image into. Default is not set.
     */
    val externalRegistry = project.property<DockerImageRegistry>(defaultValue = null)

    /**
     * Specifies an image name in form `"imageName:tag"` for a local registry.
     */
    val fullLocalImageName = localImageName.zipWithTag(imageTag)

    /**
     * Specifies an image name in form `"imageName:tag"` for an external registry.
     */
    val fullExternalImageName = externalRegistry.flatMap { it.toImage }.zipWithTag(imageTag)
}

interface DockerImageRegistry {
    /**
     * Specifies a link for [JibExtension.to.image][TargetImageParameters.image].
     */
    val toImage: Provider<String>

    /**
     * Specifies a username for a given registry.
     */
    val username: Provider<String>

    /**
     * Specifies a password for a given registry.
     */
    val password: Provider<String>

    companion object {
        /**
         * Creates a [DockerImageRegistry] for [DockerHub](https://hub.docker.com/)
         * from a given [appName], [username] and [password].
         */
        @Suppress("unused")
        fun dockerHub(
            appName: Provider<String>,
            username: Provider<String>,
            password: Provider<String>
        ): DockerImageRegistry = DockerHubRegistry(appName, username, password)

        /**
         * Creates a [DockerImageRegistry] for [Google Container Registry](https://cloud.google.com/container-registry)
         * from a given [appName] and [username].
         */
        @Suppress("unused")
        fun googleContainerRegistry(
            projectName: Provider<String>,
            appName: Provider<String>,
            username: Provider<String>,
            password: Provider<String>
        ): DockerImageRegistry = GoogleContainerRegistry(projectName, appName, username, password)
    }
}

private class DockerHubRegistry(
    appName: Provider<String>,
    override val username: Provider<String>,
    override val password: Provider<String>
) : DockerImageRegistry {
    override val toImage: Provider<String> = username.zip(appName) { user, app -> "$user/$app" }
}

private class GoogleContainerRegistry(
    projectName: Provider<String>,
    appName: Provider<String>,
    override val username: Provider<String>,
    override val password: Provider<String>
) : DockerImageRegistry {
    override val toImage: Provider<String> = projectName.zip(appName) { project, app -> "gcr.io/$project/$app" }
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

@Suppress("UnstableApiUsage")
private fun markJibTaskNotCompatible(task: Task) = task.notCompatibleWithConfigurationCache(
    "JIB plugin is not compatible with the configuration cache. " +
            "See https://github.com/GoogleContainerTools/jib/issues/3132"
)

private fun registerSetupJibTask(
    project: Project,
    taskName: String,
    setupExternalRegistry: Boolean
): TaskProvider<Task> = project.tasks.register(taskName) {
    val jibExtension = project.extensions.getByType(JibExtension::class.java)
    val dockerExtension = project.getKtorExtension<DockerExtension>()
    jibExtension.from.setImage(dockerExtension.jreVersion.map { "eclipse-temurin:${it.majorVersion}-jre" })

    if (setupExternalRegistry) {
        val externalRegistry = dockerExtension.externalRegistry
        jibExtension.to.setImage(dockerExtension.fullExternalImageName)
        jibExtension.to.auth.setUsername(externalRegistry.flatMap { it.username })
        jibExtension.to.auth.setPassword(externalRegistry.flatMap { it.password })
    } else {
        jibExtension.to.setImage(dockerExtension.fullLocalImageName)
    }

    // Eagerly check for incompatible Java versions to show a meaningful error instead of a JIB's one.
    val imageJava = dockerExtension.jreVersion.get().javaVersion
    val projectJava = project.javaVersion
    if (imageJava < projectJava) {
        throw GradleException(
            "You're trying to build an image with JRE $imageJava while your project's JDK or 'targetCompatibility' is $projectJava. " +
                    "Please use a higher version of an image JRE through the 'ktor.docker.jreVersion' extension in the build file, " +
                    "or set the 'targetCompatibility' property to a lower version."
        )
    }
}

private abstract class RunDockerTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Input
    abstract val fullImageName: Property<String>

    @TaskAction
    fun execute() {
        execOperations.exec {
            it.commandLine("docker", "run", "-p", "8080:8080", fullImageName.get())
        }
    }
}

fun configureDocker(project: Project) {
    project.plugins.apply(JibPlugin::class.java)
    val dockerExtension = project.createKtorExtension<DockerExtension>(DOCKER_EXTENSION_NAME)
    val tasks = project.tasks

    tasks.withType(JibTask::class.java).configureEach(::markJibTaskNotCompatible)

    val setupJibLocalTask = registerSetupJibTask(project, SETUP_JIB_LOCAL_TASK_NAME, setupExternalRegistry = false)
    val setupJibExternalTask = registerSetupJibTask(project, SETUP_JIB_EXTERNAL_TASK_NAME, setupExternalRegistry = true)
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

    tasks.registerKtorTask<RunDockerTask>(
        RUN_DOCKER_TASK_NAME,
        "Builds a project's image to a Docker daemon and runs it."
    ) {
        fullImageName.set(dockerExtension.fullLocalImageName)
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
